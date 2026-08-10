package dev.foggy.raycast;

import dev.foggy.math.Aabb;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Debug-only extraction of the individual boxes making up a block outline shape. */
public final class OutlineShapeSnapshot {
    private final List<Aabb> boxes;
    private final boolean exact;
    private final String stateDescription;
    private final int collisionBoxes;

    public OutlineShapeSnapshot(List<Aabb> boxes, boolean exact, String stateDescription,
                                int collisionBoxes) {
        this.boxes = Collections.unmodifiableList(new ArrayList<Aabb>(boxes));
        this.exact = exact;
        this.stateDescription = stateDescription;
        this.collisionBoxes = collisionBoxes;
    }

    public List<Aabb> boxes() { return boxes; }
    public boolean exact() { return exact; }
    public String stateDescription() { return stateDescription; }
    public int collisionBoxes() { return collisionBoxes; }

    public Aabb envelope(int blockX, int blockY, int blockZ) {
        if (boxes.isEmpty()) return new Aabb(blockX, blockY, blockZ, blockX, blockY, blockZ);
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY,
                minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY,
                maxZ = Double.NEGATIVE_INFINITY;
        for (Aabb box : boxes) {
            minX = Math.min(minX, box.minX()); minY = Math.min(minY, box.minY());
            minZ = Math.min(minZ, box.minZ()); maxX = Math.max(maxX, box.maxX());
            maxY = Math.max(maxY, box.maxY()); maxZ = Math.max(maxZ, box.maxZ());
        }
        return new Aabb(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
