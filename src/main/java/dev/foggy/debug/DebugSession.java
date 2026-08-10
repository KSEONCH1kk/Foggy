package dev.foggy.debug;

import java.util.UUID;

/** Immutable operator debug selection. */
public final class DebugSession {
    private final UUID targetId;
    private final boolean particles;

    public DebugSession(UUID targetId, boolean particles) {
        this.targetId = targetId;
        this.particles = particles;
    }

    public UUID targetId() { return targetId; }
    public boolean particles() { return particles; }
    public DebugSession withParticles(boolean enabled) { return new DebugSession(targetId, enabled); }
}
