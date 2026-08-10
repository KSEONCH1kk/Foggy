package dev.foggy.packet;

/** Immutable per-viewer PacketEvents state shown by the debug command. */
public final class PacketDebugState {
    private final boolean hiddenId;
    private final boolean observedTracked;
    private final boolean clientKnown;
    private final boolean paperTracked;

    public PacketDebugState(boolean hiddenId, boolean observedTracked,
                            boolean clientKnown, boolean paperTracked) {
        this.hiddenId = hiddenId;
        this.observedTracked = observedTracked;
        this.clientKnown = clientKnown;
        this.paperTracked = paperTracked;
    }

    public boolean hiddenId() { return hiddenId; }
    public boolean observedTracked() { return observedTracked; }
    public boolean clientKnown() { return clientKnown; }
    public boolean paperTracked() { return paperTracked; }
}
