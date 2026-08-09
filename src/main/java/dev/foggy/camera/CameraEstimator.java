package dev.foggy.camera;

import dev.foggy.config.FoggyConfig;
import dev.foggy.raycast.VanillaBlockRaycaster;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * Estimates all camera poses that an unmodified server must conservatively consider.
 *
 * <p>This follows the constants and direction of Mojang 1.21.4
 * {@code Camera#setup(...)} / {@code Camera#getMaxZoom(float)}: third-person distance is
 * {@code 4 * entityScale} and is shortened by block clipping. The Bukkit approximation uses
 * collision-shape tracing; exact client perspective/FOV/offset can be supplied by the companion.</p>
 */
public final class CameraEstimator {
    private static final double CAMERA_CLIP_PADDING = 0.10;

    private final FoggyConfig config;
    private final CompanionCameraRegistry companion;
    private final VanillaBlockRaycaster blockRaycaster;

    /**
     * Creates an estimator with fallback and companion sources.
     *
     * @param config camera/FOV limits
     * @param companion current companion telemetry
     * @param blockRaycaster Minecraft shape bridge used for camera clipping
     */
    public CameraEstimator(FoggyConfig config, CompanionCameraRegistry companion,
                           VanillaBlockRaycaster blockRaycaster) {
        this.config = config;
        this.companion = companion;
        this.blockRaycaster = blockRaycaster;
    }

    /**
     * Returns either one companion-derived pose or the union of first-person and plausible F5 poses.
     * The union prevents false hiding when perspective is unknowable to the server.
     *
     * @param viewer player whose camera is estimated
     * @return nonempty immutable camera-pose list
     */
    public List<CameraPose> estimate(Player viewer) {
        Basis basis = basis(viewer.getEyeLocation().getDirection());
        Vector eye = viewer.getEyeLocation().toVector();
        Optional<CameraSample> exact = companion.current(viewer);
        if (exact.isPresent()) {
            CameraSample sample = exact.get();
            Vector position = eye.clone()
                    .add(basis.right().clone().multiply(sample.offsetRight()))
                    .add(basis.up().clone().multiply(sample.offsetUp()))
                    .add(basis.forward().clone().multiply(sample.offsetForward()));
            Vector look = sample.perspective() == Perspective.THIRD_PERSON_FRONT
                    ? basis.forward().clone().multiply(-1.0) : basis.forward();
            Basis cameraBasis = basis(look);
            return expanded(viewer.getWorld(), "COMPANION:" + sample.perspective(), position, cameraBasis,
                    sample.verticalFovDegrees(), sample.aspectRatio(), true);
        }

        float fov = Math.min(179.0f, config.fallbackFov() + config.fovMargin());
        List<CameraPose> result = new ArrayList<>();
        result.addAll(expanded(viewer.getWorld(), "FALLBACK:FIRST", eye, basis,
                fov, config.fallbackAspectRatio(), false));
        AttributeInstance scaleAttribute = viewer.getAttribute(Attribute.SCALE);
        double scale = scaleAttribute == null ? 1.0 : scaleAttribute.getValue();
        double maxDistance = config.thirdPersonDistance() * Math.max(0.0625, Math.min(16.0, scale));

        if (config.includeBackCamera()) {
            addThirdPersonSamples(result, viewer.getWorld(), eye, basis, basis.forward().clone().multiply(-1.0),
                    maxDistance, fov, false, "FALLBACK:THIRD_BACK");
        }
        if (config.includeFrontCamera()) {
            Basis frontBasis = basis(basis.forward().clone().multiply(-1.0));
            addThirdPersonSamples(result, viewer.getWorld(), eye, frontBasis, basis.forward(),
                    maxDistance, fov, false, "FALLBACK:THIRD_FRONT");
        }
        return List.copyOf(result);
    }

    private void addThirdPersonSamples(List<CameraPose> output, World world, Vector eye, Basis lookBasis,
                                       Vector offsetDirection, double maxDistance, float fov, boolean exact,
                                       String source) {
        for (int i = 1; i <= config.cameraDistanceSamples(); i++) {
            double requested = maxDistance * i / config.cameraDistanceSamples();
            Vector position = clippedPosition(world, eye, offsetDirection, requested);
            output.addAll(expanded(world, source + "#" + i, position, lookBasis,
                    fov, config.fallbackAspectRatio(), exact));
        }
    }

    private Vector clippedPosition(World world, Vector eye, Vector offsetDirection, double requested) {
        if (requested <= 0.0) {
            return eye.clone();
        }
        Vector direction = offsetDirection.clone().normalize();
        Vector requestedPosition = eye.clone().add(direction.clone().multiply(requested));
        RayTraceResult hit = blockRaycaster.traceCollision(world, eye, requestedPosition);
        double distance = requested;
        if (hit != null && hit.getHitPosition() != null) {
            distance = Math.max(0.0, Math.min(requested,
                    hit.getHitPosition().distance(eye) - CAMERA_CLIP_PADDING));
        }
        return eye.clone().add(direction.multiply(distance));
    }

    private List<CameraPose> expanded(World world, String source, Vector position, Basis basis,
                                      float fov, float aspect, boolean exact) {
        CameraPose center = new CameraPose(world, source, position.clone(), basis.forward(), basis.right(),
                basis.up(), fov, aspect, exact);
        double margin = config.cameraSourceMargin();
        if (margin == 0.0 || exact) {
            return List.of(center);
        }
        List<CameraPose> poses = new ArrayList<>(5);
        poses.add(center);
        poses.add(poseAt(center, source + ":RIGHT", position.clone().add(basis.right().clone().multiply(margin))));
        poses.add(poseAt(center, source + ":LEFT", position.clone().subtract(basis.right().clone().multiply(margin))));
        poses.add(poseAt(center, source + ":UP", position.clone().add(basis.up().clone().multiply(margin))));
        poses.add(poseAt(center, source + ":DOWN", position.clone().subtract(basis.up().clone().multiply(margin))));
        return poses;
    }

    private static CameraPose poseAt(CameraPose source, String label, Vector position) {
        return new CameraPose(source.world(), label, position, source.forward(), source.right(), source.up(),
                source.verticalFovDegrees(), source.aspectRatio(), source.companionExact());
    }

    private static Basis basis(Vector rawForward) {
        Vector forward = rawForward.clone();
        if (forward.lengthSquared() < 1.0E-12) {
            forward.setZ(1.0);
        }
        forward.normalize();
        Vector right = forward.clone().crossProduct(new Vector(0.0, 1.0, 0.0));
        if (right.lengthSquared() < 1.0E-8) {
            right = new Vector(1.0, 0.0, 0.0);
        } else {
            right.normalize();
        }
        Vector up = right.clone().crossProduct(forward).normalize();
        return new Basis(forward, right, up);
    }

    private record Basis(Vector forward, Vector right, Vector up) {
    }
}
