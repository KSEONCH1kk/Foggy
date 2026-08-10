package dev.foggy.math;

/** Immutable version-neutral axis-aligned box. */
public final class Aabb {
    private final double minX;
    private final double minY;
    private final double minZ;
    private final double maxX;
    private final double maxY;
    private final double maxZ;

    public Aabb(double minX, double minY, double minZ,
                double maxX, double maxY, double maxZ) {
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("Inverted AABB");
        }
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }

    public double minX() { return minX; }
    public double minY() { return minY; }
    public double minZ() { return minZ; }
    public double maxX() { return maxX; }
    public double maxY() { return maxY; }
    public double maxZ() { return maxZ; }
    public double widthX() { return maxX - minX; }
    public double height() { return maxY - minY; }
    public double widthZ() { return maxZ - minZ; }

    public boolean intersectsSegment(double fromX, double fromY, double fromZ,
                                     double toX, double toY, double toZ) {
        double[] interval = {0.0, 1.0};
        return clipAxis(fromX, toX - fromX, minX, maxX, interval)
                && clipAxis(fromY, toY - fromY, minY, maxY, interval)
                && clipAxis(fromZ, toZ - fromZ, minZ, maxZ, interval)
                && interval[0] < 1.0 - 1.0E-9;
    }

    private static boolean clipAxis(double origin, double direction, double min, double max,
                                    double[] interval) {
        if (Math.abs(direction) < 1.0E-12) return origin >= min && origin <= max;
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
