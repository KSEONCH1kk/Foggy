package dev.foggy.raycast;

import dev.foggy.config.FoggyConfig;
import java.util.UUID;
import org.bukkit.World;
import org.bukkit.block.Block;
import dev.foggy.math.Aabb;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

/**
 * Facade over the cached, allocation-light Minecraft-style voxel traversal.
 *
 * <p>{@link CompensatedWorld} mirrors Mojang {@code BlockGetter#traverseBlocks} and
 * {@code VoxelShape#clip}, but resolves a block state's OUTLINE/COLLIDER sub-boxes only on a cold
 * cache entry. Hot rays traverse primitive cached boxes without calling
 * {@code CraftWorld#rayTraceBlocks}. Configured optical pass-through is applied after the exact
 * state-dependent geometry has been resolved.</p>
 */
public final class VanillaBlockRaycaster {
    private final BlockTransparencyPolicy transparencyPolicy;
    private final OutlineShapeInspector outlineShapeInspector = new OutlineShapeInspector();
    private final CompensatedWorld compensatedWorld;

    /**
     * Creates the vanilla-shape bridge.
     *
     * @param config geometry/transparency settings
     * @param compensatedWorld shared sparse world/shape snapshot
     */
    public VanillaBlockRaycaster(FoggyConfig config, CompensatedWorld compensatedWorld) {
        transparencyPolicy = new BlockTransparencyPolicy(config);
        this.compensatedWorld = compensatedWorld;
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
    public @Nullable BlockRayHit traceOcclusion(World world, Vector from, Vector to) {
        return compensatedWorld.traceOcclusion(world, from, to);
    }

    /**
     * Allocation-free production equivalent of {@code traceOcclusion(...) != null}.
     *
     * @param world ray world
     * @param from segment start
     * @param to segment end
     * @return whether optically blocking OUTLINE geometry intersects the segment
     */
    public boolean occludes(World world, Vector from, Vector to) {
        return compensatedWorld.occludes(world, from, to);
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
    public @Nullable BlockRayHit traceAnyOutline(World world, Vector from, Vector to) {
        return compensatedWorld.traceAnyOutline(world, from, to);
    }

    /**
     * Traces Minecraft's COLLIDER shape for conservative F5 camera clipping.
     *
     * @param world ray world
     * @param from segment start
     * @param to segment end
     * @return first collision hit, or null
     */
    public @Nullable BlockRayHit traceCollision(World world, Vector from, Vector to) {
        return compensatedWorld.traceCollision(world, from, to);
    }

    /**
     * Proves that the current Paper/Folia tick thread owns every chunk touched by a segment.
     *
     * @param world ray world
     * @param from segment start
     * @param to segment end
     * @return whether world access for the complete segment is legal on the current thread
     */
    public boolean ownsTrace(World world, Vector from, Vector to) {
        return compensatedWorld.ownsTrace(world, from, to);
    }

    /**
     * Allocation-free variant used by the pair camera/target envelope.
     *
     * @param world ray world
     * @param fromX segment start X
     * @param fromZ segment start Z
     * @param toX segment end X
     * @param toZ segment end Z
     * @return whether the current region owns the complete rectangle
     */
    public boolean ownsTrace(World world, double fromX, double fromZ, double toX, double toZ) {
        return compensatedWorld.ownsTrace(world, fromX, fromZ, toX, toZ);
    }

    /**
     * Builds an allocation-heavy description for operator diagnostics.
     *
     * @param result local compensated-world ray result
     * @return described block hit, or null for a non-block result
     */
    public @Nullable BlockGeometryHit describe(BlockRayHit result) {
        Block block = result.block();
        if (block == null) {
            return null;
        }
        OutlineShapeSnapshot outlineShape = outlineShapeInspector.inspect(block);
        Aabb outline = outlineShape.envelope(block.getX(), block.getY(), block.getZ());
        return new BlockGeometryHit(
                result.position().clone(),
                block.getType(),
                outlineShape.stateDescription(),
                block.getX(), block.getY(), block.getZ(),
                outline.widthX(), outline.height(), outline.widthZ(),
                outlineShape.collisionBoxes(),
                outlineShape.boxes(), outlineShape.exact(),
                transparencyPolicy.mode(block.getType()));
    }

    /**
     * Returns the shared cache diagnostics used by live debug.
     *
     * @return current compensated-world counters
     */
    public CompensatedWorld.CacheStats cacheStats() {
        return compensatedWorld.stats();
    }

    /**
     * Returns the invalidation revision for pair-level decision memoization.
     *
     * @param worldId world UUID
     * @return current world revision
     */
    public long worldRevision(UUID worldId) {
        return compensatedWorld.revision(worldId);
    }
}
