package dev.foggy.invisibility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class InvisibilityDispositionTest {
    @Test
    void vanillaInvisibilityPreservesEntityForEquipmentAndHits() {
        InvisibilityDisposition disposition = InvisibilityDisposition.resolve(true, false, true);
        assertEquals(InvisibilityDisposition.VANILLA_ENTITY, disposition);
        assertFalse(disposition.removesEntity());
    }

    @Test
    void vanishAndSpectatorSignalsOverrideVanillaPreservation() {
        assertEquals(InvisibilityDisposition.PACKET_HIDDEN,
                InvisibilityDisposition.resolve(true, true, true));
        InvisibilityDisposition hardHidden = InvisibilityDisposition.resolve(false, true, true);
        assertEquals(InvisibilityDisposition.PACKET_HIDDEN, hardHidden);
        assertTrue(hardHidden.removesEntity());
    }

    @Test
    void legacyFullHideCanBeExplicitlyRestored() {
        assertEquals(InvisibilityDisposition.PACKET_HIDDEN,
                InvisibilityDisposition.resolve(true, false, false));
    }
}
