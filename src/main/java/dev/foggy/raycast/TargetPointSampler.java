package dev.foggy.raycast;

import dev.foggy.config.FoggyConfig;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/** Samples the target hitbox at partial-tick positions, including corners and face centers. */
public final class TargetPointSampler {
    private final FoggyConfig config;

    /**
     * Creates a hitbox sampler.
     *
     * @param config interpolation settings
     */
    public TargetPointSampler(FoggyConfig config) {
        this.config = config;
    }

    /**
     * Returns stable sample points across the swept, interpolated target hitbox.
     *
     * @param currentBox current target box captured on its owning entity scheduler
     * @param previous previous target position
     * @param current current target position
     * @return hitbox point list
     */
    public List<Vector> sample(BoundingBox currentBox, Vector previous, Vector current) {
        List<Vector> points = new ArrayList<>(config.interpolationSamples() * 11);
        int count = config.interpolationSamples();
        for (int index = 0; index < count; index++) {
            double t = count == 1 ? 1.0 : (double) index / (count - 1);
            Vector interpolated = previous.clone().multiply(1.0 - t).add(current.clone().multiply(t));
            Vector shift = interpolated.subtract(current);
            addBoxPoints(points, currentBox, shift);
        }
        return List.copyOf(points);
    }

    private static void addBoxPoints(List<Vector> output, BoundingBox box, Vector shift) {
        double insetX = Math.min(0.03, (box.getWidthX() * 0.1));
        double insetZ = Math.min(0.03, (box.getWidthZ() * 0.1));
        double minX = box.getMinX() + insetX + shift.getX();
        double maxX = box.getMaxX() - insetX + shift.getX();
        double minY = box.getMinY() + 0.02 + shift.getY();
        double maxY = box.getMaxY() - 0.02 + shift.getY();
        double minZ = box.getMinZ() + insetZ + shift.getZ();
        double maxZ = box.getMaxZ() - insetZ + shift.getZ();
        double centerX = (minX + maxX) * 0.5;
        double centerY = (minY + maxY) * 0.5;
        double centerZ = (minZ + maxZ) * 0.5;
        double lowerY = minY + (maxY - minY) * 0.25;
        double upperY = minY + (maxY - minY) * 0.75;

        output.add(new Vector(centerX, centerY, centerZ));
        output.add(new Vector(centerX, maxY, centerZ));
        output.add(new Vector(centerX, minY, centerZ));
        output.add(new Vector(minX, lowerY, minZ));
        output.add(new Vector(maxX, lowerY, minZ));
        output.add(new Vector(minX, lowerY, maxZ));
        output.add(new Vector(maxX, lowerY, maxZ));
        output.add(new Vector(minX, upperY, minZ));
        output.add(new Vector(maxX, upperY, minZ));
        output.add(new Vector(minX, upperY, maxZ));
        output.add(new Vector(maxX, upperY, maxZ));
    }
}
