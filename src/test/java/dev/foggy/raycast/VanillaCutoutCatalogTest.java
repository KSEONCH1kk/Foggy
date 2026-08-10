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
        assertTrue(VanillaCutoutCatalog.contains("OAK_FENCE"));
        assertTrue(VanillaCutoutCatalog.contains("PALE_OAK_FENCE_GATE"));
    }

    @Test
    void coversPreFlatteningCutoutNames() {
        assertTrue(VanillaCutoutCatalog.contains("FENCE"));
        assertTrue(VanillaCutoutCatalog.contains("NETHER_FENCE"));
        assertTrue(VanillaCutoutCatalog.contains("FENCE_GATE"));
        assertTrue(VanillaCutoutCatalog.contains("COBBLE_WALL"));
        assertTrue(VanillaCutoutCatalog.contains("WOODEN_DOOR"));
        assertTrue(VanillaCutoutCatalog.contains("IRON_DOOR_BLOCK"));
        assertTrue(VanillaCutoutCatalog.contains("TRAP_DOOR"));
        assertTrue(VanillaCutoutCatalog.contains("SIGN_POST"));
        assertTrue(VanillaCutoutCatalog.contains("SKULL"));
        assertTrue(VanillaCutoutCatalog.contains("IRON_FENCE"));
        assertTrue(VanillaCutoutCatalog.contains("WEB"));
    }

    @Test
    void excludesOpaqueBaseAndOrdinarySolidBlocks() {
        assertFalse(VanillaCutoutCatalog.contains("STONE"));
        assertFalse(VanillaCutoutCatalog.contains("GRASS_BLOCK"));
        assertFalse(VanillaCutoutCatalog.contains("CACTUS"));
        assertFalse(VanillaCutoutCatalog.contains("PISTON_HEAD"));
        assertFalse(VanillaCutoutCatalog.contains("PISTON_BASE"));
    }
}
