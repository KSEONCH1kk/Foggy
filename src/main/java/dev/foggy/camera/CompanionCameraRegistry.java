package dev.foggy.camera;

import dev.foggy.config.FoggyConfig;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

/**
 * Receives the optional {@code foggy:camera} binary protocol.
 *
 * <p>The transport authenticates only the player's connection, not the truthfulness of the
 * values. Operators using Foggy as an anti-cheat boundary must therefore treat companion data
 * as advisory or require a separately attested mod pack.</p>
 */
public final class CompanionCameraRegistry implements PluginMessageListener {
    /** Current wire protocol version. */
    public static final byte PROTOCOL_VERSION = 1;
    /** Exact payload size: version, perspective, sequence and five floats. */
    public static final int PAYLOAD_BYTES = 30;

    private final FoggyConfig config;
    private final Map<UUID, CameraSample> samples = new ConcurrentHashMap<>();

    /**
     * Creates a camera telemetry registry.
     *
     * @param config validated telemetry limits
     */
    public CompanionCameraRegistry(FoggyConfig config) {
        this.config = config;
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte @NotNull [] message) {
        if (!config.companionEnabled() || !config.companionChannel().equals(channel) || message.length != PAYLOAD_BYTES) {
            return;
        }
        ByteBuffer input = ByteBuffer.wrap(message);
        if (input.get() != PROTOCOL_VERSION) {
            return;
        }
        try {
            Perspective perspective = Perspective.fromWire(Byte.toUnsignedInt(input.get()));
            long sequence = input.getLong();
            float fov = input.getFloat();
            float aspect = input.getFloat();
            float right = input.getFloat();
            float up = input.getFloat();
            float forward = input.getFloat();
            if (!valid(fov, aspect, right, up, forward)) {
                return;
            }
            CameraSample sample = new CameraSample(sequence, perspective, fov, aspect, right, up, forward, System.nanoTime());
            samples.compute(player.getUniqueId(), (ignored, previous) ->
                    previous == null || Long.compareUnsigned(sequence, previous.sequence()) > 0 ? sample : previous);
        } catch (IllegalArgumentException ignored) {
            // Malformed untrusted client payloads are dropped without affecting the network thread.
        }
    }

    /**
     * Returns a non-expired sample for the player.
     *
     * @param player owning connection
     * @return current sample, or empty when missing/stale
     */
    public Optional<CameraSample> current(Player player) {
        CameraSample sample = samples.get(player.getUniqueId());
        if (sample == null) {
            return Optional.empty();
        }
        long age = System.nanoTime() - sample.receivedNanos();
        if (age < 0 || age > config.companionTtlMillis() * 1_000_000L) {
            samples.remove(player.getUniqueId(), sample);
            return Optional.empty();
        }
        return Optional.of(sample);
    }

    /**
     * Removes telemetry when a connection closes.
     *
     * @param player departing player
     */
    public void remove(Player player) {
        samples.remove(player.getUniqueId());
    }

    /** Clears all connection-bound samples. */
    public void clear() {
        samples.clear();
    }

    private boolean valid(float fov, float aspect, float right, float up, float forward) {
        if (!Float.isFinite(fov) || !Float.isFinite(aspect) || !Float.isFinite(right)
                || !Float.isFinite(up) || !Float.isFinite(forward)) {
            return false;
        }
        if (fov < config.minFov() || fov > config.maxFov() || aspect < 0.25f || aspect > 8.0f) {
            return false;
        }
        double squaredOffset = (double) right * right + (double) up * up + (double) forward * forward;
        return squaredOffset <= config.maxCameraOffset() * config.maxCameraOffset();
    }
}
