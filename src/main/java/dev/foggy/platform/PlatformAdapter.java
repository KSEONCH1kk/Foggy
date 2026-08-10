package dev.foggy.platform;

import dev.foggy.math.Aabb;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Capability-driven bridge across Bukkit 1.8, Spigot/Paper 1.16-1.20 and Paper/Folia 26+.
 *
 * <p>The base plugin never links against Folia classes. Modern entity/global schedulers and
 * ownership checks are discovered reflectively. On classic Bukkit the same operations use the
 * single server thread. This is important for old JVMs as well as old server APIs: merely placing
 * a missing Folia type in a method descriptor can make the JVM reject a class before it runs.</p>
 */
public final class PlatformAdapter {
    private static final long TICK_NANOS = 50_000_000L;

    private final Plugin plugin;
    private final Method entityGetScheduler;
    private final Method ownedEntity;
    private final Method ownedChunkRange;
    private final Method serverGetGlobalScheduler;
    private final Method globalExecute;
    private final Method playerGetBoundingBox;
    private final Method entityGetTrackedBy;
    private final Method entityIsInvisible;
    private final Method worldGetMinHeight;
    private final Method worldGetMaxHeight;
    private final Method entityGetPassengers;
    private final boolean folia;

    /** Discovers the capabilities exposed by the running server. */
    public PlatformAdapter(Plugin plugin) {
        this.plugin = plugin;
        this.entityGetScheduler = method(Entity.class, "getScheduler");
        this.ownedEntity = method(Bukkit.class, "isOwnedByCurrentRegion", Entity.class);
        this.ownedChunkRange = method(Bukkit.class, "isOwnedByCurrentRegion",
                World.class, int.class, int.class, int.class, int.class);
        this.serverGetGlobalScheduler = method(Bukkit.getServer().getClass(), "getGlobalRegionScheduler");
        Object global = invokeQuietly(serverGetGlobalScheduler, Bukkit.getServer());
        this.globalExecute = global == null ? null
                : compatibleMethod(global.getClass(), "execute", Plugin.class, Runnable.class);
        this.playerGetBoundingBox = method(Entity.class, "getBoundingBox");
        this.entityGetTrackedBy = method(Entity.class, "getTrackedBy");
        this.entityIsInvisible = method(Entity.class, "isInvisible");
        this.worldGetMinHeight = method(World.class, "getMinHeight");
        this.worldGetMaxHeight = method(World.class, "getMaxHeight");
        this.entityGetPassengers = method(Entity.class, "getPassengers");
        this.folia = entityGetScheduler != null && ownedEntity != null && globalExecute != null;
    }

    /** Returns whether regionised scheduling and ownership checks are available. */
    public boolean folia() {
        return folia;
    }

    /** Human-readable runtime mode used by startup/debug output. */
    public String description() {
        return folia ? "Folia entity scheduler" : "classic Bukkit scheduler";
    }

    /** Monotonic approximate server tick, available even before Bukkit#getCurrentTick existed. */
    public int currentTick() {
        return (int) (System.nanoTime() / TICK_NANOS);
    }

    /** Runs a repeating player-owned task on Folia or the main thread on classic Bukkit. */
    public TaskHandle runEntityTimer(final Player player, final Runnable action, final Runnable retired,
                                     long delay, long period) {
        if (entityGetScheduler != null) {
            try {
                final Object scheduler = entityGetScheduler.invoke(player);
                Method run = findEntityTimer(scheduler.getClass());
                final Consumer<Object> callback = new Consumer<Object>() {
                    @Override
                    public void accept(Object ignored) {
                        action.run();
                    }
                };
                Object task = run.invoke(scheduler, plugin, callback, retired, delay, period);
                if (task == null) {
                    return null;
                }
                final Method cancel = compatibleMethod(task.getClass(), "cancel");
                return new TaskHandle() {
                    @Override
                    public void cancel() {
                        invokeQuietly(cancel, task);
                    }
                };
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Cannot schedule Folia entity timer", unwrap(exception));
            }
        }
        final BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    action.run();
                } else {
                    retired.run();
                }
            }
        }, delay, period);
        return new TaskHandle() {
            @Override
            public void cancel() {
                task.cancel();
            }
        };
    }

    /** Runs once on a player's owning context. */
    public void runEntity(final Player player, final Runnable action, final Runnable retired) {
        if (owns(player)) {
            action.run();
            return;
        }
        if (entityGetScheduler != null) {
            try {
                Object scheduler = entityGetScheduler.invoke(player);
                Method run = findEntityRun(scheduler.getClass());
                Consumer<Object> callback = new Consumer<Object>() {
                    @Override
                    public void accept(Object ignored) {
                        action.run();
                    }
                };
                run.invoke(scheduler, plugin, callback, retired);
                return;
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Cannot schedule Folia entity task", unwrap(exception));
            }
        }
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    action.run();
                } else {
                    retired.run();
                }
            }
        });
    }

    /** Runs on the global context on Folia or the main thread on classic Bukkit. */
    public void runGlobal(Runnable action) {
        if (globalExecute != null) {
            Object global = invokeQuietly(serverGetGlobalScheduler, Bukkit.getServer());
            if (global != null) {
                try {
                    globalExecute.invoke(global, plugin, action);
                    return;
                } catch (ReflectiveOperationException exception) {
                    throw new IllegalStateException("Cannot schedule Folia global task", unwrap(exception));
                }
            }
        }
        Bukkit.getScheduler().runTask(plugin, action);
    }

    /** Returns whether the current thread owns a player; classic Bukkit is a single region. */
    public boolean owns(Player player) {
        if (ownedEntity == null) {
            return Bukkit.isPrimaryThread();
        }
        Object value = invokeQuietly(ownedEntity, null, player);
        return Boolean.TRUE.equals(value);
    }

    /** Returns whether the complete X/Z chunk rectangle is owned by the current region. */
    public boolean owns(World world, int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ) {
        if (ownedChunkRange == null) {
            return Bukkit.isPrimaryThread();
        }
        Object value = invokeQuietly(ownedChunkRange, null,
                world, minChunkX, minChunkZ, maxChunkX, maxChunkZ);
        return Boolean.TRUE.equals(value);
    }

    /** Runtime-compatible world minimum height. */
    public int minHeight(World world) {
        Object value = invokeQuietly(worldGetMinHeight, world);
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    /** Runtime-compatible world maximum height. */
    public int maxHeight(World world) {
        Object value = invokeQuietly(worldGetMaxHeight, world);
        return value instanceof Number ? ((Number) value).intValue() : 256;
    }

    /** Captures the exact public Paper bounding box when available, otherwise a legacy player box. */
    public Aabb playerBounds(Player player, Location feet) {
        Object box = invokeQuietly(playerGetBoundingBox, player);
        Aabb converted = box == null ? null : reflectiveAabb(box);
        if (converted != null) {
            return converted;
        }
        double halfWidth = 0.30;
        double height = player.isSneaking() ? 1.65 : 1.80;
        return new Aabb(feet.getX() - halfWidth, feet.getY(), feet.getZ() - halfWidth,
                feet.getX() + halfWidth, feet.getY() + height, feet.getZ() + halfWidth);
    }

    /** Returns Paper tracker viewers, or a conservative classic-Bukkit snapshot on old servers. */
    public Set<UUID> trackedViewers(Player target) {
        Object value = invokeQuietly(entityGetTrackedBy, target);
        if (value instanceof Collection<?>) {
            Set<UUID> result = new HashSet<UUID>();
            for (Object entry : (Collection<?>) value) {
                if (entry instanceof Player) {
                    result.add(((Player) entry).getUniqueId());
                }
            }
            return result;
        }
        Set<UUID> result = new HashSet<UUID>();
        for (Player player : onlinePlayers()) {
            if (!player.getUniqueId().equals(target.getUniqueId())
                    && player.getWorld().equals(target.getWorld())) {
                result.add(player.getUniqueId());
            }
        }
        return result;
    }

    /** Returns the entity invisible flag when the API exposes it (1.9+). */
    public boolean invisibleFlag(Player player) {
        return Boolean.TRUE.equals(invokeQuietly(entityIsInvisible, player));
    }

    /** Returns the generic scale attribute on versions that have it, otherwise vanilla 1.0. */
    public double entityScale(Player player) {
        try {
            Class<?> attributeType = Class.forName("org.bukkit.attribute.Attribute");
            Object scale = staticField(attributeType, "SCALE");
            if (scale == null) {
                scale = staticField(attributeType, "GENERIC_SCALE");
            }
            if (scale == null) {
                return 1.0;
            }
            Method getAttribute = player.getClass().getMethod("getAttribute", attributeType);
            Object instance = getAttribute.invoke(player, scale);
            if (instance == null) {
                return 1.0;
            }
            Method getValue = instance.getClass().getMethod("getValue");
            return ((Number) getValue.invoke(instance)).doubleValue();
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return 1.0;
        }
    }

    /** Returns current vehicle passengers without linking to the post-1.8 API. */
    public Collection<? extends Entity> passengers(Entity vehicle) {
        Object value = invokeQuietly(entityGetPassengers, vehicle);
        if (value instanceof Collection<?>) {
            Collection<?> raw = (Collection<?>) value;
            Collection<Entity> result = new ArrayList<Entity>(raw.size());
            for (Object entry : raw) {
                if (entry instanceof Entity) {
                    result.add((Entity) entry);
                }
            }
            return result;
        }
        Entity passenger = vehicle == null ? null : vehicle.getPassenger();
        return passenger == null ? Collections.<Entity>emptyList()
                : Collections.singletonList(passenger);
    }

    @SuppressWarnings("unchecked")
    private static Collection<? extends Player> onlinePlayers() {
        Object value = Bukkit.getOnlinePlayers();
        if (value instanceof Collection<?>) {
            return (Collection<? extends Player>) value;
        }
        Player[] array = (Player[]) value;
        ArrayList<Player> players = new ArrayList<Player>(array.length);
        Collections.addAll(players, array);
        return players;
    }

    private static Aabb reflectiveAabb(Object box) {
        try {
            return new Aabb(number(box, "getMinX", "a"), number(box, "getMinY", "b"),
                    number(box, "getMinZ", "c"), number(box, "getMaxX", "d"),
                    number(box, "getMaxY", "e"), number(box, "getMaxZ", "f"));
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static double number(Object target, String methodName, String fieldName)
            throws ReflectiveOperationException {
        Method getter = method(target.getClass(), methodName);
        if (getter != null) {
            return ((Number) getter.invoke(target)).doubleValue();
        }
        Field field = target.getClass().getField(fieldName);
        return ((Number) field.get(target)).doubleValue();
    }

    private static Method findEntityTimer(Class<?> type) throws NoSuchMethodException {
        for (Method method : type.getMethods()) {
            if (method.getName().equals("runAtFixedRate") && method.getParameterTypes().length == 5) {
                return method;
            }
        }
        throw new NoSuchMethodException(type.getName() + "#runAtFixedRate(5 args)");
    }

    private static Method findEntityRun(Class<?> type) throws NoSuchMethodException {
        for (Method method : type.getMethods()) {
            if (method.getName().equals("run") && method.getParameterTypes().length == 4) {
                return method;
            }
        }
        throw new NoSuchMethodException(type.getName() + "#run(4 args)");
    }

    private static Method compatibleMethod(Class<?> type, String name, Class<?>... parameters) {
        try {
            return type.getMethod(name, parameters);
        } catch (NoSuchMethodException ignored) {
            for (Method candidate : type.getMethods()) {
                if (candidate.getName().equals(name)
                        && candidate.getParameterTypes().length == parameters.length) {
                    return candidate;
                }
            }
            return null;
        }
    }

    private static Method method(Class<?> type, String name, Class<?>... parameters) {
        try {
            return type.getMethod(name, parameters);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Object staticField(Class<?> type, String name) {
        try {
            return type.getField(name).get(null);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Object invokeQuietly(Method method, Object target, Object... arguments) {
        if (method == null) {
            return null;
        }
        try {
            return method.invoke(target, arguments);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static Throwable unwrap(ReflectiveOperationException exception) {
        if (exception instanceof InvocationTargetException
                && ((InvocationTargetException) exception).getCause() != null) {
            return ((InvocationTargetException) exception).getCause();
        }
        return exception;
    }
}
