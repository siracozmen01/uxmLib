package com.uxplima.uxmlib.advancement;

import java.util.Objects;

import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;

/**
 * Grants and revokes whole advancements for a player against the player's live progress. "Grant" awards
 * every criterion the player has not yet earned (so a multi-criterion advancement completes in one call);
 * "revoke" takes back every criterion the player currently holds. Both operate through the native
 * {@link AdvancementProgress} API — no NMS — and are no-ops past the point where there is nothing left to
 * award or revoke, so they are safe to call repeatedly.
 *
 * <p>These must run on the thread that owns the player (the main/region thread on Folia); the caller is
 * responsible for that, as with any direct player mutation.
 */
public final class Advancements {

    private Advancements() {}

    /**
     * Awards every not-yet-earned criterion of {@code advancement} to {@code player}, completing it. Returns
     * {@code true} if at least one criterion was newly awarded.
     */
    public static boolean grant(Player player, Advancement advancement) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(advancement, "advancement");
        AdvancementProgress progress = player.getAdvancementProgress(advancement);
        boolean changed = false;
        // Snapshot the remaining set: awardCriteria mutates the live progress, so iterating it directly
        // while awarding would be iterating a collection that is changing underneath us.
        for (String criterion : progress.getRemainingCriteria().toArray(new String[0])) {
            changed |= progress.awardCriteria(criterion);
        }
        return changed;
    }

    /**
     * Revokes every currently-held criterion of {@code advancement} from {@code player}. Returns
     * {@code true} if at least one criterion was taken back.
     */
    public static boolean revoke(Player player, Advancement advancement) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(advancement, "advancement");
        AdvancementProgress progress = player.getAdvancementProgress(advancement);
        boolean changed = false;
        for (String criterion : progress.getAwardedCriteria().toArray(new String[0])) {
            changed |= progress.revokeCriteria(criterion);
        }
        return changed;
    }
}
