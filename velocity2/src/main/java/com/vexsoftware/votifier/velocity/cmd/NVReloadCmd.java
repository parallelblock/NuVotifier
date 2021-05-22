package com.vexsoftware.votifier.velocity.cmd;

import com.velocitypowered.api.command.Command;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.vexsoftware.votifier.velocity.VotifierPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.checkerframework.checker.nullness.qual.NonNull;

public class NVReloadCmd implements SimpleCommand {

    private final VotifierPlugin plugin;

    public NVReloadCmd(VotifierPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(SimpleCommand.Invocation invocation) {
        CommandSource sender = invocation.source();
        sender.sendMessage(Component.text("Reloading NuVotifier...").color(NamedTextColor.GRAY));
        if (plugin.reload()) {
            sender.sendMessage(Component.text("NuVotifier has been reloaded!").color(NamedTextColor.DARK_GREEN));
        } else {
            sender.sendMessage(Component.text("Looks like there was a problem reloading NuVotifier, check the console!").color(NamedTextColor.DARK_RED));
        }
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("nuvotifier.reload");
    }
}
