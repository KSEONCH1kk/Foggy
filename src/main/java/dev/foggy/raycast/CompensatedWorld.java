package dev.foggy.raycast;

import dev.foggy.config.FoggyConfig;
import dev.foggy.platform.PlatformAdapter;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

/**
 * Sparse, tick-coherent block-state and voxel-shape view used by all Foggy rays.
 *
 * <p>The first ray touching a cell reads its canonical NMS block-state identity and resolves the
 * exact OUTLINE/COLLIDER boxes. All remaining rays reuse primitive cached geometry. Vanilla block
 * events invalidate immediately; periodic identity validation catches direct plugin/NMS edits.
 * No Bukkit/NMS raycast is used after the runtime bridge has initialized successfully.</p>
 */
public final class CompensatedWorld {
    private static final double TRAVERSE_EPSILON = -1.0E-7;
    private static final int SECTION_VOLUME = 16 * 16 * 16;
    private static final int PRUNE_INTERVAL_TICKS = 200;
    private static final BlockRayHit UNLOADED_MISS = new BlockRayHit(new Vector(), null, null);
    private static final BlockRayHit OCCLUSION_HIT = new BlockRayHit(new Vector(), null, null);

    private final int validationTicks;
    private final int retentionTicks;
    private final BlockTransparencyPolicy transparencyPolicy;
    private final Logger logger;
    private final PlatformAdapter platform;
    private final NmsShapeAccess shapeAccess = new NmsShapeAccess();
    private final ConcurrentMap<UUID, WorldCache> worlds = new ConcurrentHashMap<>();
    private final ConcurrentMap<Object, NmsShapeAccess.Geometry> stableGeometry = new ConcurrentHashMap<>();
    private final AtomicBoolean bridgeUnavailable = new AtomicBoolean();
    private final AtomicBoolean bridgeWarningLogged = new AtomicBoolean();
    private final LongAdder traces = new LongAdder();
    private final LongAdder cellHits = new LongAdder();
    private final LongAdder cellRefreshes = new LongAdder();
    private final LongAdder fallbackTraces = new LongAdder();

    /**
     * Creates an empty compensated world cache.
     *
     * @param config validation, retention and transparency settings
     * @param logger plugin logger used for the one-time compatibility fallback warning
     */
    public CompensatedWorld(FoggyConfig config, Logger logger, PlatformAdapter platform) {
        validationTicks = config.worldCacheValidationTicks();
        retentionTicks = config.worldCacheRetentionTicks();
        transparencyPolicy = new BlockTransparencyPolicy(config);
        this.logger = logger;
        this.platform = platform;
    }

    /**
     * Returns whether the current region owns every chunk in the segment rectangle.
     *
     * @param world ray world
     * @param from segment start
     * @param to segment end
     * @return whether every chunk is owned by the current Paper/Folia region
     */
    public boolean ownsTrace(World world, Vector from, Vector to) {
        return ownsTrace(world, from.getX(), from.getZ(), to.getX(), to.getZ());
    }

    /**
     * Allocation-free ownership test for an X/Z segment rectangle.
     *
     * @param world ray world
     * @param fromX segment start X
     * @param fromZ segment start Z
     * @param toX segment end X
     * @param toZ segment end Z
     * @return whether every chunk is owned by the current Paper/Folia region
     */
    public boolean ownsTrace(World world, double fromX, double fromZ, double toX, double toZ) {
        int minChunkX = Math.min(blockToChunk(fromX), blockToChunk(toX));
        int maxChunkX = Math.max(blockToChunk(fromX), blockToChunk(toX));
        int minChunkZ = Math.min(blockToChunk(fromZ), blockToChunk(toZ));
        int maxChunkZ = Math.max(blockToChunk(fromZ), blockToChunk(toZ));
        return platform.owns(world, minChunkX, minChunkZ, maxChunkX, maxChunkZ);
    }

    /**
     * Traces exact OUTLINE geometry, skipping configured optically pass-through materials.
     *
     * @param world ray world
     * @param from segment start
     * @param to segment end
     * @return nearest detailed block hit, or null
     */
    public @Nullable BlockRayHit traceOcclusion(World world, Vector from, Vector to) {
        return trace(world, from, to, ShapeKind.OUTLINE_OCCLUDING);
    }

    /**
     * Allocation-free production test against optically blocking OUTLINE geometry.
     *
     * @param world ray world
     * @param from segment start
     * @param to segment end
     * @return whether the segment intersects an optically blocking shape
     */
    public boolean occludes(World world, Vector from, Vector to) {
        traces.increment();
        if (from.equals(to)) {
            return false;
        }
        if (bridgeUnavailable.get()) {
            return fallback(world, from, to, ShapeKind.OUTLINE_OCCLUDING) != null;
        }
        int tick = platform.currentTick();
        WorldCache cache = worlds.computeIfAbsent(world.getUID(), ignored -> new WorldCache());
        cache.prune(tick);
        TraceLookup lookup = new TraceLookup(world, cache, tick);
        try {
            BlockRayHit result = traverse(from, to, (x, y, z) -> {
                if (!lookup.validHeight(y)) {
                    return null;
                }
                if (!lookup.loaded(x, z)) {
                    return UNLOADED_MISS;
                }
                CachedCell cell = lookup.cell(x, y, z);
                if (!cell.opticalMode.blocksRay()) {
                    return null;
                }
                return cell.geometry.outline().intersects(
                        from.getX(), from.getY(), from.getZ(), to.getX(), to.getY(), to.getZ(), x, y, z)
                        ? OCCLUSION_HIT : null;
            });
            return result == OCCLUSION_HIT;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            disableBridge(exception);
            return fallback(world, from, to, ShapeKind.OUTLINE_OCCLUDING) != null;
        }
    }

    /**
     * Traces every exact OUTLINE geometry box, including transparent/cutout blocks.
     *
     * @param world ray world
     * @param from segment start
     * @param to segment end
     * @return nearest detailed outline hit, or null
     */
    public @Nullable BlockRayHit traceAnyOutline(World world, Vector from, Vector to) {
        return trace(world, from, to, ShapeKind.OUTLINE_ALL);
    }

    /**
     * Traces exact COLLIDER geometry for fallback third-person camera clipping.
     *
     * @param world ray world
     * @param from segment start
     * @param to segment end
     * @return nearest detailed collision hit, or null
     */
    public @Nullable BlockRayHit traceCollision(World world, Vector from, Vector to) {
        return trace(world, from, to, ShapeKind.COLLISION);
    }

    /**
     * Invalidates one block and directly adjacent shapes. Call on the owning region thread.
     *
     * @param block center of the invalidated neighbourhood
     */
    public void invalidateAround(Block block) {
        WorldCache cache = worlds.get(block.getWorld().getUID());
        if (cache == null) {
            return;
        }
        for (int x = block.getX() - 1; x <= block.getX() + 1; x++) {
            for (int y = block.getY() - 1; y <= block.getY() + 1; y++) {
                for (int z = block.getZ() - 1; z <= block.getZ() + 1; z++) {
                    cache.invalidate(x, y, z);
                }
            }
        }
        cache.revision.incrementAndGet();
    }

    /**
     * Drops all compensated sections for one unloaded chunk.
     *
     * @param world owning world
     * @param chunkX unloaded chunk X
     * @param chunkZ unloaded chunk Z
     */
    public void removeChunk(World world, int chunkX, int chunkZ) {
        WorldCache cache = worlds.get(world.getUID());
        if (cache != null) {
            cache.sections.entrySet().removeIf(entry -> unpackChunkX(entry.getKey()) == chunkX
                    && unpackChunkZ(entry.getKey()) == chunkZ);
            cache.revision.incrementAndGet();
        }
    }

    /**
     * Drops an unloaded world and all of its cached sections.
     *
     * @param worldId unloaded world UUID
     */
    public void removeWorld(UUID worldId) {
        worlds.remove(worldId);
    }

    /**
     * Returns the monotonic invalidation revision used by pair-level optical memoization.
     *
     * @param worldId world UUID
     * @return current revision, or zero before the world is touched
     */
    public long revision(UUID worldId) {
        WorldCache cache = worlds.get(worldId);
        return cache == null ? 0L : cache.revision.get();
    }

    /**
     * Returns allocation-light aggregate cache diagnostics.
     *
     * @return current aggregate counters
     */
    public CacheStats stats() {
        long sections = 0L;
        long cells = 0L;
        for (WorldCache world : worlds.values()) {
            sections += world.sections.size();
            for (Section section : world.sections.values()) {
                cells += section.populated.get();
            }
        }
        return new CacheStats(traces.sum(), cellHits.sum(), cellRefreshes.sum(), fallbackTraces.sum(),
                worlds.size(), sections, cells, stableGeometry.size(), bridgeUnavailable.get());
    }

    /** Clears all cached world and geometry state. */
    public void clear() {
        worlds.clear();
        stableGeometry.clear();
    }

    private @Nullable BlockRayHit trace(World world, Vector from, Vector to, ShapeKind kind) {
        traces.increment();
        if (from.equals(to)) {
            return null;
        }
        if (bridgeUnavailable.get()) {
            return fallback(world, from, to, kind);
        }
        int tick = platform.currentTick();
        WorldCache cache = worlds.computeIfAbsent(world.getUID(), ignored -> new WorldCache());
        cache.prune(tick);
        TraceLookup lookup = new TraceLookup(world, cache, tick);
        try {
            BlockRayHit result = traverse(from, to, (x, y, z) -> {
                if (!lookup.validHeight(y)) {
                    return null;
                }
                if (!lookup.loaded(x, z)) {
                    // BlockGetter#getBlockStateIfLoaded returns a MISS and ends the vanilla
                    // traversal. Never load/generate a chunk merely because a visibility ray
                    // crossed it.
                    return UNLOADED_MISS;
                }
                CachedCell cell = lookup.cell(x, y, z);
                if (kind == ShapeKind.OUTLINE_OCCLUDING && !cell.opticalMode.blocksRay()) {
                    return null;
                }
                CompensatedShape shape = kind == ShapeKind.COLLISION
                        ? cell.geometry.collision() : cell.geometry.outline();
                CompensatedShape.ShapeHit hit = shape.clip(
                        from.getX(), from.getY(), from.getZ(), to.getX(), to.getY(), to.getZ(), x, y, z);
                if (hit == null) {
                    return null;
                }
                return new BlockRayHit(hit.position(), world.getBlockAt(x, y, z), hit.face());
            });
            return result == UNLOADED_MISS ? null : result;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            disableBridge(exception);
            return fallback(world, from, to, kind);
        }
    }

    private void disableBridge(Exception exception) {
        bridgeUnavailable.set(true);
        if (bridgeWarningLogged.compareAndSet(false, true)) {
            logger.log(Level.WARNING,
                    "CompensatedWorld native shape bridge unavailable; using conservative local full-cube DDA", exception);
        }
    }

    private CachedCell loadCell(World world, WorldCache cache, int tick, int x, int y, int z,
                                @Nullable CachedCell previous) throws ReflectiveOperationException {
        if (previous != null && tick - previous.verifiedTick >= 0
                && tick - previous.verifiedTick < validationTicks) {
            cellHits.increment();
            return previous;
        }
        Block block = world.getBlockAt(x, y, z);
        Object state = shapeAccess.state(block);
        if (previous != null && previous.state == state && !previous.dynamic) {
            previous.verifiedTick = tick;
            cellHits.increment();
            return previous;
        }
        NmsShapeAccess.Geometry geometry = stableGeometry.get(state);
        boolean dynamic = false;
        if (geometry == null) {
            dynamic = shapeAccess.dynamic(state);
            if (dynamic) {
                geometry = shapeAccess.geometry(block, state);
            } else {
                NmsShapeAccess.Geometry resolved = shapeAccess.geometry(block, state);
                NmsShapeAccess.Geometry raced = stableGeometry.putIfAbsent(state, resolved);
                geometry = raced == null ? resolved : raced;
            }
        }
        if (previous != null) {
            // A changed identity or a context-dependent shape invalidates pair decisions even when
            // no Bukkit event was available (direct plugin/NMS mutation, neighbour-sensitive shape).
            cache.revision.incrementAndGet();
        }
        cellRefreshes.increment();
        return new CachedCell(state, geometry, transparencyPolicy.mode(block.getType()), dynamic, tick);
    }

    private @Nullable BlockRayHit fallback(World world, Vector from, Vector to, ShapeKind kind) {
        fallbackTraces.increment();
        try {
            return traverse(from, to, (x, y, z) -> {
                if (y < platform.minHeight(world) || y >= platform.maxHeight(world)) {
                    return null;
                }
                Block block = world.getBlockAt(x, y, z);
                String material = block.getType().name();
                if (material.equals("AIR") || material.endsWith("_AIR")
                        || (kind == ShapeKind.OUTLINE_OCCLUDING
                        && !transparencyPolicy.mode(block.getType()).blocksRay())) {
                    return null;
                }
                CompensatedShape.ShapeHit hit = CompensatedShape.FULL_BLOCK.clip(
                        from.getX(), from.getY(), from.getZ(), to.getX(), to.getY(), to.getZ(), x, y, z);
                return hit == null ? null : new BlockRayHit(hit.position(), block, hit.face());
            });
        } catch (ReflectiveOperationException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /** Mirrors Mojang {@code BlockGetter#traverseBlocks} including its ±1e-7 boundary bias. */
    static @Nullable BlockRayHit traverse(Vector from, Vector to, CellVisitor visitor)
            throws ReflectiveOperationException {
        if (from.equals(to)) {
            return null;
        }
        double endX = lerp(TRAVERSE_EPSILON, to.getX(), from.getX());
        double endY = lerp(TRAVERSE_EPSILON, to.getY(), from.getY());
        double endZ = lerp(TRAVERSE_EPSILON, to.getZ(), from.getZ());
        double startX = lerp(TRAVERSE_EPSILON, from.getX(), to.getX());
        double startY = lerp(TRAVERSE_EPSILON, from.getY(), to.getY());
        double startZ = lerp(TRAVERSE_EPSILON, from.getZ(), to.getZ());
        int blockX = floor(startX);
        int blockY = floor(startY);
        int blockZ = floor(startZ);
        BlockRayHit initial = visitor.visit(blockX, blockY, blockZ);
        if (initial != null) {
            return initial;
        }

        double deltaX = endX - startX;
        double deltaY = endY - startY;
        double deltaZ = endZ - startZ;
        int stepX = sign(deltaX);
        int stepY = sign(deltaY);
        int stepZ = sign(deltaZ);
        double incrementX = stepX == 0 ? Double.MAX_VALUE : stepX / deltaX;
        double incrementY = stepY == 0 ? Double.MAX_VALUE : stepY / deltaY;
        double incrementZ = stepZ == 0 ? Double.MAX_VALUE : stepZ / deltaZ;
        double progressX = incrementX * (stepX > 0 ? 1.0 - fraction(startX) : fraction(startX));
        double progressY = incrementY * (stepY > 0 ? 1.0 - fraction(startY) : fraction(startY));
        double progressZ = incrementZ * (stepZ > 0 ? 1.0 - fraction(startZ) : fraction(startZ));
        while (progressX <= 1.0 || progressY <= 1.0 || progressZ <= 1.0) {
            if (progressX < progressY) {
                if (progressX < progressZ) {
                    blockX += stepX;
                    progressX += incrementX;
                } else {
                    blockZ += stepZ;
                    progressZ += incrementZ;
                }
            } else if (progressY < progressZ) {
                blockY += stepY;
                progressY += incrementY;
            } else {
                blockZ += stepZ;
                progressZ += incrementZ;
            }
            BlockRayHit hit = visitor.visit(blockX, blockY, blockZ);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    private static double lerp(double delta, double start, double end) {
        return start + delta * (end - start);
    }

    private static int floor(double value) {
        int integer = (int) value;
        return value < integer ? integer - 1 : integer;
    }

    private static double fraction(double value) {
        return value - Math.floor(value);
    }

    private static int sign(double value) {
        return value == 0.0 ? 0 : value > 0.0 ? 1 : -1;
    }

    private static int blockToChunk(double coordinate) {
        return floor(coordinate) >> 4;
    }

    private static long sectionKey(int blockX, int blockY, int blockZ) {
        long chunkX = (blockX >> 4) & 0x3FFFFFL;
        long chunkZ = (blockZ >> 4) & 0x3FFFFFL;
        long sectionY = (blockY >> 4) & 0xFFFFFL;
        return (chunkX << 42) | (chunkZ << 20) | sectionY;
    }

    private static int unpackChunkX(long key) {
        return (int) (key << 0 >> 42);
    }

    private static int unpackChunkZ(long key) {
        return (int) (key << 22 >> 42);
    }

    private static int localIndex(int x, int y, int z) {
        return (y & 15) << 8 | (z & 15) << 4 | (x & 15);
    }

    /** Aggregate counters exposed by live debug. */
    public static final class CacheStats {
        private final long traces, cellHits, cellRefreshes, fallbackTraces;
        private final long worlds, sections, cells, stableStates;
        private final boolean bridgeUnavailable;

        /**
         * Creates a counter snapshot.
         *
         * @param traces total detailed and boolean traces
         * @param cellHits cached-cell reuses
         * @param cellRefreshes cold or validation refreshes
         * @param fallbackTraces conservative local full-cube fallback invocations
         * @param worlds cached worlds
         * @param sections cached 16-cubed sections
         * @param cells populated cells
         * @param stableStates globally cached non-dynamic block states
         * @param bridgeUnavailable whether the NMS bridge was disabled
         */
        CacheStats(long traces, long cellHits, long cellRefreshes, long fallbackTraces,
                   long worlds, long sections, long cells, long stableStates,
                   boolean bridgeUnavailable) {
            this.traces = traces; this.cellHits = cellHits; this.cellRefreshes = cellRefreshes;
            this.fallbackTraces = fallbackTraces; this.worlds = worlds; this.sections = sections;
            this.cells = cells; this.stableStates = stableStates;
            this.bridgeUnavailable = bridgeUnavailable;
        }

        public long traces() { return traces; }
        public long cellHits() { return cellHits; }
        public long cellRefreshes() { return cellRefreshes; }
        public long fallbackTraces() { return fallbackTraces; }
        public long worlds() { return worlds; }
        public long sections() { return sections; }
        public long cells() { return cells; }
        public long stableStates() { return stableStates; }
        public boolean bridgeUnavailable() { return bridgeUnavailable; }
    }

    private enum ShapeKind {
        OUTLINE_OCCLUDING,
        OUTLINE_ALL,
        COLLISION
    }

    @FunctionalInterface
    interface CellVisitor {
        @Nullable BlockRayHit visit(int x, int y, int z) throws ReflectiveOperationException;
    }

    private final class TraceLookup {
        private final World world;
        private final WorldCache cache;
        private final int tick;
        private long lastKey = Long.MIN_VALUE;
        private Section lastSection;
        private int lastChunkX = Integer.MIN_VALUE;
        private int lastChunkZ = Integer.MIN_VALUE;
        private boolean lastChunkLoaded;

        private TraceLookup(World world, WorldCache cache, int tick) {
            this.world = world;
            this.cache = cache;
            this.tick = tick;
        }

        private CachedCell cell(int x, int y, int z) throws ReflectiveOperationException {
            long key = sectionKey(x, y, z);
            if (lastSection == null || key != lastKey) {
                lastKey = key;
                lastSection = cache.sections.computeIfAbsent(key, ignored -> new Section());
            }
            lastSection.lastAccessTick = tick;
            int index = localIndex(x, y, z);
            CachedCell previous = lastSection.cells.get(index);
            CachedCell loaded = loadCell(world, cache, tick, x, y, z, previous);
            if (loaded != previous) {
                if (previous == null) {
                    lastSection.populated.incrementAndGet();
                }
                lastSection.cells.set(index, loaded);
            }
            return loaded;
        }

        private boolean loaded(int blockX, int blockZ) {
            int chunkX = blockX >> 4;
            int chunkZ = blockZ >> 4;
            if (chunkX != lastChunkX || chunkZ != lastChunkZ) {
                lastChunkX = chunkX;
                lastChunkZ = chunkZ;
                lastChunkLoaded = world.isChunkLoaded(chunkX, chunkZ);
            }
            return lastChunkLoaded;
        }

        private boolean validHeight(int blockY) {
            return blockY >= platform.minHeight(world) && blockY < platform.maxHeight(world);
        }
    }

    private final class WorldCache {
        private final ConcurrentMap<Long, Section> sections = new ConcurrentHashMap<>();
        private final AtomicLong revision = new AtomicLong();
        private volatile int lastPruneTick;

        private void invalidate(int x, int y, int z) {
            Section section = sections.get(sectionKey(x, y, z));
            if (section == null) {
                return;
            }
            int index = localIndex(x, y, z);
            if (section.cells.getAndSet(index, null) != null) {
                section.populated.decrementAndGet();
            }
        }

        private void prune(int tick) {
            if (tick - lastPruneTick < PRUNE_INTERVAL_TICKS) {
                return;
            }
            lastPruneTick = tick;
            for (Map.Entry<Long, Section> entry : sections.entrySet()) {
                if (tick - entry.getValue().lastAccessTick > retentionTicks) {
                    sections.remove(entry.getKey(), entry.getValue());
                }
            }
        }
    }

    private static final class Section {
        private final AtomicReferenceArray<CachedCell> cells = new AtomicReferenceArray<>(SECTION_VOLUME);
        private final java.util.concurrent.atomic.AtomicInteger populated =
                new java.util.concurrent.atomic.AtomicInteger();
        private volatile int lastAccessTick;
    }

    private static final class CachedCell {
        private final Object state;
        private final NmsShapeAccess.Geometry geometry;
        private final BlockOpticalMode opticalMode;
        private final boolean dynamic;
        private volatile int verifiedTick;

        private CachedCell(Object state, NmsShapeAccess.Geometry geometry,
                           BlockOpticalMode opticalMode, boolean dynamic, int verifiedTick) {
            this.state = state;
            this.geometry = geometry;
            this.opticalMode = opticalMode;
            this.dynamic = dynamic;
            this.verifiedTick = verifiedTick;
        }
    }
}
