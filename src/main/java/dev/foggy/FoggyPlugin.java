package dev.foggy;

import com.github.retrooper.packetevents.PacketEvents;
import dev.foggy.camera.CameraEstimator;
import dev.foggy.camera.CompanionCameraRegistry;
import dev.foggy.config.FoggyConfig;
import dev.foggy.debug.FoggyDebugCommand;
import dev.foggy.invisibility.InvisibilityTracker;
import dev.foggy.listener.FoggyListener;
import dev.foggy.packet.FoggyPacketListener;
import dev.foggy.packet.PacketVisibilityController;
import dev.foggy.raycast.BukkitRaycastService;
import dev.foggy.raycast.MotionTracker;
import dev.foggy.raycast.TargetPointSampler;
import dev.foggy.raycast.VanillaBlockRaycaster;
import dev.foggy.visibility.VisibilityEngine;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.logging.Level;

/** Paper entry point for Foggy. */
public final class FoggyPlugin extends JavaPlugin {
    private FoggyPacketListener packetListener;
    private VisibilityEngine visibilityEngine;
    private CompanionCameraRegistry companion;
    private FoggyDebugCommand debugCommand;
    private FoggyListener bukkitListener;
    private String companionChannel;
    private FoggyConfig activeSettings;

    /** Public no-argument constructor used by Paper's plugin loader. */
    public FoggyPlugin() {
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        final FoggyConfig settings;
        try {
            settings = FoggyConfig.load(getConfig());
        } catch (IllegalArgumentException exception) {
            getLogger().severe("Invalid config.yml: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        try {
            startRuntime(settings);
        } catch (RuntimeException exception) {
            getLogger().log(Level.SEVERE, "Could not start Foggy runtime", exception);
            stopRuntime();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getLogger().info("Foggy " + getPluginMeta().getVersion()
                + " enabled for Paper 1.21.4 / PacketEvents 2.13.0");
    }

    @Override
    public void onDisable() {
        stopRuntime();
        activeSettings = null;
    }

    /**
     * Reloads and validates {@code config.yml}, then replaces all configuration-bound services.
     * The currently active runtime is left untouched when validation fails. If starting the new
     * runtime fails, Foggy attempts to reconstruct the previous one before returning an error.
     *
     * @return reload outcome suitable for displaying to a command sender
     */
    public ReloadResult reloadRuntime() {
        if (!getServer().isPrimaryThread()) {
            return new ReloadResult(false, "reload разрешён только в основном потоке сервера");
        }

        final FoggyConfig candidate;
        try {
            reloadConfig();
            candidate = FoggyConfig.load(getConfig());
        } catch (RuntimeException exception) {
            getLogger().warning("Config reload rejected: " + exception.getMessage());
            return new ReloadResult(false, "config.yml не применён: " + exception.getMessage());
        }

        FoggyConfig previous = activeSettings;
        stopRuntime();
        try {
            startRuntime(candidate);
            visibilityEngine.tick();
            getLogger().info("Configuration reloaded successfully");
            return new ReloadResult(true,
                    "конфиг перезагружен: radius=" + candidate.visibilityRadius()
                            + ", hideTicks=" + candidate.hideConfirmationTicks()
                            + ", transparent=" + candidate.transparentBlockMode()
                            + ", cutout=" + candidate.cutoutBlockMode());
        } catch (RuntimeException exception) {
            getLogger().log(Level.SEVERE, "Could not start runtime from reloaded config", exception);
            stopRuntime();
            if (previous != null) {
                try {
                    startRuntime(previous);
                    visibilityEngine.tick();
                    return new ReloadResult(false,
                            "новый конфиг не запущен; предыдущая конфигурация восстановлена. Причина: "
                                    + exception.getMessage());
                } catch (RuntimeException restoreException) {
                    getLogger().log(Level.SEVERE, "Could not restore previous Foggy runtime", restoreException);
                }
            }
            getServer().getPluginManager().disablePlugin(this);
            return new ReloadResult(false,
                    "ошибка reload и восстановления; Foggy отключён. Проверь console log");
        }
    }

    private void startRuntime(FoggyConfig settings) {

        companion = new CompanionCameraRegistry(settings);
        companionChannel = settings.companionChannel();
        if (settings.companionEnabled()) {
            getServer().getMessenger().registerIncomingPluginChannel(this, companionChannel, companion);
            getServer().getMessenger().registerOutgoingPluginChannel(this, companionChannel);
        }

        MotionTracker motionTracker = new MotionTracker();
        TargetPointSampler pointSampler = new TargetPointSampler(settings, motionTracker);
        PacketVisibilityController packetController = new PacketVisibilityController(getLogger());
        VanillaBlockRaycaster blockRaycaster = new VanillaBlockRaycaster(settings);
        CameraEstimator cameraEstimator = new CameraEstimator(settings, companion, blockRaycaster);
        BukkitRaycastService raycastService = new BukkitRaycastService(settings, pointSampler, blockRaycaster);
        InvisibilityTracker invisibilityTracker = new InvisibilityTracker(settings, getLogger());
        visibilityEngine = new VisibilityEngine(
                settings,
                cameraEstimator,
                raycastService,
                invisibilityTracker,
                packetController,
                motionTracker);

        debugCommand = new FoggyDebugCommand(
                this, cameraEstimator, raycastService, invisibilityTracker, visibilityEngine, packetController);
        PluginCommand foggyCommand = getCommand("foggy");
        if (foggyCommand == null) {
            throw new IllegalStateException("Command 'foggy' is missing from plugin.yml");
        }
        foggyCommand.setExecutor(debugCommand);
        foggyCommand.setTabCompleter(debugCommand);

        packetListener = new FoggyPacketListener(packetController);
        PacketEvents.getAPI().getEventManager().registerListener(packetListener);
        bukkitListener = new FoggyListener(visibilityEngine, companion, debugCommand);
        getServer().getPluginManager().registerEvents(bukkitListener, this);
        activeSettings = settings;
    }

    private void stopRuntime() {
        if (bukkitListener != null) {
            HandlerList.unregisterAll(bukkitListener);
            bukkitListener = null;
        }
        if (visibilityEngine != null) {
            visibilityEngine.shutdown();
            visibilityEngine = null;
        }
        if (debugCommand != null) {
            debugCommand.shutdown();
            debugCommand = null;
        }
        if (packetListener != null && PacketEvents.getAPI() != null) {
            PacketEvents.getAPI().getEventManager().unregisterListener(packetListener);
            packetListener = null;
        }
        if (companion != null) {
            companion.clear();
            companion = null;
        }
        if (companionChannel != null) {
            getServer().getMessenger().unregisterIncomingPluginChannel(this, companionChannel);
            getServer().getMessenger().unregisterOutgoingPluginChannel(this, companionChannel);
            companionChannel = null;
        }
    }

    /**
     * Result of a live runtime reload.
     *
     * @param success whether the new configuration is active
     * @param message concise operator-facing explanation
     */
    public record ReloadResult(boolean success, String message) {
    }
}
