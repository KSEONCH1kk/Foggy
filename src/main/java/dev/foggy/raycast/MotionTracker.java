package dev.foggy.raycast;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/** Maintains previous/current server positions for conservative partial-tick target sampling. */
public final class MotionTracker {
    private final Map<UUID, MotionFrame> frames = new HashMap<>();
    private long generation;

    /** Creates an empty motion history. */
    public MotionTracker() {
    }

    /**
     * Captures one end-of-server-tick position frame for all online players.
     *
     * @param players immutable online-player snapshot
     */
    public void capture(Collection<? extends Player> players) {
        generation++;
        Set<UUID> online = new HashSet<>();
        for (Player player : players) {
            UUID id = player.getUniqueId();
            online.add(id);
            Location location = player.getLocation();
            Vector now = location.toVector();
            frames.compute(id, (ignored, old) -> old == null
                    ? new MotionFrame(now.clone(), now)
                    : new MotionFrame(old.current(), now));
        }
        frames.keySet().retainAll(online);
    }

    /**
     * Gets the latest frame, falling back to a stationary current position.
     *
     * @param player target player
     * @return previous/current position frame
     */
    public MotionFrame frame(Player player) {
        Vector now = player.getLocation().toVector();
        return frames.getOrDefault(player.getUniqueId(), new MotionFrame(now.clone(), now));
    }

    /**
     * Returns the position-frame generation, incremented by every global capture.
     *
     * @return current generation
     */
    public long generation() {
        return generation;
    }

    /**
     * Previous and current authoritative server positions.
     *
     * @param previous previous tick position
     * @param current current tick position
     */
    public record MotionFrame(Vector previous, Vector current) {
        /**
         * Returns a linearly interpolated position for partial tick {@code t}.
         *
         * @param t interpolation fraction
         * @return interpolated position
         */
        public Vector interpolate(double t) {
            return previous.clone().multiply(1.0 - t).add(current.clone().multiply(t));
        }
    }
}
