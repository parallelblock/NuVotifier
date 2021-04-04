package com.vexsoftware.votifier.sponge8.config;

import com.vexsoftware.votifier.util.TokenUtil;
import org.spongepowered.api.Sponge;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;
import org.spongepowered.configurate.objectmapping.meta.Setting;

import java.util.Collections;
import java.util.Map;

@ConfigSerializable
public class SpongeConfig {

    @Setting
    @Comment("The IP to listen to. Use 0.0.0.0 if you wish to listen to all interfaces on your server. (All IP addresses)\n" +
            "This defaults to the IP you have configured your server to listen on, or 0.0.0.0 if you have not configured this.")
    public String host = Sponge.server().boundAddress().map(a -> a.getAddress().getHostAddress()).orElse("0.0.0.0");

    @Setting
    @Comment("Port to listen for new votes on")
    public int port = 8192;

    @Setting
    @Comment("Whether or not to print debug messages. In a production system, this should be set to false.\n" +
            "This is useful when initially setting up NuVotifier to ensure votes are being delivered.")
    public boolean debug = true;

    @Setting("disable-v1-protocol")
    @Comment("Setting this option to true will disable handling of Protocol v1 packets. While the old protocol is not secure, this\n" +
            "option is currently not recommended as most voting sites only support the old protocol at present. However, if you are\n" +
            "using NuVotifier's proxy forwarding mechanism, enabling this option will increase your server's security.")
    public boolean disableV1Protocol = false;

    @Setting
    @Comment("All tokens, labeled by the serviceName of each server list.\n" +
            "Default token for all server lists, if another isn't supplied.")
    public Map<String, String> tokens = Collections.singletonMap("default", TokenUtil.newToken());

    @Setting
    @Comment("Configuration section for all vote forwarding to NuVotifier")
    public Forwarding forwarding = new Forwarding();

    @ConfigSerializable
    public static class Forwarding {

        @Setting
        @Comment("Sets whether to set up a remote method for fowarding. Supported methods:\n" +
                "- none - Does not set up a forwarding method.\n" +
                "- pluginMessaging - Sets up plugin messaging")
        public String method = "none";

        @Setting
        public PluginMessaging pluginMessaging = new PluginMessaging();

        @ConfigSerializable
        public static class PluginMessaging {

            @Setting
            public String channel = "nuvotifier:votes";
        }
    }
}
