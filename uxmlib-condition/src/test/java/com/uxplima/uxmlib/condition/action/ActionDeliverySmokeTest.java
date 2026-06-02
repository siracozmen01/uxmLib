package com.uxplima.uxmlib.condition.action;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Bukkit;
import org.bukkit.event.inventory.InventoryType;

import com.uxplima.uxmlib.condition.OperandResolver;
import com.uxplima.uxmlib.text.Text;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.sound.AudioExperience;

/**
 * Smoke test for the native delivery wiring against a real (mock) Paper server. Every closure that touches the
 * Adventure/Bukkit plumbing is exercised end to end: {@code [message]}/{@code [broadcast]} on a real audience,
 * {@code [console]}/{@code [player]} through a command sink, {@code [sound]} on the player's sound receiver
 * (including the malformed-key fail-soft path), {@code [title]} into the title plumbing, and {@code [close]}
 * against a real open inventory. The parser and ordering logic are covered by their own pure tests; this asserts
 * the native plumbing the closures rely on holds together.
 */
class ActionDeliverySmokeTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void messageActionLandsOnTheRealPlayerAudience() {
        PlayerMock player = server.addPlayer("Steve");
        ActionContext context =
                ActionContext.builder(OperandResolver.identity()).player(player).build();

        ActionList.parse(List.of("[message] <green>hi there")).run(context);

        assertThat(Text.plain(player.nextComponentMessage())).isEqualTo("hi there");
    }

    @Test
    void consoleActionDispatchesThroughBukkit() {
        AtomicReference<String> dispatched = new AtomicReference<>();
        CommandSink consoleSink = command -> {
            dispatched.set(command);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        };
        ActionContext context = ActionContext.builder(OperandResolver.identity())
                .consoleSink(consoleSink)
                .build();

        ActionList.parse(List.of("[console] /say hello")).run(context);

        assertThat(dispatched.get()).isEqualTo("say hello");
    }

    @Test
    void soundActionReachesThePlayerSoundReceiver() {
        PlayerMock player = server.addPlayer("Steve");
        ActionContext context =
                ActionContext.builder(OperandResolver.identity()).player(player).build();

        ActionList.parse(List.of("[sound] minecraft:entity.player.levelup 0.5 1.5"))
                .run(context);

        List<AudioExperience> heard = player.getHeardSounds();
        assertThat(heard).hasSize(1);
        assertThat(heard.get(0).getSound()).isEqualTo("minecraft:entity.player.levelup");
        assertThat(heard.get(0).getVolume()).isEqualTo(0.5f);
        assertThat(heard.get(0).getPitch()).isEqualTo(1.5f);
    }

    @Test
    void malformedSoundKeyIsSkippedAndNeverThrowsOnTheRealAudience() {
        PlayerMock player = server.addPlayer("Steve");
        // The key resolves to an uppercase/garbage value Key.key would reject; the action must fail soft so the
        // following [message] still lands on the real player audience.
        OperandResolver resolver = (subject, template) -> template.replace("%sound%", "NOT A VALID KEY");
        ActionContext context = ActionContext.builder(resolver).player(player).build();

        ActionList.parse(List.of("[sound] %sound%", "[message] still here")).run(context);

        assertThat(player.getHeardSounds()).isEmpty();
        assertThat(Text.plain(player.nextComponentMessage())).isEqualTo("still here");
    }

    @Test
    void titleActionThreadsThroughTheTitlePlumbingWithoutThrowing() {
        // MockBukkit does not capture an Adventure showTitle(Title) into nextTitle(), so the observable signal
        // here is that the title plumbing (Title.title + Audience#showTitle) holds together end to end and the
        // following [message] still lands — i.e. the title closure did not throw.
        PlayerMock player = server.addPlayer("Steve");
        ActionContext context =
                ActionContext.builder(OperandResolver.identity()).player(player).build();

        ActionList.parse(List.of("[title] <gold>welcome", "[message] after title"))
                .run(context);

        assertThat(Text.plain(player.nextComponentMessage())).isEqualTo("after title");
    }

    @Test
    void broadcastReachesAServerWideAudience() {
        PlayerMock everyone = server.addPlayer("Alex");
        ActionContext context = ActionContext.builder(OperandResolver.identity())
                .broadcast(everyone)
                .build();

        ActionList.parse(List.of("[broadcast] <red>restarting")).run(context);

        assertThat(Text.plain(everyone.nextComponentMessage())).isEqualTo("restarting");
    }

    @Test
    void closeActionClosesARealOpenInventory() {
        PlayerMock player = server.addPlayer("Steve");
        player.openInventory(Bukkit.createInventory(player, InventoryType.CHEST));
        assertThat(player.getOpenInventory().getType()).isEqualTo(InventoryType.CHEST);
        ActionContext context =
                ActionContext.builder(OperandResolver.identity()).player(player).build();

        ActionList.parse(List.of("[close]")).run(context);

        assertThat(player.getOpenInventory().getType()).isNotEqualTo(InventoryType.CHEST);
    }

    @Test
    void playerCommandDispatchesThroughTheSinkOnARealServer() {
        AtomicReference<String> dispatched = new AtomicReference<>();
        PlayerMock player = server.addPlayer("Steve");
        ActionContext context = ActionContext.builder(OperandResolver.identity())
                .player(player)
                .playerSink(command -> {
                    dispatched.set(command);
                    Bukkit.dispatchCommand(player, command);
                })
                .build();

        ActionList.parse(List.of("[player] /spawn")).run(context);

        assertThat(dispatched.get()).isEqualTo("spawn");
    }
}
