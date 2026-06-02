package com.uxplima.uxmlib.hud;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** Verifies the one-shared-timer re-send, deadline expiry and self-cancellation of the sticky action bar. */
class ActionBarManagerTest {

    private ServerMock server;
    private FakeScheduler scheduler;
    private AtomicLong now;
    private ActionBarManager manager;

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
        manager = new ActionBarManager(scheduler, server, now::get);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void showSendsOnceAndStartsTheSharedTimer() {
        PlayerMock player = server.addPlayer();
        manager.show(player, c("hello"), Duration.ofSeconds(5));

        assertThat(player.nextActionBar()).isEqualTo(c("hello"));
        assertThat(scheduler.starts()).isEqualTo(1);
        assertThat(manager.tracked()).isEqualTo(1);
    }

    @Test
    void twoStickyBarsShareOneTimer() {
        PlayerMock a = server.addPlayer();
        PlayerMock b = server.addPlayer();
        manager.show(a, c("a"), Duration.ofSeconds(5));
        manager.show(b, c("b"), Duration.ofSeconds(5));
        assertThat(scheduler.starts()).isEqualTo(1);
        assertThat(manager.tracked()).isEqualTo(2);
    }

    @Test
    void tickResendsBeforeTheDeadline() {
        PlayerMock player = server.addPlayer();
        manager.show(player, c("hello"), Duration.ofSeconds(5));
        player.nextActionBar(); // drain the initial send

        now.set(1000L); // still well before the 5s deadline
        scheduler.fire();
        assertThat(player.nextActionBar()).isEqualTo(c("hello"));
        assertThat(manager.tracked()).isEqualTo(1);
    }

    @Test
    void timerSelfCancelsWhenEveryEntryExpires() {
        PlayerMock player = server.addPlayer();
        manager.show(player, c("hello"), Duration.ofSeconds(2));

        now.set(2001L); // past the deadline
        scheduler.fire();
        assertThat(manager.tracked()).isZero();
        assertThat(scheduler.cancelled()).isTrue();
    }

    @Test
    void countdownRendersTheRemainingTimeAndUpdatesOnTick() {
        PlayerMock player = server.addPlayer();
        manager.countdown(player, "Closing in <time>", Duration.ofSeconds(10));
        assertThat(plain(player.nextActionBar())).isEqualTo("Closing in 10s");

        now.set(4000L);
        scheduler.fire();
        assertThat(plain(player.nextActionBar())).isEqualTo("Closing in 6s");
        assertThat(manager.tracked()).isEqualTo(1);
    }

    @Test
    void countdownExpiresAtItsDeadline() {
        PlayerMock player = server.addPlayer();
        manager.countdown(player, "<time>", Duration.ofSeconds(2));

        now.set(2001L);
        scheduler.fire();
        assertThat(manager.tracked()).isZero();
        assertThat(scheduler.cancelled()).isTrue();
    }

    @Test
    void clearStopsResending() {
        PlayerMock player = server.addPlayer();
        manager.show(player, c("hello"), Duration.ofSeconds(5));
        manager.clear(player.getUniqueId());
        assertThat(manager.tracked()).isZero();

        scheduler.fire();
        assertThat(scheduler.cancelled()).isTrue();
    }

    @Test
    void closeStopsTrackingAndCancelsTheTimer() {
        PlayerMock a = server.addPlayer();
        PlayerMock b = server.addPlayer();
        manager.show(a, c("a"), Duration.ofSeconds(5));
        manager.show(b, c("b"), Duration.ofSeconds(5));

        manager.close();

        assertThat(manager.tracked()).isZero();
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
        manager.show(player, c("a"), Duration.ofSeconds(5));
        manager.close();

        manager.show(player, c("b"), Duration.ofSeconds(5));
        assertThat(manager.tracked()).isEqualTo(1);
        assertThat(scheduler.starts()).isEqualTo(2);
    }

    @Test
    void rejectsNonPositiveDuration() {
        PlayerMock player = server.addPlayer();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> manager.show(player, c("x"), Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
