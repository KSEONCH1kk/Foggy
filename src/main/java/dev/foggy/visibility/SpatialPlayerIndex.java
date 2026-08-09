package dev.foggy.visibility;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/** Per-world uniform-grid index reducing the practical pair scan from O(n²) to O(n·k). */
public final class SpatialPlayerIndex {
    private final int cellSize;
    private final Map<UUID, Map<Long, List<Player>>> worlds = new HashMap<>();

    /**
     * Creates an index with the configured cell edge.
     *
     * @param cellSize cell edge in blocks
     */
    public SpatialPlayerIndex(int cellSize) {
        this.cellSize = cellSize;
    }

    /**
     * Rebuilds the O(n) index from an immutable online-player snapshot.
     *
     * @param players online players
     */
    public void rebuild(Collection<? extends Player> players) {
        worlds.clear();
        for (Player player : players) {
            Location location = player.getLocation();
            int x = cell(location.getX());
            int z = cell(location.getZ());
            worlds.computeIfAbsent(player.getWorld().getUID(), ignored -> new HashMap<>())
                    .computeIfAbsent(key(x, z), ignored -> new ArrayList<>())
                    .add(player);
        }
    }

    /**
     * Returns distance-filtered candidates around a viewer.
     *
     * @param viewer query center
     * @param radius maximum distance in blocks
     * @return nearby players in the same world
     */
    public List<Player> nearby(Player viewer, double radius) {
        World world = viewer.getWorld();
        Map<Long, List<Player>> cells = worlds.get(world.getUID());
        if (cells == null) {
            return List.of();
        }
        Location origin = viewer.getLocation();
        int centerX = cell(origin.getX());
        int centerZ = cell(origin.getZ());
        int range = (int) Math.ceil(radius / cellSize);
        double radiusSquared = radius * radius;
        List<Player> result = new ArrayList<>();
        for (int x = centerX - range; x <= centerX + range; x++) {
            for (int z = centerZ - range; z <= centerZ + range; z++) {
                for (Player candidate : cells.getOrDefault(key(x, z), List.of())) {
                    if (candidate != viewer && candidate.getLocation().distanceSquared(origin) <= radiusSquared) {
                        result.add(candidate);
                    }
                }
            }
        }
        return result;
    }

    private int cell(double coordinate) {
        return Math.floorDiv((int) Math.floor(coordinate), cellSize);
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFF_FFFFL);
    }
}
