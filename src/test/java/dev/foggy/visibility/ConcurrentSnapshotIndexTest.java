package dev.foggy.visibility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.foggy.invisibility.TargetInvisibilityState;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

class ConcurrentSnapshotIndexTest {
    private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_WORLD = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void filtersByWorldRadiusAndViewerIdentity() {
        ConcurrentSnapshotIndex index = new ConcurrentSnapshotIndex(16);
        PlayerVisibilitySnapshot viewer = snapshot(1, WORLD, 0.0, 64.0, 0.0, 1L);
        PlayerVisibilitySnapshot near = snapshot(2, WORLD, 15.9, 64.0, -0.1, 2L);
        PlayerVisibilitySnapshot far = snapshot(3, WORLD, 65.0, 64.0, 0.0, 3L);
        PlayerVisibilitySnapshot anotherWorld = snapshot(4, OTHER_WORLD, 1.0, 64.0, 1.0, 4L);
        index.publish(viewer);
        index.publish(near);
        index.publish(far);
        index.publish(anotherWorld);

        assertEquals(List.of(near.playerId()), index.nearby(viewer, 32.0).stream()
                .map(PlayerVisibilitySnapshot::playerId).toList());
    }

    @Test
    void movingAcrossNegativeCellBoundaryRemovesOldMembership() {
        ConcurrentSnapshotIndex index = new ConcurrentSnapshotIndex(16);
        PlayerVisibilitySnapshot oldPosition = snapshot(5, WORLD, -0.01, 64.0, -16.01, 10L);
        PlayerVisibilitySnapshot newPosition = snapshot(5, WORLD, 32.01, 64.0, 48.01, 11L);
        PlayerVisibilitySnapshot oldViewer = snapshot(6, WORLD, -1.0, 64.0, -17.0, 12L);
        PlayerVisibilitySnapshot newViewer = snapshot(7, WORLD, 33.0, 64.0, 49.0, 13L);

        index.publish(oldPosition);
        assertEquals(1, index.nearby(oldViewer, 4.0).size());
        index.publish(newPosition);

        assertEquals(0, index.nearby(oldViewer, 4.0).size());
        assertEquals(List.of(newPosition.playerId()), index.nearby(newViewer, 4.0).stream()
                .map(PlayerVisibilitySnapshot::playerId).toList());
    }

    @Test
    void rejectsNonPositiveCellSize() {
        assertThrows(IllegalArgumentException.class, () -> new ConcurrentSnapshotIndex(0));
    }

    private static PlayerVisibilitySnapshot snapshot(int id, UUID worldId,
                                                       double x, double y, double z, long capturedNanos) {
        Vector position = new Vector(x, y, z);
        BoundingBox box = new BoundingBox(x - 0.3, y, z - 0.3, x + 0.3, y + 1.8, z + 0.3);
        return new PlayerVisibilitySnapshot(
                null, new UUID(0L, id), id, "player-" + id, null, worldId,
                position, position, box, List.of(position), false,
                new TargetInvisibilityState(false, false, false, List.of()), Set.of(), capturedNanos);
    }
}
