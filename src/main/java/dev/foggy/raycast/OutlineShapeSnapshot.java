package dev.foggy.raycast;

import java.util.List;
import org.bukkit.util.BoundingBox;

/**
 * Debug-only extraction of the individual boxes making up a block OUTLINE shape.
 *
 * @param boxes world-space sub-boxes
 * @param exact true when boxes came directly from NMS {@code VoxelShape.toAabbs()}
 */
public record OutlineShapeSnapshot(List<BoundingBox> boxes, boolean exact) {
    /** Creates an immutable snapshot. */
    public OutlineShapeSnapshot {
        boxes = List.copyOf(boxes);
    }
}
