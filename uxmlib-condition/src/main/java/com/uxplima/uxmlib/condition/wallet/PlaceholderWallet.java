package com.uxplima.uxmlib.condition.wallet;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.entity.Player;

import com.uxplima.uxmlib.condition.Wallet;
import org.jspecify.annotations.Nullable;

/**
 * A balance read through a placeholder and taken by a console line.
 *
 * <p>Some economy plugins publish no API at all, and some publish one that changes every release. Nearly
 * all of them publish a placeholder for the balance and a command for a take, because that is what an
 * operator uses every day. This wallet is that pair.
 *
 * <p>What it costs is written here, because an operator has to know it. A command answers nothing, so this
 * cannot be told that a take failed. It therefore reads the balance first and refuses a take the player
 * cannot afford, and after that it trusts the line it sent. A plugin that refuses the command quietly is a
 * plugin this wallet cannot use, and {@link BridgedWallet} is there for it.
 *
 * <p>A balance that cannot be read as a number is read as zero. Zero is the safe way round: it refuses a
 * take this cannot prove the player can pay, and it never invents money.
 *
 * <p>One instance holds as many pools as it was given, keyed by the currency name a condition writes.
 * The empty name is the default pool, exactly as {@link Wallet} says. A name this wallet was not given
 * reads zero and refuses, which is what an unknown currency does.
 */
public final class PlaceholderWallet implements Wallet {

    /**
     * One balance behind a placeholder, and the line that takes from it.
     *
     * @param placeholder what is asked for the balance, such as {@code %tokens_balance%}
     * @param take the line the console is sent to take money away. {@code {player}}, {@code {uuid}} and
     *     {@code {amount}} are written into it
     * @param thousands what the plugin puts between the groups of three in its answer, removed before the
     *     number is read. An empty one means the answer carries none
     */
    public record Pool(String placeholder, String take, String thousands) {

        public Pool {
            Objects.requireNonNull(thousands, "thousands");
            if (placeholder == null || placeholder.isBlank()) {
                throw new IllegalArgumentException("a placeholder pool names no placeholder");
            }
            if (take == null || take.isBlank()) {
                throw new IllegalArgumentException("a placeholder pool names no take command");
            }
        }

        /** A pool whose plugin answers a plain number, with nothing between the groups of three. */
        public static Pool of(String placeholder, String take) {
            return new Pool(placeholder, take, "");
        }
    }

    /** What a placeholder answers for one player. Absent means nobody answered it. */
    @FunctionalInterface
    public interface Reader {
        Optional<String> read(Player player, String placeholder);
    }

    /** How one line reaches the server. True means the server took the line. */
    @FunctionalInterface
    public interface Console {
        boolean run(String line);
    }

    private final Map<String, Pool> pools;
    private final Reader reader;
    private final Console console;

    public PlaceholderWallet(Map<String, Pool> pools, Reader reader, Console console) {
        this.pools = Map.copyOf(Objects.requireNonNull(pools, "pools"));
        this.reader = Objects.requireNonNull(reader, "reader");
        this.console = Objects.requireNonNull(console, "console");
    }

    /** A wallet over the server's own PlaceholderAPI and console. */
    public static PlaceholderWallet ofServer(Map<String, Pool> pools) {
        return new PlaceholderWallet(pools, new ServerPlaceholders(), ConsoleCommands.ofServer());
    }

    /** A wallet over a single default pool, which is what a plugin with one currency wires. */
    public static PlaceholderWallet ofServer(Pool pool) {
        return ofServer(Map.of("", Objects.requireNonNull(pool, "pool")));
    }

    @Override
    public double balance(@Nullable Player player, String currency) {
        Objects.requireNonNull(currency, "currency");
        Pool pool = pools.get(currency);
        if (player == null || pool == null) {
            return 0;
        }
        return reader.read(player, pool.placeholder())
                .flatMap(answered -> number(answered, pool.thousands()))
                .map(BigDecimal::doubleValue)
                .orElse(0d);
    }

    @Override
    public boolean withdraw(@Nullable Player player, String currency, double amount) {
        Objects.requireNonNull(currency, "currency");
        if (amount <= 0) {
            return true;
        }
        Pool pool = pools.get(currency);
        if (player == null || pool == null) {
            return false;
        }
        // The whole cost is read before any of it is taken. A command cannot refuse, so this is the only
        // place the refusal can be made, and it is made before a line is sent.
        if (balance(player, currency) < amount) {
            return false;
        }
        return console.run(line(pool.take(), player, amount));
    }

    /**
     * The line to send, with the player and the amount written into it.
     *
     * <p>The amount goes in as plain digits with no grouping and no currency name, because what a number
     * looks like to a player is the plugin's business and what a command wants is a number.
     */
    private static String line(String template, Player player, double amount) {
        String name = player.getName();
        return template.replace("{player}", name)
                .replace("{uuid}", player.getUniqueId().toString())
                .replace(
                        "{amount}",
                        BigDecimal.valueOf(amount).stripTrailingZeros().toPlainString());
    }

    /** The number in the text a placeholder answered with, absent when there is none. */
    private static Optional<BigDecimal> number(String answered, String thousands) {
        String bare = answered.strip();
        if (!thousands.isEmpty()) {
            bare = bare.replace(thousands, "");
        }
        try {
            return Optional.of(new BigDecimal(bare));
        } catch (NumberFormatException notANumber) {
            return Optional.empty();
        }
    }
}
