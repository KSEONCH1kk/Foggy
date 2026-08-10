package dev.foggy.raycast;

import dev.foggy.config.FoggyConfig;
import dev.foggy.config.TransparentBlockMode;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.function.Predicate;
import org.bukkit.Material;
import org.bukkit.block.Block;

/**
 * Precomputed per-material transparency policy used by Paper's block-ray predicate.
 *
 * <p>Material globs are evaluated once at plugin startup. Runtime tests are therefore an enum-array
 * lookup rather than repeated string matching for every block cell traversed by every ray.</p>
 */
public final class BlockTransparencyPolicy {
    private final BlockOpticalMode[] modes;
    private final Predicate<Block> blockingPredicate = block -> mode(block.getType()).blocksRay();

    /**
     * Compiles config material globs into an ordinal-indexed lookup table.
     *
     * @param config Foggy raycast settings
     */
    public BlockTransparencyPolicy(FoggyConfig config) {
        List<String> transparent = normalize(config.transparentMaterials());
        List<String> cutout = normalize(config.cutoutMaterials());
        List<String> opaqueOverrides = normalize(config.opaqueMaterialOverrides());
        modes = new BlockOpticalMode[Material.values().length];
        for (Material material : Material.values()) {
            String name = material.name();
            boolean transparentMatch = matchesAny(transparent, name);
            boolean cutoutMatch = VanillaCutoutCatalog.contains(name) || matchesAny(cutout, name);
            boolean opaqueOverride = matchesAny(opaqueOverrides, name);
            BlockOpticalMode mode;
            if (opaqueOverride) {
                mode = BlockOpticalMode.OCCLUDING;
            } else if (transparentMatch) {
                mode = config.transparentBlockMode() == TransparentBlockMode.PASS_THROUGH
                        ? BlockOpticalMode.TRANSPARENT_PASS
                        : BlockOpticalMode.TRANSPARENT_OCCLUDING;
            } else if (cutoutMatch) {
                mode = config.cutoutBlockMode() == TransparentBlockMode.PASS_THROUGH
                        ? BlockOpticalMode.CUTOUT_PASS
                        : BlockOpticalMode.CUTOUT_OCCLUDING;
            } else {
                mode = BlockOpticalMode.OCCLUDING;
            }
            modes[material.ordinal()] = mode;
        }
    }

    /**
     * Returns the reusable predicate accepted by Paper's filtered raytrace overload.
     *
     * @return cached blocking predicate
     */
    public Predicate<Block> blockingPredicate() {
        return blockingPredicate;
    }

    /**
     * Returns a material's precomputed decision.
     *
     * @param material Bukkit material
     * @return optical mode
     */
    public BlockOpticalMode mode(Material material) {
        return modes[material.ordinal()];
    }

    private static List<String> normalize(List<String> patterns) {
        List<String> result = new ArrayList<String>(patterns.size());
        for (String pattern : patterns) {
            String value = pattern.toUpperCase(Locale.ROOT);
            result.add(value.startsWith("MINECRAFT:")
                    ? value.substring("MINECRAFT:".length()) : value);
        }
        return Collections.unmodifiableList(result);
    }

    private static boolean matchesAny(List<String> patterns, String materialName) {
        for (String pattern : patterns) {
            if (globMatches(pattern, materialName)) {
                return true;
            }
        }
        return false;
    }

    /** Wildcard matcher supporting any number of {@code *} characters without regex allocation. */
    static boolean globMatches(String pattern, String value) {
        int patternIndex = 0;
        int valueIndex = 0;
        int starIndex = -1;
        int retryValueIndex = -1;
        while (valueIndex < value.length()) {
            if (patternIndex < pattern.length()
                    && pattern.charAt(patternIndex) == value.charAt(valueIndex)) {
                patternIndex++;
                valueIndex++;
            } else if (patternIndex < pattern.length() && pattern.charAt(patternIndex) == '*') {
                starIndex = patternIndex++;
                retryValueIndex = valueIndex;
            } else if (starIndex >= 0) {
                patternIndex = starIndex + 1;
                valueIndex = ++retryValueIndex;
            } else {
                return false;
            }
        }
        while (patternIndex < pattern.length() && pattern.charAt(patternIndex) == '*') {
            patternIndex++;
        }
        return patternIndex == pattern.length();
    }
}
