package dev.foggy.visibility;

import dev.foggy.invisibility.TargetInvisibilityState;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/**
 * Immutable player state published from the player's owning Folia region.
 *
 * <p>The Bukkit handles are identifiers/scheduler anchors only. Code consuming a snapshot from a
 * different region must not dereference {@link #playerHandle()} for entity state or read world
 * blocks through {@link #world()} without first proving ownership with
 * {@code Bukkit.isOwnedByCurrentRegion}.</p>
 *
 * @param playerHandle live entity handle used only for its entity scheduler/ownership checks
 * @param playerId stable player UUID
 * @param entityId current network entity id
 * @param name last captured player name
 * @param world world handle used as an identity and raycast scheduler anchor
 * @param worldId stable world UUID
 * @param position current server position
 * @param previousPosition previous captured server position
 * @param boundingBox current player hitbox
 * @param targetPoints precomputed interpolated hitbox samples
 * @param bypass whether the viewer has {@code foggy.bypass}
 * @param invisibility target-owned invisibility inputs
 * @param trackedViewerIds Paper tracker viewers captured on the target region
 * @param capturedNanos monotonic capture timestamp
 */
public record PlayerVisibilitySnapshot(
        Player playerHandle,
        UUID playerId,
        int entityId,
        String name,
        World world,
        UUID worldId,
        Vector position,
        Vector previousPosition,
        BoundingBox boundingBox,
        List<Vector> targetPoints,
        boolean bypass,
        TargetInvisibilityState invisibility,
        Set<UUID> trackedViewerIds,
        long capturedNanos
) {
    /** Defensively copies mutable Bukkit values before cross-region publication. */
    public PlayerVisibilitySnapshot {
        position = position.clone();
        previousPosition = previousPosition.clone();
        boundingBox = new BoundingBox(
                boundingBox.getMinX(), boundingBox.getMinY(), boundingBox.getMinZ(),
                boundingBox.getMaxX(), boundingBox.getMaxY(), boundingBox.getMaxZ());
        targetPoints = targetPoints.stream().map(Vector::clone).toList();
        trackedViewerIds = Set.copyOf(trackedViewerIds);
    }

    /**
     * Returns squared position distance without dereferencing either entity handle.
     *
     * @param other another immutable snapshot
     * @return squared Euclidean distance
     */
    public double distanceSquared(PlayerVisibilitySnapshot other) {
        double dx = position.getX() - other.position.getX();
        double dy = position.getY() - other.position.getY();
        double dz = position.getZ() - other.position.getZ();
        return dx * dx + dy * dy + dz * dz;
    }
}
