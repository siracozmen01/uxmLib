package com.uxplima.uxmlib.command.annotation;

import java.util.List;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.uxplima.uxmlib.command.annotation.annotations.Arg;

/**
 * The resolvers shipped with the DSL: the primitives plus common Bukkit types resolved through native
 * Paper argument types, so the client validates them and tab-completes for free. Installed into a
 * {@link ParamResolvers} by {@link ParamResolvers#withDefaults()}. Enums are handled generically by
 * {@link #enumResolver(Class)}, which lists their constants as suggestions.
 */
final class BuiltinResolvers {

    private BuiltinResolvers() {}

    static void installInto(ParamResolvers r) {
        r.register(String.class, strings());
        r.register(int.class, ints());
        r.register(Integer.class, ints());
        r.register(long.class, simple(LongArgumentType::longArg, (c, n) -> LongArgumentType.getLong(c, n)));
        r.register(Long.class, simple(LongArgumentType::longArg, (c, n) -> LongArgumentType.getLong(c, n)));
        r.register(double.class, doubles());
        r.register(Double.class, doubles());
        r.register(float.class, simple(FloatArgumentType::floatArg, (c, n) -> FloatArgumentType.getFloat(c, n)));
        r.register(Float.class, simple(FloatArgumentType::floatArg, (c, n) -> FloatArgumentType.getFloat(c, n)));
        r.register(boolean.class, bools());
        r.register(Boolean.class, bools());
        r.register(UUID.class, simple(ArgumentTypes::uuid, (c, n) -> c.getArgument(n, UUID.class)));
        r.register(World.class, simple(ArgumentTypes::world, (c, n) -> c.getArgument(n, World.class)));
        r.register(Player.class, playerResolver());
        r.register(Material.class, materialResolver());
    }

    private static ParamResolver<Integer> ints() {
        return new ParamResolver<>() {
            @Override
            public ArgumentType<?> argumentType(Arg arg) {
                int min = arg.min() == Double.NEGATIVE_INFINITY ? Integer.MIN_VALUE : (int) arg.min();
                int max = arg.max() == Double.POSITIVE_INFINITY ? Integer.MAX_VALUE : (int) arg.max();
                return IntegerArgumentType.integer(min, max);
            }

            @Override
            public Integer resolve(CommandContext<CommandSourceStack> context, String name) {
                return IntegerArgumentType.getInteger(context, name);
            }
        };
    }

    private static ParamResolver<Double> doubles() {
        return new ParamResolver<>() {
            @Override
            public ArgumentType<?> argumentType(Arg arg) {
                return DoubleArgumentType.doubleArg(arg.min(), arg.max());
            }

            @Override
            public Double resolve(CommandContext<CommandSourceStack> context, String name) {
                return DoubleArgumentType.getDouble(context, name);
            }
        };
    }

    private static ParamResolver<Boolean> bools() {
        return simple(BoolArgumentType::bool, (c, n) -> BoolArgumentType.getBool(c, n));
    }

    /** A String resolver that consumes the whole rest of the input when {@code @Arg(greedy = true)}. */
    private static ParamResolver<String> strings() {
        return new ParamResolver<>() {
            @Override
            public ArgumentType<?> argumentType(Arg arg) {
                return arg.greedy() ? StringArgumentType.greedyString() : StringArgumentType.word();
            }

            @Override
            public String resolve(CommandContext<CommandSourceStack> context, String name) {
                return StringArgumentType.getString(context, name);
            }
        };
    }

    /** Resolve one online player from a selector or name (native @p/@a validation and completion). */
    private static ParamResolver<Player> playerResolver() {
        return new ParamResolver<>() {
            @Override
            public ArgumentType<?> argumentType(Arg arg) {
                return ArgumentTypes.player();
            }

            @Override
            public Player resolve(CommandContext<CommandSourceStack> context, String name) {
                PlayerSelectorArgumentResolver selector =
                        context.getArgument(name, PlayerSelectorArgumentResolver.class);
                try {
                    List<Player> players = selector.resolve(context.getSource());
                    return players.get(0);
                } catch (CommandSyntaxException failure) {
                    throw new IllegalArgumentException(failure.getMessage(), failure);
                }
            }
        };
    }

    /** Resolve a Material from the item registry, with the namespaced-key completion the client knows. */
    private static ParamResolver<Material> materialResolver() {
        return new ParamResolver<>() {
            @Override
            public ArgumentType<?> argumentType(Arg arg) {
                return ArgumentTypes.resource(io.papermc.paper.registry.RegistryKey.ITEM);
            }

            @Override
            @SuppressWarnings("deprecation") // asMaterial bridges the new ItemType arg back to the Material API
            public Material resolve(CommandContext<CommandSourceStack> context, String name) {
                org.bukkit.inventory.ItemType itemType = context.getArgument(name, org.bukkit.inventory.ItemType.class);
                Material material = itemType.asMaterial();
                if (material == null) {
                    throw new IllegalArgumentException("not a placeable/obtainable material: " + name);
                }
                return material;
            }
        };
    }

    /** A resolver for any enum: a word argument validated and completed against the constant names. */
    @SuppressWarnings("unchecked") // the caller only passes Class<? extends Enum>; cast is guarded by isEnum()
    static ParamResolver<?> enumResolver(Class<?> enumType) {
        Enum<?>[] constants = (Enum<?>[]) enumType.getEnumConstants();
        return new EnumResolver(constants);
    }

    /**
     * Functional shortcut for a resolver. The argument type comes from a supplier so the native Paper
     * type (e.g. {@code ArgumentTypes.uuid()}) is created lazily when a command is built, not eagerly when
     * the registry is populated — registering defaults must not touch the server registries.
     */
    private static <T> ParamResolver<T> simple(java.util.function.Supplier<ArgumentType<?>> type, Reader<T> reader) {
        return new ParamResolver<>() {
            @Override
            public ArgumentType<?> argumentType(Arg arg) {
                return type.get();
            }

            @Override
            public T resolve(CommandContext<CommandSourceStack> context, String name) {
                return reader.read(context, name);
            }
        };
    }

    @FunctionalInterface
    private interface Reader<T> {
        T read(CommandContext<CommandSourceStack> context, String name);
    }
}
