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
 */
public final class WorldGuardRegionService implements RegionService {

    private static final String PLUGIN = "WorldGuard";

    private WorldGuardRegionService() {}

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
        Set<String> ids = new HashSet<>();
        for (ProtectedRegion region : applicableRegions(location).getRegions()) {
            ids.add(region.getId());
        }
        return ids;
    }

    @Override
    public boolean isWilderness(Location location) {
        Objects.requireNonNull(location, "location");
        return applicableRegions(location).size() == 0;
    }

    private boolean testFlag(Player player, Location location, com.sk89q.worldguard.protection.flags.StateFlag flag) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(location, "location");
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
