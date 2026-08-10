package dev.foggy.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.bukkit.configuration.file.FileConfiguration;

/** Immutable, validated runtime settings for Foggy. */
public final class FoggyConfig {
    private final double visibilityRadius;
    private final int spatialCellSize;
    private final int hideConfirmationTicks;
    private final boolean useFov;
    private final float fallbackFov;
    private final float fovMargin;
    private final float fallbackAspectRatio;
    private final double thirdPersonDistance;
    private final int cameraDistanceSamples;
    private final double cameraSourceMargin;
    private final boolean includeFrontCamera;
    private final boolean includeBackCamera;
    private final int interpolationSamples;
    private final double endpointEpsilon;
    private final int worldCacheValidationTicks;
    private final int worldCacheRetentionTicks;
    private final int decisionCacheTicks;
    private final TransparentBlockMode transparentBlockMode;
    private final List<String> transparentMaterials;
    private final TransparentBlockMode cutoutBlockMode;
    private final List<String> cutoutMaterials;
    private final List<String> opaqueMaterialOverrides;
    private final boolean potionInvisibility;
    private final boolean entityInvisibleFlag;
    private final boolean spectatorInvisibility;
    private final boolean respectCanSee;
    private final boolean reflectiveVanishHooks;
    private final boolean preserveVanillaInvisibleEntity;
    private final boolean companionEnabled;
    private final String companionChannel;
    private final long companionTtlMillis;
    private final double maxCameraOffset;
    private final float minFov;
    private final float maxFov;

    /**
     * Creates a complete immutable configuration.
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
     * @param worldCacheValidationTicks maximum block-state cache age when no invalidation event fired
     * @param worldCacheRetentionTicks unused compensated-world sections are evicted after this age
     * @param decisionCacheTicks maximum reuse age for an unchanged entity-pair optical decision
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
     * @param preserveVanillaInvisibleEntity retain vanilla-invisible entities for equipment and hits
     * @param companionEnabled accept authenticated-by-connection camera telemetry
     * @param companionChannel plugin messaging channel
     * @param companionTtlMillis maximum age of a companion sample
     * @param maxCameraOffset maximum accepted companion camera offset
     * @param minFov minimum accepted companion FOV
     * @param maxFov maximum accepted companion FOV
     */
    public FoggyConfig(double visibilityRadius, int spatialCellSize, int hideConfirmationTicks,
                       boolean useFov, float fallbackFov, float fovMargin,
                       float fallbackAspectRatio, double thirdPersonDistance,
                       int cameraDistanceSamples, double cameraSourceMargin,
                       boolean includeFrontCamera, boolean includeBackCamera,
                       int interpolationSamples, double endpointEpsilon,
                       int worldCacheValidationTicks, int worldCacheRetentionTicks,
                       int decisionCacheTicks, TransparentBlockMode transparentBlockMode,
                       List<String> transparentMaterials, TransparentBlockMode cutoutBlockMode,
                       List<String> cutoutMaterials, List<String> opaqueMaterialOverrides,
                       boolean potionInvisibility, boolean entityInvisibleFlag,
                       boolean spectatorInvisibility, boolean respectCanSee,
                       boolean reflectiveVanishHooks, boolean preserveVanillaInvisibleEntity,
                       boolean companionEnabled, String companionChannel, long companionTtlMillis,
                       double maxCameraOffset, float minFov, float maxFov) {
        this.visibilityRadius = visibilityRadius;
        this.spatialCellSize = spatialCellSize;
        this.hideConfirmationTicks = hideConfirmationTicks;
        this.useFov = useFov;
        this.fallbackFov = fallbackFov;
        this.fovMargin = fovMargin;
        this.fallbackAspectRatio = fallbackAspectRatio;
        this.thirdPersonDistance = thirdPersonDistance;
        this.cameraDistanceSamples = cameraDistanceSamples;
        this.cameraSourceMargin = cameraSourceMargin;
        this.includeFrontCamera = includeFrontCamera;
        this.includeBackCamera = includeBackCamera;
        this.interpolationSamples = interpolationSamples;
        this.endpointEpsilon = endpointEpsilon;
        this.worldCacheValidationTicks = worldCacheValidationTicks;
        this.worldCacheRetentionTicks = worldCacheRetentionTicks;
        this.decisionCacheTicks = decisionCacheTicks;
        this.transparentBlockMode = transparentBlockMode;
        this.transparentMaterials = immutable(transparentMaterials);
        this.cutoutBlockMode = cutoutBlockMode;
        this.cutoutMaterials = immutable(cutoutMaterials);
        this.opaqueMaterialOverrides = immutable(opaqueMaterialOverrides);
        this.potionInvisibility = potionInvisibility;
        this.entityInvisibleFlag = entityInvisibleFlag;
        this.spectatorInvisibility = spectatorInvisibility;
        this.respectCanSee = respectCanSee;
        this.reflectiveVanishHooks = reflectiveVanishHooks;
        this.preserveVanillaInvisibleEntity = preserveVanillaInvisibleEntity;
        this.companionEnabled = companionEnabled;
        this.companionChannel = companionChannel;
        this.companionTtlMillis = companionTtlMillis;
        this.maxCameraOffset = maxCameraOffset;
        this.minFov = minFov;
        this.maxFov = maxFov;
    }

    public double visibilityRadius() { return visibilityRadius; }
    public int spatialCellSize() { return spatialCellSize; }
    public int hideConfirmationTicks() { return hideConfirmationTicks; }
    public boolean useFov() { return useFov; }
    public float fallbackFov() { return fallbackFov; }
    public float fovMargin() { return fovMargin; }
    public float fallbackAspectRatio() { return fallbackAspectRatio; }
    public double thirdPersonDistance() { return thirdPersonDistance; }
    public int cameraDistanceSamples() { return cameraDistanceSamples; }
    public double cameraSourceMargin() { return cameraSourceMargin; }
    public boolean includeFrontCamera() { return includeFrontCamera; }
    public boolean includeBackCamera() { return includeBackCamera; }
    public int interpolationSamples() { return interpolationSamples; }
    public double endpointEpsilon() { return endpointEpsilon; }
    public int worldCacheValidationTicks() { return worldCacheValidationTicks; }
    public int worldCacheRetentionTicks() { return worldCacheRetentionTicks; }
    public int decisionCacheTicks() { return decisionCacheTicks; }
    public TransparentBlockMode transparentBlockMode() { return transparentBlockMode; }
    public List<String> transparentMaterials() { return transparentMaterials; }
    public TransparentBlockMode cutoutBlockMode() { return cutoutBlockMode; }
    public List<String> cutoutMaterials() { return cutoutMaterials; }
    public List<String> opaqueMaterialOverrides() { return opaqueMaterialOverrides; }
    public boolean potionInvisibility() { return potionInvisibility; }
    public boolean entityInvisibleFlag() { return entityInvisibleFlag; }
    public boolean spectatorInvisibility() { return spectatorInvisibility; }
    public boolean respectCanSee() { return respectCanSee; }
    public boolean reflectiveVanishHooks() { return reflectiveVanishHooks; }
    public boolean preserveVanillaInvisibleEntity() { return preserveVanillaInvisibleEntity; }
    public boolean companionEnabled() { return companionEnabled; }
    public String companionChannel() { return companionChannel; }
    public long companionTtlMillis() { return companionTtlMillis; }
    public double maxCameraOffset() { return maxCameraOffset; }
    public float minFov() { return minFov; }
    public float maxFov() { return maxFov; }

    private static final List<String> DEFAULT_TRANSPARENT_MATERIALS = Arrays.asList(
            "AIR", "CAVE_AIR", "VOID_AIR",
            "WATER", "STATIONARY_WATER", "LAVA", "STATIONARY_LAVA", "BUBBLE_COLUMN",
            "GLASS", "*_STAINED_GLASS", "TINTED_GLASS",
            "STAINED_GLASS", "GLASS_PANE", "THIN_GLASS", "*_STAINED_GLASS_PANE",
            "STAINED_GLASS_PANE", "ICE", "PACKED_ICE", "FROSTED_ICE",
            "SLIME_BLOCK", "HONEY_BLOCK", "LEAVES", "LEAVES_2", "*_LEAVES",
            "PORTAL", "ENDER_PORTAL", "NETHER_PORTAL", "END_PORTAL", "END_GATEWAY",
            "BARRIER", "STRUCTURE_VOID", "LIGHT",
            "BEACON"
    );
    private static final List<String> DEFAULT_CUTOUT_MATERIALS = Arrays.asList(
            "FENCE", "NETHER_FENCE", "FENCE_GATE", "COBBLE_WALL", "*_FENCE", "*_FENCE_GATE"
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
        int validationTicks = bounded(config.getInt("raycast.cache.validation-ticks", 20),
                1, 200, "raycast.cache.validation-ticks");
        int retentionTicks = bounded(config.getInt("raycast.cache.retention-ticks", 600),
                20, 12_000, "raycast.cache.retention-ticks");
        int decisionTicks = bounded(config.getInt("raycast.cache.decision-ticks", 20),
                1, 100, "raycast.cache.decision-ticks");
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
                config, "raycast.opaque-material-overrides", Collections.<String>emptyList());
        long ttl = bounded(config.getLong("companion.sample-ttl-millis", 1500L), 50L, 30_000L, "companion.sample-ttl-millis");
        double maxOffset = bounded(config.getDouble("companion.max-camera-offset-blocks", 5.0), 0.0, 16.0, "companion.max-camera-offset-blocks");
        float minFov = (float) bounded(config.getDouble("companion.min-fov-degrees", 10.0), 1.0, 178.0, "companion.min-fov-degrees");
        float maxFov = (float) bounded(config.getDouble("companion.max-fov-degrees", 150.0), minFov, 179.0, "companion.max-fov-degrees");

        return new FoggyConfig(
                radius, cell, debounce, config.getBoolean("visibility.use-fov", true),
                fallbackFov, fovMargin, aspect, thirdDistance, cameraSamples, sourceMargin,
                config.getBoolean("camera.include-front-third-person", true),
                config.getBoolean("camera.include-back-third-person", true), interpolation, epsilon,
                validationTicks, retentionTicks, decisionTicks,
                transparentMode, transparentMaterials, cutoutMode, cutoutMaterials, opaqueOverrides,
                config.getBoolean("invisibility.potion-effect", true),
                config.getBoolean("invisibility.entity-invisible-flag", true),
                config.getBoolean("invisibility.spectator", true),
                config.getBoolean("invisibility.respect-bukkit-can-see", true),
                config.getBoolean("invisibility.reflective-vanish-hooks", true),
                config.getBoolean("invisibility.preserve-vanilla-entity", true),
                config.getBoolean("companion.enabled", true),
                config.getString("companion.channel", "foggy:camera"), ttl, maxOffset, minFov, maxFov
        );
    }

    private static List<String> stringListOrDefault(FileConfiguration config, String path, List<String> defaults) {
        List<String> values = config.contains(path) ? config.getStringList(path) : defaults;
        List<String> result = new ArrayList<String>();
        for (String value : values) {
            String trimmed = value.trim();
            if (!trimmed.isEmpty()) result.add(trimmed);
        }
        return Collections.unmodifiableList(result);
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

    private static List<String> immutable(List<String> values) {
        return Collections.unmodifiableList(new ArrayList<String>(values));
    }
}
