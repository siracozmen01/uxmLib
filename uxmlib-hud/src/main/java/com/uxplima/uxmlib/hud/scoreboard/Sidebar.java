package com.uxplima.uxmlib.hud.scoreboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import net.kyori.adventure.text.Component;

/**
 * A per-player sidebar backed by a native Paper {@link Scoreboard}. Each line is a {@link Team} whose entry
 * is a fixed, unique, invisible colour-code key and whose visible text is the team prefix. Rendering diffs
 * the new lines against the shown ones ({@link SidebarDiff}) and only touches the lines that changed, so the
 * client never sees a clear-and-rebuild — no flicker. Up to fifteen lines are supported (the colour-code key
 * space), which is the practical sidebar height anyway.
 *
 * <p>Created and owned by {@link SidebarManager}; one instance per viewing player.
 */
public final class Sidebar {

    /** The maximum number of lines a sidebar can show, bounded by the invisible-key space. */
    public static final int MAX_LINES = 15;

    private final Player player;
    private final Scoreboard board;
    private final Objective objective;
    private final List<Team> teams = new ArrayList<>();
    private final List<Component> shown = new ArrayList<>();
    private Component titleText;

    Sidebar(Player player, Scoreboard board, Component title) {
        this.player = Objects.requireNonNull(player, "player");
        this.board = Objects.requireNonNull(board, "board");
        this.titleText = Objects.requireNonNull(title, "title");
        this.objective = board.registerNewObjective("uxmsb", Criteria.DUMMY, title);
        this.objective.setDisplaySlot(DisplaySlot.SIDEBAR);
    }

    /** Replace the sidebar title. */
    public void title(Component title) {
        Objects.requireNonNull(title, "title");
        this.titleText = title;
        objective.displayName(title);
    }

    /** The current sidebar title, for tests and for capturing a board to restore later. */
    public Component title() {
        return titleText;
    }

    /**
     * Set the full set of lines top-to-bottom, re-sending only what changed since the last render. Passing a
     * shorter list shrinks the board; passing a longer one grows it (up to {@link #MAX_LINES}).
     */
    public void lines(List<Component> lines) {
        Objects.requireNonNull(lines, "lines");
        if (lines.size() > MAX_LINES) {
            throw new IllegalArgumentException("a sidebar holds at most " + MAX_LINES + " lines");
        }
        List<Component> next = List.copyOf(lines);
        SidebarDiff.Plan plan = SidebarDiff.diff(shown, next);
        if (plan.isEmpty()) {
            return;
        }
        applyRemovals(plan.removed());
        applyAdditions(next, plan.added());
        applyChanges(next, plan.changed());
        shown.clear();
        shown.addAll(next);
    }

    /** Show this sidebar to its player, switching their active scoreboard to it. */
    public void show() {
        player.setScoreboard(board);
    }

    /** Tear down the objective and teams; the player keeps whatever scoreboard the manager restores. */
    public void remove() {
        for (Team team : teams) {
            team.unregister();
        }
        teams.clear();
        shown.clear();
        objective.unregister();
    }

    /** The lines currently rendered, for tests and introspection. */
    public List<Component> currentLines() {
        return List.copyOf(shown);
    }

    /** The player this sidebar renders for. */
    public Player player() {
        return player;
    }

    private void applyRemovals(List<Integer> removed) {
        for (int i = removed.size() - 1; i >= 0; i--) {
            int index = removed.get(i);
            Team team = teams.remove(index);
            objective.getScore(entryKey(index)).resetScore();
            team.unregister();
        }
    }

    private void applyAdditions(List<Component> next, List<Integer> added) {
        for (int index : added) {
            String key = entryKey(index);
            Team team = board.registerNewTeam("uxmsb_" + index);
            team.addEntry(key);
            team.prefix(next.get(index));
            teams.add(team);
            objective.getScore(key).setScore(score(index));
        }
    }

    private void applyChanges(List<Component> next, Iterable<Integer> changed) {
        for (int index : changed) {
            teams.get(index).prefix(next.get(index));
        }
    }

    private int score(int index) {
        return MAX_LINES - index;
    }

    // The legacy section marker. Used only as the invisible scoreboard entry identifier below — never for
    // player-facing text, which always goes through Adventure components.
    private static final char SECTION = '§';

    /** A unique, invisible entry key for a line: a single colour code keeps it blank yet distinct. */
    static String entryKey(int index) {
        return String.valueOf(SECTION) + Integer.toHexString(index) + SECTION + "r";
    }
}
