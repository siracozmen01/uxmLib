package com.uxplima.uxmlib.packet;

import java.util.Objects;
import java.util.Optional;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.TeamColor;

/**
 * The server internals uxmLib reaches for that Bukkit does not expose.
 *
 * <p>These were a seam once. The library was published as one jar for two server lines, so each of these
 * calls was a method on an interface with one implementation per line, chosen at load time. The library now
 * serves one line, and a seam with one implementation is not a seam: it is indirection with a build file
 * behind it. The calls are written out here instead, against the server the library is compiled for.
 *
 * <p>What made the seam worth its cost is still worth knowing, because it comes back the day a second line
 * does: every method here is a place the build can no longer check for us. Anything that can be written
 * against a shared API, a registry lookup rather than a per-line constant, belongs in ordinary code and not
 * in this class.
 */
public final class ServerInternals {

    private ServerInternals() {}

    /**
     * The next free entity id from the counter the server itself spawns real entities from, so an id handed
     * out here can never collide with one.
     *
     * @param level the level the id is meant for, which lets the server skip ids that world already has in
     *     play
     */
    public static int nextEntityId(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        return level.getNextEntityId();
    }

    /**
     * Paint {@code team} in the vanilla colour named {@code vanillaColorName}, which is the name of a
     * {@link TeamColor} constant. The names line up one for one with the {@code ChatFormatting} colours.
     *
     * @throws IllegalArgumentException if there is no colour by that name
     */
    public static void applyTeamColor(PlayerTeam team, String vanillaColorName) {
        Objects.requireNonNull(team, "team");
        Objects.requireNonNull(vanillaColorName, "vanillaColorName");
        // A team may have no colour at all, which is why the setter takes an Optional.
        team.setColor(Optional.of(TeamColor.valueOf(vanillaColorName)));
    }
}
