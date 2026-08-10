package dev.foggy.raycast;

import dev.foggy.camera.CameraPose;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

/** Detailed immutable result used only by the opt-in operator debugger. */
public final class RaycastDebugSnapshot {
    private final OpticalResult result;
    private final List<CameraPose> cameras;
    private final List<Vector> targetPoints;
    private final int inFovSamples;
    private final int raysTraced;
    private final int blockedRays;
    private final List<RayDebugLine> displayRays;
    private final RayDebugLine decisiveRay;
    private final int decisiveCameraIndex;
    private final long elapsedNanos;

    public RaycastDebugSnapshot(OpticalResult result, List<CameraPose> cameras,
                                List<Vector> targetPoints, int inFovSamples, int raysTraced,
                                int blockedRays, List<RayDebugLine> displayRays,
                                @Nullable RayDebugLine decisiveRay, int decisiveCameraIndex,
                                long elapsedNanos) {
        this.result = result;
        this.cameras = Collections.unmodifiableList(new ArrayList<CameraPose>(cameras));
        this.targetPoints = Collections.unmodifiableList(new ArrayList<Vector>(targetPoints));
        this.inFovSamples = inFovSamples;
        this.raysTraced = raysTraced;
        this.blockedRays = blockedRays;
        this.displayRays = Collections.unmodifiableList(new ArrayList<RayDebugLine>(displayRays));
        this.decisiveRay = decisiveRay;
        this.decisiveCameraIndex = decisiveCameraIndex;
        this.elapsedNanos = elapsedNanos;
    }

    public OpticalResult result() { return result; }
    public List<CameraPose> cameras() { return cameras; }
    public List<Vector> targetPoints() { return targetPoints; }
    public int inFovSamples() { return inFovSamples; }
    public int raysTraced() { return raysTraced; }
    public int blockedRays() { return blockedRays; }
    public List<RayDebugLine> displayRays() { return displayRays; }
    public @Nullable RayDebugLine decisiveRay() { return decisiveRay; }
    public int decisiveCameraIndex() { return decisiveCameraIndex; }
    public long elapsedNanos() { return elapsedNanos; }
}
