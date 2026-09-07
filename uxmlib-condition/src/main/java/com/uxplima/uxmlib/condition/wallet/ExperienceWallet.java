package com.uxplima.uxmlib.condition.wallet;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

import org.bukkit.entity.Player;

import com.uxplima.uxmlib.condition.Wallet;
import org.jspecify.annotations.Nullable;

/**
 * Experience as money. It needs no economy plugin, so every server has at least this one.
 *
 * <p>It is a different kind of backend from the rest of this package, and the difference is worth
 * stating rather than hiding:
 *
 * <ul>
 *   <li><b>There is no other plugin, so there is no present-guard.</b> {@link BridgedWallet}, {@link
 *       TreasuryWallet} and {@link PlaceholderWallet} each ask the plugin manager before they name a
 *       foreign type, resolve a handle on first use and read a missing API as "absent". This one names
 *       only Bukkit. It has no provider to find, no handle to keep, no absent path and nothing to log. It
 *       is ready on the server it is built on and it stays ready.
 *   <li><b>The balance is the player's own.</b> The others ask a plugin, which may talk to a database, so
 *       {@link Wallet} tells a driver to route them off the main thread. This one reads and writes the
 *       player's own experience bar, so it must run on the thread that owns the player and it must never
 *       be routed off it. See the thread note below.
 *   <li><b>An absent player has no balance.</b> The others answer for an offline player, because the
 *       balance lives in the economy and not in the player. Experience leaves with the player, so a
 *       player who is not online reads zero and pays nothing.
 * </ul>
 *
 * <p><b>Where the work runs.</b> Every read and every write here touches the subject's experience bar, so
 * the caller must already be on the thread that owns the player: the main thread on Paper, and on Folia
 * the region that owns them at that moment. That is the same rule {@code InventoryItemStore} follows for
 * the player's inventory, and it is why the {@code [take-money]} action reports itself sync. A driver
 * that hands this wallet to an async lane is the one defect this class cannot defend itself against.
 *
 * <p><b>Points and levels are two currencies, not two names for one.</b> A level costs seven points near
 * the start and over a hundred past level thirty one, so thirty levels and thirty points are different
 * amounts of the same thing, and no rate converts one into the other. Each pool is counted in one {@link
 * Unit} and never in the other, and {@link ExperiencePoints} holds the curve the game itself uses.
 *
 * <p>Experience is counted in whole units, so an amount with a fraction in it cannot be paid and an
 * amount too large to count in an {@code int} cannot either. Both are refused, and refusing is the only
 * honest answer: rounding up would overcharge the player and rounding down would undercharge the plugin,
 * and choosing between them is a rule of play this library does not make.
 *
 * <p>One instance holds as many pools as it was given, keyed by the currency name a condition writes. The
 * empty name is the default pool, exactly as {@link Wallet} says. A name this wallet was not given reads
 * zero and refuses, which is what an unknown currency does.
 */
public final class ExperienceWallet implements Wallet {

    /**
     * How one pool counts experience.
     *
     * <p>The two are separate constants rather than a flag and a multiplier because there is no
     * multiplier: the curve between them bends at level 16 and again at level 31. Each constant knows how
     * to read its own count off a player and how to leave the player holding a new one, and neither ever
     * borrows the other's arithmetic.
     */
    public enum Unit {

        /**
         * Points, which is the honest count. A player halfway through a level keeps their half, because
         * the whole total is read, the amount comes off it, and the level and the fraction are written
         * back from what is left.
         */
        POINTS {

            @Override
            int heldBy(Player player) {
                return ExperiencePoints.totalOf(player.getLevel(), player.getExp());
            }

            @Override
            void leaveHolding(Player player, int held) {
                ExperiencePoints.Standing standing = ExperiencePoints.standingOf(held);
                player.setLevel(standing.level());
                player.setExp(standing.progress());
            }
        },

        /**
         * Levels, which is the count players read off their own screen. Taking a level leaves the
         * fraction of the way into the level where it stood, which is what the game does when it takes a
         * level away. That fraction is worth fewer points at the lower level, so a player pays a little
         * more than the level's own price. A plugin that wants the exact count prices in {@link #POINTS}.
         */
        LEVELS {

            @Override
            int heldBy(Player player) {
                return player.getLevel();
            }

            @Override
            void leaveHolding(Player player, int held) {
                player.setLevel(held);
            }
        };

        /** What the player holds, counted in this unit. */
        abstract int heldBy(Player player);

        /** Leave the player holding {@code held} of this unit. */
        abstract void leaveHolding(Player player, int held);
    }

    /** The amount that cannot be paid at all, whichever of the two reasons it is. */
    private static final int NOT_WHOLE = -1;

    private final Map<String, Unit> pools;

    public ExperienceWallet(Map<String, Unit> pools) {
        this.pools = Map.copyOf(Objects.requireNonNull(pools, "pools"));
    }

    /** One default pool, counted in points. */
    public static ExperienceWallet ofPoints() {
        return new ExperienceWallet(Map.of("", Unit.POINTS));
    }

    /** One default pool, counted in levels. */
    public static ExperienceWallet ofLevels() {
        return new ExperienceWallet(Map.of("", Unit.LEVELS));
    }

    /** Reads the subject's experience bar, so it runs on the thread that owns them. */
    @Override
    public double balance(@Nullable Player player, String currency) {
        Objects.requireNonNull(currency, "currency");
        Unit unit = pools.get(currency);
        // An offline player carries no experience anyone can reach, so they are nobody here.
        if (unit == null || player == null || !player.isOnline()) {
            return 0;
        }
        return unit.heldBy(player);
    }

    /** Writes the subject's experience bar, so it runs on the thread that owns them. */
    @Override
    public boolean withdraw(@Nullable Player player, String currency, double amount) {
        Objects.requireNonNull(currency, "currency");
        if (amount <= 0) {
            return true;
        }
        Unit unit = pools.get(currency);
        if (unit == null || player == null || !player.isOnline()) {
            return false;
        }
        int wanted = whole(amount);
        if (wanted == NOT_WHOLE) {
            return false;
        }
        // The whole cost is measured before any of it is spent. A player is never left below zero, and a
        // take that cannot be met writes nothing at all.
        int has = unit.heldBy(player);
        if (has < wanted) {
            return false;
        }
        unit.leaveHolding(player, has - wanted);
        return true;
    }

    /** {@code amount} as the whole number experience is counted in, or {@link #NOT_WHOLE}. */
    private static int whole(double amount) {
        if (!Double.isFinite(amount)) {
            return NOT_WHOLE;
        }
        BigDecimal value = BigDecimal.valueOf(amount);
        if (value.stripTrailingZeros().scale() > 0) {
            return NOT_WHOLE;
        }
        try {
            return value.intValueExact();
        } catch (ArithmeticException tooLarge) {
            // Experience is counted in an int, so an amount that does not fit in one cannot be paid. It
            // is refused here rather than thrown out of an action list that is already spending.
            return NOT_WHOLE;
        }
    }
}
