package dev.foggy.raycast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.Collections;
import java.util.stream.Collectors;

/**
 * Vanilla cutout blocks registered in the client's CUTOUT, CUTOUT_MIPPED or TRIPWIRE layer.
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
        return MATERIALS.contains(materialName) || legacyOrFutureCutout(materialName);
    }

    /**
     * Returns the number of bundled vanilla entries.
     *
     * @return catalog size
     */
    public static int size() {
        return MATERIALS.size();
    }

    /**
     * Name-stable conservative families cover legacy pre-flattening names and blocks added after
     * the bundled 1.21.4 client table plus legacy and future material families. These blocks are
     * intentionally pass-through: server-side
     * voxel geometry cannot represent holes in their alpha-tested textures.
     */
    private static boolean legacyOrFutureCutout(String name) {
        if (name.equals("PISTON_HEAD")) {
            return false;
        }
        return name.equals("FENCE") || name.equals("NETHER_FENCE") || name.equals("FENCE_GATE")
                || name.equals("COBBLE_WALL") || name.equals("WOODEN_DOOR")
                || name.equals("IRON_DOOR_BLOCK") || name.equals("TRAP_DOOR")
                || name.equals("SIGN") || name.equals("SIGN_POST") || name.equals("WALL_SIGN")
                || name.equals("SKULL") || name.equals("THIN_GLASS") || name.equals("IRON_FENCE")
                || name.equals("LONG_GRASS") || name.equals("DOUBLE_PLANT") || name.equals("WEB")
                || name.equals("RED_ROSE") || name.equals("YELLOW_FLOWER")
                || name.equals("CROPS") || name.equals("NETHER_WARTS")
                || name.endsWith("_DOOR") || name.endsWith("_TRAPDOOR")
                || name.endsWith("_FENCE") || name.endsWith("_FENCE_GATE")
                || name.endsWith("_SIGN") || name.endsWith("_HANGING_SIGN")
                || name.endsWith("_SAPLING") || name.endsWith("_FLOWER")
                || name.endsWith("_MUSHROOM") || name.endsWith("_TORCH")
                || name.endsWith("_BUTTON") || name.endsWith("_PRESSURE_PLATE")
                || name.endsWith("_HEAD") || name.endsWith("_SKULL")
                || name.endsWith("_BARS") || name.endsWith("_PANE")
                || name.equals("LADDER") || name.equals("VINE") || name.equals("COBWEB")
                || name.contains("RAIL") || name.startsWith("TRIPWIRE");
    }

    private static Set<String> load() {
        InputStream stream = VanillaCutoutCatalog.class.getResourceAsStream(RESOURCE);
        if (stream == null) {
            throw new ExceptionInInitializerError("Missing " + RESOURCE);
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            return Collections.unmodifiableSet(reader.lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .collect(Collectors.toSet()));
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
