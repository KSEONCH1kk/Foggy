package dev.foggy.raycast;

import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

/** Immutable ray selected for operator particle rendering. */
public final class RayDebugLine {
    private final Vector from;
    private final Vector to;
    private final Vector hit;
    private final boolean clear;
    private final BlockGeometryHit blockingBlock;
    private final BlockGeometryHit firstOutline;

    public RayDebugLine(Vector from, Vector to, @Nullable Vector hit, boolean clear,
                        @Nullable BlockGeometryHit blockingBlock,
                        @Nullable BlockGeometryHit firstOutline) {
        this.from = from;
        this.to = to;
        this.hit = hit;
        this.clear = clear;
        this.blockingBlock = blockingBlock;
        this.firstOutline = firstOutline;
    }

    public Vector from() { return from; }
    public Vector to() { return to; }
    public @Nullable Vector hit() { return hit; }
    public boolean clear() { return clear; }
    public @Nullable BlockGeometryHit blockingBlock() { return blockingBlock; }
    public @Nullable BlockGeometryHit firstOutline() { return firstOutline; }
}
