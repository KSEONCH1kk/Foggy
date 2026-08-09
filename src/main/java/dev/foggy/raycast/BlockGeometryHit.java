package dev.foggy.raycast;

import org.bukkit.Material;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import java.util.List;

/**
 * Human-readable description of a Minecraft block-shape ray hit.
 *
 * @param position exact outline-shape intersection
 * @param material block material
 * @param blockData complete state string, including stair/slab/facing/connectivity properties
 * @param blockX block x
 * @param blockY block y
 * @param blockZ block z
 * @param outlineWidth width of the outline shape's enclosing bounds
 * @param outlineHeight height of the outline shape's enclosing bounds
 * @param outlineDepth depth of the outline shape's enclosing bounds
 * @param collisionSubBoxes number of individual boxes in the collision VoxelShape
 * @param outlineSubBoxes individual world-space boxes in the OUTLINE VoxelShape
 * @param exactOutlineBoxes whether OUTLINE boxes came directly from NMS
 * @param opticalMode transparency-policy decision
 */
public record BlockGeometryHit(
        Vector position,
        Material material,
        String blockData,
        int blockX,
        int blockY,
        int blockZ,
        double outlineWidth,
        double outlineHeight,
        double outlineDepth,
        int collisionSubBoxes,
        List<BoundingBox> outlineSubBoxes,
        boolean exactOutlineBoxes,
        BlockOpticalMode opticalMode
) {
    /** Creates an immutable debug hit. */
    public BlockGeometryHit {
        outlineSubBoxes = List.copyOf(outlineSubBoxes);
    }
}
