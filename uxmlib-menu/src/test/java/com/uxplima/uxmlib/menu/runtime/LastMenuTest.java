package com.uxplima.uxmlib.menu.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.uxplima.uxmlib.menu.runtime.LastMenu.LastOpen;
import org.junit.jupiter.api.Test;

/**
 * The per-player menu history behind {@code /menu last} and a {@code back} button. Only a disk-loaded custom menu is
 * ever recorded here, so everything this class remembers can be reopened from its id alone.
 */
class LastMenuTest {

    private final LastMenu history = new LastMenu();

    private final UUID player = UUID.randomUUID();

    private static LastOpen open(String menuId, int page) {
        return new LastOpen(menuId, page, Map.of());
    }

    @Test
    void aPlayerWhoHasOpenedNothingHasNoCurrentMenu() {
        assertThat(history.get(player)).isEmpty();
        assertThat(history.back(player)).isEmpty();
    }

    @Test
    void theCurrentMenuIsTheOneMostRecentlyRecorded() {
        history.record(player, open("warps", 0));
        history.record(player, open("kits", 0));

        assertThat(history.get(player)).contains(open("kits", 0));
    }

    @Test
    void backStepsToThePreviousMenuAndLeavesItAsTheCurrentOne() {
        history.record(player, open("warps", 0));
        history.record(player, open("kits", 0));

        assertThat(history.back(player)).contains(open("warps", 0));
        assertThat(history.get(player))
                .as("the previous open becomes the current one, so a further back steps further")
                .contains(open("warps", 0));
    }

    @Test
    void backFromTheOnlyRememberedMenuExhaustsTheHistory() {
        history.record(player, open("warps", 0));

        assertThat(history.back(player)).isEmpty();
        assertThat(history.get(player))
                .as("an emptied history drops its entry rather than keeping a player with an empty stack")
                .isEmpty();
    }

    @Test
    void reopeningTheWindowAlreadyOnTopIsNotStacked() {
        history.record(player, open("warps", 0));
        history.record(player, open("warps", 0));

        assertThat(history.back(player))
                .as("a refresh must not consume a back step")
                .isEmpty();
    }

    @Test
    void aDifferentPageOfTheSameMenuIsADifferentOpen() {
        history.record(player, open("warps", 0));
        history.record(player, open("warps", 1));

        assertThat(history.back(player)).contains(open("warps", 0));
    }

    @Test
    void theSameMenuOpenedWithDifferentArgumentsIsADifferentOpen() {
        history.record(player, new LastOpen("shop", 0, Map.of("category", "tools")));
        history.record(player, new LastOpen("shop", 0, Map.of("category", "food")));

        assertThat(history.back(player)).contains(new LastOpen("shop", 0, Map.of("category", "tools")));
    }

    @Test
    void aRememberedOpenCopiesItsArgumentsSoTheCallersMapCannotRewriteIt() {
        Map<String, String> arguments = new HashMap<>();
        arguments.put("category", "tools");
        history.record(player, new LastOpen("shop", 0, arguments));

        arguments.put("category", "food");

        assertThat(history.get(player)).contains(new LastOpen("shop", 0, Map.of("category", "tools")));
    }

    @Test
    void aPlayerWhoWandersPastTheDepthLimitLosesTheOldestOpenRatherThanGrowingWithoutBound() {
        for (int i = 0; i < 40; i++) {
            history.record(player, open("menu-" + i, 0));
        }

        for (int i = 39; i > 8; i--) {
            assertThat(history.get(player)).contains(open("menu-" + i, 0));
            history.back(player);
        }

        assertThat(history.get(player))
                .as("thirty-two remain, so the deepest is menu-8 and everything older was evicted")
                .contains(open("menu-8", 0));
        assertThat(history.back(player)).isEmpty();
    }

    @Test
    void oneQuitForgetsThatPlayerAndNobodyElse() {
        UUID other = UUID.randomUUID();
        history.record(player, open("warps", 0));
        history.record(other, open("kits", 0));

        history.clear(player);

        assertThat(history.get(player)).isEmpty();
        assertThat(history.get(other)).contains(open("kits", 0));
    }
}
