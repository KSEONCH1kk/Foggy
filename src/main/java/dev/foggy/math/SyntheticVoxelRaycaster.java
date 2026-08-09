package dev.foggy.math;

import java.util.List;
import java.util.function.Predicate;

/** Minimal voxel-shape raycaster used by synthetic wall/corner/slab regression tests. */
public final class SyntheticVoxelRaycaster {
    private SyntheticVoxelRaycaster() {
    }

    /**
     * Returns true if any supplied collision/visual sub-shape blocks the segment.
     *
     * @param shapes voxel sub-shapes
     * @param fromX source x
     * @param fromY source y
     * @param fromZ source z
     * @param toX destination x
     * @param toY destination y
     * @param toZ destination z
     * @return whether any shape blocks the segment
     */
    public static boolean occluded(List<Aabb> shapes, double fromX, double fromY, double fromZ,
                                   double toX, double toY, double toZ) {
        return occluded(shapes, ignored -> true, fromX, fromY, fromZ, toX, toY, toZ);
    }

    /**
     * Returns true if a sub-shape accepted by an optical material predicate blocks the segment.
     * This mirrors Paper applying its block predicate before {@code VoxelShape.clip}.
     *
     * @param shapes voxel sub-shapes
     * @param blocksRay optical filter
     * @param fromX source x
     * @param fromY source y
     * @param fromZ source z
     * @param toX destination x
     * @param toY destination y
     * @param toZ destination z
     * @return whether an accepted shape blocks the segment
     */
    public static boolean occluded(List<Aabb> shapes, Predicate<Aabb> blocksRay,
                                   double fromX, double fromY, double fromZ,
                                   double toX, double toY, double toZ) {
        for (Aabb shape : shapes) {
            if (blocksRay.test(shape)
                    && shape.intersectsSegment(fromX, fromY, fromZ, toX, toY, toZ)) {
                return true;
            }
        }
        return false;
    }
}
