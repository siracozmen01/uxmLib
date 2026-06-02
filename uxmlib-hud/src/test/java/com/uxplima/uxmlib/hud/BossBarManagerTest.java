package com.uxplima.uxmlib.hud;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Drives the manager with a fake clock and the captured shared timer. MockBukkit's PlayerMock round-trips
 * boss bars (it keeps a live {@code getBossBars()} set), so we can assert a bar is shown, that its progress
 * tracks the ramp, and that a finished countdown hides it and self-cancels the timer.
 */
class BossBarManagerTest {

    private ServerMock server;
    private FakeScheduler scheduler;
    private AtomicLong now;
    private BossBarManager manager;

    private static Component c(String s) {
        return Component.text(s);
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        scheduler = new FakeScheduler();
        now = new AtomicLong(0L);
        manager = new BossBarManager(scheduler, server, now::get);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void showDisplaysTheBarAndStartsTheSharedTimer() {
        PlayerMock player = server.addPlayer();
        BossBar bar = BossBar.bossBar(c("hi"), 0.5f, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
        manager.show(player, bar, BossBarMode.PERMANENT, null);

        assertThat(player.getBossBars()).contains(bar);
        assertThat(scheduler.starts()).isEqualTo(1);
        assertThat(manager.tracked()).isEqualTo(1);
    }

    @Test
    void twoBarsShareOneTimer() {
        PlayerMock a = server.addPlayer();
        PlayerMock b = server.addPlayer();
        manager.countdown(a, c("a"), Duration.ofSeconds(10));
        manager.countdown(b, c("b"), Duration.ofSeconds(10));
        assertThat(scheduler.starts()).isEqualTo(1);
        assertThat(manager.tracked()).isEqualTo(2);
    }

    @Test
    void permanentProgressDoesNotMoveOnTick() {
        PlayerMock player = server.addPlayer();
        BossBar bar = BossBar.bossBar(c("hi"), 0.5f, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
        manager.show(player, bar, BossBarMode.PERMANENT, null);

        now.set(5000L);
        scheduler.fire();
        assertThat(bar.progress()).isEqualTo(0.5f);
        assertThat(manager.tracked()).isEqualTo(1);
    }

    @Test
    void fillingRampsTowardFull() {
        PlayerMock player = server.addPlayer();
        BossBar bar = BossBar.bossBar(c("load"), 0.0f, BossBar.Color.GREEN, BossBar.Overlay.PROGRESS);
        manager.show(player, bar, BossBarMode.FILLING, Duration.ofSeconds(10));

        now.set(5000L);
        scheduler.fire();
        assertThat(bar.progress()).isCloseTo(0.5f, within(0.001f));
    }

    @Test
    void countdownRampsDownThenAutoHidesAndSelfCancels() {
        PlayerMock player = server.addPlayer();
        manager.countdown(player, c("boom"), Duration.ofSeconds(4));
        BossBar bar = java.util.Objects.requireNonNull(manager.barOf(player.getUniqueId()));
        assertThat(bar.progress()).isEqualTo(1.0f);

        now.set(2000L);
        scheduler.fire();
        assertThat(bar.progress()).isCloseTo(0.5f, within(0.001f));
        assertThat(player.getBossBars()).contains(bar);

        now.set(4001L);
        scheduler.fire();
        assertThat(player.getBossBars()).doesNotContain(bar);
        assertThat(manager.tracked()).isZero();
        assertThat(scheduler.cancelled()).isTrue();
    }

    @Test
    void dynamicReEvaluatesProgressEachTick() {
        PlayerMock player = server.addPlayer();
        BossBar bar = manager.dynamic(player, c("hp"), p -> 0.25f);
        assertThat(bar.progress()).isEqualTo(0.25f);

        now.set(1000L);
        scheduler.fire();
        assertThat(bar.progress()).isEqualTo(0.25f);
    }

    @Test
    void dynamicReEvaluatesNameEachTick() {
        PlayerMock player = server.addPlayer();
        BossBar bar = manager.dynamic(player, p -> c("frame"), p -> 0.4f);

        now.set(1000L);
        scheduler.fire();
        assertThat(bar.name()).isEqualTo(c("frame"));
        assertThat(bar.progress()).isEqualTo(0.4f);
    }

    @Test
    void countdownWithTitleRendersTheRemainingTimeAndUpdatesAsItDrains() {
        PlayerMock player = server.addPlayer();
        BossBar bar = manager.countdown(player, "Ends in <time>", Duration.ofSeconds(10));
        assertThat(plain(bar.name())).isEqualTo("Ends in 10s");

        now.set(4000L);
        scheduler.fire();
        assertThat(plain(bar.name())).isEqualTo("Ends in 6s");
        assertThat(bar.progress()).isCloseTo(0.6f, within(0.001f));
    }

    @Test
    void countdownWithTitleStillAutoHidesAtZero() {
        PlayerMock player = server.addPlayer();
        BossBar bar = manager.countdown(player, "<time>", Duration.ofSeconds(3));

        now.set(3001L);
        scheduler.fire();
        assertThat(player.getBossBars()).doesNotContain(bar);
        assertThat(manager.tracked()).isZero();
    }

    @Test
    void hideRemovesTheBarFromThePlayer() {
        PlayerMock player = server.addPlayer();
        BossBar bar = manager.countdown(player, c("x"), Duration.ofSeconds(10));
        manager.hide(player.getUniqueId());

        assertThat(manager.tracked()).isZero();
        assertThat(player.getBossBars()).doesNotContain(bar);
    }

    @Test
    void timerStopsWhenTheLastBarLeaves() {
        PlayerMock player = server.addPlayer();
        manager.countdown(player, c("x"), Duration.ofSeconds(10));
        manager.hide(player.getUniqueId());

        scheduler.fire();
        assertThat(scheduler.cancelled()).isTrue();
    }

    @Test
    void offlinePlayerEntryIsDropped() {
        PlayerMock player = server.addPlayer();
        manager.countdown(player, c("x"), Duration.ofSeconds(10));
        player.disconnect();

        scheduler.fire();
        assertThat(manager.tracked()).isZero();
    }

    @Test
    void backwardClockStepFreezesACountdownInsteadOfFinishing() {
        // Demonstrates the wall-clock hazard: when the clock steps backward (an NTP correction), elapsed goes
        // negative, progress clamps to full and the bar never auto-hides. The default constructor avoids this
        // by deriving elapsed from a monotonic source rather than System.currentTimeMillis.
        PlayerMock player = server.addPlayer();
        manager.countdown(player, c("boom"), Duration.ofSeconds(4));
        BossBar bar = java.util.Objects.requireNonNull(manager.barOf(player.getUniqueId()));

        now.set(-10_000L); // the wall clock jumped backwards after the bar started
        scheduler.fire();
        assertThat(bar.progress()).isEqualTo(1.0f); // frozen full, never finishes
        assertThat(manager.tracked()).isEqualTo(1);
    }

    @Test
    void defaultConstructorUsesAMonotonicClock() {
        // The public constructor must not read wall time, which can step backward over an NTP correction.
        BossBarManager defaulted = new BossBarManager(scheduler, server);
        PlayerMock player = server.addPlayer();
        defaulted.countdown(player, c("boom"), Duration.ofSeconds(4));
        assertThat(defaulted.tracked()).isEqualTo(1);
    }

    @Test
    void countdownRejectsNonPositiveDuration() {
        PlayerMock player = server.addPlayer();
        assertThatThrownBy(() -> manager.countdown(player, c("x"), Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void closeHidesEveryBarAndCancelsTheTimer() {
        PlayerMock a = server.addPlayer();
        PlayerMock b = server.addPlayer();
        BossBar barA = manager.countdown(a, c("a"), Duration.ofSeconds(10));
        BossBar barB = manager.countdown(b, c("b"), Duration.ofSeconds(10));

        manager.close();

        assertThat(manager.tracked()).isZero();
        assertThat(a.getBossBars()).doesNotContain(barA);
        assertThat(b.getBossBars()).doesNotContain(barB);
        assertThat(scheduler.cancelled()).isTrue();
    }

    @Test
    void closeOnAnEmptyManagerIsHarmless() {
        org.assertj.core.api.Assertions.assertThatCode(() -> manager.close()).doesNotThrowAnyException();
        assertThat(manager.tracked()).isZero();
    }

    @Test
    void managerIsReusableAfterClose() {
        PlayerMock player = server.addPlayer();
        manager.countdown(player, c("a"), Duration.ofSeconds(10));
        manager.close();

        manager.countdown(player, c("b"), Duration.ofSeconds(10));
        assertThat(manager.tracked()).isEqualTo(1);
        assertThat(scheduler.starts()).isEqualTo(2);
    }

    @Test
    void timedShowRequiresADuration() {
        PlayerMock player = server.addPlayer();
        BossBar bar = BossBar.bossBar(c("hi"), 0.0f, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
        assertThatThrownBy(() -> manager.show(player, bar, BossBarMode.FILLING, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
