package dev.foggy.math;

/**
 * Immutable axis-aligned box used by deterministic synthetic raycast tests.
 *
 * @param minX minimum x
 * @param minY minimum y
 * @param minZ minimum z
 * @param maxX maximum x
 * @param maxY maximum y
 * @param maxZ maximum z
 */
public record Aabb(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
    /** Creates a validated box. */
    public Aabb {
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("Inverted AABB");
        }
    }

    /**
     * Returns true when the closed segment intersects this box before its endpoint.
     *
     * @param fromX source x
     * @param fromY source y
     * @param fromZ source z
     * @param toX destination x
     * @param toY destination y
     * @param toZ destination z
     * @return whether this shape blocks the segment
     */
    public boolean intersectsSegment(double fromX, double fromY, double fromZ,
                                     double toX, double toY, double toZ) {
        double[] interval = {0.0, 1.0};
        return clipAxis(fromX, toX - fromX, minX, maxX, interval)
                && clipAxis(fromY, toY - fromY, minY, maxY, interval)
                && clipAxis(fromZ, toZ - fromZ, minZ, maxZ, interval)
                && interval[0] < 1.0 - 1.0E-9;
    }

    private static boolean clipAxis(double origin, double direction, double min, double max, double[] interval) {
        if (Math.abs(direction) < 1.0E-12) {
            return origin >= min && origin <= max;
        }
        double t1 = (min - origin) / direction;
        double t2 = (max - origin) / direction;
        if (t1 > t2) {
            double swap = t1;
            t1 = t2;
            t2 = swap;
        }
        interval[0] = Math.max(interval[0], t1);
        interval[1] = Math.min(interval[1], t2);
        return interval[0] <= interval[1];
    }
}
