package dev.foggy.camera;

/** Validated immutable camera telemetry from a client companion. */
public final class CameraSample {
    private final long sequence;
    private final Perspective perspective;
    private final float verticalFovDegrees;
    private final float aspectRatio;
    private final float offsetRight;
    private final float offsetUp;
    private final float offsetForward;
    private final long receivedNanos;

    public CameraSample(long sequence, Perspective perspective, float verticalFovDegrees,
                        float aspectRatio, float offsetRight, float offsetUp, float offsetForward,
                        long receivedNanos) {
        this.sequence = sequence;
        this.perspective = perspective;
        this.verticalFovDegrees = verticalFovDegrees;
        this.aspectRatio = aspectRatio;
        this.offsetRight = offsetRight;
        this.offsetUp = offsetUp;
        this.offsetForward = offsetForward;
        this.receivedNanos = receivedNanos;
    }

    public long sequence() { return sequence; }
    public Perspective perspective() { return perspective; }
    public float verticalFovDegrees() { return verticalFovDegrees; }
    public float aspectRatio() { return aspectRatio; }
    public float offsetRight() { return offsetRight; }
    public float offsetUp() { return offsetUp; }
    public float offsetForward() { return offsetForward; }
    public long receivedNanos() { return receivedNanos; }
}
