package com.uxplima.uxmlib.command.annotation;

import static org.assertj.core.api.Assertions.assertThat;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmlib.command.Sender;
import com.uxplima.uxmlib.command.annotation.annotations.Arg;
import com.uxplima.uxmlib.command.annotation.annotations.Command;
import com.uxplima.uxmlib.command.annotation.annotations.Permission;
import com.uxplima.uxmlib.command.annotation.annotations.Subcommand;
import org.junit.jupiter.api.Test;

/** Verifies an auto-generated help subcommand is attached, and can be opted out. */
class HelpTest {

    @Command(name = "town", description = "Town commands")
    static class TownCommand {
        @Subcommand(value = "create", description = "Found a town")
        void create(Sender sender, @Arg("name") String name) {}

        @Subcommand(value = "delete", description = "Disband your town")
        @Permission("town.delete")
        void delete(Sender sender) {}
    }

    @Command(name = "nohelp", help = false)
    static class NoHelpCommand {
        @Subcommand("go")
        void go(Sender sender) {}
    }

    @Test
    void helpSubcommandIsAddedByDefault() {
        LiteralCommandNode<CommandSourceStack> node = AnnotatedCommands.buildNode(new TownCommand());
        assertThat(node.getChild("help")).isNotNull();
        assertThat(node.getChild("create")).isNotNull();
    }

    @Test
    void helpCanBeOptedOut() {
        LiteralCommandNode<CommandSourceStack> node = AnnotatedCommands.buildNode(new NoHelpCommand());
        assertThat(node.getChild("help")).isNull();
    }
}
