package dev.foggy.packet;

/**
 * Per-viewer PacketEvents state shown by the debug command.
 *
 * @param hiddenId target id is suppressed by Foggy's packet listener
 * @param observedTracked target id was observed in an ordinary server spawn
 * @param clientKnown Foggy believes the client currently owns the entity
 * @param paperTracked Paper's entity tracker currently includes the viewer
 */
public record PacketDebugState(
        boolean hiddenId,
        boolean observedTracked,
        boolean clientKnown,
        boolean paperTracked
) {
}
