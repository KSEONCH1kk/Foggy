package dev.foggy.packet;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.HashSet;
import org.junit.jupiter.api.Test;

class RelatedEntityRegistryTest {
    @Test
    void movesRendererBetweenPrimaryEntitiesWithoutLeavingStaleAlias() {
        RelatedEntityRegistry registry = new RelatedEntityRegistry();
        registry.register(10, 99);
        registry.register(20, 99);

        assertTrue(registry.related(10).isEmpty());
        assertEquals(Collections.singleton(99), registry.related(20));
        assertEquals(20, registry.primaryOrSelf(99));

        assertEquals(Collections.singleton(99), registry.removePrimary(20));
        assertEquals(99, registry.primaryOrSelf(99));
    }

    @Test
    void filtersOnlyHiddenPassengersAndPreservesArrayWhenUnchanged() {
        int[] passengers = {31, 32, 33};

        assertSame(passengers, PacketVisibilityController.filterPassengers(
                passengers, Collections.<Integer>emptySet()));
        assertArrayEquals(new int[] {31, 33}, PacketVisibilityController.filterPassengers(
                passengers, new HashSet<Integer>(Collections.singletonList(32))));
    }
}
