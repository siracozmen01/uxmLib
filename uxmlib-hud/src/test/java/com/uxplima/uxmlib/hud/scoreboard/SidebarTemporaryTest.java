package com.uxplima.uxmlib.hud.scoreboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;

import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmlib.hud.FakeScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The temporary-with-restore state machine: a temp sidebar shows over whatever the player had, and after its
 * {@link Duration} the captured one-shot restore swaps them back. Driven through the real Paper scoreboard API
 * (MockBukkit) and a fake scheduler whose one-shot task we fire by hand.
 */
class SidebarTemporaryTest {

    private ServerMock server;
    private FakeScheduler scheduler;
    private SidebarManager manager;

    private static Component c(String s) {
        return Component.text(s);
    }

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        scheduler = new FakeScheduler();
        manager = new SidebarManager(server.getScoreboardManager(), scheduler);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void temporaryRestoresThePriorBareScoreboardAfterTheDuration() {
        PlayerMock player = server.addPlayer();
        var before = player.getScoreboard();

        manager.showTemporary(player, c("Temp"), List.of(c("a")), Duration.ofSeconds(5));
        Objective shown = player.getScoreboard().getObjective(DisplaySlot.SIDEBAR);
        assertThat(shown).isNotNull();
        assertThat(shown.displayName()).isEqualTo(c("Temp"));

        scheduler.runLater();
        assertThat(player.getScoreboard()).isSameAs(before);
        assertThat(manager.count()).isZero();
    }

    @Test
    void temporaryRestoresThePriorManagedSidebarAfterTheDuration() {
        PlayerMock player = server.addPlayer();
        manager.create(player, c("Permanent"));

        manager.showTemporary(player, c("Temp"), List.of(c("x")), Duration.ofSeconds(3));
        assertThat(player.getScoreboard().getObjective(DisplaySlot.SIDEBAR).displayName())
                .isEqualTo(c("Temp"));

        scheduler.runLater();
        Objective restored = player.getScoreboard().getObjective(DisplaySlot.SIDEBAR);
        assertThat(restored).isNotNull();
        assertThat(restored.displayName()).isEqualTo(c("Permanent"));
        assertThat(manager.count()).isEqualTo(1);
    }

    @Test
    void removingTheTemporaryBeforeItLapsesCancelsTheRestore() {
        PlayerMock player = server.addPlayer();
        manager.create(player, c("Permanent"));

        manager.showTemporary(player, c("Temp"), List.of(c("x")), Duration.ofSeconds(3));
        manager.remove(player);

        // The pending restore now has nothing to do; firing it must not resurrect the temp board.
        scheduler.runLater();
        assertThat(manager.count()).isZero();
    }

    @Test
    void temporaryRequiresAScheduler() {
        SidebarManager noScheduler = new SidebarManager(server.getScoreboardManager());
        PlayerMock player = server.addPlayer();
        assertThatThrownBy(() -> noScheduler.showTemporary(player, c("Temp"), List.of(c("a")), Duration.ofSeconds(1)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void temporaryRejectsNonPositiveDuration() {
        PlayerMock player = server.addPlayer();
        assertThatThrownBy(() -> manager.showTemporary(player, c("Temp"), List.of(c("a")), Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
