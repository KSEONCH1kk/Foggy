package dev.foggy.invisibility;

import java.util.List;

/**
 * State-based visibility inputs shown by the operator debugger.
 *
 * @param potionEffect target has invisibility potion
 * @param entityInvisibleFlag Bukkit/NMS invisible flag is set
 * @param spectator target is in spectator mode
 * @param bukkitCanSee current {@code viewer.canSee(target)} value
 * @param vanishHooks hooks that reported the target vanished
 * @param disposition final configured packet treatment
 */
public record InvisibilityDebugSnapshot(
        boolean potionEffect,
        boolean entityInvisibleFlag,
        boolean spectator,
        boolean bukkitCanSee,
        List<String> vanishHooks,
        InvisibilityDisposition disposition
) {
    /**
     * Returns whether state-based invisibility removes the entity from this viewer.
     *
     * @return true only for hard/legacy packet hiding
     */
    public boolean packetHidden() {
        return disposition.removesEntity();
    }
}
