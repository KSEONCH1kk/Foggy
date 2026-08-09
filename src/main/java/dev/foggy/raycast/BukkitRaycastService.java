package dev.foggy.raycast;

import dev.foggy.camera.CameraPose;
import dev.foggy.config.FoggyConfig;
import dev.foggy.math.FrustumMath;
import dev.foggy.visibility.PlayerVisibilitySnapshot;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * Paper implementation backed by Minecraft's complete OUTLINE {@code VoxelShape} traversal.
 *
 * <p>{@link VanillaBlockRaycaster} selects {@code ClipContext.Block.OUTLINE}, not a single
 * enclosing block box. Fluids are ignored and optical transparency is applied by the Paper block
 * predicate before Minecraft clips each state-dependent shape.</p>
 */
public final class BukkitRaycastService implements RaycastService {
    private static final int MAX_DEBUG_BLOCKED_RAYS = 8;
    private final FoggyConfig config;
    private final VanillaBlockRaycaster blockRaycaster;

    /**
     * Creates the production raycast service.
     *
     * @param config raycast/FOV settings
     * @param blockRaycaster exact block geometry bridge
     */
    public BukkitRaycastService(FoggyConfig config, VanillaBlockRaycaster blockRaycaster) {
        this.config = config;
        this.blockRaycaster = blockRaycaster;
    }

    @Override
    public OpticalResult evaluate(PlayerVisibilitySnapshot target, List<CameraPose> cameras) {
        List<Vector> targetPoints = target.targetPoints();
        boolean anyInsideFov = false;
        for (CameraPose camera : cameras) {
            if (!camera.world().equals(target.world())) {
                continue;
            }
            for (Vector point : targetPoints) {
                if (config.useFov() && !insideFov(camera, point)) {
                    continue;
                }
                anyInsideFov = true;
                TraceResult trace = trace(camera, point);
                if (!trace.owned()) {
                    return OpticalResult.REGION_UNOWNED;
                }
                if (trace.hit() == null) {
                    return OpticalResult.VISIBLE;
                }
            }
        }
        return anyInsideFov ? OpticalResult.OCCLUDED : OpticalResult.OUTSIDE_FOV;
    }

    /**
     * Runs the same decision path while collecting bounded, human-readable diagnostics.
     * This method is intentionally not used for ordinary pairs because it allocates debug data.
     *
     * @param target immutable target snapshot
     * @param cameras plausible viewer cameras
     * @return detailed optical snapshot
     */
    public RaycastDebugSnapshot diagnose(PlayerVisibilitySnapshot target, List<CameraPose> cameras) {
        long started = System.nanoTime();
        List<Vector> targetPoints = target.targetPoints();
        List<RayDebugLine> representative = new ArrayList<>(MAX_DEBUG_BLOCKED_RAYS);
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
                TraceResult trace = trace(camera, point);
                if (!trace.owned()) {
                    return new RaycastDebugSnapshot(
                            OpticalResult.REGION_UNOWNED, List.copyOf(cameras), targetPoints, inFov, traced,
                            blocked, List.copyOf(representative), null, -1,
                            System.nanoTime() - started);
                }
                RayTraceResult hit = trace.hit();
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

    private static boolean insideFov(CameraPose camera, Vector point) {
        Vector delta = point.clone().subtract(camera.position());
        Vector forward = camera.forward();
        Vector right = camera.right();
        Vector up = camera.up();
        return FrustumMath.contains(
                delta.getX(), delta.getY(), delta.getZ(),
                forward.getX(), forward.getY(), forward.getZ(),
                right.getX(), right.getY(), right.getZ(),
                up.getX(), up.getY(), up.getZ(),
                camera.verticalFovDegrees(), camera.aspectRatio());
    }

    private TraceResult trace(CameraPose camera, Vector target) {
        Vector endpoint = traceEndpoint(camera.position(), target);
        if (endpoint == null) {
            return new TraceResult(true, null);
        }
        if (!blockRaycaster.ownsTrace(camera.world(), camera.position(), endpoint)) {
            return new TraceResult(false, null);
        }
        return new TraceResult(true,
                blockRaycaster.traceOcclusion(camera.world(), camera.position(), endpoint));
    }

    private RayDebugLine debugLine(CameraPose camera, Vector target, RayTraceResult blocking, boolean clear) {
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

    private Vector traceEndpoint(Vector source, Vector target) {
        Vector delta = target.clone().subtract(source);
        double distance = delta.length();
        if (distance <= config.endpointEpsilon()) {
            return null;
        }
        double traceDistance = Math.max(0.0, distance - config.endpointEpsilon());
        return source.clone().add(delta.multiply(traceDistance / distance));
    }

    private record TraceResult(boolean owned, RayTraceResult hit) {
    }
}
