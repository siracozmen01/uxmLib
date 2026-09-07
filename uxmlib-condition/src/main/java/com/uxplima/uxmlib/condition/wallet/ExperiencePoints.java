package com.uxplima.uxmlib.condition.wallet;

/**
 * The vanilla experience curve: how many points a level costs, and which level a number of points buys.
 *
 * <p>The server offers a level and a fraction of the way into the next one, and no reliable total. Those
 * two numbers are not one currency. A level near the start costs seven points and a level past thirty one
 * costs over a hundred, so a plugin asking for thirty levels and a plugin asking for thirty points are
 * asking for very different amounts. This class is the only place the two meet, and {@link
 * ExperienceWallet.Unit} decides which of them a currency is counted in.
 *
 * <p>The three formulas are the ones the game itself uses. They change at level 16 and at level 31, and
 * that is why a straight multiplication is wrong everywhere except by accident.
 *
 * <p>It is arithmetic and nothing else: it reads no player, touches no Bukkit type and runs on any thread.
 */
public final class ExperiencePoints {

    private ExperiencePoints() {}

    /** The total number of points a player at {@code level} with no progress into the next one has. */
    public static int totalAt(int level) {
        if (level < 0) {
            throw new IllegalArgumentException("level must not be negative, got: " + level);
        }
        if (level <= 16) {
            return level * level + 6 * level;
        }
        if (level <= 31) {
            return (int) Math.round(2.5 * level * level - 40.5 * level + 360.0);
        }
        return (int) Math.round(4.5 * level * level - 162.5 * level + 2220.0);
    }

    /** How many points it takes to go from {@code level} to the next one. */
    public static int toNextFrom(int level) {
        if (level < 0) {
            throw new IllegalArgumentException("level must not be negative, got: " + level);
        }
        if (level <= 15) {
            return 2 * level + 7;
        }
        if (level <= 30) {
            return 5 * level - 38;
        }
        return 9 * level - 158;
    }

    /** The total a player at {@code level} who is {@code progress} of the way to the next one has. */
    public static int totalOf(int level, float progress) {
        return totalAt(level) + Math.round(progress * toNextFrom(level));
    }

    /** The level that {@code total} points buys, and how far into the next one it leaves the player. */
    public static Standing standingOf(int total) {
        if (total < 0) {
            throw new IllegalArgumentException("total must not be negative, got: " + total);
        }
        int level = 0;
        while (totalAt(level + 1) <= total) {
            level++;
        }
        int intoTheLevel = total - totalAt(level);
        return new Standing(level, (float) intoTheLevel / toNextFrom(level));
    }

    /** A level, and the fraction of the way to the next one. */
    public record Standing(int level, float progress) {}
}
