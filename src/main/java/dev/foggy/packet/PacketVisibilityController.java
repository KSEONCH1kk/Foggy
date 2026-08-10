package dev.foggy.packet;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.player.PlayerManager;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import dev.foggy.visibility.PlayerVisibilitySnapshot;
import dev.foggy.platform.PlatformAdapter;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEffect;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityHeadLook;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityVelocity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnPlayer;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateAttributes;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Applies directed visibility decisions exclusively with PacketEvents packets.
 *
 * <p>The controller retains the server's player-info/tab entry. Reappearance is therefore a
 * normal player {@code SPAWN_ENTITY} followed in the same flush by current metadata, scale,
 * equipment, effects, head rotation and velocity. Silently sent packets bypass Foggy's own
 * listener while ordinary server updates to hidden ids are cancelled per viewer.</p>
 */
public final class PacketVisibilityController {
    private final Logger logger;
    private final PlatformAdapter platform;
    private final Map<UUID, ViewerPacketState> viewers = new ConcurrentHashMap<>();

    /**
     * Creates a controller.
     *
     * @param plugin scheduler owner
     * @param logger plugin logger
     */
    public PacketVisibilityController(Plugin plugin, Logger logger, PlatformAdapter platform) {
        this.logger = logger;
        this.platform = platform;
    }

    /**
     * Hides a currently tracked target, or pre-arms cancellation for its future spawn.
     *
     * @param viewer receiving player
     * @param target immutable target snapshot
     */
    public void hide(Player viewer, PlayerVisibilitySnapshot target) {
        int entityId = target.entityId();
        ViewerPacketState state = state(viewer);
        state.hiddenIds.add(entityId);
        boolean trackedNow = target.trackedViewerIds().contains(viewer.getUniqueId());
        if (trackedNow) {
            state.trackedIds.add(entityId);
        }
        if (trackedNow || state.clientKnownIds.remove(entityId)) {
            sendSilently(viewer, new WrapperPlayServerDestroyEntities(entityId));
            state.clientKnownIds.remove(entityId);
        }
    }

    /**
     * Shows a target immediately when Paper's tracker says this viewer tracks it.
     *
     * @param viewer receiving player
     * @param target immutable target snapshot
     */
    public void show(Player viewer, PlayerVisibilitySnapshot target) {
        int entityId = target.entityId();
        ViewerPacketState state = state(viewer);
        state.hiddenIds.remove(entityId);
        boolean trackedNow = target.trackedViewerIds().contains(viewer.getUniqueId())
                || state.trackedIds.contains(entityId);
        if (!trackedNow) {
            state.trackedIds.remove(entityId);
            state.clientKnownIds.remove(entityId);
            return;
        }
        state.trackedIds.add(entityId);
        if (state.clientKnownIds.add(entityId)) {
            requestSnapshot(viewer, target);
        }
    }

    /**
     * Called by the PacketEvents listener before an ordinary server spawn is sent.
     *
     * @param viewer receiving player
     * @param entityId spawned id
     * @return whether the spawn must be cancelled
     */
    public boolean onServerSpawn(Player viewer, int entityId) {
        ViewerPacketState state = state(viewer);
        state.trackedIds.add(entityId);
        if (state.hiddenIds.contains(entityId)) {
            state.clientKnownIds.remove(entityId);
            return true;
        }
        state.clientKnownIds.add(entityId);
        return false;
    }

    /**
     * Called when the server stops tracking one or more ids for a viewer.
     *
     * @param viewer receiving player
     * @param entityIds destroyed ids
     */
    public void onServerDestroy(Player viewer, int[] entityIds) {
        ViewerPacketState state = state(viewer);
        for (int entityId : entityIds) {
            state.trackedIds.remove(entityId);
            state.clientKnownIds.remove(entityId);
        }
    }

    /**
     * Returns whether ordinary target-specific packets must be suppressed.
     *
     * @param viewer receiving player
     * @param entityId packet target id
     * @return whether the packet must be cancelled
     */
    public boolean isHidden(Player viewer, int entityId) {
        ViewerPacketState state = viewers.get(viewer.getUniqueId());
        return state != null && state.hiddenIds.contains(entityId);
    }

    /**
     * Returns packet/tracker state for one directed pair.
     *
     * @param viewer receiving player
     * @param target immutable target snapshot
     * @return immutable packet diagnostic
     */
    public PacketDebugState inspect(Player viewer, PlayerVisibilitySnapshot target) {
        ViewerPacketState state = viewers.get(viewer.getUniqueId());
        int entityId = target.entityId();
        return new PacketDebugState(
                state != null && state.hiddenIds.contains(entityId),
                state != null && state.trackedIds.contains(entityId),
                state != null && state.clientKnownIds.contains(entityId),
                target.trackedViewerIds().contains(viewer.getUniqueId()));
    }

    /**
     * Forgets an obsolete entity id after respawn/id reuse.
     *
     * @param viewer receiving player
     * @param entityId obsolete id
     */
    public void forgetEntity(Player viewer, int entityId) {
        ViewerPacketState state = viewers.get(viewer.getUniqueId());
        if (state != null) {
            state.hiddenIds.remove(entityId);
            state.trackedIds.remove(entityId);
            state.clientKnownIds.remove(entityId);
        }
    }

    /**
     * Removes both viewer state and occurrences of a departing target id.
     *
     * @param player departing player
     */
    public void removePlayer(Player player) {
        removePlayer(player.getUniqueId(), player.getEntityId());
    }

    /**
     * Removes connection state using immutable identity values from a retired entity scheduler.
     *
     * @param playerId departing viewer UUID
     * @param entityId departing target entity id
     */
    public void removePlayer(UUID playerId, int entityId) {
        viewers.remove(playerId);
        for (ViewerPacketState state : viewers.values()) {
            state.hiddenIds.remove(entityId);
            state.trackedIds.remove(entityId);
            state.clientKnownIds.remove(entityId);
        }
    }

    /** Clears all thread-safe packet snapshots. */
    public void clear() {
        viewers.clear();
    }

    private ViewerPacketState state(Player viewer) {
        return viewers.computeIfAbsent(viewer.getUniqueId(), ignored -> new ViewerPacketState());
    }

    private void requestSnapshot(Player viewer, PlayerVisibilitySnapshot target) {
        Player handle = target.playerHandle();
        int expectedEntityId = target.entityId();
        UUID viewerId = viewer.getUniqueId();
        if (platform.owns(handle)) {
            completeSnapshot(viewer, expectedEntityId, PlayerSpawnSnapshot.capture(handle, platform));
            return;
        }
        platform.runEntity(handle, () -> {
            if (!handle.isOnline() || handle.getEntityId() != expectedEntityId) {
                abortSnapshot(viewerId, expectedEntityId);
                return;
            }
            final PlayerSpawnSnapshot snapshot;
            try {
                snapshot = PlayerSpawnSnapshot.capture(handle, platform);
            } catch (RuntimeException exception) {
                abortSnapshot(viewerId, expectedEntityId);
                logger.log(Level.SEVERE, "Could not capture cross-region spawn for " + target.name(), exception);
                return;
            }
            platform.runEntity(viewer,
                    () -> completeSnapshot(viewer, expectedEntityId, snapshot),
                    () -> abortSnapshot(viewerId, expectedEntityId));
        }, () -> abortSnapshot(viewerId, expectedEntityId));
    }

    private void completeSnapshot(Player viewer, int expectedEntityId, PlayerSpawnSnapshot target) {
        ViewerPacketState viewerState = state(viewer);
        if (target.entityId() != expectedEntityId
                || viewerState.hiddenIds.contains(expectedEntityId)
                || !viewerState.trackedIds.contains(expectedEntityId)) {
            viewerState.clientKnownIds.remove(expectedEntityId);
            return;
        }
        try {
            ServerVersion version = PacketEvents.getAPI().getServerManager().getVersion();
            if (version.isOlderThan(ServerVersion.V_1_20_2)) {
                sendSilently(viewer, new WrapperPlayServerSpawnPlayer(
                        target.entityId(), target.playerId(), target.location(), target.metadata()));
            } else {
                sendSilently(viewer, new WrapperPlayServerSpawnEntity(
                        target.entityId(),
                        target.playerId(),
                        EntityTypes.PLAYER,
                        target.location(),
                        target.location().getYaw(),
                        0,
                        target.velocity()));
            }

            sendSilently(viewer, new WrapperPlayServerEntityMetadata(
                    target.entityId(), target.metadata()));
            if (target.scale() != null) {
                sendSilently(viewer, new WrapperPlayServerUpdateAttributes(
                        target.entityId(), java.util.Collections.singletonList(target.scale())));
            }
            sendSilently(viewer, new WrapperPlayServerEntityEquipment(target.entityId(), target.equipment()));
            for (PlayerSpawnSnapshot.EffectSnapshot effect : target.effects()) {
                sendSilently(viewer, new WrapperPlayServerEntityEffect(
                        target.entityId(), effect.type(), effect.amplifier(), effect.duration(), effect.flags()));
            }
            sendSilently(viewer, new WrapperPlayServerEntityHeadLook(
                    target.entityId(), target.location().getYaw()));
            sendSilently(viewer, new WrapperPlayServerEntityVelocity(
                    target.entityId(), target.velocity()));
            if (version.isNewerThanOrEquals(ServerVersion.V_1_9)
                    && target.vehicleId() >= 0 && viewerState.trackedIds.contains(target.vehicleId())) {
                int[] passengerIds = target.passengerIds().stream().mapToInt(Integer::intValue).toArray();
                sendSilently(viewer, new WrapperPlayServerSetPassengers(target.vehicleId(), passengerIds));
            }
        } catch (RuntimeException exception) {
            viewerState.clientKnownIds.remove(target.entityId());
            logger.log(Level.SEVERE, "Could not respawn " + target.name() + " for " + viewer.getName(), exception);
        }
    }

    private void abortSnapshot(UUID viewerId, int entityId) {
        ViewerPacketState state = viewers.get(viewerId);
        if (state != null) {
            state.clientKnownIds.remove(entityId);
        }
    }

    private static void sendSilently(Player viewer, com.github.retrooper.packetevents.wrapper.PacketWrapper<?> packet) {
        PlayerManager manager = PacketEvents.getAPI().getPlayerManager();
        manager.sendPacketSilently(viewer, packet);
    }

    private static final class ViewerPacketState {
        private final Set<Integer> hiddenIds = ConcurrentHashMap.newKeySet();
        private final Set<Integer> trackedIds = ConcurrentHashMap.newKeySet();
        private final Set<Integer> clientKnownIds = ConcurrentHashMap.newKeySet();
    }
}
