package dev.foggy.math;

/** Pure projection/frustum helpers shared by production code and unit tests. */
public final class FrustumMath {
    private FrustumMath() {
    }

    /**
     * Tests a point against a symmetric perspective frustum.
     *
     * <p>This is algebraically equivalent to transforming by a perspective projection and
     * checking NDC x/y in [-1, 1]: {@code |viewX/viewZ| <= tan(fovY/2)*aspect} and
     * {@code |viewY/viewZ| <= tan(fovY/2)}.</p>
     *
     * @param dx point x relative to camera
     * @param dy point y relative to camera
     * @param dz point z relative to camera
     * @param forwardX forward-basis x
     * @param forwardY forward-basis y
     * @param forwardZ forward-basis z
     * @param rightX right-basis x
     * @param rightY right-basis y
     * @param rightZ right-basis z
     * @param upX up-basis x
     * @param upY up-basis y
     * @param upZ up-basis z
     * @param verticalFovDegrees vertical perspective angle
     * @param aspectRatio framebuffer width divided by height
     * @return whether the point lies inside the side planes and ahead of the camera
     */
    public static boolean contains(
            double dx, double dy, double dz,
            double forwardX, double forwardY, double forwardZ,
            double rightX, double rightY, double rightZ,
            double upX, double upY, double upZ,
            double verticalFovDegrees, double aspectRatio
    ) {
        double viewZ = dx * forwardX + dy * forwardY + dz * forwardZ;
        if (viewZ <= 1.0E-7) {
            return false;
        }
        double viewX = dx * rightX + dy * rightY + dz * rightZ;
        double viewY = dx * upX + dy * upY + dz * upZ;
        double tanHalfVertical = Math.tan(Math.toRadians(verticalFovDegrees) * 0.5);
        return Math.abs(viewY / viewZ) <= tanHalfVertical
                && Math.abs(viewX / viewZ) <= tanHalfVertical * aspectRatio;
    }
}
