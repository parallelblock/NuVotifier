package com.vexsoftware.votifier.velocity;

import com.velocitypowered.api.proxy.messages.PluginChannelId;
import net.kyori.adventure.key.Key;

public class VelocityUtil {
    private static final Key MODERN_CHANNEL = Key.key("nuvotifier", "votes");

    private VelocityUtil() {
        throw new AssertionError();
    }

    public static PluginChannelId getId(String channel) {
        if (channel.contains(":")) {
            return PluginChannelId.wrap(Key.key(channel));
        }
        return PluginChannelId.withLegacy(channel, MODERN_CHANNEL);
    }
}
