package com.vexsoftware.votifier.velocity;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.messages.PluginChannelId;
import com.vexsoftware.votifier.support.forwarding.AbstractPluginMessagingForwardingSource;
import com.vexsoftware.votifier.support.forwarding.ServerFilter;
import com.vexsoftware.votifier.support.forwarding.cache.VoteCache;

public class PluginMessagingForwardingSource extends AbstractPluginMessagingForwardingSource {

    private final VotifierPlugin plugin;
    private final PluginChannelId velocityChannelId;

    public PluginMessagingForwardingSource(String channel, ServerFilter serverFilter, VotifierPlugin plugin, VoteCache cache, int dumpRate) {
        super(channel, serverFilter, plugin, cache, dumpRate);
        this.plugin = plugin;
        this.velocityChannelId = VelocityUtil.getId(channel);
        plugin.getServer().channelRegistrar().register(velocityChannelId);
        plugin.getServer().eventManager().register(plugin, this);
    }

    @Subscribe
    public void onServerConnected(final ServerConnectedEvent e) { //Attempt to resend any votes that were previously cached.
        onServerConnect(new VelocityBackendServer(plugin.getServer(), e.target()));
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent e) {
        if (e.channel().equals(velocityChannelId)) {
            e.setHandled(true);
        }
    }
}
