package dev.foggy.raycast;

import dev.foggy.math.Aabb;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.util.Vector;

/** Immutable human-readable description of a Minecraft block-shape hit. */
public final class BlockGeometryHit {
    private final Vector position;
    private final Material material;
    private final String blockData;
    private final int blockX;
    private final int blockY;
    private final int blockZ;
    private final double outlineWidth;
    private final double outlineHeight;
    private final double outlineDepth;
    private final int collisionSubBoxes;
    private final List<Aabb> outlineSubBoxes;
    private final boolean exactOutlineBoxes;
    private final BlockOpticalMode opticalMode;

    public BlockGeometryHit(Vector position, Material material, String blockData,
                            int blockX, int blockY, int blockZ,
                            double outlineWidth, double outlineHeight, double outlineDepth,
                            int collisionSubBoxes, List<Aabb> outlineSubBoxes,
                            boolean exactOutlineBoxes, BlockOpticalMode opticalMode) {
        this.position = position;
        this.material = material;
        this.blockData = blockData;
        this.blockX = blockX;
        this.blockY = blockY;
        this.blockZ = blockZ;
        this.outlineWidth = outlineWidth;
        this.outlineHeight = outlineHeight;
        this.outlineDepth = outlineDepth;
        this.collisionSubBoxes = collisionSubBoxes;
        this.outlineSubBoxes = Collections.unmodifiableList(new ArrayList<Aabb>(outlineSubBoxes));
        this.exactOutlineBoxes = exactOutlineBoxes;
        this.opticalMode = opticalMode;
    }

    public Vector position() { return position; }
    public Material material() { return material; }
    public String blockData() { return blockData; }
    public int blockX() { return blockX; }
    public int blockY() { return blockY; }
    public int blockZ() { return blockZ; }
    public double outlineWidth() { return outlineWidth; }
    public double outlineHeight() { return outlineHeight; }
    public double outlineDepth() { return outlineDepth; }
    public int collisionSubBoxes() { return collisionSubBoxes; }
    public List<Aabb> outlineSubBoxes() { return outlineSubBoxes; }
    public boolean exactOutlineBoxes() { return exactOutlineBoxes; }
    public BlockOpticalMode opticalMode() { return opticalMode; }
}
