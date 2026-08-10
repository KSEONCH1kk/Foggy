package dev.foggy.visibility;

import java.util.UUID;

/** Directed visibility relation: viewer sees target. */
public final class PairKey {
    private final UUID viewerId;
    private final UUID targetId;

    public PairKey(UUID viewerId, UUID targetId) {
        this.viewerId = viewerId;
        this.targetId = targetId;
    }

    public UUID viewerId() { return viewerId; }
    public UUID targetId() { return targetId; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PairKey)) return false;
        PairKey key = (PairKey) other;
        return viewerId.equals(key.viewerId) && targetId.equals(key.targetId);
    }

    @Override public int hashCode() { return 31 * viewerId.hashCode() + targetId.hashCode(); }
}
