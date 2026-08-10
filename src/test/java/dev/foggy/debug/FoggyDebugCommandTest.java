package dev.foggy.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.retrooper.packetevents.manager.server.ServerVersion;
import org.junit.jupiter.api.Test;

class FoggyDebugCommandTest {
    @Test
    void protocol47UsesLegacyChatGameInfoInsteadOfUnsupportedActionBarPacket() {
        assertEquals(FoggyDebugCommand.ActionBarTransport.LEGACY_CHAT,
                FoggyDebugCommand.actionBarTransport(ServerVersion.V_1_8_8));
    }

    @Test
    void protocol116UsesChatGameInfoWithSenderUuid() {
        assertEquals(FoggyDebugCommand.ActionBarTransport.CHAT_1_16,
                FoggyDebugCommand.actionBarTransport(ServerVersion.V_1_16_5));
    }

    @Test
    void protocol117AndNewerUseDedicatedActionBarPacket() {
        assertEquals(FoggyDebugCommand.ActionBarTransport.DEDICATED,
                FoggyDebugCommand.actionBarTransport(ServerVersion.V_1_17));
    }
}
