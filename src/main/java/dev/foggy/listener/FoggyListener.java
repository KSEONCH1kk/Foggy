package dev.foggy.listener;

import dev.foggy.camera.CompanionCameraRegistry;
import dev.foggy.debug.FoggyDebugCommand;
import dev.foggy.visibility.VisibilityEngine;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/** Bridges region-owned player lifecycle events to the entity-scheduled visibility engine. */
public final class FoggyListener implements Listener {
    private final VisibilityEngine visibilityEngine;
    private final CompanionCameraRegistry companion;
    private final FoggyDebugCommand debugCommand;

    /**
     * Creates the Bukkit/Paper event bridge.
     *
     * @param visibilityEngine visibility service
     * @param companion camera telemetry registry
     * @param debugCommand operator debug overlay
     */
    public FoggyListener(VisibilityEngine visibilityEngine, CompanionCameraRegistry companion,
                         FoggyDebugCommand debugCommand) {
        this.visibilityEngine = visibilityEngine;
        this.companion = companion;
        this.debugCommand = debugCommand;
    }

    /**
     * Pre-arms pair state before normal tracker spawn packets are flushed.
     *
     * @param event player join event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        visibilityEngine.track(event.getPlayer());
    }

    /**
     * Discards connection-bound state.
     *
     * @param event player quit event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        companion.remove(event.getPlayer());
        debugCommand.remove(event.getPlayer());
        visibilityEngine.remove(event.getPlayer());
    }

    /**
     * Rebinds the repeating task to the post-respawn entity scheduler.
     *
     * @param event completed player respawn
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        visibilityEngine.retrack(event.getPlayer());
    }

    /**
     * Re-evaluates the new world's pairs; the regular entity task releases stale old-world pairs.
     *
     * @param event completed world-change event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        visibilityEngine.refresh(event.getPlayer());
    }

    /**
     * Requests a region-owned refresh when a player enters spectator. The repeating entity task
     * observes the committed game mode no later than the following region tick.
     *
     * @param event game-mode transition
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameMode(PlayerGameModeChangeEvent event) {
        if (event.getNewGameMode() == GameMode.SPECTATOR) {
            visibilityEngine.refresh(event.getPlayer());
        }
    }
}
