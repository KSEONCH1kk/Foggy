package dev.foggy.visibility;

/**
 * Read-only visibility-engine state exposed to the operator debug command.
 *
 * @param managed whether the pair is currently inside Foggy's managed radius
 * @param reason latest engine decision
 * @param hidden whether the transition state is hidden
 * @param pendingHideTicks current hide debounce count
 * @param entityId target entity id stored with the pair
 */
public record PairDebugState(
        boolean managed,
        HideReason reason,
        boolean hidden,
        int pendingHideTicks,
        int entityId
) {
    /**
     * State returned for a pair not currently managed by the engine.
     *
     * @param entityId current target entity id
     * @return unmanaged state
     */
    public static PairDebugState unmanaged(int entityId) {
        return new PairDebugState(false, HideReason.NONE, false, 0, entityId);
    }
}
