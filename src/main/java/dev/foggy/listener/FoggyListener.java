package dev.foggy.listener;

import com.destroystokyo.paper.event.server.ServerTickEndEvent;
import dev.foggy.camera.CompanionCameraRegistry;
import dev.foggy.debug.FoggyDebugCommand;
import dev.foggy.visibility.VisibilityEngine;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffectType;

/** Bridges Paper lifecycle/discrete-state events to the end-of-tick visibility engine. */
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
     * Evaluates after movement and world changes have been applied for this server tick.
     *
     * @param event Paper tick-end event
     */
    @EventHandler
    public void onTickEnd(ServerTickEndEvent event) {
        visibilityEngine.tick();
        debugCommand.tick();
    }

    /**
     * Pre-arms pair state before normal tracker spawn packets are flushed.
     *
     * @param event player join event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        visibilityEngine.recomputePlayer(event.getPlayer());
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
     * Re-evaluates the new world's pairs; the regular tick pass releases stale old-world pairs.
     *
     * @param event completed world-change event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        visibilityEngine.recomputePlayer(event.getPlayer());
    }

    /**
     * Immediately hides newly invisible players; removals are shown at this same tick's end.
     *
     * @param event potion-effect transition
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPotionEffect(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player)
                || !event.getModifiedType().equals(PotionEffectType.INVISIBILITY)) {
            return;
        }
        if (event.getNewEffect() != null) {
            visibilityEngine.recomputeTarget(player, true);
        }
    }

    /**
     * Immediately hides players entering spectator; leaving is shown at this same tick's end.
     *
     * @param event game-mode transition
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameMode(PlayerGameModeChangeEvent event) {
        if (event.getNewGameMode() == GameMode.SPECTATOR) {
            visibilityEngine.recomputeTarget(event.getPlayer(), true);
        }
    }
}
