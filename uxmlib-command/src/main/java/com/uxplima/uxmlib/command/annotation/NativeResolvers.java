package com.uxplima.uxmlib.command.annotation;

import java.util.Collection;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.World;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.FinePositionResolver;
import io.papermc.paper.command.brigadier.argument.resolvers.PlayerProfileListResolver;
import io.papermc.paper.math.FinePosition;
import io.papermc.paper.registry.RegistryKey;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.uxplima.uxmlib.command.annotation.annotations.Arg;

/**
 * Resolvers for the Bukkit types beyond player/world/material that 1.21 exposes natively, so they cost a
 * thin wrapper over a Paper {@link ArgumentTypes} rather than any NMS: a {@link Location} from
 * {@code finePosition} (resolved against the sender's world), an {@link OfflinePlayer} from a player profile,
 * and a {@link Sound} from the sound-event registry. Installed alongside the primitives by
 * {@link BuiltinResolvers}. Split out of {@code BuiltinResolvers} so each file stays within its size budget.
 */
final class NativeResolvers {

    private NativeResolvers() {}

    static void installInto(ParamResolvers r) {
        r.register(Location.class, locationResolver());
        r.register(OfflinePlayer.class, offlinePlayerResolver());
        r.register(Sound.class, soundResolver());
    }

    /** Resolve a {@link Location} from {@code x y z}, anchored to the world the command source is in. */
    private static ParamResolver<Location> locationResolver() {
        return new ParamResolver<>() {
            @Override
            public ArgumentType<?> argumentType(Arg arg) {
                return ArgumentTypes.finePosition();
            }

            @Override
            public boolean nativeArgument() {
                return true;
            }

            @Override
            public Location resolve(CommandContext<CommandSourceStack> context, String name) {
                FinePositionResolver positions = context.getArgument(name, FinePositionResolver.class);
                CommandSourceStack source = context.getSource();
                try {
                    FinePosition position = positions.resolve(source);
                    World world = source.getLocation().getWorld();
                    return position.toLocation(world);
                } catch (CommandSyntaxException failure) {
                    throw new IllegalArgumentException(failure.getMessage(), failure);
                }
            }
        };
    }

    /**
     * Resolve an {@link OfflinePlayer} from a player profile: a single name or UUID the client completes
     * against known profiles. The profile's id is looked up to an offline player off the cache the server
     * already holds, so this never blocks on a network call.
     */
    private static ParamResolver<OfflinePlayer> offlinePlayerResolver() {
        return new ParamResolver<>() {
            @Override
            public ArgumentType<?> argumentType(Arg arg) {
                return ArgumentTypes.playerProfiles();
            }

            @Override
            public boolean nativeArgument() {
                return true;
            }

            @Override
            public OfflinePlayer resolve(CommandContext<CommandSourceStack> context, String name) {
                PlayerProfileListResolver profiles = context.getArgument(name, PlayerProfileListResolver.class);
                try {
                    Collection<PlayerProfile> resolved = profiles.resolve(context.getSource());
                    return firstOfflinePlayer(resolved, name);
                } catch (CommandSyntaxException failure) {
                    throw new IllegalArgumentException(failure.getMessage(), failure);
                }
            }
        };
    }

    private static OfflinePlayer firstOfflinePlayer(Collection<PlayerProfile> profiles, String name) {
        for (PlayerProfile profile : profiles) {
            UUID id = profile.getId();
            if (id != null) {
                return Bukkit.getOfflinePlayer(id);
            }
        }
        throw new IllegalArgumentException("no such player: " + name);
    }

    /** Resolve a {@link Sound} from the sound-event registry, with the key completion the client knows. */
    private static ParamResolver<Sound> soundResolver() {
        return new ParamResolver<>() {
            @Override
            public ArgumentType<?> argumentType(Arg arg) {
                return ArgumentTypes.resource(RegistryKey.SOUND_EVENT);
            }

            @Override
            public boolean nativeArgument() {
                return true;
            }

            @Override
            public Sound resolve(CommandContext<CommandSourceStack> context, String name) {
                return context.getArgument(name, Sound.class);
            }
        };
    }
}
