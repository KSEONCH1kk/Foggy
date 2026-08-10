package dev.foggy.raycast;

import dev.foggy.camera.CameraPose;
import dev.foggy.config.FoggyConfig;
import dev.foggy.math.FrustumMath;
import dev.foggy.visibility.PlayerVisibilitySnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

/**
 * Allocation-light visibility tests over {@link CompensatedWorld}'s exact Minecraft shapes.
 *
 * <p>The hot path performs one Folia ownership check for the complete camera/target envelope,
 * rather than one check and one CraftWorld/NMS traversal per ray. An unchanged viewer/target pair
 * reuses its result until either entity geometry, camera state, or the compensated-world revision
 * changes. The debug path deliberately bypasses decision memoization so its measurements and hit
 * descriptions always describe the current tick.</p>
 */
public final class CompensatedRaycastService implements RaycastService {
    private static final int MAX_DEBUG_BLOCKED_RAYS = 8;
    private static final int DECISION_PRUNE_INTERVAL_TICKS = 200;
    private final FoggyConfig config;
    private final VanillaBlockRaycaster blockRaycaster;
    private final ConcurrentMap<DecisionKey, CachedDecision> decisions = new ConcurrentHashMap<>();
    private final AtomicInteger lastDecisionPruneTick = new AtomicInteger();
    private final LongAdder decisionHits = new LongAdder();
    private final LongAdder decisionMisses = new LongAdder();

    /**
     * Creates the production raycast service.
     *
     * @param config FOV, endpoint and cache settings
     * @param blockRaycaster compensated block geometry facade
     */
    public CompensatedRaycastService(FoggyConfig config, VanillaBlockRaycaster blockRaycaster) {
        this.config = config;
        this.blockRaycaster = blockRaycaster;
    }

    @Override
    public OpticalResult evaluate(PlayerVisibilitySnapshot viewer, PlayerVisibilitySnapshot target,
                                  List<CameraPose> cameras) {
        List<Vector> targetPoints = target.targetPoints();
        if (!ownsEnvelope(target.world(), targetPoints, cameras)) {
            return OpticalResult.REGION_UNOWNED;
        }

        int tick = Bukkit.getCurrentTick();
        long revision = blockRaycaster.worldRevision(target.worldId());
        long signature = signature(targetPoints, cameras);
        DecisionKey key = new DecisionKey(viewer.playerId(), target.playerId());
        CachedDecision cached = decisions.get(key);
        if (cached != null && cached.signature == signature && cached.worldRevision == revision
                && tick - cached.tick >= 0 && tick - cached.tick < config.decisionCacheTicks()) {
            decisionHits.increment();
            return cached.result;
        }

        decisionMisses.increment();
        OpticalResult result = evaluateOwned(target.world(), targetPoints, cameras);
        decisions.put(key, new CachedDecision(
                signature, blockRaycaster.worldRevision(target.worldId()), tick, result));
        pruneDecisions(tick);
        return result;
    }

    /**
     * Runs the uncached decision path while collecting bounded operator diagnostics.
     *
     * @param target immutable target snapshot
     * @param cameras plausible viewer cameras
     * @return detailed current-tick ray snapshot
     */
    public RaycastDebugSnapshot diagnose(PlayerVisibilitySnapshot target, List<CameraPose> cameras) {
        long started = System.nanoTime();
        List<Vector> targetPoints = target.targetPoints();
        List<RayDebugLine> representative = new ArrayList<>(MAX_DEBUG_BLOCKED_RAYS);
        if (!ownsEnvelope(target.world(), targetPoints, cameras)) {
            return new RaycastDebugSnapshot(
                    OpticalResult.REGION_UNOWNED, List.copyOf(cameras), targetPoints, 0, 0,
                    0, List.of(), null, -1, System.nanoTime() - started);
        }

        int inFov = 0;
        int traced = 0;
        int blocked = 0;
        for (int cameraIndex = 0; cameraIndex < cameras.size(); cameraIndex++) {
            CameraPose camera = cameras.get(cameraIndex);
            if (!camera.world().equals(target.world())) {
                continue;
            }
            for (Vector point : targetPoints) {
                if (config.useFov() && !insideFov(camera, point)) {
                    continue;
                }
                inFov++;
                traced++;
                RayTraceResult hit = traceOwned(camera, point);
                if (hit == null) {
                    RayDebugLine decisive = debugLine(camera, point, null, true);
                    return new RaycastDebugSnapshot(
                            OpticalResult.VISIBLE, List.copyOf(cameras), targetPoints, inFov, traced,
                            blocked, List.copyOf(representative), decisive, cameraIndex,
                            System.nanoTime() - started);
                }
                blocked++;
                if (representative.size() < MAX_DEBUG_BLOCKED_RAYS) {
                    representative.add(debugLine(camera, point, hit, false));
                }
            }
        }
        OpticalResult result = inFov == 0 ? OpticalResult.OUTSIDE_FOV : OpticalResult.OCCLUDED;
        return new RaycastDebugSnapshot(
                result, List.copyOf(cameras), targetPoints, inFov, traced, blocked,
                List.copyOf(representative), null, -1, System.nanoTime() - started);
    }

    /**
     * Returns compensated-world and pair-decision cache counters for {@code /foggy debug}.
     *
     * @return aggregate cache counters
     */
    public CacheStats cacheStats() {
        return new CacheStats(blockRaycaster.cacheStats(), decisionHits.sum(), decisionMisses.sum(),
                decisions.size());
    }

    private OpticalResult evaluateOwned(World world, List<Vector> targetPoints, List<CameraPose> cameras) {
        boolean anyInsideFov = false;
        for (CameraPose camera : cameras) {
            if (!camera.world().equals(world)) {
                continue;
            }
            for (Vector point : targetPoints) {
                if (config.useFov() && !insideFov(camera, point)) {
                    continue;
                }
                anyInsideFov = true;
                if (!blockedOwned(camera, point)) {
                    return OpticalResult.VISIBLE;
                }
            }
        }
        return anyInsideFov ? OpticalResult.OCCLUDED : OpticalResult.OUTSIDE_FOV;
    }

    private boolean ownsEnvelope(World world, List<Vector> targetPoints, List<CameraPose> cameras) {
        double minX = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        boolean foundCamera = false;
        for (CameraPose camera : cameras) {
            if (!camera.world().equals(world)) {
                continue;
            }
            Vector position = camera.position();
            minX = Math.min(minX, position.getX());
            minZ = Math.min(minZ, position.getZ());
            maxX = Math.max(maxX, position.getX());
            maxZ = Math.max(maxZ, position.getZ());
            foundCamera = true;
        }
        if (!foundCamera || targetPoints.isEmpty()) {
            return true;
        }
        for (Vector point : targetPoints) {
            minX = Math.min(minX, point.getX());
            minZ = Math.min(minZ, point.getZ());
            maxX = Math.max(maxX, point.getX());
            maxZ = Math.max(maxZ, point.getZ());
        }
        return blockRaycaster.ownsTrace(world, minX, minZ, maxX, maxZ);
    }

    private static boolean insideFov(CameraPose camera, Vector point) {
        Vector position = camera.position();
        double deltaX = point.getX() - position.getX();
        double deltaY = point.getY() - position.getY();
        double deltaZ = point.getZ() - position.getZ();
        Vector forward = camera.forward();
        Vector right = camera.right();
        Vector up = camera.up();
        return FrustumMath.contains(
                deltaX, deltaY, deltaZ,
                forward.getX(), forward.getY(), forward.getZ(),
                right.getX(), right.getY(), right.getZ(),
                up.getX(), up.getY(), up.getZ(),
                camera.verticalFovDegrees(), camera.aspectRatio());
    }

    private @Nullable RayTraceResult traceOwned(CameraPose camera, Vector target) {
        Vector endpoint = traceEndpoint(camera.position(), target);
        return endpoint == null ? null
                : blockRaycaster.traceOcclusion(camera.world(), camera.position(), endpoint);
    }

    private boolean blockedOwned(CameraPose camera, Vector target) {
        Vector endpoint = traceEndpoint(camera.position(), target);
        return endpoint != null && blockRaycaster.occludes(camera.world(), camera.position(), endpoint);
    }

    private RayDebugLine debugLine(CameraPose camera, Vector target, @Nullable RayTraceResult blocking,
                                   boolean clear) {
        Vector endpoint = traceEndpoint(camera.position(), target);
        RayTraceResult firstOutline = endpoint == null ? null
                : blockRaycaster.traceAnyOutline(camera.world(), camera.position(), endpoint);
        BlockGeometryHit blockingDescription = blocking == null ? null : blockRaycaster.describe(blocking);
        BlockGeometryHit firstDescription = firstOutline == null ? null : blockRaycaster.describe(firstOutline);
        Vector hitPosition = blocking == null || blocking.getHitPosition() == null
                ? null : blocking.getHitPosition().clone();
        return new RayDebugLine(
                camera.position().clone(), target.clone(), hitPosition, clear,
                blockingDescription, firstDescription);
    }

    private @Nullable Vector traceEndpoint(Vector source, Vector target) {
        Vector delta = target.clone().subtract(source);
        double distance = delta.length();
        if (distance <= config.endpointEpsilon()) {
            return null;
        }
        double traceDistance = Math.max(0.0, distance - config.endpointEpsilon());
        return source.clone().add(delta.multiply(traceDistance / distance));
    }

    private void pruneDecisions(int tick) {
        int previous = lastDecisionPruneTick.get();
        if (tick - previous < DECISION_PRUNE_INTERVAL_TICKS
                || !lastDecisionPruneTick.compareAndSet(previous, tick)) {
            return;
        }
        int maximumAge = Math.max(DECISION_PRUNE_INTERVAL_TICKS, config.decisionCacheTicks() * 4);
        decisions.entrySet().removeIf(entry -> tick - entry.getValue().tick > maximumAge);
    }

    private static long signature(List<Vector> targetPoints, List<CameraPose> cameras) {
        long hash = 0xCBF29CE484222325L;
        hash = mix(hash, targetPoints.size());
        for (Vector point : targetPoints) {
            hash = mixVector(hash, point);
        }
        hash = mix(hash, cameras.size());
        for (CameraPose camera : cameras) {
            hash = mixVector(hash, camera.position());
            hash = mixVector(hash, camera.forward());
            hash = mixVector(hash, camera.right());
            hash = mixVector(hash, camera.up());
            hash = mix(hash, Float.floatToIntBits(camera.verticalFovDegrees()));
            hash = mix(hash, Float.floatToIntBits(camera.aspectRatio()));
        }
        return hash;
    }

    private static long mixVector(long hash, Vector vector) {
        hash = mix(hash, Double.doubleToLongBits(vector.getX()));
        hash = mix(hash, Double.doubleToLongBits(vector.getY()));
        return mix(hash, Double.doubleToLongBits(vector.getZ()));
    }

    private static long mix(long hash, long value) {
        hash ^= value;
        return hash * 0x100000001B3L;
    }

    /**
     * Aggregate counters exposed by live debug.
     *
     * @param world compensated-world counters
     * @param decisionHits reused pair decisions
     * @param decisionMisses recomputed pair decisions
     * @param decisionEntries currently retained pair entries
     */
    public record CacheStats(CompensatedWorld.CacheStats world, long decisionHits,
                             long decisionMisses, int decisionEntries) {
    }

    private record DecisionKey(UUID viewerId, UUID targetId) {
    }

    private record CachedDecision(long signature, long worldRevision, int tick, OpticalResult result) {
    }
}
