package com.uxplima.uxmlib.advancement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;

import org.junit.jupiter.api.Test;

/**
 * Tests the grant/revoke logic against a mocked {@link AdvancementProgress}. The progress API is pure
 * Bukkit, so a Mockito mock fully captures the award-remaining / revoke-awarded loops without a server (which
 * MockBukkit cannot stand up for advancements anyway).
 */
class AdvancementsTest {

    @Test
    void grantAwardsEveryRemainingCriterion() {
        Advancement advancement = mock(Advancement.class);
        AdvancementProgress progress = mock(AdvancementProgress.class);
        Player player = mock(Player.class);
        when(player.getAdvancementProgress(advancement)).thenReturn(progress);
        when(progress.getRemainingCriteria()).thenReturn(List.of("a", "b"));
        when(progress.awardCriteria("a")).thenReturn(true);
        when(progress.awardCriteria("b")).thenReturn(true);

        assertThat(Advancements.grant(player, advancement)).isTrue();
        verify(progress).awardCriteria("a");
        verify(progress).awardCriteria("b");
    }

    @Test
    void grantReturnsFalseWhenNothingRemains() {
        Advancement advancement = mock(Advancement.class);
        AdvancementProgress progress = mock(AdvancementProgress.class);
        Player player = mock(Player.class);
        when(player.getAdvancementProgress(advancement)).thenReturn(progress);
        when(progress.getRemainingCriteria()).thenReturn(List.of());

        assertThat(Advancements.grant(player, advancement)).isFalse();
        verify(progress, never()).awardCriteria(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void revokeTakesBackEveryAwardedCriterion() {
        Advancement advancement = mock(Advancement.class);
        AdvancementProgress progress = mock(AdvancementProgress.class);
        Player player = mock(Player.class);
        when(player.getAdvancementProgress(advancement)).thenReturn(progress);
        when(progress.getAwardedCriteria()).thenReturn(List.of("x"));
        when(progress.revokeCriteria("x")).thenReturn(true);

        assertThat(Advancements.revoke(player, advancement)).isTrue();
        verify(progress).revokeCriteria("x");
    }
}
