package dev.foggy.math;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("load")
class VisibilityLoadTest {
    @Test
    void measuresTwoHundredPlayerPairBudget() {
        int players = Integer.getInteger("foggy.load.players", 200);
        SplittableRandom random = new SplittableRandom(0xF099L);
        List<Point> positions = new ArrayList<>(players);
        List<Aabb> blockers = new ArrayList<>(64);
        for (int i = 0; i < players; i++) {
            positions.add(new Point(random.nextDouble(-128, 128), 1.62, random.nextDouble(-128, 128)));
        }
        for (int i = 0; i < 64; i++) {
            double x = random.nextDouble(-128, 128);
            double z = random.nextDouble(-128, 128);
            blockers.add(new Aabb(x, 0.0, z, x + 1.0, 3.0, z + 1.0));
        }

        long start = System.nanoTime();
        long checked = 0;
        long visible = 0;
        double radiusSquared = 96.0 * 96.0;
        for (int repeat = 0; repeat < 20; repeat++) {
            for (int i = 0; i < players; i++) {
                Point from = positions.get(i);
                for (int j = 0; j < players; j++) {
                    if (i == j) {
                        continue;
                    }
                    Point to = positions.get(j);
                    double dx = to.x - from.x;
                    double dz = to.z - from.z;
                    if (dx * dx + dz * dz > radiusSquared) {
                        continue;
                    }
                    checked++;
                    if (!SyntheticVoxelRaycaster.occluded(blockers, from.x, from.y, from.z, to.x, to.y, to.z)) {
                        visible++;
                    }
                }
            }
        }
        double millis = (System.nanoTime() - start) / 1_000_000.0;
        System.out.printf("Foggy synthetic load: players=%d checks=%d visible=%d time=%.3fms throughput=%.0f checks/s%n",
                players, checked, visible, millis, checked / (millis / 1000.0));
        assertTrue(checked > 0);
        assertTrue(visible >= 0);
    }

    private record Point(double x, double y, double z) {
    }
}
