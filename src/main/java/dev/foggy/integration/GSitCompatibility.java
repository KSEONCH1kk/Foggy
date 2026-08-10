package dev.foggy.integration;

import dev.foggy.packet.PacketVisibilityController;
import dev.foggy.platform.PlatformAdapter;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;

/**
 * Reflective compatibility bridge for GSit poses and seats.
 *
 * <p>Normal GSit sitting is represented by a vehicle/passenger relationship and is handled by
 * Foggy's generic passenger packet path. GSit poses ({@code /lay}, {@code /layback},
 * {@code /bellyflop}, and {@code /spin}) render a separate fake-player NPC while making the real
 * player invisible. GSit does not expose per-viewer pose rendering in its public API, so this
 * bridge registers the documented pose events and calls the pose implementation's existing
 * per-viewer add/remove methods reflectively. No GSit type appears in Foggy's class descriptors,
 * allowing the same JAR to load when GSit is absent and on Minecraft 1.8.</p>
 */
public final class GSitCompatibility implements PlayerRenderCompatibility, Listener {
    private static final String API_CLASS = "dev.geco.gsit.api.GSitAPI";
    private static final String POSE_EVENT = "dev.geco.gsit.api.event.PlayerPoseEvent";
    private static final String STOP_POSE_EVENT = "dev.geco.gsit.api.event.PlayerStopPoseEvent";

    private final Plugin plugin;
    private final PacketVisibilityController controller;
    private final PlatformAdapter platform;
    private final Logger logger;
    private final Map<UUID, ActivePose> poses = new ConcurrentHashMap<>();
    private final AtomicBoolean invocationWarning = new AtomicBoolean();
    private final AtomicBoolean discoveryWarning = new AtomicBoolean();
    private volatile boolean enabled;

    private GSitCompatibility(Plugin plugin, PacketVisibilityController controller,
                              PlatformAdapter platform, Logger logger) {
        this.plugin = plugin;
        this.controller = controller;
        this.platform = platform;
        this.logger = logger;
    }

    /**
     * Installs the optional bridge when an enabled GSit plugin is present.
     *
     * @return active bridge, or the no-op implementation when GSit is absent/incompatible
     */
    public static PlayerRenderCompatibility install(Plugin plugin,
                                                     PacketVisibilityController controller,
                                                     PlatformAdapter platform, Logger logger) {
        Plugin gsit = Bukkit.getPluginManager().getPlugin("GSit");
        if (gsit == null || !gsit.isEnabled()) {
            return PlayerRenderCompatibility.NONE;
        }
        GSitCompatibility bridge = new GSitCompatibility(plugin, controller, platform, logger);
        try {
            bridge.register(gsit);
            logger.info("GSit compatibility enabled for seats, passenger stacks and pose NPCs"
                    + " (GSit " + gsit.getDescription().getVersion() + ")");
            return bridge;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            bridge.close();
            logger.log(Level.WARNING, "GSit detected, but its pose API is incompatible;"
                    + " seat/passenger compatibility remains active", exception);
            return PlayerRenderCompatibility.NONE;
        }
    }

    @Override
    public boolean hide(Player viewer, UUID targetId) {
        ActivePose pose = poses.get(targetId);
        if (pose != null) {
            return invokeViewer(pose.access.removeViewer, pose.pose, viewer, "hide");
        }
        return false;
    }

    @Override
    public void show(Player viewer, UUID targetId) {
        ActivePose pose = poses.get(targetId);
        if (pose != null) {
            invokeViewer(pose.access.addViewer, pose.pose, viewer, "show");
        }
    }

    @Override
    public void close() {
        enabled = false;
        HandlerList.unregisterAll(this);
        for (ActivePose pose : poses.values()) {
            controller.unregisterRelatedEntity(pose.playerEntityId, pose.access.entityId);
        }
        poses.clear();
    }

    private void register(Plugin gsit) throws ReflectiveOperationException {
        ClassLoader loader = gsit.getClass().getClassLoader();
        Class<? extends Event> poseEvent = eventType(loader, POSE_EVENT);
        Class<? extends Event> stopEvent = eventType(loader, STOP_POSE_EVENT);
        registerEvent(poseEvent, false);
        registerEvent(stopEvent, true);
        enabled = true;
        discoverExisting(loader);
    }

    private void registerEvent(Class<? extends Event> eventType, final boolean stopping) {
        Bukkit.getPluginManager().registerEvent(eventType, this, EventPriority.MONITOR,
                new EventExecutor() {
                    @Override
                    public void execute(Listener ignored, Event event) {
                        if (!enabled) {
                            return;
                        }
                        try {
                            if (stopping) {
                                unregisterPose(player(event).getUniqueId());
                            } else {
                                registerPose(player(event), invoke(event, "getPose"));
                            }
                        } catch (ReflectiveOperationException | RuntimeException exception) {
                            if (discoveryWarning.compareAndSet(false, true)) {
                                logger.log(Level.WARNING, "Could not track a GSit pose renderer;"
                                        + " future discovery failures will be suppressed", exception);
                            }
                        }
                    }
                }, plugin, true);
    }

    private void discoverExisting(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> api = Class.forName(API_CLASS, true, loader);
        Object value = api.getMethod("getAllPoses").invoke(null);
        if (!(value instanceof Map<?, ?>)) {
            return;
        }
        Collection<?> existing = ((Map<?, ?>) value).values();
        for (Object pose : existing) {
            Object target = invoke(pose, "getPlayer");
            if (target instanceof Player) {
                registerPose((Player) target, pose);
            }
        }
    }

    private void registerPose(Player player, Object pose) throws ReflectiveOperationException {
        PoseAccess access = PoseAccess.discover(pose);
        ActivePose active = new ActivePose(pose, access, player.getEntityId());
        ActivePose previous = poses.put(player.getUniqueId(), active);
        if (previous != null) {
            controller.unregisterRelatedEntity(previous.playerEntityId, previous.access.entityId);
        }
        controller.registerRelatedEntity(active.playerEntityId, access.entityId);

        for (UUID viewerId : controller.hiddenViewers(active.playerEntityId)) {
            final Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer == null || !viewer.isOnline()) {
                continue;
            }
            platform.runEntity(viewer,
                    new Runnable() {
                        @Override
                        public void run() {
                            invokeViewer(access.removeViewer, pose, viewer, "hide newly-created");
                        }
                    }, new Runnable() {
                        @Override
                        public void run() {
                        }
                    });
        }
    }

    private void unregisterPose(UUID playerId) {
        ActivePose pose = poses.remove(playerId);
        if (pose != null) {
            controller.unregisterRelatedEntity(pose.playerEntityId, pose.access.entityId);
        }
    }

    private boolean invokeViewer(Method method, Object pose, Player viewer, String action) {
        try {
            method.invoke(pose, viewer);
            return true;
        } catch (IllegalAccessException | InvocationTargetException
                 | LinkageError | RuntimeException exception) {
            if (invocationWarning.compareAndSet(false, true)) {
                logger.log(Level.WARNING, "Could not " + action + " a GSit pose renderer;"
                        + " future failures will be suppressed", unwrap(exception));
            }
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Event> eventType(ClassLoader loader, String name)
            throws ClassNotFoundException {
        return (Class<? extends Event>) Class.forName(name, false, loader).asSubclass(Event.class);
    }

    private static Player player(Object event) throws ReflectiveOperationException {
        Object value = invoke(event, "getPlayer");
        if (!(value instanceof Player)) {
            throw new IllegalStateException("GSit pose event did not expose a Bukkit Player");
        }
        return (Player) value;
    }

    private static Object invoke(Object target, String method) throws ReflectiveOperationException {
        return target.getClass().getMethod(method).invoke(target);
    }

    private static Throwable unwrap(Throwable throwable) {
        return throwable instanceof InvocationTargetException
                && ((InvocationTargetException) throwable).getCause() != null
                ? ((InvocationTargetException) throwable).getCause() : throwable;
    }

    private static final class ActivePose {
        private final Object pose;
        private final PoseAccess access;
        private final int playerEntityId;

        private ActivePose(Object pose, PoseAccess access, int playerEntityId) {
            this.pose = pose;
            this.access = access;
            this.playerEntityId = playerEntityId;
        }
    }

    /** Cached access to the stable GSit pose renderer internals across its NMS version modules. */
    static final class PoseAccess {
        private final int entityId;
        private final Method addViewer;
        private final Method removeViewer;

        private PoseAccess(int entityId, Method addViewer, Method removeViewer) {
            this.entityId = entityId;
            this.addViewer = addViewer;
            this.removeViewer = removeViewer;
        }

        static PoseAccess discover(Object pose) throws ReflectiveOperationException {
            Field npcField = field(pose.getClass(), "playerNpc");
            npcField.setAccessible(true);
            Object npc = npcField.get(pose);
            if (npc == null) {
                throw new IllegalStateException("GSit pose has no renderer NPC");
            }
            Method entityId = method(npc.getClass(), "getId", "getEntityId");
            entityId.setAccessible(true);
            int id = ((Number) entityId.invoke(npc)).intValue();
            Method add = declaredMethod(pose.getClass(), "addViewerPlayer", Player.class);
            Method remove = declaredMethod(pose.getClass(), "removeViewerPlayer", Player.class);
            add.setAccessible(true);
            remove.setAccessible(true);
            return new PoseAccess(id, add, remove);
        }

        int entityId() {
            return entityId;
        }

        void add(Object pose, Player viewer) throws ReflectiveOperationException {
            addViewer.invoke(pose, viewer);
        }

        void remove(Object pose, Player viewer) throws ReflectiveOperationException {
            removeViewer.invoke(pose, viewer);
        }

        private static Field field(Class<?> type, String name) throws NoSuchFieldException {
            for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass()) {
                try {
                    return cursor.getDeclaredField(name);
                } catch (NoSuchFieldException ignored) {
                }
            }
            throw new NoSuchFieldException(type.getName() + "#" + name);
        }

        private static Method declaredMethod(Class<?> type, String name, Class<?> parameter)
                throws NoSuchMethodException {
            for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass()) {
                try {
                    return cursor.getDeclaredMethod(name, parameter);
                } catch (NoSuchMethodException ignored) {
                }
            }
            throw new NoSuchMethodException(type.getName() + "#" + name);
        }

        private static Method method(Class<?> type, String... names) throws NoSuchMethodException {
            for (String name : names) {
                try {
                    return type.getMethod(name);
                } catch (NoSuchMethodException ignored) {
                }
            }
            throw new NoSuchMethodException(type.getName() + "#getId/getEntityId");
        }
    }
}
