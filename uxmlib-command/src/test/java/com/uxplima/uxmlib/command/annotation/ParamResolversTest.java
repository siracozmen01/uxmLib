package com.uxplima.uxmlib.command.annotation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmlib.command.Sender;
import com.uxplima.uxmlib.command.annotation.annotations.Arg;
import com.uxplima.uxmlib.command.annotation.annotations.Command;
import com.uxplima.uxmlib.command.annotation.annotations.Subcommand;
import org.junit.jupiter.api.Test;

/** Verifies the resolver registry: rich built-in types build a tree, enums and custom types resolve. */
class ParamResolversTest {

    enum Mode {
        SURVIVAL,
        CREATIVE
    }

    @Command(name = "rich")
    static class RichCommand {
        @Subcommand("tp")
        void tp(Sender sender, @Arg("target") org.bukkit.entity.Player target) {}

        @Subcommand("world")
        void world(Sender sender, @Arg("w") org.bukkit.World world) {}

        @Subcommand("mode")
        void mode(Sender sender, @Arg("mode") Mode mode) {}

        @Subcommand("give")
        void give(Sender sender, @Arg("item") org.bukkit.Material item, @Arg("id") java.util.UUID id) {}
    }

    record Point(int x, int z) {}

    @Command(name = "custom")
    static class CustomCommand {
        @Subcommand("at")
        void at(Sender sender, @Arg("point") Point point) {}
    }

    @Test
    void buildsRichBuiltinArgumentTrees() {
        LiteralCommandNode<CommandSourceStack> node = AnnotatedCommands.buildNode(new RichCommand());

        assertThat(child(node, "tp", "target")).isNotNull();
        assertThat(child(node, "world", "w")).isNotNull();
        assertThat(child(node, "mode", "mode")).isNotNull();
        assertThat(child(node, "give", "item")).isNotNull();
    }

    @Test
    void aCustomResolverTeachesTheDslANewType() {
        ParamResolvers resolvers = ParamResolvers.withDefaults().register(Point.class, new ParamResolver<Point>() {
            @Override
            public com.mojang.brigadier.arguments.ArgumentType<?> argumentType(Arg arg) {
                return com.mojang.brigadier.arguments.StringArgumentType.word();
            }

            @Override
            public Point resolve(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, String name) {
                return new Point(0, 0);
            }
        });

        LiteralCommandNode<CommandSourceStack> node = AnnotatedCommands.buildNode(new CustomCommand(), resolvers);
        assertThat(child(node, "at", "point")).isNotNull();
    }

    @Test
    void rejectsATypeNoResolverHandles() {
        // Without the custom resolver, Point has no resolver and registration fails loudly.
        assertThatThrownBy(() -> AnnotatedCommands.buildNode(new CustomCommand()))
                .isInstanceOf(CommandParseException.class)
                .hasMessageContaining("resolver");
    }

    private static @org.jspecify.annotations.Nullable CommandNode<CommandSourceStack> child(
            LiteralCommandNode<CommandSourceStack> root, String literal, String arg) {
        CommandNode<CommandSourceStack> lit = root.getChild(literal);
        return lit == null ? null : lit.getChild(arg);
    }
}
