package dev.foggy.raycast;

import dev.foggy.camera.CameraPose;
import java.util.List;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

/**
 * Detailed result used only by the opt-in operator debugger.
 *
 * @param result final optical result
 * @param cameras camera poses evaluated
 * @param targetPoints interpolated hitbox samples
 * @param inFovSamples camera/point combinations inside a frustum
 * @param raysTraced world rays actually traced
 * @param blockedRays rays terminated by a block shape
 * @param displayRays bounded set of representative blocked rays
 * @param decisiveRay first clear ray, if visible
 * @param decisiveCameraIndex index of the camera that supplied the clear ray, or -1
 * @param elapsedNanos diagnostic calculation duration
 */
public record RaycastDebugSnapshot(
        OpticalResult result,
        List<CameraPose> cameras,
        List<Vector> targetPoints,
        int inFovSamples,
        int raysTraced,
        int blockedRays,
        List<RayDebugLine> displayRays,
        @Nullable RayDebugLine decisiveRay,
        int decisiveCameraIndex,
        long elapsedNanos
) {
}
