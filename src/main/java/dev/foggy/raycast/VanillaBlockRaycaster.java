package dev.foggy.raycast;

import dev.foggy.config.FoggyConfig;
import io.papermc.paper.math.Position;
import org.bukkit.FluidCollisionMode;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

/**
 * Accesses Paper 1.21.4's direct bridge to Minecraft's grid traversal and {@code VoxelShape.clip}.
 *
 * <p>Visibility rays deliberately request {@code ignorePassableBlocks=false}. CraftWorld maps that
 * flag to Mojang {@code ClipContext.Block.OUTLINE}, so the current block state's complete outline
 * shape is used: every stair section, slab half, fence arm, wall post, sign plate, ladder plane and
 * other sub-box participates. A Paper block predicate removes only materials configured as
 * optically transparent; this preserves the original NMS traversal and shape math.</p>
 */
public final class VanillaBlockRaycaster {
    private final BlockTransparencyPolicy transparencyPolicy;
    private final OutlineShapeInspector outlineShapeInspector = new OutlineShapeInspector();

    /**
     * Creates the vanilla-shape bridge.
     *
     * @param config geometry/transparency settings
     */
    public VanillaBlockRaycaster(FoggyConfig config) {
        transparencyPolicy = new BlockTransparencyPolicy(config);
    }

    /**
     * Traces against every occluding OUTLINE shape while allowing configured transparent shapes
     * to pass.
     *
     * @param world ray world
     * @param from segment start
     * @param to segment end
     * @return first blocking hit, or null
     */
    public @Nullable RayTraceResult traceOcclusion(World world, Vector from, Vector to) {
        return traceOutline(world, from, to, transparencyPolicy.blockingPredicate());
    }

    /**
     * Traces against all OUTLINE shapes without transparency filtering. Used only for debug to
     * expose a transparent shape crossed before the first actual blocker.
     *
     * @param world ray world
     * @param from segment start
     * @param to segment end
     * @return first outline hit, or null
     */
    public @Nullable RayTraceResult traceAnyOutline(World world, Vector from, Vector to) {
        return traceOutline(world, from, to, ignored -> true);
    }

    /**
     * Traces Minecraft's COLLIDER shape for conservative F5 camera clipping.
     *
     * @param world ray world
     * @param from segment start
     * @param to segment end
     * @return first collision hit, or null
     */
    public @Nullable RayTraceResult traceCollision(World world, Vector from, Vector to) {
        Vector delta = to.clone().subtract(from);
        double distance = delta.length();
        if (distance <= 1.0E-12) {
            return null;
        }
        return world.rayTraceBlocks(from.toLocation(world), delta.multiply(1.0 / distance), distance,
                FluidCollisionMode.NEVER, true);
    }

    /**
     * Builds an allocation-heavy description for operator diagnostics.
     *
     * @param result Bukkit ray result
     * @return described block hit, or null for a non-block result
     */
    public @Nullable BlockGeometryHit describe(RayTraceResult result) {
        Block block = result.getHitBlock();
        if (block == null || result.getHitPosition() == null) {
            return null;
        }
        BoundingBox outline = block.getBoundingBox();
        OutlineShapeSnapshot outlineShape = outlineShapeInspector.inspect(block);
        return new BlockGeometryHit(
                result.getHitPosition().clone(),
                block.getType(),
                block.getBlockData().getAsString(),
                block.getX(), block.getY(), block.getZ(),
                outline.getWidthX(), outline.getHeight(), outline.getWidthZ(),
                block.getCollisionShape().getBoundingBoxes().size(),
                outlineShape.boxes(), outlineShape.exact(),
                transparencyPolicy.mode(block.getType()));
    }

    private static @Nullable RayTraceResult traceOutline(
            World world, Vector from, Vector to, java.util.function.Predicate<? super Block> predicate) {
        Vector delta = to.clone().subtract(from);
        double distance = delta.length();
        if (distance <= 1.0E-12) {
            return null;
        }
        Position start = from.toLocation(world);
        return world.rayTraceBlocks(start, delta.multiply(1.0 / distance), distance,
                FluidCollisionMode.NEVER, false, predicate);
    }
}
