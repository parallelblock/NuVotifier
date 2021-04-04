package com.vexsoftware.votifier.sponge8.forwarding;

import com.vexsoftware.votifier.sponge8.NuVotifier;
import com.vexsoftware.votifier.support.forwarding.AbstractPluginMessagingForwardingSink;
import com.vexsoftware.votifier.support.forwarding.ForwardedVoteListener;
import org.spongepowered.api.ResourceKey;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.network.EngineConnectionSide;
import org.spongepowered.api.network.ServerSideConnection;
import org.spongepowered.api.network.channel.ChannelBuf;
import org.spongepowered.api.network.channel.raw.RawDataChannel;
import org.spongepowered.api.network.channel.raw.play.RawPlayDataHandler;

import static com.vexsoftware.votifier.sponge8.NuVotifier.PLUGIN_ID;

public class SpongePluginMessagingForwardingSink extends AbstractPluginMessagingForwardingSink implements RawPlayDataHandler<ServerSideConnection> {

    private final NuVotifier plugin;
    private final RawDataChannel channelBinding;

    public SpongePluginMessagingForwardingSink(NuVotifier plugin, String channel, ForwardedVoteListener listener) {
        super(listener);
        this.plugin = plugin;
        this.channelBinding = Sponge.game().channelRegistry().ofType(ResourceKey.of(PLUGIN_ID, channel), RawDataChannel.class);
        this.channelBinding.play().addHandler(EngineConnectionSide.SERVER, this);
    }

    @Override
    public void handlePayload(ChannelBuf data, ServerSideConnection connection) {
        byte[] msgDirBuf = data.readBytes(data.available());
        try {
            this.handlePluginMessage(msgDirBuf);
        } catch (Exception e) {
            plugin.getLogger().error("There was an unknown error when processing a forwarded vote.", e);
        }
    }

    @Override
    public void halt() {
        this.channelBinding.play().removeHandler(this);
    }
}
