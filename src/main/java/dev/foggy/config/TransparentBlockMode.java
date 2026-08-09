package dev.foggy.config;

import java.util.Locale;

/** Defines how configured optically transparent block materials affect visibility rays. */
public enum TransparentBlockMode {
    /** Transparent shapes are still inspected by Minecraft, but a ray may continue through them. */
    PASS_THROUGH,

    /** Transparent shapes terminate a ray just like opaque shapes. */
    OCCLUDE;

    /**
     * Parses the human-readable config value.
     *
     * @param value config value such as {@code pass-through} or {@code occlude}
     * @return parsed mode
     */
    public static TransparentBlockMode parse(String value) {
        return parse(value, "raycast.transparent-block-mode");
    }

    /**
     * Parses a human-readable mode and reports a caller-provided config path on error.
     *
     * @param value config value
     * @param path config path used in validation errors
     * @return parsed mode
     */
    public static TransparentBlockMode parse(String value, String path) {
        String normalized = value.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    path + " must be 'pass-through' or 'occlude'", exception);
        }
    }
}
