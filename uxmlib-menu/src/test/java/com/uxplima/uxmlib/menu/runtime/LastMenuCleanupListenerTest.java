package com.uxplima.uxmlib.menu.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;

import net.kyori.adventure.text.Component;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * The one hole in a map that is otherwise self-bounding. While a player is online the engine forgets one open when it
 * records the next, so the map holds at most one entry per online player; a player who logs off and never comes back
 * is the single case that leaves an entry behind forever. This listener is that case, and the test that matters is
 * that quitting clears the quitter and only the quitter.
 */
class LastMenuCleanupListenerTest {

    private LastMenu lastMenu;

    private LastMenuCleanupListener listener;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        lastMenu = new LastMenu();
        listener = new LastMenuCleanupListener(lastMenu);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private PlayerQuitEvent quit(Player player) {
        return new PlayerQuitEvent(player, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED);
    }

    @Test
    void quittingDropsTheQuittersRememberedMenu() {
        Player player = MockBukkit.getMock().addPlayer();
        lastMenu.record(player.getUniqueId(), new LastMenu.LastOpen("shop", 0, Map.of()));
        assertThat(lastMenu.get(player.getUniqueId()))
                .map(LastMenu.LastOpen::menuId)
                .contains("shop");

        listener.onQuit(quit(player));

        assertThat(lastMenu.get(player.getUniqueId())).isEmpty();
    }

    @Test
    void oneQuitLeavesEveryOtherPlayersMenuAlone() {
        Player leaving = MockBukkit.getMock().addPlayer();
        Player staying = MockBukkit.getMock().addPlayer();
        lastMenu.record(leaving.getUniqueId(), new LastMenu.LastOpen("shop", 0, Map.of()));
        lastMenu.record(staying.getUniqueId(), new LastMenu.LastOpen("warps", 2, Map.of()));

        listener.onQuit(quit(leaving));

        assertThat(lastMenu.get(leaving.getUniqueId())).isEmpty();
        assertThat(lastMenu.get(staying.getUniqueId()))
                .map(LastMenu.LastOpen::menuId)
                .contains("warps");
    }

    @Test
    void aQuitByAPlayerWhoOpenedNoMenuIsNotAFailure() {
        Player player = MockBukkit.getMock().addPlayer();

        listener.onQuit(quit(player));

        assertThat(lastMenu.get(player.getUniqueId())).isEmpty();
    }

    @Test
    void quittingTwiceClearsOnceAndIsNoWorseForIt() {
        Player player = MockBukkit.getMock().addPlayer();
        lastMenu.record(player.getUniqueId(), new LastMenu.LastOpen("shop", 0, Map.of()));

        listener.onQuit(quit(player));
        listener.onQuit(quit(player));

        assertThat(lastMenu.get(player.getUniqueId())).isEmpty();
    }

    @Test
    void aPlayerWhoNeverQuitsKeepsWhatWasRecordedForThem() {
        UUID absent = UUID.randomUUID();
        lastMenu.record(absent, new LastMenu.LastOpen("shop", 0, Map.of()));

        listener.onQuit(quit(MockBukkit.getMock().addPlayer()));

        assertThat(lastMenu.get(absent)).map(LastMenu.LastOpen::menuId).contains("shop");
    }
}
