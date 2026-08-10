package dev.foggy.visibility;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Region-safe entity history analogous to an anti-cheat compensated-entity view.
 * Each player publishes only its own immutable state; viewer tasks never read a foreign entity.
 */
public final class CompensatedEntities {
    private final ConcurrentMap<UUID, PlayerVisibilitySnapshot> snapshots = new ConcurrentHashMap<>();
    private final ConcurrentSnapshotIndex spatialIndex;

    /**
     * Creates the compensated entity registry.
     *
     * @param spatialCellSize X/Z grid cell edge in blocks
     */
    public CompensatedEntities(int spatialCellSize) {
        spatialIndex = new ConcurrentSnapshotIndex(spatialCellSize);
    }

    /**
     * Publishes a target-owned immutable snapshot.
     *
     * @param snapshot newly captured state
     */
    public void publish(PlayerVisibilitySnapshot snapshot) {
        snapshots.put(snapshot.playerId(), snapshot);
        spatialIndex.publish(snapshot);
    }

    /**
     * Returns one last published snapshot.
     *
     * @param playerId player UUID
     * @return latest snapshot, or null
     */
    public PlayerVisibilitySnapshot snapshot(UUID playerId) {
        return snapshots.get(playerId);
    }

    /**
     * Returns a stable copy for commands/completion.
     *
     * @return current snapshots
     */
    public List<PlayerVisibilitySnapshot> snapshots() {
        return Collections.unmodifiableList(new ArrayList<PlayerVisibilitySnapshot>(snapshots.values()));
    }

    /**
     * Returns nearby immutable snapshots without accessing Bukkit entities.
     *
     * @param viewer query center snapshot
     * @param radius maximum distance in blocks
     * @return distance-filtered snapshot copy
     */
    public List<PlayerVisibilitySnapshot> nearby(PlayerVisibilitySnapshot viewer, double radius) {
        return spatialIndex.nearby(viewer, radius);
    }

    /**
     * Removes a disconnected/retired entity.
     *
     * @param playerId player UUID
     * @return removed snapshot, or null
     */
    public PlayerVisibilitySnapshot remove(UUID playerId) {
        PlayerVisibilitySnapshot removed = snapshots.remove(playerId);
        spatialIndex.remove(playerId);
        return removed;
    }

    /** Clears all compensated entity state. */
    public void clear() {
        snapshots.clear();
        spatialIndex.clear();
    }
}
