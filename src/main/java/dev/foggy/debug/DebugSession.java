package dev.foggy.debug;

import java.util.UUID;

/**
 * Mutable operator debug selection.
 *
 * @param targetId selected target UUID
 * @param particles whether visual ray overlays are enabled
 */
public record DebugSession(UUID targetId, boolean particles) {
    /**
     * Returns a copy with particle rendering changed.
     *
     * @param enabled new particle state
     * @return updated session
     */
    public DebugSession withParticles(boolean enabled) {
        return new DebugSession(targetId, enabled);
    }
}
