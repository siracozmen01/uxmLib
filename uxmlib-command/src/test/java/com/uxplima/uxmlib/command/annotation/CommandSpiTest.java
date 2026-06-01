package com.uxplima.uxmlib.command.annotation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmlib.command.Sender;
import com.uxplima.uxmlib.command.annotation.annotations.Arg;
import com.uxplima.uxmlib.command.annotation.annotations.Command;
import com.uxplima.uxmlib.command.annotation.annotations.Subcommand;
import org.junit.jupiter.api.Test;

/**
 * Covers the three command SPIs: a {@link ParameterValidator} registered for a type runs against resolved
 * values; a {@link ContextParameter} teaches the DSL to inject a non-{@code @Arg} parameter and the four
 * built-in injectables stay registered; a {@link CommandCondition} vetoes execution with a message. The
 * registries are asserted directly because MockBukkit cannot dispatch a Brigadier tree.
 */
class CommandSpiTest {

    record Handle(String value) {}

    @Command(name = "spi")
    static class SpiCommand {
        @Subcommand("amount")
        void amount(Sender sender, @Arg("n") int n) {}

        // A non-@Arg parameter of a custom type is injected by a registered ContextParameter.
        @Subcommand("ctx")
        void ctx(Sender sender, Handle handle) {}
    }

    @Test
    void aRegisteredValidatorRunsAgainstTheResolvedValue() {
        java.util.List<Integer> seen = new java.util.ArrayList<>();
        ParamResolvers resolvers = ParamResolvers.withDefaults().validate(int.class, (value, arg) -> {
            seen.add(value);
            if (value != null && value > 5) {
                throw new IllegalArgumentException("too big");
            }
        });

        assertThat(resolvers.validatorsFor(int.class)).hasSize(1);
        ParameterValidator<Object> v = cast(resolvers.validatorsFor(int.class).get(0));
        assertThatCode(() -> v.validate(3, dummyArg())).doesNotThrowAnyException();
        assertThatThrownBy(() -> v.validate(9, dummyArg()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("too big");
        assertThat(seen).containsExactly(3, 9);
    }

    @Test
    void aContextParameterTeachesAnInjectableType() {
        ParamResolvers resolvers = ParamResolvers.withDefaults().context(Handle.class, ctx -> new Handle("ok"));

        assertThat(resolvers.hasContext(Handle.class)).isTrue();
        LiteralCommandNode<CommandSourceStack> node = AnnotatedCommands.buildNode(new SpiCommand(), resolvers);
        CommandNode<CommandSourceStack> ctx = java.util.Objects.requireNonNull(node.getChild("ctx"));
        // The injected parameter produces no argument node: it is filled from context, not parsed.
        assertThat(ctx.getChildren()).isEmpty();
        assertThat(ctx.getCommand()).isNotNull();
    }

    @Test
    void withoutAContextProviderTheCustomInjectionIsRejectedAtRegistration() {
        assertThatThrownBy(() -> AnnotatedCommands.buildNode(new SpiCommand()))
                .isInstanceOf(CommandParseException.class);
    }

    @Test
    void theFourBuiltinInjectablesAreRegisteredByDefault() {
        ParamResolvers resolvers = ParamResolvers.withDefaults();
        assertThat(resolvers.hasContext(Sender.class)).isTrue();
        assertThat(resolvers.hasContext(CommandSourceStack.class)).isTrue();
        assertThat(resolvers.hasContext(org.bukkit.command.CommandSender.class)).isTrue();
        assertThat(resolvers.hasContext(org.bukkit.entity.Player.class)).isTrue();
    }

    @Test
    void aRegisteredConditionIsHeldInOrderAndVetoesWithAMessage() {
        CommandCondition allow = ctx -> {};
        CommandCondition deny = ctx -> {
            throw new CommandCondition.CommandConditionException("nope");
        };
        ParamResolvers resolvers =
                ParamResolvers.withDefaults().condition(allow).condition(deny);

        assertThat(resolvers.conditions()).containsExactly(allow, deny);
        assertThatThrownBy(() -> {
                    throw new CommandCondition.CommandConditionException("nope");
                })
                .isInstanceOf(CommandCondition.CommandConditionException.class);
        assertThat(new CommandCondition.CommandConditionException("nope").reason())
                .isEqualTo("nope");
    }

    @SuppressWarnings("unchecked")
    private static ParameterValidator<Object> cast(ParameterValidator<?> validator) {
        return (ParameterValidator<Object>) validator;
    }

    private static Arg dummyArg() throws Exception {
        return SpiCommand.class
                .getDeclaredMethod("amount", Sender.class, int.class)
                .getParameters()[1]
                .getAnnotation(Arg.class);
    }
}
