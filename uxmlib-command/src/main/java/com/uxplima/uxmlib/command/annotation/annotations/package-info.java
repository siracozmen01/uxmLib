/**
 * The annotation types a handler class is marked up with:
 * {@link com.uxplima.uxmlib.command.annotation.annotations.Command} on the class,
 * {@link com.uxplima.uxmlib.command.annotation.annotations.Subcommand} on each method,
 * {@link com.uxplima.uxmlib.command.annotation.annotations.Arg} on a parameter,
 * {@link com.uxplima.uxmlib.command.annotation.annotations.Permission} to gate a branch,
 * {@link com.uxplima.uxmlib.command.annotation.annotations.PlayerOnly} to require a player sender, and
 * {@link com.uxplima.uxmlib.command.annotation.annotations.Range} /
 * {@link com.uxplima.uxmlib.command.annotation.annotations.Length} to bound a numeric or string argument.
 * The reflection that reads them and builds the Brigadier tree lives one package up in
 * {@link com.uxplima.uxmlib.command.annotation.AnnotatedCommands}.
 */
@NullMarked
package com.uxplima.uxmlib.command.annotation.annotations;

import org.jspecify.annotations.NullMarked;
