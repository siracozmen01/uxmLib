package com.uxplima.uxmlib.command.annotation;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

import com.uxplima.uxmlib.command.annotation.annotations.Permission;
import com.uxplima.uxmlib.command.annotation.annotations.Subcommand;
import org.jspecify.annotations.Nullable;

/**
 * One executable branch in the platform-neutral command model: a {@code @}{@link Subcommand} method reduced
 * to the data a renderer needs — its literal path beneath the root, an optional method-level permission, its
 * ordered positional {@link ArgBinder.ParamArg}s and {@link FlagModel}s, and the {@link Method} the executor
 * invokes. The reflective scan ({@code CommandModels}) produces this; {@code BrigadierRenderer} walks it to
 * emit the Brigadier node tree. Holding the model separate from Brigadier is the seam that lets flags,
 * tests, and a future non-Brigadier surface target the model rather than builder soup.
 */
final class BranchModel {

    private final Method method;
    private final String path;
    private final @Nullable Permission permission;
    private final List<ArgBinder.ParamArg> args;
    private final List<FlagModel> flags;
    private final OptionalInt priority;

    BranchModel(
            Method method,
            String path,
            @Nullable Permission permission,
            List<ArgBinder.ParamArg> args,
            List<FlagModel> flags,
            OptionalInt priority) {
        this.method = Objects.requireNonNull(method, "method");
        this.path = Objects.requireNonNull(path, "path");
        this.permission = permission;
        this.args = List.copyOf(Objects.requireNonNull(args, "args"));
        this.flags = List.copyOf(Objects.requireNonNull(flags, "flags"));
        this.priority = Objects.requireNonNull(priority, "priority");
    }

    /** The handler method this branch invokes. */
    Method method() {
        return method;
    }

    /** The space-separated literal path beneath the root; empty for the root executor. */
    String path() {
        return path;
    }

    /** The method-level permission gating this branch, or {@code null} when there is none. */
    @Nullable Permission permission() {
        return permission;
    }

    /**
     * The explicit {@code @CommandPriority} value of this branch, or empty when it declares none. A lower
     * value attaches first so an overlapping overload wins on ambiguity; an empty value ranks last.
     */
    OptionalInt priority() {
        return priority;
    }

    /** The ordered positional arguments of this branch; empty when it takes none. */
    List<ArgBinder.ParamArg> args() {
        return args;
    }

    /** The flags and switches of this branch; empty when it declares none. */
    List<FlagModel> flags() {
        return flags;
    }

    /** Whether this branch declares any {@code @Flag}/{@code @Switch} parameters. */
    boolean hasFlags() {
        return !flags.isEmpty();
    }

    /** The literal tokens of {@link #path()}, or an empty array for the root executor. */
    String[] literals() {
        return path.isEmpty() ? new String[0] : path.split("\\s+");
    }
}
