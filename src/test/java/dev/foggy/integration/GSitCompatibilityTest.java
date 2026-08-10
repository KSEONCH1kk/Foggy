package dev.foggy.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class GSitCompatibilityTest {
    @Test
    void discoversVersionModuleNpcAndPrivateViewerMethods() throws ReflectiveOperationException {
        FakePose pose = new FakePose();

        GSitCompatibility.PoseAccess access = GSitCompatibility.PoseAccess.discover(pose);
        access.remove(pose, null);
        access.add(pose, null);

        assertEquals(417, access.entityId());
        assertEquals(1, pose.removed);
        assertEquals(1, pose.added);
    }

    private static class PoseBase {
        @SuppressWarnings("unused")
        private final FakeNpc playerNpc = new FakeNpc();
    }

    private static final class FakePose extends PoseBase {
        private int added;
        private int removed;

        @SuppressWarnings("unused")
        private void addViewerPlayer(Player viewer) {
            added++;
        }

        @SuppressWarnings("unused")
        private void removeViewerPlayer(Player viewer) {
            removed++;
        }
    }

    private static final class FakeNpc {
        @SuppressWarnings("unused")
        public int getId() {
            return 417;
        }
    }
}
