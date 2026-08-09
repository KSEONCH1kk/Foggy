package dev.foggy.camera;

import org.bukkit.World;
import org.bukkit.util.Vector;

/**
 * One plausible client camera used by the conservative server-side visibility union.
 * All vectors are normalized and represented in Bukkit world coordinates.
 *
 * @param world camera world
 * @param source human-readable estimator source
 * @param position camera origin in world coordinates
 * @param forward normalized look vector
 * @param right normalized local-right vector
 * @param up normalized local-up vector
 * @param verticalFovDegrees vertical perspective angle
 * @param aspectRatio framebuffer width divided by height
 * @param companionExact whether the pose came from fresh companion telemetry
 */
public record CameraPose(
        World world,
        String source,
        Vector position,
        Vector forward,
        Vector right,
        Vector up,
        float verticalFovDegrees,
        float aspectRatio,
        boolean companionExact
) {
}
