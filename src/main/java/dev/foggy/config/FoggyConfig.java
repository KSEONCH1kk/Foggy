package dev.foggy.config;

import java.util.List;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Immutable, validated runtime settings for Foggy.
 *
 * @param visibilityRadius maximum distance at which Foggy manages a player pair
 * @param spatialCellSize spatial-index cell edge in blocks
 * @param hideConfirmationTicks consecutive optical-hide decisions required before hiding
 * @param useFov whether targets outside all plausible view frusta are hidden
 * @param fallbackFov fallback vertical FOV in degrees
 * @param fovMargin additional fallback FOV margin in degrees
 * @param fallbackAspectRatio assumed framebuffer aspect ratio
 * @param thirdPersonDistance vanilla fallback camera distance
 * @param cameraDistanceSamples number of samples between eye and third-person camera
 * @param cameraSourceMargin lateral/up source expansion in blocks
 * @param includeFrontCamera include the front-facing F5 camera in fallback poses
 * @param includeBackCamera include the rear F5 camera in fallback poses
 * @param interpolationSamples number of target positions sampled over the previous tick
 * @param endpointEpsilon ray length removed at the target endpoint
 * @param transparentBlockMode whether configured transparent materials pass or terminate rays
 * @param transparentMaterials material-name globs treated as optically transparent
 * @param cutoutBlockMode whether vanilla/configured cutout materials pass or terminate rays
 * @param cutoutMaterials extra material-name globs treated as conservative client-model cutouts
 * @param opaqueMaterialOverrides material-name globs that always terminate rays
 * @param potionInvisibility track the invisibility potion effect
 * @param entityInvisibleFlag track {@code Entity#isInvisible()}
 * @param spectatorInvisibility hide spectator targets
 * @param respectCanSee respect visibility state published by other Bukkit plugins
 * @param reflectiveVanishHooks enable optional SuperVanish/PremiumVanish reflection hooks
 * @param companionEnabled accept authenticated-by-connection camera telemetry
 * @param companionChannel plugin messaging channel
 * @param companionTtlMillis maximum age of a companion sample
 * @param maxCameraOffset maximum accepted companion camera offset
 * @param minFov minimum accepted companion FOV
 * @param maxFov maximum accepted companion FOV
 */
public record FoggyConfig(
        double visibilityRadius,
        int spatialCellSize,
        int hideConfirmationTicks,
        boolean useFov,
        float fallbackFov,
        float fovMargin,
        float fallbackAspectRatio,
        double thirdPersonDistance,
        int cameraDistanceSamples,
        double cameraSourceMargin,
        boolean includeFrontCamera,
        boolean includeBackCamera,
        int interpolationSamples,
        double endpointEpsilon,
        TransparentBlockMode transparentBlockMode,
        List<String> transparentMaterials,
        TransparentBlockMode cutoutBlockMode,
        List<String> cutoutMaterials,
        List<String> opaqueMaterialOverrides,
        boolean potionInvisibility,
        boolean entityInvisibleFlag,
        boolean spectatorInvisibility,
        boolean respectCanSee,
        boolean reflectiveVanishHooks,
        boolean companionEnabled,
        String companionChannel,
        long companionTtlMillis,
        double maxCameraOffset,
        float minFov,
        float maxFov
) {
    private static final List<String> DEFAULT_TRANSPARENT_MATERIALS = List.of(
            "AIR", "CAVE_AIR", "VOID_AIR",
            "WATER", "LAVA", "BUBBLE_COLUMN",
            "GLASS", "*_STAINED_GLASS", "TINTED_GLASS",
            "GLASS_PANE", "*_STAINED_GLASS_PANE",
            "ICE", "FROSTED_ICE", "SLIME_BLOCK", "HONEY_BLOCK",
            "*_LEAVES",
            "NETHER_PORTAL", "END_PORTAL", "END_GATEWAY",
            "BARRIER", "STRUCTURE_VOID", "LIGHT",
            "BEACON"
    );
    private static final List<String> DEFAULT_CUTOUT_MATERIALS = List.of(
            "*_FENCE", "*_FENCE_GATE"
    );

    /**
     * Loads and range-checks settings from Bukkit configuration.
     *
     * @param config source Bukkit configuration
     * @return validated immutable settings
     */
    public static FoggyConfig load(FileConfiguration config) {
        double radius = bounded(config.getDouble("visibility.radius-blocks", 128.0), 1.0, 512.0, "visibility.radius-blocks");
        int cell = bounded(config.getInt("visibility.spatial-cell-size", 16), 4, 64, "visibility.spatial-cell-size");
        int debounce = bounded(config.getInt("visibility.hide-confirmation-ticks", 1), 1, 20, "visibility.hide-confirmation-ticks");
        float fallbackFov = (float) bounded(config.getDouble("fov.fallback-vertical-degrees", 70.0), 1.0, 179.0, "fov.fallback-vertical-degrees");
        float fovMargin = (float) bounded(config.getDouble("fov.conservative-margin-degrees", 20.0), 0.0, 90.0, "fov.conservative-margin-degrees");
        float aspect = (float) bounded(config.getDouble("fov.fallback-aspect-ratio", 16.0 / 9.0), 0.25, 8.0, "fov.fallback-aspect-ratio");
        double thirdDistance = bounded(config.getDouble("camera.fallback-third-person-distance", 4.0), 0.0, 8.0, "camera.fallback-third-person-distance");
        int cameraSamples = bounded(config.getInt("camera.distance-samples", 4), 1, 16, "camera.distance-samples");
        double sourceMargin = bounded(config.getDouble("camera.source-margin-blocks", 0.1), 0.0, 1.0, "camera.source-margin-blocks");
        int interpolation = bounded(config.getInt("raycast.interpolation-samples", 3), 1, 8, "raycast.interpolation-samples");
        double epsilon = bounded(config.getDouble("raycast.endpoint-epsilon", 0.0001), 0.0, 0.1, "raycast.endpoint-epsilon");
        TransparentBlockMode transparentMode = TransparentBlockMode.parse(
                config.getString("raycast.transparent-block-mode", "pass-through"));
        List<String> transparentMaterials = stringListOrDefault(
                config, "raycast.transparent-materials", DEFAULT_TRANSPARENT_MATERIALS);
        TransparentBlockMode cutoutMode = TransparentBlockMode.parse(
                config.getString("raycast.cutout-block-mode", "pass-through"),
                "raycast.cutout-block-mode");
        List<String> cutoutMaterials = stringListOrDefault(
                config, "raycast.cutout-materials", DEFAULT_CUTOUT_MATERIALS);
        List<String> opaqueOverrides = stringListOrDefault(
                config, "raycast.opaque-material-overrides", List.of());
        long ttl = bounded(config.getLong("companion.sample-ttl-millis", 1500L), 50L, 30_000L, "companion.sample-ttl-millis");
        double maxOffset = bounded(config.getDouble("companion.max-camera-offset-blocks", 5.0), 0.0, 16.0, "companion.max-camera-offset-blocks");
        float minFov = (float) bounded(config.getDouble("companion.min-fov-degrees", 10.0), 1.0, 178.0, "companion.min-fov-degrees");
        float maxFov = (float) bounded(config.getDouble("companion.max-fov-degrees", 150.0), minFov, 179.0, "companion.max-fov-degrees");

        return new FoggyConfig(
                radius, cell, debounce, config.getBoolean("visibility.use-fov", true),
                fallbackFov, fovMargin, aspect, thirdDistance, cameraSamples, sourceMargin,
                config.getBoolean("camera.include-front-third-person", true),
                config.getBoolean("camera.include-back-third-person", true), interpolation, epsilon,
                transparentMode, transparentMaterials, cutoutMode, cutoutMaterials, opaqueOverrides,
                config.getBoolean("invisibility.potion-effect", true),
                config.getBoolean("invisibility.entity-invisible-flag", true),
                config.getBoolean("invisibility.spectator", true),
                config.getBoolean("invisibility.respect-bukkit-can-see", true),
                config.getBoolean("invisibility.reflective-vanish-hooks", true),
                config.getBoolean("companion.enabled", true),
                config.getString("companion.channel", "foggy:camera"), ttl, maxOffset, minFov, maxFov
        );
    }

    private static List<String> stringListOrDefault(FileConfiguration config, String path, List<String> defaults) {
        List<String> values = config.contains(path) ? config.getStringList(path) : defaults;
        return values.stream().map(String::trim).filter(value -> !value.isEmpty()).toList();
    }

    private static double bounded(double value, double min, double max, String path) {
        if (!Double.isFinite(value) || value < min || value > max) {
            throw new IllegalArgumentException(path + " must be in [" + min + ", " + max + "]");
        }
        return value;
    }

    private static int bounded(int value, int min, int max, String path) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(path + " must be in [" + min + ", " + max + "]");
        }
        return value;
    }

    private static long bounded(long value, long min, long max, String path) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(path + " must be in [" + min + ", " + max + "]");
        }
        return value;
    }
}
