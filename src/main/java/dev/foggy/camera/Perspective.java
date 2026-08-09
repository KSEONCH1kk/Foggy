package dev.foggy.camera;

/** Client camera perspective reported by the optional companion mod. */
public enum Perspective {
    /** Normal eye camera. */
    FIRST_PERSON,
    /** Detached camera behind the player, looking in the player's direction. */
    THIRD_PERSON_BACK,
    /** Detached camera in front of the player, looking back at the player. */
    THIRD_PERSON_FRONT;

    static Perspective fromWire(int value) {
        if (value < 0 || value >= values().length) {
            throw new IllegalArgumentException("Unknown perspective id " + value);
        }
        return values()[value];
    }
}
