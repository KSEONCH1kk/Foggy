package dev.foggy.integration;

import java.util.UUID;
import org.bukkit.entity.Player;

/**
 * Optional bridge for plugins that render a player through additional client-side entities.
 *
 * <p>Foggy always owns the authoritative player visibility decision. Implementations mirror that
 * decision to renderer entities without changing Bukkit's global {@code canSee} state.</p>
 */
public interface PlayerRenderCompatibility {
    /** No-op implementation used when no supported renderer plugin is installed. */
    PlayerRenderCompatibility NONE = new PlayerRenderCompatibility() {
        @Override
        public boolean hide(Player viewer, UUID targetId) {
            return false;
        }

        @Override
        public void show(Player viewer, UUID targetId) {
        }

        @Override
        public void close() {
        }
    };

    /**
     * Hides renderer entities belonging to {@code targetId} from one viewer.
     *
     * @return whether the bridge emitted the renderer removal packets
     */
    boolean hide(Player viewer, UUID targetId);

    /** Recreates renderer entities belonging to {@code targetId} for one viewer. */
    void show(Player viewer, UUID targetId);

    /** Unregisters listeners and releases renderer state. */
    void close();
}
