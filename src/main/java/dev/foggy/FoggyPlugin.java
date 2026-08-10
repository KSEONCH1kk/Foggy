package dev.foggy;

import com.github.retrooper.packetevents.PacketEvents;
import dev.foggy.camera.CameraEstimator;
import dev.foggy.camera.CompanionCameraRegistry;
import dev.foggy.config.FoggyConfig;
import dev.foggy.debug.FoggyDebugCommand;
import dev.foggy.integration.GSitCompatibility;
import dev.foggy.integration.PlayerRenderCompatibility;
import dev.foggy.invisibility.InvisibilityTracker;
import dev.foggy.listener.FoggyListener;
import dev.foggy.packet.FoggyPacketListener;
import dev.foggy.packet.PacketVisibilityController;
import dev.foggy.platform.PlatformAdapter;
import dev.foggy.raycast.CompensatedRaycastService;
import dev.foggy.raycast.CompensatedWorld;
import dev.foggy.raycast.CompensatedWorldListener;
import dev.foggy.raycast.TargetPointSampler;
import dev.foggy.raycast.VanillaBlockRaycaster;
import dev.foggy.visibility.VisibilityEngine;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.function.Consumer;
import java.util.logging.Level;

/** Paper/Folia entry point for Foggy. */
public final class FoggyPlugin extends JavaPlugin {
    private FoggyPacketListener packetListener;
    private PacketVisibilityController packetController;
    private VisibilityEngine visibilityEngine;
    private CompanionCameraRegistry companion;
    private FoggyDebugCommand debugCommand;
    private FoggyListener bukkitListener;
    private CompensatedWorldListener compensatedWorldListener;
    private CompensatedWorld compensatedWorld;
    private PlatformAdapter platform;
    private PlayerRenderCompatibility renderCompatibility;
    private String companionChannel;
    private FoggyConfig activeSettings;

    /** Public no-argument constructor used by Paper's plugin loader. */
    public FoggyPlugin() {
    }

    @Override
    public void onEnable() {
        platform = new PlatformAdapter(this);
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
            stopRuntime(false);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getLogger().info("Foggy " + getDescription().getVersion()
                + " enabled on " + getServer().getBukkitVersion() + " using " + platform.description()
                + " / PacketEvents " + PacketEvents.getAPI().getVersion());
    }

    @Override
    public void onDisable() {
        stopRuntime(false);
        activeSettings = null;
    }

    /**
     * Reloads and validates {@code config.yml}, then replaces all configuration-bound services.
     * The currently active runtime is left untouched when validation fails. If starting the new
     * runtime fails, Foggy attempts to reconstruct the previous one before returning an error.
     *
     * @param callback receives the reload outcome on the global region
     */
    public void reloadRuntime(Consumer<ReloadResult> callback) {
        platform.runGlobal(() -> callback.accept(reloadRuntimeNow()));
    }

    private ReloadResult reloadRuntimeNow() {
        final FoggyConfig candidate;
        try {
            reloadConfig();
            candidate = FoggyConfig.load(getConfig());
        } catch (RuntimeException exception) {
            getLogger().warning("Config reload rejected: " + exception.getMessage());
            return new ReloadResult(false, "config.yml не применён: " + exception.getMessage());
        }

        FoggyConfig previous = activeSettings;
        stopRuntime(true);
        try {
            startRuntime(candidate);
            getLogger().info("Configuration reloaded successfully");
            return new ReloadResult(true,
                    "конфиг перезагружен: radius=" + candidate.visibilityRadius()
                            + ", hideTicks=" + candidate.hideConfirmationTicks()
                            + ", transparent=" + candidate.transparentBlockMode()
                            + ", cutout=" + candidate.cutoutBlockMode());
        } catch (RuntimeException exception) {
            getLogger().log(Level.SEVERE, "Could not start runtime from reloaded config", exception);
            stopRuntime(true);
            if (previous != null) {
                try {
                    startRuntime(previous);
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

        TargetPointSampler pointSampler = new TargetPointSampler(settings);
        if (packetController == null) {
            packetController = new PacketVisibilityController(this, getLogger(), platform);
            renderCompatibility = GSitCompatibility.install(
                    this, packetController, platform, getLogger());
            packetController.setRenderCompatibility(renderCompatibility);
        }
        compensatedWorld = new CompensatedWorld(settings, getLogger(), platform);
        VanillaBlockRaycaster blockRaycaster = new VanillaBlockRaycaster(settings, compensatedWorld);
        CameraEstimator cameraEstimator = new CameraEstimator(settings, companion, blockRaycaster, platform);
        CompensatedRaycastService raycastService = new CompensatedRaycastService(
                settings, blockRaycaster, platform);
        InvisibilityTracker invisibilityTracker = new InvisibilityTracker(settings, getLogger(), platform);
        visibilityEngine = new VisibilityEngine(
                this,
                settings,
                cameraEstimator,
                raycastService,
                invisibilityTracker,
                packetController,
                pointSampler,
                platform);

        debugCommand = new FoggyDebugCommand(
                this, cameraEstimator, raycastService, invisibilityTracker, visibilityEngine, packetController,
                platform);
        visibilityEngine.setViewerTickHook(debugCommand::tickViewer);
        PluginCommand foggyCommand = getCommand("foggy");
        if (foggyCommand == null) {
            throw new IllegalStateException("Command 'foggy' is missing from plugin.yml");
        }
        foggyCommand.setExecutor(debugCommand);
        foggyCommand.setTabCompleter(debugCommand);

        if (packetListener == null) {
            packetListener = new FoggyPacketListener(packetController);
            PacketEvents.getAPI().getEventManager().registerListener(packetListener);
        }
        bukkitListener = new FoggyListener(visibilityEngine, companion, debugCommand);
        getServer().getPluginManager().registerEvents(bukkitListener, this);
        compensatedWorldListener = new CompensatedWorldListener(compensatedWorld);
        getServer().getPluginManager().registerEvents(compensatedWorldListener, this);
        compensatedWorldListener.registerOptionalEvents(this);
        visibilityEngine.start(getServer().getOnlinePlayers());
        activeSettings = settings;
    }

    private void stopRuntime(boolean preservePacketState) {
        if (bukkitListener != null) {
            HandlerList.unregisterAll(bukkitListener);
            bukkitListener = null;
        }
        if (compensatedWorldListener != null) {
            HandlerList.unregisterAll(compensatedWorldListener);
            compensatedWorldListener = null;
        }
        if (visibilityEngine != null) {
            visibilityEngine.shutdown(!preservePacketState);
            visibilityEngine = null;
        }
        if (debugCommand != null) {
            debugCommand.shutdown();
            debugCommand = null;
        }
        if (!preservePacketState) {
            if (renderCompatibility != null) {
                renderCompatibility.close();
                renderCompatibility = null;
            }
            if (packetListener != null && PacketEvents.getAPI() != null) {
                PacketEvents.getAPI().getEventManager().unregisterListener(packetListener);
                packetListener = null;
            }
            if (packetController != null) {
                packetController.clear();
            }
            packetController = null;
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
        if (compensatedWorld != null) {
            compensatedWorld.clear();
            compensatedWorld = null;
        }
    }

    /** Result of a live runtime reload. */
    public static final class ReloadResult {
        private final boolean success;
        private final String message;

        /**
         * Creates a reload result.
         *
         * @param success whether the new configuration is active
         * @param message concise operator-facing explanation
         */
        public ReloadResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean success() { return success; }
        public String message() { return message; }
    }
}
