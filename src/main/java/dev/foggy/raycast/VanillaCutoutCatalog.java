package dev.foggy.raycast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Vanilla 1.21.4 blocks registered in the client's CUTOUT, CUTOUT_MIPPED or TRIPWIRE layer.
 *
 * <p>The resource is extracted from Mojang {@code ItemBlockRenderTypes} rather than inferred from
 * Bukkit collision flags. {@code GRASS_BLOCK} and {@code CACTUS} are intentionally excluded: their
 * render layer supports alpha, but their visible base geometry is not a see-through cutout.</p>
 */
public final class VanillaCutoutCatalog {
    private static final String RESOURCE = "/vanilla-1.21.4-cutout-materials.txt";
    private static final Set<String> MATERIALS = load();

    private VanillaCutoutCatalog() {
    }

    /**
     * Reports whether a Bukkit material name is in the bundled catalog.
     *
     * @param materialName uppercase Bukkit material name
     * @return true for a catalog entry
     */
    public static boolean contains(String materialName) {
        return MATERIALS.contains(materialName);
    }

    /**
     * Returns the number of bundled vanilla entries.
     *
     * @return catalog size
     */
    public static int size() {
        return MATERIALS.size();
    }

    private static Set<String> load() {
        InputStream stream = VanillaCutoutCatalog.class.getResourceAsStream(RESOURCE);
        if (stream == null) {
            throw new ExceptionInInitializerError("Missing " + RESOURCE);
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            return reader.lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .collect(Collectors.toUnmodifiableSet());
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
