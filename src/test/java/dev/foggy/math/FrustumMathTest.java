package dev.foggy.math;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FrustumMathTest {
    @Test
    void acceptsCenterAndRejectsPointBehindCamera() {
        assertTrue(inside(0.0, 0.0, 10.0, 70.0, 16.0 / 9.0));
        assertFalse(inside(0.0, 0.0, -1.0, 70.0, 16.0 / 9.0));
    }

    @Test
    void verticalFovConvertsThroughTangent() {
        double edge = Math.tan(Math.toRadians(70.0) / 2.0) * 10.0;
        assertTrue(inside(0.0, edge * 0.999, 10.0, 70.0, 16.0 / 9.0));
        assertFalse(inside(0.0, edge * 1.001, 10.0, 70.0, 16.0 / 9.0));
    }

    @Test
    void horizontalLimitIncludesAspectRatio() {
        double edge = Math.tan(Math.toRadians(70.0) / 2.0) * (16.0 / 9.0) * 10.0;
        assertTrue(inside(edge * 0.999, 0.0, 10.0, 70.0, 16.0 / 9.0));
        assertFalse(inside(edge * 1.001, 0.0, 10.0, 70.0, 16.0 / 9.0));
    }

    private static boolean inside(double x, double y, double z, double fov, double aspect) {
        return FrustumMath.contains(x, y, z,
                0.0, 0.0, 1.0,
                1.0, 0.0, 0.0,
                0.0, 1.0, 0.0,
                fov, aspect);
    }
}
