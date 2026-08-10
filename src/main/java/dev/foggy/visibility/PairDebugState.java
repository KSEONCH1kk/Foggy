package dev.foggy.visibility;

/** Immutable visibility-engine state exposed to operator diagnostics. */
public final class PairDebugState {
    private final boolean managed;
    private final HideReason reason;
    private final boolean hidden;
    private final int pendingHideTicks;
    private final int entityId;

    public PairDebugState(boolean managed, HideReason reason, boolean hidden,
                          int pendingHideTicks, int entityId) {
        this.managed = managed;
        this.reason = reason;
        this.hidden = hidden;
        this.pendingHideTicks = pendingHideTicks;
        this.entityId = entityId;
    }

    public static PairDebugState unmanaged(int entityId) {
        return new PairDebugState(false, HideReason.NONE, false, 0, entityId);
    }

    public boolean managed() { return managed; }
    public HideReason reason() { return reason; }
    public boolean hidden() { return hidden; }
    public int pendingHideTicks() { return pendingHideTicks; }
    public int entityId() { return entityId; }
}
