package dev.foggy.invisibility;

/**
 * Packet treatment for state-based invisibility.
 *
 * <p>Vanilla invisibility must retain the client entity: the invisible metadata flag hides the
 * player model while Minecraft continues to render equipment and accept interaction with the
 * entity. Vanish/spectator state is a stronger privacy signal and removes the entity entirely.</p>
 */
public enum InvisibilityDisposition {
    /** No state-based invisibility applies. */
    NONE,
    /** Keep the entity and let vanilla metadata render invisibility and equipment. */
    VANILLA_ENTITY,
    /** Remove the entity and suppress its outgoing packets for this viewer. */
    PACKET_HIDDEN;

    /**
     * Returns whether Foggy must send {@code DestroyEntities} for this disposition.
     *
     * @return true only for complete packet hiding
     */
    public boolean removesEntity() {
        return this == PACKET_HIDDEN;
    }

    /**
     * Resolves signal priority without Bukkit dependencies, allowing regression tests to cover
     * the equipment/hit-registration behavior.
     *
     * @param vanillaInvisible enabled potion effect or entity invisible flag
     * @param hardHidden enabled spectator, Bukkit canSee or vanish signal
     * @param preserveVanillaEntity whether vanilla invisibility keeps the client entity
     * @return effective packet treatment
     */
    public static InvisibilityDisposition resolve(boolean vanillaInvisible, boolean hardHidden,
                                                    boolean preserveVanillaEntity) {
        if (hardHidden) {
            return PACKET_HIDDEN;
        }
        if (!vanillaInvisible) {
            return NONE;
        }
        return preserveVanillaEntity ? VANILLA_ENTITY : PACKET_HIDDEN;
    }
}
