package com.uxplima.uxmlib.hud;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.bukkit.event.player.PlayerQuitEvent;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** Verifies the quit listener releases a departing player's boss bar and sticky action bar immediately. */
class HudListenerTest {

    private ServerMock server;
    private FakeScheduler scheduler;
    private BossBarManager bossBars;
    private ActionBarManager actionBars;
    private HudListener listener;

    private static Component c(String s) {
        return Component.text(s);
    }

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        scheduler = new FakeScheduler();
        bossBars = new BossBarManager(scheduler, server);
        actionBars = new ActionBarManager(scheduler, server);
        listener = new HudListener(bossBars, actionBars);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void quittingDropsBothHudEntries() {
        PlayerMock player = server.addPlayer();
        BossBar bar = bossBars.countdown(player, c("boom"), Duration.ofSeconds(10));
        actionBars.show(player, c("hello"), Duration.ofSeconds(10));
        assertThat(bossBars.tracked()).isEqualTo(1);
        assertThat(actionBars.tracked()).isEqualTo(1);

        listener.onQuit(new PlayerQuitEvent(player, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));

        assertThat(bossBars.tracked()).isZero();
        assertThat(actionBars.tracked()).isZero();
        assertThat(player.getBossBars()).doesNotContain(bar);
    }
}
