package dev.foggy.raycast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.SplittableRandom;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;

class CompensatedShapeTest {
    private static final double EPSILON = 1.0E-12;

    @Test
    void clipsTheNearestSubBoxWithVanillaFaceDirection() {
        CompensatedShape shape = new CompensatedShape(new double[] {
                0.75, 0.0, 0.0, 1.0, 1.0, 1.0,
                0.0, 0.0, 0.0, 0.25, 1.0, 1.0
        });

        CompensatedShape.ShapeHit hit = shape.clip(
                -2.0, 0.5, 0.5, 4.0, 0.5, 0.5, 0, 0, 0);
        assertNotNull(hit);

        assertEquals(2.0 / 6.0, hit.distanceFraction(), EPSILON);
        assertEquals(BlockFace.WEST, hit.face());
        assertEquals(0.0, hit.position().getX(), EPSILON);
    }

    @Test
    void slabBlocksLowRayButLeavesHighRayClear() {
        CompensatedShape slab = new CompensatedShape(new double[] {0, 0, 0, 1, 0.5, 1});

        assertNotNull(slab.clip(-1, 4.25, 0.5, 2, 4.25, 0.5, 0, 4, 0));
        assertNull(slab.clip(-1, 4.75, 0.5, 2, 4.75, 0.5, 0, 4, 0));
    }

    @Test
    void startInsideUsesMinecraftVoxelShapeInsideHit() {
        CompensatedShape cube = new CompensatedShape(new double[] {0, 0, 0, 1, 1, 1});

        CompensatedShape.ShapeHit hit = cube.clip(0.5, 0.5, 0.5, 2.0, 0.5, 0.5, 0, 0, 0);
        assertNotNull(hit);
        assertEquals(0.001, hit.distanceFraction(), EPSILON);
        assertEquals(BlockFace.WEST, hit.face());
    }

    @Test
    void endpointExactlyOnFaceIsExcludedLikeMinecraftAabbClip() {
        CompensatedShape cube = new CompensatedShape(new double[] {0, 0, 0, 1, 1, 1});

        assertNull(cube.clip(-1.0, 0.5, 0.5, 0.0, 0.5, 0.5, 0, 0, 0));
    }

    @Test
    void thinFenceArmDoesNotBecomeAFullCube() {
        CompensatedShape arm = new CompensatedShape(new double[] {0.375, 0, 0, 0.625, 1.5, 1});

        assertNotNull(arm.clip(0.5, 0.75, -1, 0.5, 0.75, 2, 0, 0, 0));
        assertNull(arm.clip(0.1, 0.75, -1, 0.1, 0.75, 2, 0, 0, 0));
    }

    @Test
    void allocationFreeHotPathMatchesDetailedClip() {
        CompensatedShape shape = new CompensatedShape(new double[] {
                0, 0, 0, 1, 0.5, 1,
                0.5, 0.5, 0, 1, 1, 1,
                0.375, 1, 0.375, 0.625, 1.5, 0.625
        });
        SplittableRandom random = new SplittableRandom(0xF099120L);
        for (int index = 0; index < 20_000; index++) {
            double fromX = random.nextDouble(-2, 3);
            double fromY = random.nextDouble(-2, 3);
            double fromZ = random.nextDouble(-2, 3);
            double toX = random.nextDouble(-2, 3);
            double toY = random.nextDouble(-2, 3);
            double toZ = random.nextDouble(-2, 3);
            assertEquals(shape.clip(fromX, fromY, fromZ, toX, toY, toZ, 0, 0, 0) != null,
                    shape.intersects(fromX, fromY, fromZ, toX, toY, toZ, 0, 0, 0));
        }
    }
}
