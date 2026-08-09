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
 * @param hidden final configured invisibility result
 */
public record InvisibilityDebugSnapshot(
        boolean potionEffect,
        boolean entityInvisibleFlag,
        boolean spectator,
        boolean bukkitCanSee,
        List<String> vanishHooks,
        boolean hidden
) {
}
