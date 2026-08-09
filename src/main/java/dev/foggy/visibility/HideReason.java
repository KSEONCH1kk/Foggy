package dev.foggy.visibility;

/** Why a player pair currently resolves to hidden. */
public enum HideReason {
    /** Target is visible. */
    NONE(false),
    /** Hard state-based invisibility (vanish/spectator) requires an immediate hide. */
    INVISIBLE(true),
    /** Every in-frustum target ray is block-occluded. */
    OCCLUDED(false),
    /** Target lies outside every plausible camera frustum. */
    OUTSIDE_FOV(false);

    private final boolean immediate;

    HideReason(boolean immediate) {
        this.immediate = immediate;
    }

    /**
     * Hard vanish/spectator state is security driven and bypasses optical debounce.
     *
     * @return whether this reason bypasses confirmation ticks
     */
    public boolean immediate() {
        return immediate;
    }
}
