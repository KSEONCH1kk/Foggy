package dev.foggy.visibility;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Concurrent per-world X/Z uniform grid for immutable player snapshots.
 * Region threads publish only their own player and may query neighboring cells without reading
 * Bukkit entity state, retaining the practical O(n*k) behavior of the Paper engine.
 */
public final class ConcurrentSnapshotIndex {
    private final int cellSize;
    private final ConcurrentMap<CellKey, ConcurrentMap<UUID, PlayerVisibilitySnapshot>> cells =
            new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, CellKey> membership = new ConcurrentHashMap<>();

    /**
     * Creates an index using the configured cell edge.
     *
     * @param cellSize positive X/Z cell edge in blocks
     */
    public ConcurrentSnapshotIndex(int cellSize) {
        if (cellSize <= 0) {
            throw new IllegalArgumentException("cellSize must be positive");
        }
        this.cellSize = cellSize;
    }

    /**
     * Publishes/replaces one immutable snapshot from its owning entity region.
     *
     * @param snapshot newly captured player state
     */
    public void publish(PlayerVisibilitySnapshot snapshot) {
        CellKey next = key(snapshot);
        CellKey previous = membership.put(snapshot.playerId(), next);
        if (previous != null && !previous.equals(next)) {
            removeFromCell(previous, snapshot.playerId());
        }
        cells.computeIfAbsent(next, ignored -> new ConcurrentHashMap<>())
                .put(snapshot.playerId(), snapshot);
    }

    /**
     * Removes one retired/disconnected player from the grid.
     *
     * @param playerId player to remove
     */
    public void remove(UUID playerId) {
        CellKey previous = membership.remove(playerId);
        if (previous != null) {
            removeFromCell(previous, playerId);
        }
    }

    /**
     * Returns distance-filtered candidates around an immutable viewer snapshot.
     *
     * @param viewer query center
     * @param radius maximum Euclidean distance
     * @return stable candidate copy
     */
    public List<PlayerVisibilitySnapshot> nearby(PlayerVisibilitySnapshot viewer, double radius) {
        int centerX = cell(viewer.position().getX());
        int centerZ = cell(viewer.position().getZ());
        int range = (int) Math.ceil(radius / cellSize);
        double radiusSquared = radius * radius;
        Map<UUID, PlayerVisibilitySnapshot> result = new HashMap<>();
        for (int x = centerX - range; x <= centerX + range; x++) {
            for (int z = centerZ - range; z <= centerZ + range; z++) {
                ConcurrentMap<UUID, PlayerVisibilitySnapshot> cell = cells.get(
                        new CellKey(viewer.worldId(), x, z));
                if (cell == null) {
                    continue;
                }
                for (PlayerVisibilitySnapshot candidate : cell.values()) {
                    if (!candidate.playerId().equals(viewer.playerId())
                            && viewer.distanceSquared(candidate) <= radiusSquared) {
                        result.merge(candidate.playerId(), candidate,
                                (left, right) -> left.capturedNanos() >= right.capturedNanos() ? left : right);
                    }
                }
            }
        }
        return Collections.unmodifiableList(new ArrayList<PlayerVisibilitySnapshot>(result.values()));
    }

    /** Clears all plugin-owned immutable state. */
    public void clear() {
        membership.clear();
        cells.clear();
    }

    private CellKey key(PlayerVisibilitySnapshot snapshot) {
        return new CellKey(snapshot.worldId(), cell(snapshot.position().getX()), cell(snapshot.position().getZ()));
    }

    private int cell(double coordinate) {
        return Math.floorDiv((int) Math.floor(coordinate), cellSize);
    }

    private void removeFromCell(CellKey key, UUID playerId) {
        ConcurrentMap<UUID, PlayerVisibilitySnapshot> cell = cells.get(key);
        if (cell != null) {
            cell.remove(playerId);
            if (cell.isEmpty()) {
                cells.remove(key, cell);
            }
        }
    }

    private static final class CellKey {
        private final UUID worldId;
        private final int x;
        private final int z;

        private CellKey(UUID worldId, int x, int z) {
            this.worldId = worldId; this.x = x; this.z = z;
        }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof CellKey)) return false;
            CellKey key = (CellKey) other;
            return x == key.x && z == key.z && worldId.equals(key.worldId);
        }

        @Override public int hashCode() {
            int result = worldId.hashCode();
            result = 31 * result + x;
            return 31 * result + z;
        }
    }
}
