package dev.foggy.camera;

import org.bukkit.World;
import org.bukkit.util.Vector;

/** One immutable plausible client camera used by the conservative visibility union. */
public final class CameraPose {
    private final World world;
    private final String source;
    private final Vector position;
    private final Vector forward;
    private final Vector right;
    private final Vector up;
    private final float verticalFovDegrees;
    private final float aspectRatio;
    private final boolean companionExact;

    public CameraPose(World world, String source, Vector position, Vector forward, Vector right,
                      Vector up, float verticalFovDegrees, float aspectRatio, boolean companionExact) {
        this.world = world;
        this.source = source;
        this.position = position;
        this.forward = forward;
        this.right = right;
        this.up = up;
        this.verticalFovDegrees = verticalFovDegrees;
        this.aspectRatio = aspectRatio;
        this.companionExact = companionExact;
    }

    public World world() { return world; }
    public String source() { return source; }
    public Vector position() { return position; }
    public Vector forward() { return forward; }
    public Vector right() { return right; }
    public Vector up() { return up; }
    public float verticalFovDegrees() { return verticalFovDegrees; }
    public float aspectRatio() { return aspectRatio; }
    public boolean companionExact() { return companionExact; }
}
