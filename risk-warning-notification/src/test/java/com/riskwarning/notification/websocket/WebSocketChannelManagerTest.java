package com.riskwarning.notification.websocket;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.DefaultChannelId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WebSocketChannelManagerTest {
    private final WebSocketChannelManager manager = new WebSocketChannelManager();
    @AfterEach void cleanup() { manager.closeAll(); }

    @Test void oldConnectionRemovalMustNotUnbindReplacement() {
        EmbeddedChannel oldChannel = new EmbeddedChannel(DefaultChannelId.newInstance());
        EmbeddedChannel newChannel = new EmbeddedChannel(DefaultChannelId.newInstance());
        try {
            manager.bindUser(1L, oldChannel);
            manager.bindUser(1L, newChannel);
            manager.removeChannel(oldChannel);
            assertSame(newChannel, manager.getChannel(1L));
            assertTrue(manager.isUserOnline(1L));
        } finally { oldChannel.finishAndReleaseAll(); newChannel.finishAndReleaseAll(); }
    }

    @Test void repeatedBindMustNotCloseSameConnection() {
        EmbeddedChannel channel = new EmbeddedChannel(DefaultChannelId.newInstance());
        try {
            manager.bindUser(1L, channel);
            manager.bindUser(1L, channel);
            assertTrue(channel.isActive());
        } finally { channel.finishAndReleaseAll(); }
    }
}
