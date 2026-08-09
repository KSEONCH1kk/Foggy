package dev.foggy.visibility;

/** State transition emitted by the asymmetric anti-flicker guard. */
public enum VisibilityTransition {
    /** No packet transition. */
    NONE,
    /** Send a destroy transition. */
    HIDE,
    /** Send a full spawn transition. */
    SHOW
}
