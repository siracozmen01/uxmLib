package com.uxplima.uxmlib.hook.region;

import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import com.uxplima.uxmlib.hook.Hooks;

/**
 * A WorldGuard-backed {@link RegionService}. It queries WorldGuard's region container directly: a Bukkit
 * {@link Location} is adapted to a WorldEdit location, the player wrapped to a {@code LocalPlayer}, and the
 * BUILD / INTERACT state flags are tested through a {@link RegionQuery}. The {@code com.sk89q} classes are
 * referenced only inside {@link #find()} past the presence guard and in the query methods, so a server
 * without WorldGuard still loads (the JVM resolves these classes lazily, on first use).
 *
 * <p>A {@link Location} with a null world is legal in Bukkit but cannot be adapted to WorldEdit; every query
 * short-circuits such a point to a safe default (build/interact permitted, no regions, wilderness) instead of
 * letting {@code BukkitAdapter.adapt} throw deep inside WorldEdit.
 */
public final class WorldGuardRegionService implements RegionService {

    private static final String PLUGIN = "WorldGuard";

    // Package-private (not public) so the present-guard in find() stays the only public entry point, while a
    // same-package test can still exercise the null-world short-circuits that return before any WorldGuard call.
    WorldGuardRegionService() {}

    /** The WorldGuard region service, or empty when WorldGuard is not installed. */
    public static Optional<RegionService> find() {
        if (!Hooks.isPresent(PLUGIN)) {
            return Optional.empty();
        }
        return Optional.of(new WorldGuardRegionService());
    }

    @Override
    public String pluginName() {
        return PLUGIN;
    }

    @Override
    public boolean isAvailable() {
        return Hooks.isPresent(PLUGIN);
    }

    @Override
    public boolean canBuild(Player player, Location location) {
        return testFlag(player, location, Flags.BUILD);
    }

    @Override
    public boolean canInteract(Player player, Location location) {
        return testFlag(player, location, Flags.INTERACT);
    }

    @Override
    public Set<String> regionsAt(Location location) {
        Objects.requireNonNull(location, "location");
        if (location.getWorld() == null) {
            return Set.of();
        }
        Set<String> ids = new HashSet<>();
        for (ProtectedRegion region : applicableRegions(location).getRegions()) {
            ids.add(region.getId());
        }
        return ids;
    }

    @Override
    public boolean isWilderness(Location location) {
        Objects.requireNonNull(location, "location");
        // A world-less point belongs to no claim; treat it as wilderness rather than NPE inside WorldEdit.
        return location.getWorld() == null || applicableRegions(location).size() == 0;
    }

    private boolean testFlag(Player player, Location location, com.sk89q.worldguard.protection.flags.StateFlag flag) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(location, "location");
        // A Location with a null world is legal in Bukkit but makes BukkitAdapter.adapt NPE deep inside
        // WorldEdit; there is no region to consult, so fall back to permitting the action rather than throwing.
        if (location.getWorld() == null) {
            return true;
        }
        LocalPlayer wrapped = WorldGuardPlugin.inst().wrapPlayer(player);
        return query().testState(BukkitAdapter.adapt(location), wrapped, flag);
    }

    private ApplicableRegionSet applicableRegions(Location location) {
        return query().getApplicableRegions(BukkitAdapter.adapt(location));
    }

    private static RegionQuery query() {
        return WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
    }
}
