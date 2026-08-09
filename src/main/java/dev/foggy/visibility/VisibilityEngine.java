package dev.foggy.visibility;

import dev.foggy.camera.CameraEstimator;
import dev.foggy.camera.CameraPose;
import dev.foggy.config.FoggyConfig;
import dev.foggy.invisibility.InvisibilityTracker;
import dev.foggy.packet.PacketVisibilityController;
import dev.foggy.raycast.MotionTracker;
import dev.foggy.raycast.OpticalResult;
import dev.foggy.raycast.RaycastService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Central end-of-tick visibility service.
 *
 * <p>Every directed nearby pair is evaluated from an O(n) uniform-grid snapshot. Decisions are
 * cached, and packets are emitted only on state transitions. World access remains on the Paper
 * main thread; PacketEvents' Netty listener consumes only controller snapshots.</p>
 */
public final class VisibilityEngine {
    private final FoggyConfig config;
    private final CameraEstimator cameraEstimator;
    private final RaycastService raycastService;
    private final InvisibilityTracker invisibilityTracker;
    private final PacketVisibilityController packetController;
    private final MotionTracker motionTracker;
    private final SpatialPlayerIndex spatialIndex;
    private final Map<PairKey, PairVisibilityState> states = new HashMap<>();

    /**
     * Creates the visibility engine and its transition cache.
     *
     * @param config engine settings
     * @param cameraEstimator viewer camera estimator
     * @param raycastService optical visibility service
     * @param invisibilityTracker state visibility service
     * @param packetController per-viewer packet controller
     * @param motionTracker target position history
     */
    public VisibilityEngine(FoggyConfig config, CameraEstimator cameraEstimator, RaycastService raycastService,
                            InvisibilityTracker invisibilityTracker, PacketVisibilityController packetController,
                            MotionTracker motionTracker) {
        this.config = config;
        this.cameraEstimator = cameraEstimator;
        this.raycastService = raycastService;
        this.invisibilityTracker = invisibilityTracker;
        this.packetController = packetController;
        this.motionTracker = motionTracker;
        this.spatialIndex = new SpatialPlayerIndex(config.spatialCellSize());
    }

    /** Recomputes all nearby directed pairs once at the end of a server tick. */
    public void tick() {
        List<Player> online = List.copyOf(Bukkit.getOnlinePlayers());
        motionTracker.capture(online);
        spatialIndex.rebuild(online);
        Set<PairKey> seen = new HashSet<>();
        for (Player viewer : online) {
            List<CameraPose> cameras = cameraEstimator.estimate(viewer);
            for (Player target : spatialIndex.nearby(viewer, config.visibilityRadius())) {
                PairKey key = new PairKey(viewer.getUniqueId(), target.getUniqueId());
                seen.add(key);
                process(viewer, target, null, cameras);
            }
        }
        releaseUnmanagedPairs(seen);
    }

    /**
     * Immediately recomputes both directions involving one player, used for joins and discrete
     * invisibility/game-mode changes that occur after the regular tick pass.
     *
     * @param player changed player
     */
    public void recomputePlayer(Player player) {
        recomputePlayer(player, null);
    }

    /**
     * Recomputes viewers of a target with an event-derived invisibility override.
     *
     * @param target changed target
     * @param forcedInvisible event-derived state
     */
    public void recomputeTarget(Player target, boolean forcedInvisible) {
        for (Player viewer : nearbyDirect(target)) {
            process(viewer, target, forcedInvisible, cameraEstimator.estimate(viewer));
        }
    }

    /**
     * Returns the current transition-cache state for an operator diagnostic.
     *
     * @param viewer receiving player
     * @param target target player
     * @return immutable pair state
     */
    public PairDebugState inspect(Player viewer, Player target) {
        PairVisibilityState state = states.get(new PairKey(viewer.getUniqueId(), target.getUniqueId()));
        return state == null
                ? PairDebugState.unmanaged(target.getEntityId())
                : new PairDebugState(true, state.lastReason(), state.hidden(),
                        state.pendingHideTicks(), state.entityId());
    }

    /**
     * Removes pair state and connection state for a disconnected player.
     *
     * @param player departing player
     */
    public void remove(Player player) {
        List<Map.Entry<PairKey, PairVisibilityState>> removed = states.entrySet().stream()
                .filter(entry -> entry.getKey().viewerId().equals(player.getUniqueId())
                        || entry.getKey().targetId().equals(player.getUniqueId()))
                .toList();
        for (Map.Entry<PairKey, PairVisibilityState> entry : removed) {
            states.remove(entry.getKey());
        }
        packetController.removePlayer(player);
    }

    /** Reveals every still-tracked hidden target and clears all engine state. */
    public void shutdown() {
        for (Map.Entry<PairKey, PairVisibilityState> entry : new ArrayList<>(states.entrySet())) {
            if (!entry.getValue().hidden()) {
                continue;
            }
            Player viewer = Bukkit.getPlayer(entry.getKey().viewerId());
            Player target = Bukkit.getPlayer(entry.getKey().targetId());
            if (viewer != null && target != null) {
                packetController.show(viewer, target);
            }
        }
        states.clear();
        packetController.clear();
    }

    private void recomputePlayer(Player player, @Nullable Boolean forcedInvisible) {
        List<CameraPose> playerCameras = cameraEstimator.estimate(player);
        for (Player other : nearbyDirect(player)) {
            process(player, other, null, playerCameras);
            process(other, player, forcedInvisible, cameraEstimator.estimate(other));
        }
    }

    private Collection<Player> nearbyDirect(Player center) {
        double radiusSquared = config.visibilityRadius() * config.visibilityRadius();
        List<Player> result = new ArrayList<>();
        for (Player candidate : center.getWorld().getPlayers()) {
            if (candidate != center && candidate.getLocation().distanceSquared(center.getLocation()) <= radiusSquared) {
                result.add(candidate);
            }
        }
        return result;
    }

    private void process(Player viewer, Player target, @Nullable Boolean forcedInvisible,
                         List<CameraPose> cameras) {
        if (!viewer.isOnline() || !target.isOnline() || viewer.getWorld() != target.getWorld()) {
            return;
        }
        PairKey key = new PairKey(viewer.getUniqueId(), target.getUniqueId());
        PairVisibilityState state = states.computeIfAbsent(key, ignored -> new PairVisibilityState(target.getEntityId()));
        if (state.entityId() != target.getEntityId()) {
            packetController.forgetEntity(viewer, state.entityId());
            state.resetForEntity(target.getEntityId());
        }
        HideReason reason = decide(viewer, target, forcedInvisible, cameras);
        VisibilityTransition transition = state.apply(reason, config.hideConfirmationTicks());
        if (transition == VisibilityTransition.HIDE) {
            packetController.hide(viewer, target);
        } else if (transition == VisibilityTransition.SHOW) {
            packetController.show(viewer, target);
        }
    }

    private HideReason decide(Player viewer, Player target, @Nullable Boolean forcedInvisible,
                              List<CameraPose> cameras) {
        if (viewer.hasPermission("foggy.bypass")) {
            return HideReason.NONE;
        }
        boolean invisible = forcedInvisible != null
                ? forcedInvisible : invisibilityTracker.isInvisibleTo(viewer, target);
        if (invisible) {
            return HideReason.INVISIBLE;
        }
        OpticalResult optical = raycastService.evaluate(target, cameras);
        return switch (optical) {
            case VISIBLE -> HideReason.NONE;
            case OCCLUDED -> HideReason.OCCLUDED;
            case OUTSIDE_FOV -> HideReason.OUTSIDE_FOV;
        };
    }

    private void releaseUnmanagedPairs(Set<PairKey> seen) {
        List<Map.Entry<PairKey, PairVisibilityState>> stale = states.entrySet().stream()
                .filter(entry -> !seen.contains(entry.getKey()))
                .toList();
        for (Map.Entry<PairKey, PairVisibilityState> entry : stale) {
            PairVisibilityState state = entry.getValue();
            if (state.hidden()) {
                Player viewer = Bukkit.getPlayer(entry.getKey().viewerId());
                Player target = Bukkit.getPlayer(entry.getKey().targetId());
                if (viewer != null && target != null && viewer.getWorld() == target.getWorld()) {
                    packetController.show(viewer, target);
                }
            }
            states.remove(entry.getKey());
        }
    }
}
