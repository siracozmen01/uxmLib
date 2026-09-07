package com.uxplima.uxmlib.claim;

import java.lang.reflect.Method;
import java.util.Objects;

import org.bukkit.Location;
import org.bukkit.Server;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The reflective way into WorldGuard's region container, for the one caller in this package that needs it.
 *
 * <p>WorldGuard is named only by string class name ({@code com.sk89q.worldguard.WorldGuard},
 * {@code com.sk89q.worldedit.bukkit.BukkitAdapter}), so no field or method signature here carries a
 * {@code com.sk89q} type and none of its classes load on a server without it. The caller guards on the plugin
 * being present before invoking anything below.
 *
 * <p>Every method rethrows its reflective failure rather than swallowing it, so the caller keeps its own
 * fail-open policy. {@link WorldGuardClaimProvider} degrades to "unclaimed" and warns once.
 */
@NullMarked
final class WorldGuardReflection {

    /** The Bukkit plugin name, which is the whole of the present-guard. */
    static final String PLUGIN = "WorldGuard";

    private WorldGuardReflection() {}

    /**
     * Whether WorldGuard is installed <em>and</em> enabled. A query against a plugin that failed its own
     * startup would throw rather than answer, so "installed" alone is not enough.
     */
    static boolean isEnabled(Server server) {
        Objects.requireNonNull(server, "server");
        return server.getPluginManager().isPluginEnabled(PLUGIN);
    }

    /** {@code WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery()}. */
    static Object createQuery() throws ReflectiveOperationException {
        Object instance = Class.forName("com.sk89q.worldguard.WorldGuard")
                .getMethod("getInstance")
                .invoke(null);
        Object platform = instance.getClass().getMethod("getPlatform").invoke(instance);
        Object container = platform.getClass().getMethod("getRegionContainer").invoke(platform);
        return container.getClass().getMethod("createQuery").invoke(container);
    }

    /** Adapt a Bukkit {@link Location} to the WorldEdit location the region query expects. */
    static Object adapt(Location location) throws ReflectiveOperationException {
        return Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter")
                .getMethod("adapt", Location.class)
                .invoke(null, location);
    }

    /**
     * {@code RegionQuery#getApplicableRegions(Location)}, matched by its single WorldEdit-location argument
     * rather than by a signature, so a WorldEdit release that moves the parameter type still binds.
     */
    static @Nullable Object applicableRegions(Object query, Object weLocation) throws ReflectiveOperationException {
        for (Method candidate : query.getClass().getMethods()) {
            if (candidate.getName().equals("getApplicableRegions")
                    && candidate.getParameterCount() == 1
                    && candidate.getParameterTypes()[0].isInstance(weLocation)) {
                return candidate.invoke(query, weLocation);
            }
        }
        throw new NoSuchMethodException("getApplicableRegions");
    }
}
