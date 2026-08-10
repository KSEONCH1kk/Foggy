package dev.foggy.visibility;

import dev.foggy.invisibility.TargetInvisibilityState;
import dev.foggy.math.Aabb;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/** Immutable player state published from the player's owning scheduler. */
public final class PlayerVisibilitySnapshot {
    private final Player playerHandle;
    private final UUID playerId;
    private final int entityId;
    private final String name;
    private final World world;
    private final UUID worldId;
    private final Vector position;
    private final Vector previousPosition;
    private final Aabb boundingBox;
    private final List<Vector> targetPoints;
    private final boolean bypass;
    private final TargetInvisibilityState invisibility;
    private final Set<UUID> trackedViewerIds;
    private final long capturedNanos;

    public PlayerVisibilitySnapshot(Player playerHandle, UUID playerId, int entityId, String name,
                                    World world, UUID worldId, Vector position, Vector previousPosition,
                                    Aabb boundingBox, List<Vector> targetPoints, boolean bypass,
                                    TargetInvisibilityState invisibility, Set<UUID> trackedViewerIds,
                                    long capturedNanos) {
        this.playerHandle = playerHandle;
        this.playerId = playerId;
        this.entityId = entityId;
        this.name = name;
        this.world = world;
        this.worldId = worldId;
        this.position = position.clone();
        this.previousPosition = previousPosition.clone();
        this.boundingBox = new Aabb(boundingBox.minX(), boundingBox.minY(), boundingBox.minZ(),
                boundingBox.maxX(), boundingBox.maxY(), boundingBox.maxZ());
        List<Vector> points = new ArrayList<Vector>(targetPoints.size());
        for (Vector point : targetPoints) points.add(point.clone());
        this.targetPoints = Collections.unmodifiableList(points);
        this.bypass = bypass;
        this.invisibility = invisibility;
        this.trackedViewerIds = Collections.unmodifiableSet(new HashSet<UUID>(trackedViewerIds));
        this.capturedNanos = capturedNanos;
    }

    public Player playerHandle() { return playerHandle; }
    public UUID playerId() { return playerId; }
    public int entityId() { return entityId; }
    public String name() { return name; }
    public World world() { return world; }
    public UUID worldId() { return worldId; }
    public Vector position() { return position; }
    public Vector previousPosition() { return previousPosition; }
    public Aabb boundingBox() { return boundingBox; }
    public List<Vector> targetPoints() { return targetPoints; }
    public boolean bypass() { return bypass; }
    public TargetInvisibilityState invisibility() { return invisibility; }
    public Set<UUID> trackedViewerIds() { return trackedViewerIds; }
    public long capturedNanos() { return capturedNanos; }

    public double distanceSquared(PlayerVisibilitySnapshot other) {
        double dx = position.getX() - other.position.getX();
        double dy = position.getY() - other.position.getY();
        double dz = position.getZ() - other.position.getZ();
        return dx * dx + dy * dy + dz * dz;
    }
}
