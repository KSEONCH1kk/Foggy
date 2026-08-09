package dev.foggy.visibility;

/**
 * Per-pair state with hide-only hysteresis. Showing always transitions on the first visible decision.
 */
public final class PairVisibilityState {
    private boolean hidden;
    private int pendingHideTicks;
    private int entityId;
    private HideReason lastReason = HideReason.NONE;

    /**
     * Creates a pair in the client-visible state.
     *
     * @param entityId current Bukkit entity id
     */
    public PairVisibilityState(int entityId) {
        this.entityId = entityId;
    }

    /**
     * Applies a decision and returns the packet-level transition, if any.
     *
     * @param reason current visibility decision
     * @param hideConfirmationTicks optical hide threshold
     * @return transition to apply
     */
    public VisibilityTransition apply(HideReason reason, int hideConfirmationTicks) {
        lastReason = reason;
        if (reason == HideReason.NONE) {
            pendingHideTicks = 0;
            if (hidden) {
                hidden = false;
                return VisibilityTransition.SHOW;
            }
            return VisibilityTransition.NONE;
        }
        if (hidden) {
            return VisibilityTransition.NONE;
        }
        pendingHideTicks++;
        int required = reason.immediate() ? 1 : hideConfirmationTicks;
        if (pendingHideTicks >= required) {
            hidden = true;
            pendingHideTicks = 0;
            return VisibilityTransition.HIDE;
        }
        return VisibilityTransition.NONE;
    }

    /**
     * Resets state after the Bukkit entity id changes.
     *
     * @param newEntityId replacement entity id
     */
    public void resetForEntity(int newEntityId) {
        hidden = false;
        pendingHideTicks = 0;
        entityId = newEntityId;
        lastReason = HideReason.NONE;
    }

    /**
     * Returns whether Foggy has destroyed the target for this viewer.
     *
     * @return hidden state
     */
    public boolean hidden() {
        return hidden;
    }

    /**
     * Returns the entity id associated with this state.
     *
     * @return current entity id
     */
    public int entityId() {
        return entityId;
    }

    /**
     * Returns the most recent engine decision.
     *
     * @return latest hide reason
     */
    public HideReason lastReason() {
        return lastReason;
    }

    /**
     * Returns the current optical-hide confirmation counter.
     *
     * @return pending consecutive hide decisions
     */
    public int pendingHideTicks() {
        return pendingHideTicks;
    }
}
