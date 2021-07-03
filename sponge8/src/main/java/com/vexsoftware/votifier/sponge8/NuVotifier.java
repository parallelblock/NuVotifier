package com.vexsoftware.votifier.sponge8;

import com.google.inject.Inject;
import com.vexsoftware.votifier.VoteHandler;
import com.vexsoftware.votifier.model.Vote;
import com.vexsoftware.votifier.net.VotifierServerBootstrap;
import com.vexsoftware.votifier.net.VotifierSession;
import com.vexsoftware.votifier.net.protocol.v1crypto.RSAIO;
import com.vexsoftware.votifier.net.protocol.v1crypto.RSAKeygen;
import com.vexsoftware.votifier.platform.LoggingAdapter;
import com.vexsoftware.votifier.platform.VotifierPlugin;
import com.vexsoftware.votifier.platform.scheduler.VotifierScheduler;
import com.vexsoftware.votifier.sponge8.config.ConfigLoader;
import com.vexsoftware.votifier.sponge8.event.VotifierEvent;
import com.vexsoftware.votifier.sponge8.forwarding.SpongePluginMessagingForwardingSink;
import com.vexsoftware.votifier.support.forwarding.ForwardedVoteListener;
import com.vexsoftware.votifier.support.forwarding.ForwardingVoteSink;
import com.vexsoftware.votifier.util.KeyCreator;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.apache.logging.log4j.Logger;
import org.spongepowered.api.Server;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.Command;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.parameter.CommandContext;
import org.spongepowered.api.command.parameter.Parameter;
import org.spongepowered.api.config.ConfigDir;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.lifecycle.RefreshGameEvent;
import org.spongepowered.api.event.lifecycle.RegisterCommandEvent;
import org.spongepowered.api.event.lifecycle.StartedEngineEvent;
import org.spongepowered.api.event.lifecycle.StoppingEngineEvent;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.plugin.PluginContainer;
import org.spongepowered.plugin.jvm.Plugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Key;
import java.security.KeyPair;
import java.util.HashMap;
import java.util.Map;

import static com.vexsoftware.votifier.sponge8.NuVotifier.PLUGIN_ID;

@Plugin(PLUGIN_ID)
public class NuVotifier implements VoteHandler, VotifierPlugin, ForwardedVoteListener {

    public static final String PLUGIN_ID = "nuvotifier";

    @Inject
    public PluginContainer pluginContainer;

    @Inject
    public Logger logger;

    private Log4J2Logger loggerAdapter;

    @Inject
    @ConfigDir(sharedRoot = false)
    public Path configDir;

    private VotifierScheduler scheduler;

    private boolean loadAndBind() {
        // Load configuration.
        ConfigLoader.loadConfig(this);

        /*
         * Create RSA directory and keys if it does not exist; otherwise, read
         * keys.
         */
        Path rsaDirectory = configDir.resolve("rsa");
        try {
            if (!Files.exists(rsaDirectory)) {
                try {
                    Files.createDirectories(rsaDirectory);
                } catch (IOException e) {
                    throw new RuntimeException("Unable to create the RSA key folder " + rsaDirectory, e);
                }
                keyPair = RSAKeygen.generate(2048);
                RSAIO.save(rsaDirectory.toFile(), keyPair);
            } else {
                keyPair = RSAIO.load(rsaDirectory.toFile());
            }
        } catch (Exception ex) {
            logger.error("Error creating or reading RSA tokens", ex);
            return false;
        }

        debug = ConfigLoader.getSpongeConfig().debug;

        // Load Votifier tokens.
        ConfigLoader.getSpongeConfig().tokens.forEach((s, s2) -> {
            tokens.put(s, KeyCreator.createKeyFrom(s2));
            logger.info("Loaded token for website: " + s);
        });

        // Initialize the receiver.
        final String host = ConfigLoader.getSpongeConfig().host;
        final int port = ConfigLoader.getSpongeConfig().port;

        if (!debug)
            logger.info("QUIET mode enabled!");

        if (port >= 0) {
            final boolean disablev1 = ConfigLoader.getSpongeConfig().disableV1Protocol;
            if (disablev1) {
                logger.info("------------------------------------------------------------------------------");
                logger.info("Votifier protocol v1 parsing has been disabled. Most voting websites do not");
                logger.info("currently support the modern Votifier protocol in NuVotifier.");
                logger.info("------------------------------------------------------------------------------");
            }

            this.bootstrap = new VotifierServerBootstrap(host, port, this, disablev1);
            this.bootstrap.start(err -> {
            });
        } else {
            getLogger().info("------------------------------------------------------------------------------");
            getLogger().info("Your Votifier port is less than 0, so we assume you do NOT want to start the");
            getLogger().info("votifier port server! Votifier will not listen for votes over any port, and");
            getLogger().info("will only listen for pluginMessaging forwarded votes!");
            getLogger().info("------------------------------------------------------------------------------");
        }

        if (ConfigLoader.getSpongeConfig().forwarding != null) {
            String method = ConfigLoader.getSpongeConfig().forwarding.method.toLowerCase(); //Default to lower case for case-insensitive searches
            if ("none".equals(method)) {
                getLogger().info("Method none selected for vote forwarding: Votes will not be received from a forwarder.");
            } else if ("pluginmessaging".equals(method)) {
                String channel = ConfigLoader.getSpongeConfig().forwarding.pluginMessaging.channel;
                try {
                    forwardingMethod = new SpongePluginMessagingForwardingSink(this, channel, this);
                    getLogger().info("Receiving votes over PluginMessaging channel '" + channel + "'.");
                } catch (RuntimeException e) {
                    logger.error("NuVotifier could not set up PluginMessaging for vote forwarding!", e);
                }
            } else {
                logger.error("No vote forwarding method '" + method + "' known. Defaulting to noop implementation.");
            }
        }
        return true;
    }

    private void halt() {
        // Shut down the network handlers.
        if (bootstrap != null) {
            bootstrap.shutdown();
            bootstrap = null;
        }

        if (forwardingMethod != null) {
            forwardingMethod.halt();
            forwardingMethod = null;
        }
    }

    public boolean reload() {
        try {
            halt();
        } catch (Exception ex) {
            logger.error("On halt, an exception was thrown. This may be fine!", ex);
        }

        if (loadAndBind()) {
            logger.info("Reload was successful.");
            return true;
        } else {
            try {
                halt();
                logger.error("On reload, there was a problem with the configuration. Votifier currently does nothing!");
            } catch (Exception ex) {
                logger.error("On reload, there was a problem loading, and we could not re-halt the server. Votifier is in an unstable state!", ex);
            }
            return false;
        }
    }

    @Listener
    public void onServerStart(StartedEngineEvent<Server> event) {
        this.scheduler = new SpongeScheduler(pluginContainer, event.engine().scheduler(), event.game().asyncScheduler());
        this.loggerAdapter = new Log4J2Logger(logger);

        if (!loadAndBind()) {
            gracefulExit();
        }
    }

    @Listener
    public void onGameReload(RefreshGameEvent event) {
        this.reload();
    }

    @Listener
    public void onServerStop(StoppingEngineEvent<Server> event) {
        halt();
        logger.info("Votifier disabled.");
    }

    @Listener
    public void onRegisterCommands(final RegisterCommandEvent<Command.Parameterized> event) {
        final Command.Parameterized reloadCommand = Command.builder()
                .extendedDescription(Component.text("Reloads NuVotifier"))
                .permission("nuvotifier.reload")
                .executor(this::reloadCommand)
                .build();
        event.register(pluginContainer, reloadCommand, "nvreload");

        final Parameter.Value<String> username = Parameter.string().key("username").build();
        final Parameter.Value<String> serviceName = Parameter.string().optional().key("serviceName").build();
        final Parameter.Value<String> address = Parameter.string().optional().key("address").build();
        final Parameter.Value<String> timestamp = Parameter.string().optional().key("timestamp").build();

        final Command.Parameterized testCommand = Command.builder()
                .extendedDescription(Component.text("Sends a test vote to the server's listeners"))
                .addParameters(username, serviceName, address, timestamp)
                .permission("nuvotifier.testvote")
                .executor(context -> testCommand(context, username, serviceName, address, timestamp))
                .build();
        event.register(pluginContainer, testCommand, "testvote");
    }

    private CommandResult reloadCommand(CommandContext context) {
        context.sendMessage(Identity.nil(), Component.text("Reloading NuVotifier...", NamedTextColor.GRAY));
        if (reload())
            return CommandResult.success();
        else
            return CommandResult.empty();
    }

    private CommandResult testCommand(CommandContext context, Parameter.Value<String> username, Parameter.Value<String> serviceName, Parameter.Value<String> address, Parameter.Value<String> timestamp) {
        Vote v = new Vote(context.one(serviceName).orElse("TestVote"),
                context.requireOne(username),
                context.one(address).orElse("127.0.0.1"),
                context.one(timestamp).orElse(Long.toString(System.currentTimeMillis(), 10)));
        onVoteReceived(v, VotifierSession.ProtocolVersion.TEST, "localhost.test");
        context.sendMessage(Identity.nil(), Component.text("TestVote sent! " + v.getUsername() + ": " + v.getServiceName() + " " + v.getTimeStamp() + " " + v.getAddress(), NamedTextColor.GRAY));
        return CommandResult.success();
    }

    public Logger getLogger() {
        return logger;
    }

    /**
     * The server bootstrap.
     */
    private VotifierServerBootstrap bootstrap;

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

    private ForwardingVoteSink forwardingMethod;

    private void gracefulExit() {
        logger.error("Votifier did not initialize properly!");
    }

    @Override
    public LoggingAdapter getPluginLogger() {
        return loggerAdapter;
    }

    @Override
    public VotifierScheduler getScheduler() {
        return scheduler;
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

    public Path getConfigDir() {
        return configDir;
    }

    @Override
    public void onVoteReceived(final Vote vote, VotifierSession.ProtocolVersion protocolVersion, String remoteAddress) {
        if (debug) {
            if (protocolVersion == VotifierSession.ProtocolVersion.ONE) {
                logger.info("Got a protocol v1 vote record from " + remoteAddress + " -> " + vote);
            } else {
                logger.info("Got a protocol v2 vote record from " + remoteAddress + " -> " + vote);
            }
        }
        this.fireVoteEvent(vote);
    }

    @Override
    public void onError(Throwable throwable, boolean alreadyHandledVote, String remoteAddress) {
        if (debug) {
            if (alreadyHandledVote) {
                logger.error("Vote processed, however an exception " +
                        "occurred with a vote from " + remoteAddress, throwable);
            } else {
                logger.error("Unable to process vote from " + remoteAddress, throwable);
            }
        } else if (!alreadyHandledVote) {
            logger.error("Unable to process vote from " + remoteAddress);
        }
    }

    @Override
    public void onForward(final Vote v) {
        if (debug) {
            logger.info("Got a forwarded vote -> " + v);
        }
        fireVoteEvent(v);
    }

    private void fireVoteEvent(final Vote vote) {
        VotifierEvent event = new VotifierEvent(vote, Sponge.server().causeStackManager().currentCause());
        final Task task = Task.builder()
                .plugin(pluginContainer)
                .execute(() -> Sponge.eventManager().post(event))
                .build();
        Sponge.server().scheduler().submit(task);
    }
}
