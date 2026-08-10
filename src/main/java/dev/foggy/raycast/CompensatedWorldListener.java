package dev.foggy.raycast;

import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.Event;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import java.lang.reflect.Method;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.event.world.WorldUnloadEvent;

/** Invalidates compensated cells for vanilla block mutations before the next visibility pass. */
public final class CompensatedWorldListener implements Listener {
    private final CompensatedWorld world;

    /**
     * Creates the mutation bridge.
     *
     * @param world compensated cache to invalidate
     */
    public CompensatedWorldListener(CompensatedWorld world) {
        this.world = world;
    }

    /**
     * Invalidates a broken block.
     * @param event completed block break
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        invalidate(event.getBlock());
    }

    /**
     * Invalidates a placed block.
     * @param event completed block placement
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        invalidate(event.getBlockPlaced());
    }

    /**
     * Invalidates a physics-updated neighbourhood.
     * @param event block-neighbour physics update
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent event) {
        invalidate(event.getBlock());
    }

    /**
     * Invalidates both ends of a fluid move.
     * @param event fluid movement between blocks
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        invalidate(event.getBlock());
        invalidate(event.getToBlock());
    }

    /**
     * Invalidates a formed block.
     * @param event block formation
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onForm(BlockFormEvent event) {
        invalidate(event.getBlock());
    }

    /**
     * Invalidates a faded block.
     * @param event block fade
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) {
        invalidate(event.getBlock());
    }

    /**
     * Invalidates a grown block.
     * @param event block growth
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGrow(BlockGrowEvent event) {
        invalidate(event.getBlock());
    }

    /**
     * Invalidates spread source and destination blocks.
     * @param event block spread
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent event) {
        invalidate(event.getBlock());
        invalidate(event.getSource());
    }

    /**
     * Invalidates a burned block.
     * @param event block burn
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        invalidate(event.getBlock());
    }

    /**
     * Invalidates a decayed leaf block.
     * @param event leaf decay
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLeavesDecay(LeavesDecayEvent event) {
        invalidate(event.getBlock());
    }

    /**
     * Invalidates a changed redstone state.
     * @param event redstone state change
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onRedstone(BlockRedstoneEvent event) {
        invalidate(event.getBlock());
    }

    /**
     * Invalidates a player-toggled state-dependent shape.
     * @param event player interaction that can toggle a state-dependent shape
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() != null) {
            invalidate(event.getClickedBlock());
        }
    }

    /**
     * Invalidates an entity-mutated block.
     * @param event entity-driven block mutation
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityChange(EntityChangeBlockEvent event) {
        invalidate(event.getBlock());
    }

    /**
     * Invalidates a piston extension and its moved blocks.
     * @param event piston extension
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        invalidatePiston(event.getBlock(), event.getBlocks());
    }

    /**
     * Invalidates a piston retraction and its moved blocks.
     * @param event piston retraction
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        invalidatePiston(event.getBlock(), event.getBlocks());
    }

    /**
     * Invalidates a block-originated explosion batch.
     * @param event block-originated explosion
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplosion(BlockExplodeEvent event) {
        invalidate(event.getBlock());
        event.blockList().forEach(this::invalidate);
    }

    /**
     * Invalidates an entity-originated explosion batch.
     * @param event entity-originated explosion
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplosion(EntityExplodeEvent event) {
        event.blockList().forEach(this::invalidate);
    }

    /**
     * Invalidates a structure growth batch.
     * @param event structure growth block batch
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onStructure(StructureGrowEvent event) {
        event.getBlocks().stream().map(BlockState::getBlock).forEach(this::invalidate);
    }

    /**
     * Invalidates a portal creation batch.
     * @param event portal block batch
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPortal(PortalCreateEvent event) {
        invalidateBlocks(event.getBlocks());
    }

    /**
     * Evicts an unloaded chunk.
     * @param event unloaded chunk
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        world.removeChunk(event.getWorld(), event.getChunk().getX(), event.getChunk().getZ());
    }

    /**
     * Evicts an unloaded world.
     * @param event unloaded world
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldUnload(WorldUnloadEvent event) {
        world.removeWorld(event.getWorld().getUID());
    }

    private void invalidatePiston(Block piston, Iterable<Block> moved) {
        invalidate(piston);
        for (Block block : moved) {
            invalidate(block);
        }
    }

    private void invalidate(Block block) {
        world.invalidateAround(block);
    }

    /** Registers block-mutation events introduced after Bukkit 1.8 without hard-linking them. */
    public void registerOptionalEvents(Plugin plugin) {
        registerOptional(plugin, "org.bukkit.event.block.FluidLevelChangeEvent");
        registerOptional(plugin, "org.bukkit.event.block.BlockFertilizeEvent");
        registerOptional(plugin, "org.bukkit.event.block.SpongeAbsorbEvent");
    }

    @SuppressWarnings("unchecked")
    private void registerOptional(Plugin plugin, String className) {
        try {
            Class<?> raw = Class.forName(className, false, plugin.getClass().getClassLoader());
            if (!Event.class.isAssignableFrom(raw)) {
                return;
            }
            plugin.getServer().getPluginManager().registerEvent((Class<? extends Event>) raw, this,
                    EventPriority.MONITOR, new EventExecutor() {
                        @Override
                        public void execute(Listener ignored, Event event) {
                            invalidateOptionalEvent(event);
                        }
                    }, plugin, true);
        } catch (ClassNotFoundException ignored) {
            // This server predates the optional event; periodic identity validation is the guard.
        }
    }

    private void invalidateOptionalEvent(Event event) {
        Object block = invoke(event, "getBlock");
        if (block instanceof Block) {
            invalidate((Block) block);
        }
        Object blocks = invoke(event, "getBlocks");
        if (blocks instanceof Iterable<?>) {
            invalidateBlocks((Iterable<?>) blocks);
        }
    }

    private void invalidateBlocks(Iterable<?> values) {
        for (Object value : values) {
            if (value instanceof Block) {
                invalidate((Block) value);
            } else if (value instanceof BlockState) {
                invalidate(((BlockState) value).getBlock());
            }
        }
    }

    private static Object invoke(Object target, String name) {
        try {
            Method method = target.getClass().getMethod(name);
            return method.invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
