package dev.foggy.raycast;

/** Result of FOV and block-occlusion evaluation. */
public enum OpticalResult {
    /** At least one target sample is unobstructed from a plausible camera. */
    VISIBLE,
    /** No target sample lies in any plausible view frustum. */
    OUTSIDE_FOV,
    /** Target samples are in-frustum but every ray is blocked. */
    OCCLUDED,
    /** The current Folia region does not own the complete ray corridor; target stays visible. */
    REGION_UNOWNED
}
