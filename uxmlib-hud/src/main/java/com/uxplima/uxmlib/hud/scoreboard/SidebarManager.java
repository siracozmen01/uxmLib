package com.uxplima.uxmlib.hud.scoreboard;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmlib.scheduler.Scheduler;
import org.jspecify.annotations.Nullable;

/**
 * Owns the per-player {@link Sidebar} instances. {@link #create} builds a fresh sidebar on its own native
 * {@link Scoreboard} (so two players never share board state) and shows it, snapshotting the player's prior
 * scoreboard so {@link #remove} can restore it. A second {@code create} for the same player replaces the
 * first. Per-player state lives on this instance — no static map.
 *
 * <p>Quit cleanup is wired by registering a {@link SidebarListener} (it forwards each quitting player's UUID
 * to {@link #forget}); the manager itself stays free of Bukkit event plumbing.
 */
public final class SidebarManager {

    private final ScoreboardManager scoreboards;
    private final @Nullable Scheduler scheduler;
    private final Map<UUID, Sidebar> active = new ConcurrentHashMap<>();
    private final Map<UUID, Scoreboard> prior = new ConcurrentHashMap<>();

    public SidebarManager(ScoreboardManager scoreboards) {
        this(scoreboards, null);
    }

    /**
     * As {@link #SidebarManager(ScoreboardManager)}, plus a {@link Scheduler} so {@link #showTemporary} can
     * arm a one-shot restore. Inject the scheduler when temporary sidebars are needed.
     */
    public SidebarManager(ScoreboardManager scoreboards, @Nullable Scheduler scheduler) {
        this.scoreboards = Objects.requireNonNull(scoreboards, "scoreboards");
        this.scheduler = scheduler;
    }

    /** Create, show and track a sidebar with {@code title} for {@code player}, replacing any prior one. */
    public Sidebar create(Player player, Component title) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(title, "title");
        UUID id = player.getUniqueId();
        Sidebar existing = active.remove(id);
        if (existing != null) {
            existing.remove();
        } else {
            prior.put(id, player.getScoreboard());
        }
        Sidebar sidebar = new Sidebar(player, scoreboards.getNewScoreboard(), title);
        active.put(id, sidebar);
        sidebar.show();
        return sidebar;
    }

    /**
     * Show a temporary sidebar titled {@code title} with {@code lines} for {@code duration}, then restore
     * whatever the player had before — their prior managed sidebar (rebuilt with its title and lines) or their
     * bare scoreboard. Requires a {@link Scheduler}; the no-arg constructor cannot arm the restore.
     */
    public Sidebar showTemporary(Player player, Component title, List<Component> lines, Duration duration) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(lines, "lines");
        requirePositive(duration);
        Scheduler timer = requireScheduler();
        UUID id = player.getUniqueId();
        Restore restore = snapshot(id);
        Sidebar temporary = create(player, title);
        temporary.lines(lines);
        timer.globalLater(duration, () -> restore(player, id, temporary, restore));
        return temporary;
    }

    /** The sidebar currently shown to {@code player}, or {@code null} if none. */
    public @Nullable Sidebar get(UUID player) {
        Objects.requireNonNull(player, "player");
        return active.get(player);
    }

    /** Remove {@code player}'s sidebar and restore the scoreboard they had before it. */
    public void remove(Player player) {
        Objects.requireNonNull(player, "player");
        UUID id = player.getUniqueId();
        Sidebar sidebar = active.remove(id);
        if (sidebar == null) {
            return;
        }
        sidebar.remove();
        Scoreboard restore = prior.remove(id);
        if (restore != null) {
            player.setScoreboard(restore);
        }
    }

    /** Drop a departed player's sidebar without restoring (they are gone); called by the quit listener. */
    public void forget(UUID player) {
        Objects.requireNonNull(player, "player");
        Sidebar sidebar = active.remove(player);
        if (sidebar != null) {
            sidebar.remove();
        }
        prior.remove(player);
    }

    /** How many players currently have a sidebar. */
    public int count() {
        return active.size();
    }

    /** Capture what the player shows now so a temporary sidebar can put it back when it lapses. */
    private Restore snapshot(UUID id) {
        Sidebar current = active.get(id);
        if (current != null) {
            return new Restore(current.title(), current.currentLines());
        }
        return new Restore(null, null);
    }

    /** Tear down the temporary sidebar and re-establish the captured prior board, if the temp is still shown. */
    private void restore(Player player, UUID id, Sidebar temporary, Restore priorBoard) {
        if (active.get(id) != temporary) {
            return;
        }
        remove(player);
        Component title = priorBoard.title();
        List<Component> lines = priorBoard.lines();
        if (title != null && lines != null) {
            create(player, title).lines(lines);
        }
    }

    private Scheduler requireScheduler() {
        if (scheduler == null) {
            throw new IllegalStateException("a Scheduler is required for temporary sidebars");
        }
        return scheduler;
    }

    private static void requirePositive(Duration duration) {
        Objects.requireNonNull(duration, "duration");
        if (duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException("duration must be positive");
        }
    }

    /** The title and lines of the board to re-show when a temporary sidebar lapses; both null means bare. */
    private record Restore(@Nullable Component title, @Nullable List<Component> lines) {}
}
