package dev.foggy.raycast;

import dev.foggy.math.Aabb;
import java.util.Collections;
import org.bukkit.block.Block;

/** Reads the production shape representation for allocation-heavy debug output. */
final class OutlineShapeInspector {
    private final NmsShapeAccess access = new NmsShapeAccess();

    /** Extracts world-space primitive boxes and a complete state string for one debug hit. */
    OutlineShapeSnapshot inspect(Block block) {
        try {
            Object state = access.state(block);
            NmsShapeAccess.Geometry geometry = access.geometry(block, state);
            return new OutlineShapeSnapshot(
                    geometry.outline().worldBoxes(block.getX(), block.getY(), block.getZ()),
                    true, String.valueOf(state), geometry.collision().boxCount());
        } catch (ReflectiveOperationException | RuntimeException exception) {
            String material = block.getType().name();
            if (material.equals("AIR") || material.endsWith("_AIR")) {
                return new OutlineShapeSnapshot(Collections.<Aabb>emptyList(), false,
                        legacyState(block), 0);
            }
            Aabb cube = new Aabb(block.getX(), block.getY(), block.getZ(),
                    block.getX() + 1.0, block.getY() + 1.0, block.getZ() + 1.0);
            return new OutlineShapeSnapshot(Collections.singletonList(cube), false,
                    legacyState(block), 1);
        }
    }

    @SuppressWarnings("deprecation")
    private static String legacyState(Block block) {
        return block.getType().name() + ":" + block.getData();
    }
}
