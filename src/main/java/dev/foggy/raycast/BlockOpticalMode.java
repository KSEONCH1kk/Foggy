package dev.foggy.raycast;

/** Cached optical classification of a Bukkit material. */
public enum BlockOpticalMode {
    /** Ordinary material or an explicit opaque override. */
    OCCLUDING(true),

    /** Configured transparent material through which visibility rays continue. */
    TRANSPARENT_PASS(false),

    /** Vanilla/client-model cutout conservatively allowed to pass. */
    CUTOUT_PASS(false),

    /** Configured transparent material forced to terminate rays by global mode. */
    TRANSPARENT_OCCLUDING(true),

    /** Vanilla/client-model cutout forced to terminate rays by global mode. */
    CUTOUT_OCCLUDING(true);

    private final boolean blocksRay;

    BlockOpticalMode(boolean blocksRay) {
        this.blocksRay = blocksRay;
    }

    /**
     * Reports whether this material terminates an outline-shape visibility ray.
     *
     * @return true when the ray stops
     */
    public boolean blocksRay() {
        return blocksRay;
    }
}
