package dev.foggy.raycast;

import dev.foggy.camera.CameraPose;
import dev.foggy.visibility.PlayerVisibilitySnapshot;
import java.util.List;

/** Performs per-viewer target visibility tests against world OUTLINE voxel shapes. */
public interface RaycastService {
    /**
     * Computes whether any sampled target point is in a plausible FOV and unobstructed.
     *
     * @param target immutable target snapshot captured by its owning region
     * @param cameras plausible viewer cameras
     * @return optical visibility result
     */
    OpticalResult evaluate(PlayerVisibilitySnapshot target, List<CameraPose> cameras);
}
