package com.uxplima.uxmlib.command.annotation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Parameter;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmlib.command.Sender;
import com.uxplima.uxmlib.command.annotation.annotations.Arg;
import com.uxplima.uxmlib.command.annotation.annotations.Command;
import com.uxplima.uxmlib.command.annotation.annotations.Length;
import com.uxplima.uxmlib.command.annotation.annotations.Range;
import com.uxplima.uxmlib.command.annotation.annotations.Subcommand;
import org.junit.jupiter.api.Test;

/**
 * Verifies {@code @Range}/{@code @Length}: {@code @Range} maps to native Brigadier numeric bounds on the
 * built node, and the server-side {@link ArgValidators} re-check rejects out-of-range numbers and
 * out-of-length strings (the only check Brigadier cannot make for string length).
 */
class RangeLengthTest {

    @Command(name = "bounded")
    static class BoundedCommand {
        @Subcommand("buy")
        void buy(Sender sender, @Arg("amount") @Range(min = 1, max = 64) int amount) {}

        @Subcommand("page")
        void page(Sender sender, @Arg("n") @Range(min = 0) long n) {}

        @Subcommand("name")
        void name(Sender sender, @Arg("nick") @Length(min = 3, max = 16) String nick) {}
    }

    @Test
    void rangeMapsToNativeIntegerBounds() {
        LiteralCommandNode<CommandSourceStack> node = AnnotatedCommands.buildNode(new BoundedCommand());
        IntegerArgumentType type = argType(node, "buy", "amount", IntegerArgumentType.class);
        assertThat(type.getMinimum()).isEqualTo(1);
        assertThat(type.getMaximum()).isEqualTo(64);
    }

    @Test
    void rangeMapsToNativeLongBounds() {
        LiteralCommandNode<CommandSourceStack> node = AnnotatedCommands.buildNode(new BoundedCommand());
        LongArgumentType type = argType(node, "page", "n", LongArgumentType.class);
        assertThat(type.getMinimum()).isEqualTo(0L);
    }

    @Test
    void rangeAlsoEnforcesServerSide() throws Exception {
        Parameter amount = BoundedCommand.class.getDeclaredMethod("buy", Sender.class, int.class)
                .getParameters()[1];
        assertThatCode(() -> ArgValidators.check(amount, 10)).doesNotThrowAnyException();
        assertThatThrownBy(() -> ArgValidators.check(amount, 100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount")
                .hasMessageContaining("64");
        assertThatThrownBy(() -> ArgValidators.check(amount, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lengthEnforcesServerSideOnly() throws Exception {
        Parameter nick = BoundedCommand.class.getDeclaredMethod("name", Sender.class, String.class)
                .getParameters()[1];
        assertThatCode(() -> ArgValidators.check(nick, "Steve")).doesNotThrowAnyException();
        assertThatThrownBy(() -> ArgValidators.check(nick, "ab"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("characters");
        assertThatThrownBy(() -> ArgValidators.check(nick, "a".repeat(20)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static <T> T argType(
            LiteralCommandNode<CommandSourceStack> root, String literal, String arg, Class<T> argTypeClass) {
        CommandNode<CommandSourceStack> lit = java.util.Objects.requireNonNull(root.getChild(literal));
        CommandNode<CommandSourceStack> node = java.util.Objects.requireNonNull(lit.getChild(arg));
        return argTypeClass.cast(((ArgumentCommandNode<CommandSourceStack, ?>) node).getType());
    }
}
