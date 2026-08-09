package dev.foggy.raycast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VanillaCutoutCatalogTest {
    @Test
    void loadsOfficialClientCutoutRegistrations() {
        assertEquals(280, VanillaCutoutCatalog.size());
        assertTrue(VanillaCutoutCatalog.contains("OAK_DOOR"));
        assertTrue(VanillaCutoutCatalog.contains("IRON_TRAPDOOR"));
        assertTrue(VanillaCutoutCatalog.contains("IRON_BARS"));
        assertTrue(VanillaCutoutCatalog.contains("SPAWNER"));
        assertTrue(VanillaCutoutCatalog.contains("COPPER_GRATE"));
    }

    @Test
    void excludesOpaqueBaseAndOrdinarySolidBlocks() {
        assertFalse(VanillaCutoutCatalog.contains("STONE"));
        assertFalse(VanillaCutoutCatalog.contains("GRASS_BLOCK"));
        assertFalse(VanillaCutoutCatalog.contains("CACTUS"));
        assertFalse(VanillaCutoutCatalog.contains("PISTON_HEAD"));
        assertFalse(VanillaCutoutCatalog.contains("OAK_FENCE"));
    }
}
