package dev.foggy.invisibility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable target-owned invisibility signals captured on its owning scheduler. */
public final class TargetInvisibilityState {
    private final boolean potionEffect;
    private final boolean entityInvisibleFlag;
    private final boolean spectator;
    private final List<String> vanishHooks;

    public TargetInvisibilityState(boolean potionEffect, boolean entityInvisibleFlag,
                                   boolean spectator, List<String> vanishHooks) {
        this.potionEffect = potionEffect;
        this.entityInvisibleFlag = entityInvisibleFlag;
        this.spectator = spectator;
        this.vanishHooks = Collections.unmodifiableList(new ArrayList<String>(vanishHooks));
    }

    public boolean potionEffect() { return potionEffect; }
    public boolean entityInvisibleFlag() { return entityInvisibleFlag; }
    public boolean spectator() { return spectator; }
    public List<String> vanishHooks() { return vanishHooks; }
}
