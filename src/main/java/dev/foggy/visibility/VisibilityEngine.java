package dev.foggy.visibility;

import dev.foggy.camera.CameraEstimator;
import dev.foggy.camera.CameraPose;
import dev.foggy.config.FoggyConfig;
import dev.foggy.invisibility.InvisibilityDisposition;
import dev.foggy.invisibility.InvisibilityTracker;
import dev.foggy.packet.PacketVisibilityController;
import dev.foggy.raycast.OpticalResult;
import dev.foggy.raycast.RaycastService;
import dev.foggy.raycast.TargetPointSampler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/**
 * Paper/Folia visibility service driven exclusively by per-player entity schedulers.
 *
 * <p>Every Bukkit entity read is performed by the scheduler that owns that entity. The resulting
 * immutable {@link PlayerVisibilitySnapshot} is published through a concurrent registry and may
 * be consumed by other regions without dereferencing the target entity. Block raycasts are made
 * only after Paper confirms that the viewer's current region owns the complete ray corridor.</p>
 */
public final class VisibilityEngine {
    private static final long SNAPSHOT_TTL_NANOS = Duration.ofSeconds(1).toNanos();

    private final Plugin plugin;
    private final FoggyConfig config;
    private final CameraEstimator cameraEstimator;
    private final RaycastService raycastService;
    private final InvisibilityTracker invisibilityTracker;
    private final PacketVisibilityController packetController;
    private final TargetPointSampler targetPointSampler;
    private final ConcurrentSnapshotIndex spatialIndex;
    private final ConcurrentMap<UUID, PlayerVisibilitySnapshot> snapshots = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, ConcurrentMap<UUID, PairVisibilityState>> viewerStates =
            new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, TaskRegistration> tasks = new ConcurrentHashMap<>();
    private volatile Consumer<Player> viewerTickHook = ignored -> { };

    /**
     * Creates the region-safe visibility engine.
     *
     * @param plugin scheduler owner
     * @param config engine settings
     * @param cameraEstimator viewer camera estimator
     * @param raycastService optical visibility service
     * @param invisibilityTracker state visibility service
     * @param packetController per-viewer packet controller
     * @param targetPointSampler stateless interpolated-hitbox sampler
     */
    public VisibilityEngine(Plugin plugin, FoggyConfig config, CameraEstimator cameraEstimator,
                            RaycastService raycastService, InvisibilityTracker invisibilityTracker,
                            PacketVisibilityController packetController, TargetPointSampler targetPointSampler) {
        this.plugin = plugin;
        this.config = config;
        this.cameraEstimator = cameraEstimator;
        this.raycastService = raycastService;
        this.invisibilityTracker = invisibilityTracker;
        this.packetController = packetController;
        this.targetPointSampler = targetPointSampler;
        this.spatialIndex = new ConcurrentSnapshotIndex(config.spatialCellSize());
    }

    /**
     * Installs a region-owned callback invoked after each viewer decision pass.
     *
     * @param hook callback, normally the debug overlay updater
     */
    public void setViewerTickHook(Consumer<Player> hook) {
        viewerTickHook = hook;
    }

    /**
     * Starts one repeating entity-scheduler task for every supplied online player.
     *
     * @param onlinePlayers current player handles used only to reach their entity schedulers
     */
    public void start(Collection<? extends Player> onlinePlayers) {
        for (Player player : onlinePlayers) {
            track(player);
        }
    }

    /**
     * Starts visibility processing for a joining player.
     *
     * @param player player whose entity scheduler follows it across regions
     */
    public void track(Player player) {
        UUID playerId = player.getUniqueId();
        TaskRegistration registration = new TaskRegistration();
        if (tasks.putIfAbsent(playerId, registration) != null) {
            return;
        }
        ScheduledTask scheduled = player.getScheduler().runAtFixedRate(
                plugin,
                ignored -> tickViewer(player),
                () -> retire(playerId, registration),
                1L,
                1L);
        if (scheduled == null) {
            tasks.remove(playerId, registration);
            return;
        }
        registration.bind(scheduled);
    }

    /**
     * Replaces the scheduler registration after Bukkit has constructed a respawned player entity.
     * An old scheduler's delayed retired callback is generation-checked and cannot remove the new
     * registration.
     *
     * @param player respawned player
     */
    public void retrack(Player player) {
        UUID playerId = player.getUniqueId();
        TaskRegistration previous = tasks.remove(playerId);
        if (previous != null) {
            previous.cancel();
        }
        track(player);
        refresh(player);
    }

    /**
     * Requests an immediate region-owned refresh after a discrete player event.
     *
     * @param player changed player
     */
    public void refresh(Player player) {
        if (Bukkit.isOwnedByCurrentRegion(player)) {
            tickViewer(player);
            return;
        }
        player.getScheduler().run(plugin, ignored -> tickViewer(player),
                () -> { });
    }

    /**
     * Returns the latest immutable target snapshot.
     *
     * @param playerId target UUID
     * @return snapshot, or null before first capture/after retirement
     */
    public PlayerVisibilitySnapshot snapshot(UUID playerId) {
        return snapshots.get(playerId);
    }

    /**
     * Returns a stable immutable copy of currently captured players for commands/completion.
     *
     * @return current published snapshots
     */
    public List<PlayerVisibilitySnapshot> snapshots() {
        return List.copyOf(snapshots.values());
    }

    /**
     * Returns the current transition-cache state. Call from the viewer's entity scheduler.
     *
     * @param viewer viewer UUID
     * @param target target snapshot
     * @return immutable pair state
     */
    public PairDebugState inspect(UUID viewer, PlayerVisibilitySnapshot target) {
        Map<UUID, PairVisibilityState> states = viewerStates.get(viewer);
        PairVisibilityState state = states == null ? null : states.get(target.playerId());
        return state == null
                ? PairDebugState.unmanaged(target.entityId())
                : new PairDebugState(true, state.lastReason(), state.hidden(),
                        state.pendingHideTicks(), state.entityId());
    }

    /**
     * Removes all connection-bound state for a quitting player.
     *
     * @param player departing player, owned by the current event region
     */
    public void remove(Player player) {
        UUID playerId = player.getUniqueId();
        TaskRegistration task = tasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
        PlayerVisibilitySnapshot removed = snapshots.remove(playerId);
        spatialIndex.remove(playerId);
        viewerStates.remove(playerId);
        for (Map<UUID, PairVisibilityState> states : viewerStates.values()) {
            states.remove(playerId);
        }
        packetController.removePlayer(player);
        if (removed != null && removed.entityId() != player.getEntityId()) {
            packetController.removePlayer(playerId, removed.entityId());
        }
    }

    /**
     * Cancels tasks and clears snapshots.
     *
     * @param clearPacketState false during live reload so hidden ids transfer to the new engine
     */
    public void shutdown(boolean clearPacketState) {
        for (TaskRegistration task : tasks.values()) {
            task.cancel();
        }
        tasks.clear();
        snapshots.clear();
        spatialIndex.clear();
        viewerStates.clear();
        if (clearPacketState) {
            packetController.clear();
        }
    }

    private void tickViewer(Player viewer) {
        if (!viewer.isOnline() || !Bukkit.isOwnedByCurrentRegion(viewer)) {
            return;
        }
        PlayerVisibilitySnapshot viewerSnapshot = capture(viewer);
        snapshots.put(viewerSnapshot.playerId(), viewerSnapshot);
        spatialIndex.publish(viewerSnapshot);
        processViewer(viewer, viewerSnapshot);
        viewerTickHook.accept(viewer);
    }

    private PlayerVisibilitySnapshot capture(Player player) {
        Location location = player.getLocation();
        Vector current = location.toVector();
        UUID playerId = player.getUniqueId();
        UUID worldId = location.getWorld().getUID();
        PlayerVisibilitySnapshot previous = snapshots.get(playerId);
        Vector previousPosition = previous != null
                && previous.worldId().equals(worldId)
                && previous.entityId() == player.getEntityId()
                ? previous.position() : current;
        BoundingBox box = player.getBoundingBox();
        Set<UUID> trackedViewers = new HashSet<>();
        for (Player tracked : player.getTrackedBy()) {
            trackedViewers.add(tracked.getUniqueId());
        }
        return new PlayerVisibilitySnapshot(
                player, playerId, player.getEntityId(), player.getName(), location.getWorld(), worldId,
                current, previousPosition, box,
                targetPointSampler.sample(box, previousPosition, current),
                player.hasPermission("foggy.bypass"), invisibilityTracker.captureTarget(player),
                trackedViewers, System.nanoTime());
    }

    private void processViewer(Player viewer, PlayerVisibilitySnapshot viewerSnapshot) {
        ConcurrentMap<UUID, PairVisibilityState> states = viewerStates.computeIfAbsent(
                viewerSnapshot.playerId(), ignored -> new ConcurrentHashMap<>());
        Set<UUID> seen = new HashSet<>();
        List<CameraPose> cameras = null;
        long now = System.nanoTime();
        for (PlayerVisibilitySnapshot target : spatialIndex.nearby(
                viewerSnapshot, config.visibilityRadius())) {
            if (target.playerId().equals(viewerSnapshot.playerId())
                    || !target.worldId().equals(viewerSnapshot.worldId())
                    || now - target.capturedNanos() < 0L
                    || now - target.capturedNanos() > SNAPSHOT_TTL_NANOS) {
                continue;
            }
            seen.add(target.playerId());
            PairVisibilityState state = states.computeIfAbsent(target.playerId(), ignored ->
                    new PairVisibilityState(target.entityId(),
                            packetController.isHidden(viewer, target.entityId())));
            if (state.entityId() != target.entityId()) {
                packetController.forgetEntity(viewer, state.entityId());
                state.resetForEntity(target.entityId());
            }
            if (cameras == null) {
                cameras = cameraEstimator.estimate(viewer);
            }
            HideReason reason = decide(viewer, viewerSnapshot, target, cameras);
            VisibilityTransition transition = state.apply(reason, config.hideConfirmationTicks());
            if (transition == VisibilityTransition.HIDE) {
                packetController.hide(viewer, target);
            } else if (transition == VisibilityTransition.SHOW) {
                packetController.show(viewer, target);
            }
        }
        releaseUnmanaged(viewer, states, seen);
    }

    private HideReason decide(Player viewer, PlayerVisibilitySnapshot viewerSnapshot,
                              PlayerVisibilitySnapshot target, List<CameraPose> cameras) {
        if (viewerSnapshot.bypass()) {
            return HideReason.NONE;
        }
        boolean canSee = true;
        if (Bukkit.isOwnedByCurrentRegion(target.playerHandle())) {
            canSee = viewer.canSee(target.playerHandle());
        }
        InvisibilityDisposition invisibility = invisibilityTracker.disposition(target.invisibility(), canSee);
        if (invisibility.removesEntity()) {
            return HideReason.INVISIBLE;
        }
        OpticalResult optical = raycastService.evaluate(target, cameras);
        return switch (optical) {
            case VISIBLE, REGION_UNOWNED -> HideReason.NONE;
            case OCCLUDED -> HideReason.OCCLUDED;
            case OUTSIDE_FOV -> HideReason.OUTSIDE_FOV;
        };
    }

    private void releaseUnmanaged(Player viewer, ConcurrentMap<UUID, PairVisibilityState> states,
                                  Set<UUID> seen) {
        for (Map.Entry<UUID, PairVisibilityState> entry : new ArrayList<>(states.entrySet())) {
            if (seen.contains(entry.getKey()) || !states.remove(entry.getKey(), entry.getValue())) {
                continue;
            }
            PlayerVisibilitySnapshot target = snapshots.get(entry.getKey());
            if (entry.getValue().hidden() && target != null) {
                packetController.show(viewer, target);
            }
        }
    }

    private void retire(UUID playerId, TaskRegistration registration) {
        if (!tasks.remove(playerId, registration)) {
            return;
        }
        PlayerVisibilitySnapshot removed = snapshots.remove(playerId);
        spatialIndex.remove(playerId);
        viewerStates.remove(playerId);
        for (Map<UUID, PairVisibilityState> states : viewerStates.values()) {
            states.remove(playerId);
        }
        if (removed != null) {
            packetController.removePlayer(playerId, removed.entityId());
        }
    }

    private static final class TaskRegistration {
        private final AtomicReference<ScheduledTask> scheduled = new AtomicReference<>();
        private final AtomicBoolean cancelled = new AtomicBoolean();

        private void bind(ScheduledTask task) {
            if (!scheduled.compareAndSet(null, task)) {
                task.cancel();
                throw new IllegalStateException("Entity task registration was bound twice");
            }
            if (cancelled.get()) {
                task.cancel();
            }
        }

        private void cancel() {
            cancelled.set(true);
            ScheduledTask task = scheduled.get();
            if (task != null) {
                task.cancel();
            }
        }
    }
}
