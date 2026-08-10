package dev.foggy.invisibility;

import java.util.List;

/** Immutable state-based visibility inputs shown by the operator debugger. */
public final class InvisibilityDebugSnapshot {
    private final boolean potionEffect;
    private final boolean entityInvisibleFlag;
    private final boolean spectator;
    private final boolean bukkitCanSee;
    private final List<String> vanishHooks;
    private final InvisibilityDisposition disposition;

    public InvisibilityDebugSnapshot(boolean potionEffect, boolean entityInvisibleFlag,
                                     boolean spectator, boolean bukkitCanSee,
                                     List<String> vanishHooks, InvisibilityDisposition disposition) {
        this.potionEffect = potionEffect;
        this.entityInvisibleFlag = entityInvisibleFlag;
        this.spectator = spectator;
        this.bukkitCanSee = bukkitCanSee;
        this.vanishHooks = vanishHooks;
        this.disposition = disposition;
    }

    public boolean potionEffect() { return potionEffect; }
    public boolean entityInvisibleFlag() { return entityInvisibleFlag; }
    public boolean spectator() { return spectator; }
    public boolean bukkitCanSee() { return bukkitCanSee; }
    public List<String> vanishHooks() { return vanishHooks; }
    public InvisibilityDisposition disposition() { return disposition; }
    public boolean packetHidden() { return disposition.removesEntity(); }
}
