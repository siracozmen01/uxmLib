/**
 * The annotation types a handler class is marked up with:
 * {@link com.uxplima.uxmlib.command.annotation.annotations.Command} on the class,
 * {@link com.uxplima.uxmlib.command.annotation.annotations.Subcommand} on each method,
 * {@link com.uxplima.uxmlib.command.annotation.annotations.Arg} on a parameter,
 * {@link com.uxplima.uxmlib.command.annotation.annotations.Permission} to gate a branch,
 * {@link com.uxplima.uxmlib.command.annotation.annotations.PlayerOnly} to require a player sender,
 * {@link com.uxplima.uxmlib.command.annotation.annotations.Cooldown} to rate-limit a branch per player,
 * {@link com.uxplima.uxmlib.command.annotation.annotations.Range} /
 * {@link com.uxplima.uxmlib.command.annotation.annotations.Length} to bound a numeric or string argument,
 * {@link com.uxplima.uxmlib.command.annotation.annotations.Flag} /
 * {@link com.uxplima.uxmlib.command.annotation.annotations.Switch} for position-independent named value
 * flags and boolean presence switches, and
 * {@link com.uxplima.uxmlib.command.annotation.annotations.CommandPriority} to break ties between
 * overlapping branches. The reflection that reads them and builds the Brigadier tree lives one package up
 * in {@link com.uxplima.uxmlib.command.annotation.AnnotatedCommands}.
 */
@NullMarked
package com.uxplima.uxmlib.command.annotation.annotations;

import org.jspecify.annotations.NullMarked;
