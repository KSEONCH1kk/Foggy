package dev.foggy.math;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SyntheticVoxelRaycasterTest {
    @Test
    void fullWallOccludesTarget() {
        List<Aabb> wall = List.of(new Aabb(2.0, 0.0, -1.0, 3.0, 3.0, 1.0));
        assertTrue(SyntheticVoxelRaycaster.occluded(wall, 0.0, 1.62, 0.0, 5.0, 1.0, 0.0));
    }

    @Test
    void diagonalCornerDoesNotLeak() {
        List<Aabb> corner = List.of(
                new Aabb(1.0, 0.0, 0.0, 2.0, 3.0, 3.0),
                new Aabb(0.0, 0.0, 1.0, 3.0, 3.0, 2.0));
        assertTrue(SyntheticVoxelRaycaster.occluded(corner, 0.25, 1.62, 0.25, 2.5, 1.0, 2.5));
    }

    @Test
    void thinVoxelShapeStillOccludes() {
        List<Aabb> pane = List.of(new Aabb(2.46875, 0.0, -1.0, 2.53125, 3.0, 1.0));
        assertTrue(SyntheticVoxelRaycaster.occluded(pane, 0.0, 1.62, 0.0, 5.0, 1.62, 0.0));
    }

    @Test
    void slabBlocksLowRayButNotHighRay() {
        List<Aabb> slab = List.of(new Aabb(2.0, 0.0, -1.0, 3.0, 0.5, 1.0));
        assertTrue(SyntheticVoxelRaycaster.occluded(slab, 0.0, 0.25, 0.0, 5.0, 0.25, 0.0));
        assertFalse(SyntheticVoxelRaycaster.occluded(slab, 0.0, 1.62, 0.0, 5.0, 1.62, 0.0));
    }

    @Test
    void stairTestsEverySubBoxWithoutUsingItsEnclosingBounds() {
        List<Aabb> stair = List.of(
                new Aabb(2.0, 0.0, -0.5, 3.0, 0.5, 0.5),
                new Aabb(2.5, 0.5, -0.5, 3.0, 1.0, 0.5));
        assertTrue(SyntheticVoxelRaycaster.occluded(
                stair, 2.75, 0.75, -2.0, 2.75, 0.75, 2.0));
        assertFalse(SyntheticVoxelRaycaster.occluded(
                stair, 2.25, 0.75, -2.0, 2.25, 0.75, 2.0));
    }

    @Test
    void thinLadderOrSignOutlinePlaneIsNotExpandedToFullBlock() {
        List<Aabb> thinPlane = List.of(new Aabb(2.0, 0.0, 0.875, 3.0, 2.0, 1.0));
        assertTrue(SyntheticVoxelRaycaster.occluded(
                thinPlane, 2.5, 1.0, 0.0, 2.5, 1.0, 2.0));
        assertFalse(SyntheticVoxelRaycaster.occluded(
                thinPlane, 1.75, 1.0, 0.0, 1.75, 1.0, 2.0));
    }

    @Test
    void transparentShapePassesBeforeLaterOpaqueShape() {
        Aabb glass = new Aabb(1.0, 0.0, -1.0, 1.0625, 3.0, 1.0);
        Aabb wall = new Aabb(3.0, 0.0, -1.0, 4.0, 3.0, 1.0);
        List<Aabb> shapes = List.of(glass, wall);
        assertTrue(SyntheticVoxelRaycaster.occluded(
                shapes, shape -> shape != glass, 0.0, 1.62, 0.0, 5.0, 1.62, 0.0));
        assertFalse(SyntheticVoxelRaycaster.occluded(
                List.of(glass), shape -> shape != glass,
                0.0, 1.62, 0.0, 5.0, 1.62, 0.0));
    }
}
