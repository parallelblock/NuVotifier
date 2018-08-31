package com.vexsoftware.votifier.velocity;

import com.google.common.io.ByteStreams;
import com.google.inject.Inject;
import com.moandjiezana.toml.Toml;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.vexsoftware.votifier.VoteHandler;
import com.vexsoftware.votifier.model.Vote;
import com.vexsoftware.votifier.net.VotifierSession;
import com.vexsoftware.votifier.net.protocol.VoteInboundHandler;
import com.vexsoftware.votifier.net.protocol.VotifierGreetingHandler;
import com.vexsoftware.votifier.net.protocol.VotifierProtocolDifferentiator;
import com.vexsoftware.votifier.net.protocol.v1crypto.RSAIO;
import com.vexsoftware.votifier.net.protocol.v1crypto.RSAKeygen;
import com.vexsoftware.votifier.platform.BackendServer;
import com.vexsoftware.votifier.platform.ProxyVotifierPlugin;
import com.vexsoftware.votifier.platform.scheduler.VotifierScheduler;
import com.vexsoftware.votifier.util.KeyCreator;
import com.vexsoftware.votifier.util.TokenUtil;
import com.vexsoftware.votifier.velocity.event.VotifierEvent;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.Key;
import java.security.KeyPair;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Plugin(id = "nuvotifier", name = "NuVotifier", version = "2.3.7", authors = "ParallelBlock LLC",
        description = "Safe, smart, and secure Votifier server plugin")
public class VotifierPlugin implements VoteHandler, ProxyVotifierPlugin {

    @Inject
    public Logger logger;

    @Inject
    @DataDirectory
    public Path configDir;

    @Inject
    public ProxyServer server;

    @Subscribe
    public void onServerStart(ProxyInitializeEvent event) {
        // Load configuration.
        Toml config;
        try {
            config = loadConfig();
        } catch (IOException e) {
            throw new RuntimeException("Unable to load configuration.", e);
        }

        /*
         * Create RSA directory and keys if it does not exist; otherwise, read
         * keys.
         */
        File rsaDirectory = new File(configDir.toFile(), "rsa");
        try {
            if (!rsaDirectory.exists()) {
                rsaDirectory.mkdir();
                keyPair = RSAKeygen.generate(2048);
                RSAIO.save(rsaDirectory, keyPair);
            } else {
                keyPair = RSAIO.load(rsaDirectory);
            }
        } catch (Exception ex) {
            logger.error("Error creating or reading RSA tokens", ex);
            gracefulExit();
            return;
        }

        debug = config.getBoolean("debug");

        // Load Votifier tokens.
        config.getTable("tokens").toMap().forEach((service, key) -> {
            if (key instanceof String) {
                tokens.put(service, KeyCreator.createKeyFrom((String) key));
                logger.info("Loaded token for website: " + service);
            }
        });

        // Initialize the receiver.
        final String host = config.getString("host");
        final int port = Math.toIntExact(config.getLong("port"));
        if (debug)
            logger.info("DEBUG mode enabled!");

        final boolean disablev1 = config.getBoolean("disable-v1-protocol", false);
        if (disablev1) {
            logger.info("------------------------------------------------------------------------------");
            logger.info("Votifier protocol v1 parsing has been disabled. Most voting websites do not");
            logger.info("currently support the modern Votifier protocol in NuVotifier.");
            logger.info("------------------------------------------------------------------------------");
        }

        serverGroup = new NioEventLoopGroup(1);

        new ServerBootstrap()
                .channel(NioServerSocketChannel.class)
                .group(serverGroup)
                .childHandler(new ChannelInitializer<NioSocketChannel>() {
                    @Override
                    protected void initChannel(NioSocketChannel channel) {
                        channel.attr(VotifierSession.KEY).set(new VotifierSession());
                        channel.attr(VotifierPlugin.KEY).set(VotifierPlugin.this);
                        channel.pipeline().addLast("greetingHandler", new VotifierGreetingHandler());
                        channel.pipeline().addLast("protocolDifferentiator", new VotifierProtocolDifferentiator(false, !disablev1));
                        channel.pipeline().addLast("voteHandler", new VoteInboundHandler(VotifierPlugin.this));
                    }
                })
                .bind(host, port)
                .addListener((ChannelFutureListener) future -> {
                    if (future.isSuccess()) {
                        serverChannel = future.channel();
                        logger.info("Votifier enabled on socket " + serverChannel.localAddress() + ".");
                    } else {
                        SocketAddress socketAddress = future.channel().localAddress();
                        if (socketAddress == null) {
                            socketAddress = new InetSocketAddress(host, port);
                        }
                        logger.error("Votifier was not able to bind to " + socketAddress, future.cause());
                    }
                });
    }

    @Subscribe
    public void onServerStop(ProxyShutdownEvent event) {
        if (serverGroup != null) {
            try {
                if (serverChannel != null)
                    serverChannel.close().sync();
                serverGroup.shutdownGracefully().sync();
            } catch (Exception e) {
                logger.error("Unable to shut down listening port gracefully.", e);
            }
        }

        logger.info("Votifier disabled.");
    }

    private Toml loadConfig() throws IOException {
        Path configPath = configDir.resolve("config.toml");
        try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            return new Toml().read(reader);
        } catch (NoSuchFileException e) {
            // This is ok. Just copy the default and load that.
            // First time run - do some initialization.
            getLogger().info("Configuring Votifier for the first time...");

            // Initialize the configuration file.
            String cfgStr = new String(ByteStreams.toByteArray(VotifierPlugin.class.getResourceAsStream("/config.toml")), StandardCharsets.UTF_8);
            String token = TokenUtil.newToken();
            cfgStr = cfgStr.replace("%default_token%", token);

            /*
             * Remind hosted server admins to be sure they have the right
             * port number.
             */
            getLogger().info("------------------------------------------------------------------------------");
            getLogger().info("Assigning NuVotifier to listen on port 8192. If you are hosting BungeeCord on a");
            getLogger().info("shared server please check with your hosting provider to verify that this port");
            getLogger().info("is available for your use. Chances are that your hosting provider will assign");
            getLogger().info("a different port, which you need to specify in config.yml");
            getLogger().info("------------------------------------------------------------------------------");
            getLogger().info("Assigning NuVotifier to listen to interface 0.0.0.0. This is usually alright,");
            getLogger().info("however, if you want NuVotifier to only listen to one interface for security ");
            getLogger().info("reasons (or you use a shared host), you may change this in the config.yml.");
            getLogger().info("------------------------------------------------------------------------------");
            getLogger().info("Your default Votifier token is " + token + ".");
            getLogger().info("You will need to provide this token when you submit your server to a voting");
            getLogger().info("list.");
            getLogger().info("------------------------------------------------------------------------------");

            Files.copy(new ByteArrayInputStream(cfgStr.getBytes(StandardCharsets.UTF_8)), configPath);
            return new Toml().read(cfgStr);
        }
    }

    public Logger getLogger() {
        return logger;
    }

    /**
     * The current Votifier version.
     */
    private String version;

    /**
     * The server channel.
     */
    private Channel serverChannel;

    /**
     * The event group handling the channel.
     */
    private NioEventLoopGroup serverGroup;

    /**
     * The RSA key pair.
     */
    private KeyPair keyPair;

    /**
     * Debug mode flag
     */
    private boolean debug;

    /**
     * Keys used for websites.
     */
    private Map<String, Key> tokens = new HashMap<>();

    private void gracefulExit() {
        logger.error("Votifier did not initialize properly!");
    }

    /**
     * Gets the version.
     *
     * @return The version
     */
    public String getVersion() {
        return version;
    }

    @Override
    public Logger getPluginLogger() {
        return logger;
    }

    @Override
    public VotifierScheduler getScheduler() {
        return new VelocityScheduler(server, this);
    }

    public boolean isDebug() {
        return debug;
    }

    @Override
    public Map<String, Key> getTokens() {
        return tokens;
    }

    @Override
    public KeyPair getProtocolV1Key() {
        return keyPair;
    }

    @Override
    public void onVoteReceived(Channel channel, final Vote vote, VotifierSession.ProtocolVersion protocolVersion) {
        if (debug) {
            if (protocolVersion == VotifierSession.ProtocolVersion.ONE) {
                logger.info("Got a protocol v1 vote record from " + channel.remoteAddress() + " -> " + vote);
            } else {
                logger.info("Got a protocol v2 vote record from " + channel.remoteAddress() + " -> " + vote);
            }
        }

        server.getEventManager().fireAndForget(new VotifierEvent(vote));
    }

    @Override
    public void onError(Channel channel, boolean alreadyHandledVote, Throwable throwable) {
        if (debug) {
            if (alreadyHandledVote) {
                logger.error("Vote processed, however an exception " +
                        "occurred with a vote from " + channel.remoteAddress(), throwable);
            } else {
                logger.error("Unable to process vote from " + channel.remoteAddress(), throwable);
            }
        } else if (!alreadyHandledVote) {
            logger.error("Unable to process vote from " + channel.remoteAddress());
        }
    }

    @Override
    public List<BackendServer> getAllBackendServers() {
        return server.getAllServers().stream().map(s -> new VelocityBackendServer(server, s)).collect(Collectors.toList());
    }

    @Override
    public Optional<BackendServer> getServer(String name) {
        return server.getServerInfo(name).map(s -> new VelocityBackendServer(server, s));
    }
}