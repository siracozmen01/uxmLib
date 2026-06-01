package com.uxplima.uxmlib.command.annotation;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.context.CommandContext;
import com.uxplima.uxmlib.command.Cmd;
import com.uxplima.uxmlib.command.Sender;
import com.uxplima.uxmlib.command.annotation.annotations.PlayerOnly;

/**
 * Builds the Brigadier {@code executes} body for a {@code @}{@link
 * com.uxplima.uxmlib.command.annotation.annotations.Subcommand} method: run the pre-execute
 * {@link CommandCondition}s, bind the arguments through {@link ArgBinder}, invoke the handler, and route
 * every failure to a clean red reply rather than a leaked Brigadier stacktrace. Split out of
 * {@code AnnotatedCommands} so that class stays within its size budget and keeps to tree building.
 */
final class CommandExecutors {

    private CommandExecutors() {}

    /**
     * The executor for {@code method} with its pre-resolved {@code args} and the active {@code resolvers}.
     * {@code commandPath} is the full literal path of this branch (root name plus subcommand spine); it
     * keys the per-branch cooldown so different branches never share a window.
     */
    static com.mojang.brigadier.Command<CommandSourceStack> executorFor(
            Object handler,
            Method method,
            List<ArgBinder.ParamArg> args,
            ParamResolvers resolvers,
            String commandPath) {
        List<CommandCondition> conditions = conditionsFor(method, resolvers, commandPath);
        return ctx -> {
            try {
                checkConditions(conditions, ctx);
            } catch (CommandCondition.CommandConditionException vetoed) {
                replyRed(ctx, vetoed.reason());
                return 0;
            }
            Object[] callArgs;
            try {
                callArgs = ArgBinder.bind(ctx, method, args, resolvers);
            } catch (IllegalArgumentException badArgument) {
                // A resolver or validator rejected the input (an offline player, an out-of-range value). Reply
                // with its message rather than letting it surface as a server error.
                replyRed(ctx, badArgument.getMessage() == null ? "Invalid argument." : badArgument.getMessage());
                return 0;
            }
            return invoke(handler, method, callArgs, ctx);
        };
    }

    private static int invoke(
            Object handler, Method method, Object[] callArgs, CommandContext<CommandSourceStack> ctx) {
        try {
            method.invoke(handler, callArgs);
            return Cmd.OK;
        } catch (InvocationTargetException thrownByHandler) {
            // The handler itself failed. Don't let Brigadier dump a red stacktrace in the player's chat: log
            // the real cause server-side and reply with a clean, generic message.
            Throwable cause = thrownByHandler.getCause();
            ctx.getSource()
                    .getSender()
                    .getServer()
                    .getLogger()
                    .log(
                            Level.SEVERE,
                            "Command '" + method.getName() + "' threw an exception",
                            cause != null ? cause : thrownByHandler);
            replyRed(ctx, "An internal error occurred while running this command.");
            return 0;
        } catch (IllegalAccessException unreachable) {
            // setAccessible(true) ran at registration, so this cannot happen for a registered handler.
            throw new CommandParseException("could not invoke " + method.getName(), unreachable);
        }
    }

    /** Send {@code message} to the dispatch's sender in red, the uniform clean-error reply. */
    private static void replyRed(CommandContext<CommandSourceStack> ctx, String message) {
        Sender.of(ctx.getSource())
                .send(net.kyori.adventure.text.Component.text(
                        message, net.kyori.adventure.text.format.NamedTextColor.RED));
    }

    /**
     * The conditions to run before {@code method}: the registry conditions, plus the implicit gates derived
     * from a method- or class-level {@code @}{@link PlayerOnly} and {@code @}{@link
     * com.uxplima.uxmlib.command.annotation.annotations.Cooldown}. Folding both into the condition seam lets
     * them compose with consumer-registered conditions the same way.
     */
    private static List<CommandCondition> conditionsFor(Method method, ParamResolvers resolvers, String commandPath) {
        List<CommandCondition> conditions = new ArrayList<>(resolvers.conditions());
        if (method.isAnnotationPresent(PlayerOnly.class)
                || method.getDeclaringClass().isAnnotationPresent(PlayerOnly.class)) {
            conditions.add(playerOnlyCondition());
        }
        CommandCondition cooldown = CooldownCondition.forMethod(method, commandPath, resolvers.cooldowns());
        if (cooldown != null) {
            conditions.add(cooldown);
        }
        return conditions;
    }

    private static CommandCondition playerOnlyCondition() {
        return ctx -> {
            if (!(ctx.getSource().getSender() instanceof org.bukkit.entity.Player)) {
                throw new CommandCondition.CommandConditionException("Only a player can run this command.");
            }
        };
    }

    private static void checkConditions(List<CommandCondition> conditions, CommandContext<CommandSourceStack> ctx) {
        for (CommandCondition condition : conditions) {
            condition.test(ctx);
        }
    }
}
