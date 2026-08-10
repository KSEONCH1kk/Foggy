package dev.foggy.raycast;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

/** Immutable version-neutral replacement for Bukkit's post-1.8 RayTraceResult. */
public final class BlockRayHit {
    private final Vector position;
    private final Block block;
    private final BlockFace face;

    public BlockRayHit(Vector position, @Nullable Block block, @Nullable BlockFace face) {
        this.position = position.clone();
        this.block = block;
        this.face = face;
    }

    public Vector position() { return position; }
    public @Nullable Block block() { return block; }
    public @Nullable BlockFace face() { return face; }
}
