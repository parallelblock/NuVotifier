package com.vexsoftware.votifier.velocity;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.connection.Player;
import com.velocitypowered.api.proxy.connection.ServerConnection;
import com.velocitypowered.api.proxy.messages.PluginChannelId;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.vexsoftware.votifier.model.Vote;
import com.vexsoftware.votifier.platform.BackendServer;
import com.vexsoftware.votifier.support.forwarding.AbstractPluginMessagingForwardingSource;
import com.vexsoftware.votifier.support.forwarding.ServerFilter;
import com.vexsoftware.votifier.support.forwarding.cache.VoteCache;

import java.util.Optional;

public final class OnlineForwardPluginMessagingForwardingSource extends AbstractPluginMessagingForwardingSource {

    private final String fallbackServer;
    private final VotifierPlugin plugin;
    private final PluginChannelId velocityChannelId;

    public OnlineForwardPluginMessagingForwardingSource(String channel, ServerFilter serverFilter, VotifierPlugin plugin, VoteCache cache, String fallbackServer, int dumpRate) {
        super(channel, serverFilter, plugin, cache, dumpRate);
        this.fallbackServer = fallbackServer;
        this.plugin = plugin;
        this.velocityChannelId = VelocityUtil.getId(channel);
        plugin.getServer().channelRegistrar().register(velocityChannelId);
        plugin.getServer().eventManager().register(plugin, this);
    }

    @Override
    public void forward(Vote v) {
        Player player = plugin.getServer().player(v.getUsername());
        if (player != null) {
            ServerConnection connection = player.connectedServer();
            if (connection != null && serverFilter.isAllowed(connection.serverInfo().name())) {
                if (forwardSpecific(new VelocityBackendServer(plugin.getServer(), connection.target()), v)) {
                    if (plugin.isDebug()) {
                        plugin.getPluginLogger().info("Successfully forwarded vote " + v + " to server " + connection.serverInfo().name());
                    }
                    return;
                }
            }
        }

        RegisteredServer fallback = fallbackServer == null ? null : plugin.getServer().server(fallbackServer);
        // nowhere to fall back to, yet still not online. lets save this vote yet!
        if (fallback == null)
            attemptToAddToPlayerCache(v, v.getUsername());
        else if (!forwardSpecific(new VelocityBackendServer(plugin.getServer(), fallback), v))
            attemptToAddToCache(v, fallbackServer);
    }

    @Subscribe
    public void onServerConnected(final ServerConnectedEvent e) { //Attempt to resend any votes that were previously cached.
        BackendServer server = new VelocityBackendServer(plugin.getServer(), e.target());
        onServerConnect(server);
        handlePlayerSwitch(server, e.player().username());
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent e) {
        if (e.channel().equals(velocityChannelId)) {
            e.setHandled(true);
        }
    }
}
