package dev.foggy.raycast;

import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

/**
 * One ray selected for operator particle rendering.
 *
 * @param from camera origin
 * @param to target hitbox point
 * @param hit blocking voxel hit, or null for a clear ray
 * @param clear whether the ray reaches the target
 * @param blockingBlock description of the terminating OUTLINE shape
 * @param firstOutline first shape hit without transparency filtering
 */
public record RayDebugLine(
        Vector from,
        Vector to,
        @Nullable Vector hit,
        boolean clear,
        @Nullable BlockGeometryHit blockingBlock,
        @Nullable BlockGeometryHit firstOutline
) {
}
