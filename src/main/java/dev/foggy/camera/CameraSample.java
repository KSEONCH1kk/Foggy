package dev.foggy.camera;

/**
 * Validated camera telemetry from a client companion.
 * Offsets use camera-local right/up/forward axes and blocks as units.
 *
 * @param sequence unsigned monotonic message sequence
 * @param perspective current client perspective
 * @param verticalFovDegrees effective vertical FOV
 * @param aspectRatio framebuffer width divided by height
 * @param offsetRight camera offset along the local right axis
 * @param offsetUp camera offset along the local up axis
 * @param offsetForward camera offset along the local forward axis
 * @param receivedNanos server monotonic receipt timestamp
 */
public record CameraSample(
        long sequence,
        Perspective perspective,
        float verticalFovDegrees,
        float aspectRatio,
        float offsetRight,
        float offsetUp,
        float offsetForward,
        long receivedNanos
) {
}
