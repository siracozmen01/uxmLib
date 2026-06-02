package com.uxplima.uxmlib.hook.region;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.TownBlock;
import com.palmergames.bukkit.towny.object.TownyPermission.ActionType;
import com.palmergames.bukkit.towny.utils.PlayerCacheUtil;
import com.uxplima.uxmlib.hook.Hooks;

/**
 * A Towny-backed {@link RegionService}. Build / interact checks go through Towny's per-player permission
 * cache ({@link PlayerCacheUtil#getCachePermission}) for the action at the block, and a point's "region" is
 * the town whose claim covers it ({@link TownyAPI#getTownBlock(Location)}); wilderness is Towny's own
 * unclaimed-land check. The {@code com.palmergames} classes are referenced only inside {@link #find()} past
 * the presence guard and in the query methods, so a server without Towny still loads.
 */
public final class TownyRegionService implements RegionService {

    private static final String PLUGIN = "Towny";

    private TownyRegionService() {}

    /** The Towny region service, or empty when Towny is not installed. */
    public static Optional<RegionService> find() {
        if (!Hooks.isPresent(PLUGIN)) {
            return Optional.empty();
        }
        return Optional.of(new TownyRegionService());
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
        return testPermission(player, location, ActionType.BUILD);
    }

    @Override
    public boolean canInteract(Player player, Location location) {
        return testPermission(player, location, ActionType.SWITCH);
    }

    @Override
    public Set<String> regionsAt(Location location) {
        Objects.requireNonNull(location, "location");
        TownBlock townBlock = TownyAPI.getInstance().getTownBlock(location);
        if (townBlock == null) {
            return Set.of();
        }
        Town town = townBlock.getTownOrNull();
        return town == null ? Set.of() : Set.of(town.getName());
    }

    @Override
    public boolean isWilderness(Location location) {
        Objects.requireNonNull(location, "location");
        return TownyAPI.getInstance().isWilderness(location);
    }

    private boolean testPermission(Player player, Location location, ActionType action) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(location, "location");
        Material material = location.getBlock().getType();
        return PlayerCacheUtil.getCachePermission(player, location, material, action);
    }
}
