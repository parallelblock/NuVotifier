package com.vexsoftware.votifier.sponge8.config;

import com.vexsoftware.votifier.sponge8.NuVotifier;
import io.leangen.geantyref.TypeToken;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.ConfigurationOptions;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigLoader {

    private static SpongeConfig spongeConfig;

    public static void loadConfig(NuVotifier pl) {
        if (!Files.exists(pl.getConfigDir())) {
            try {
                Files.createDirectories(pl.getConfigDir());
            } catch (IOException e) {
                throw new RuntimeException("Unable to create the plugin data folder " + pl.getConfigDir(), e);
            }
        }
        try {
            Path config = pl.getConfigDir().resolve("config.yml");
            if (!Files.exists(config)) {
                try {
                    Files.createFile(config);
                } catch (IOException e) {
                    throw new IOException("Unable to create the config file at " + config, e);
                }
            }
            YamlConfigurationLoader loader = YamlConfigurationLoader.builder().path(config).build();
            ConfigurationNode configNode = loader.load(ConfigurationOptions.defaults().shouldCopyDefaults(true));
            spongeConfig = configNode.get(TypeToken.get(SpongeConfig.class), new SpongeConfig());
            loader.save(configNode);
        } catch (Exception e) {
            pl.getLogger().error("Could not load config.", e);
        }
    }

    public static SpongeConfig getSpongeConfig() {
        return spongeConfig;
    }
}
