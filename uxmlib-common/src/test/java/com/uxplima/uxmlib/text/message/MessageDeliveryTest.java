package com.uxplima.uxmlib.text.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Native Adventure delivery per channel. MockBukkit round-trips chat and the action bar into a readable
 * queue, so those are asserted exactly; it does not round-trip the Adventure {@code showTitle(Title)} /
 * {@code showBossBar(BossBar)} paths into a readable form, so those are smoke-tested (assert the wiring fires
 * cleanly) and exercised against real Paper at runtime.
 */
class MessageDeliveryTest {

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
    void chatRoundTripsThroughTheMessageQueue() {
        PlayerMock player = server.addPlayer();
        new Message.Chat("ignored").send(player, Component.text("hello"));

        assertThat(player.nextComponentMessage()).isEqualTo(Component.text("hello"));
    }

    @Test
    void actionBarRoundTrips() {
        PlayerMock player = server.addPlayer();
        new Message.ActionBar("ignored").send(player, Component.text("ping"));

        assertThat(player.nextActionBar()).isEqualTo(Component.text("ping"));
    }

    @Test
    void silentDeliversNothing() {
        PlayerMock player = server.addPlayer();
        new Message.Silent().send(player, Component.text("muted"));

        assertThat(player.nextComponentMessage()).isNull();
        assertThat(player.nextActionBar()).isNull();
    }

    @Test
    void titleDeliveryDoesNotThrow() {
        PlayerMock player = server.addPlayer();
        Message.TitleText title = new Message.TitleText(
                "<gold>Hi", "<gray>sub", Duration.ofMillis(250), Duration.ofSeconds(2), Duration.ofMillis(250));

        assertThatCode(() -> title.send(player, Component.text("Hi"), Component.text("sub")))
                .doesNotThrowAnyException();
    }

    @Test
    void bossBarDeliveryDoesNotThrow() {
        PlayerMock player = server.addPlayer();
        Message.BossBarText bar =
                new Message.BossBarText("<red>boss", 0.75f, BossBar.Color.RED, BossBar.Overlay.PROGRESS);

        assertThatCode(() -> bar.send(player, Component.text("boss"))).doesNotThrowAnyException();
    }
}
