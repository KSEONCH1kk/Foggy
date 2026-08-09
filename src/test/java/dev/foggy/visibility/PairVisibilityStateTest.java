package dev.foggy.visibility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PairVisibilityStateTest {
    @Test
    void opticalHideIsDebouncedButShowIsImmediate() {
        PairVisibilityState state = new PairVisibilityState(7);
        assertEquals(VisibilityTransition.NONE, state.apply(HideReason.OCCLUDED, 2));
        assertEquals(VisibilityTransition.HIDE, state.apply(HideReason.OCCLUDED, 2));
        assertTrue(state.hidden());
        assertEquals(VisibilityTransition.SHOW, state.apply(HideReason.NONE, 2));
        assertFalse(state.hidden());
    }

    @Test
    void hardStateHideBypassesDebounce() {
        PairVisibilityState state = new PairVisibilityState(9);
        assertEquals(VisibilityTransition.HIDE, state.apply(HideReason.INVISIBLE, 10));
    }

    @Test
    void visibleSampleClearsPendingOpticalHide() {
        PairVisibilityState state = new PairVisibilityState(11);
        assertEquals(VisibilityTransition.NONE, state.apply(HideReason.OCCLUDED, 2));
        assertEquals(VisibilityTransition.NONE, state.apply(HideReason.NONE, 2));
        assertEquals(VisibilityTransition.NONE, state.apply(HideReason.OCCLUDED, 2));
    }

    @Test
    void transferredHiddenStateShowsImmediatelyAfterReload() {
        PairVisibilityState state = new PairVisibilityState(13, true);
        assertTrue(state.hidden());
        assertEquals(VisibilityTransition.SHOW, state.apply(HideReason.NONE, 20));
        assertFalse(state.hidden());
    }
}
