package com.uxplima.uxmlib.command.annotation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmlib.command.Sender;
import com.uxplima.uxmlib.command.annotation.annotations.Command;
import com.uxplima.uxmlib.command.annotation.annotations.Cooldown;
import com.uxplima.uxmlib.command.annotation.annotations.Subcommand;
import org.junit.jupiter.api.Test;

/**
 * Covers {@code @}{@link Cooldown}: the derived {@link CommandCondition} keys the {@link Cooldowns} store
 * by path + UUID, vetoes a second run inside the window with the remaining time, lets the console through,
 * and is folded into the tree at build time. The condition is exercised directly with a mocked context
 * (MockBukkit cannot dispatch a Brigadier tree); a fake clock makes the window deterministic.
 */
class CooldownConditionTest {

    @Command(name = "kit")
    static class KitCommand {
        @Cooldown("30s")
        @Subcommand("daily")
        void daily(Sender sender) {}

        @Subcommand("free")
        void free(Sender sender) {}
    }

    @Command(name = "bad")
    static class BadCooldownCommand {
        @Cooldown("soon")
        @Subcommand("go")
        void go(Sender sender) {}
    }

    @Test
    void aCooldownBranchBuildsCleanly() {
        LiteralCommandNode<CommandSourceStack> node = AnnotatedCommands.buildNode(new KitCommand());
        CommandNode<CommandSourceStack> daily = node.getChild("daily");
        assertThat(daily).isNotNull();
        assertThat(java.util.Objects.requireNonNull(daily).getCommand()).isNotNull();
    }

    @Test
    void anInvalidDurationFailsAtRegistration() {
        assertThatThrownBy(() -> AnnotatedCommands.buildNode(new BadCooldownCommand()))
                .isInstanceOf(CommandParseException.class)
                .hasMessageContaining("soon");
    }

    @Test
    void noAnnotationYieldsNoCondition() throws Exception {
        Method free = KitCommand.class.getDeclaredMethod("free", Sender.class);
        assertThat(CooldownCondition.forMethod(free, "kit free", new Cooldowns()))
                .isNull();
    }

    @Test
    void aMethodLevelCooldownDerivesACondition() throws Exception {
        Method daily = KitCommand.class.getDeclaredMethod("daily", Sender.class);
        assertThat(CooldownCondition.forMethod(daily, "kit daily", new Cooldowns()))
                .isNotNull();
    }

    @Test
    void aSecondPlayerRunInsideTheWindowIsVetoedWithTheRemainingTime() throws Exception {
        AtomicLong now = new AtomicLong(0L);
        Cooldowns cooldowns = new Cooldowns(now::get);
        CommandCondition condition = cooldownCondition(cooldowns);
        CommandContext<CommandSourceStack> player = playerContext(UUID.randomUUID());

        assertThatCode(() -> condition.test(player)).doesNotThrowAnyException(); // arms 30s

        now.set(10_000L);
        assertThatThrownBy(() -> condition.test(player))
                .isInstanceOf(CommandCondition.CommandConditionException.class)
                .hasMessageContaining("20s");
    }

    @Test
    void onceTheWindowElapsesThePlayerMayRunAgain() throws Exception {
        AtomicLong now = new AtomicLong(0L);
        Cooldowns cooldowns = new Cooldowns(now::get);
        CommandCondition condition = cooldownCondition(cooldowns);
        CommandContext<CommandSourceStack> player = playerContext(UUID.randomUUID());

        condition.test(player); // arms until 30_000
        now.set(30_000L);

        assertThatCode(() -> condition.test(player)).doesNotThrowAnyException();
    }

    @Test
    void differentPlayersCoolDownIndependently() throws Exception {
        AtomicLong now = new AtomicLong(0L);
        Cooldowns cooldowns = new Cooldowns(now::get);
        CommandCondition condition = cooldownCondition(cooldowns);

        condition.test(playerContext(UUID.randomUUID())); // first player arms

        assertThatCode(() -> condition.test(playerContext(UUID.randomUUID()))).doesNotThrowAnyException();
    }

    @Test
    void theConsoleIsNeverGated() throws Exception {
        Cooldowns cooldowns = new Cooldowns(() -> 0L);
        CommandCondition condition = cooldownCondition(cooldowns);
        CommandContext<CommandSourceStack> console = consoleContext();

        assertThatCode(() -> condition.test(console)).doesNotThrowAnyException();
        assertThatCode(() -> condition.test(console)).doesNotThrowAnyException();
        // No UUID to key by, so nothing was stored.
        assertThat(cooldowns.size()).isZero();
    }

    private static CommandCondition cooldownCondition(Cooldowns cooldowns) throws Exception {
        Method daily = KitCommand.class.getDeclaredMethod("daily", Sender.class);
        return java.util.Objects.requireNonNull(CooldownCondition.forMethod(daily, "kit daily", cooldowns));
    }

    private static CommandContext<CommandSourceStack> playerContext(UUID uuid) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);
        return contextWithSender(player);
    }

    private static CommandContext<CommandSourceStack> consoleContext() {
        return contextWithSender(mock(CommandSender.class));
    }

    @SuppressWarnings("unchecked")
    private static CommandContext<CommandSourceStack> contextWithSender(CommandSender sender) {
        CommandSourceStack source = mock(CommandSourceStack.class);
        when(source.getSender()).thenReturn(sender);
        CommandContext<CommandSourceStack> ctx = mock(CommandContext.class);
        when(ctx.getSource()).thenReturn(source);
        return ctx;
    }
}
