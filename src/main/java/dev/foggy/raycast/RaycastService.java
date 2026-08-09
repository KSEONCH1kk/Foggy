package dev.foggy.raycast;

import dev.foggy.camera.CameraPose;
import java.util.List;
import org.bukkit.entity.Player;

/** Performs per-viewer target visibility tests against world OUTLINE voxel shapes. */
public interface RaycastService {
    /**
     * Computes whether any sampled target point is in a plausible FOV and unobstructed.
     *
     * @param target target player
     * @param cameras plausible viewer cameras
     * @return optical visibility result
     */
    OpticalResult evaluate(Player target, List<CameraPose> cameras);
}
