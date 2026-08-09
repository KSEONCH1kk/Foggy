package dev.foggy.invisibility;

import java.util.List;

/**
 * Immutable target-owned invisibility signals captured on the target's entity scheduler.
 *
 * @param potionEffect target has the vanilla invisibility effect
 * @param entityInvisibleFlag target metadata invisible flag is set
 * @param spectator target is currently a spectator
 * @param vanishHooks optional vanish APIs that reported this target hidden
 */
public record TargetInvisibilityState(
        boolean potionEffect,
        boolean entityInvisibleFlag,
        boolean spectator,
        List<String> vanishHooks
) {
    /** Defensively freezes hook names for cross-region publication. */
    public TargetInvisibilityState {
        vanishHooks = List.copyOf(vanishHooks);
    }
}
