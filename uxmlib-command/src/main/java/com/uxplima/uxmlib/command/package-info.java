/**
 * A thin, practical layer over Paper's Brigadier commands. {@link com.uxplima.uxmlib.command.Cmd} mirrors
 * Paper's {@code Commands.literal}/{@code argument} factories bound to {@code CommandSourceStack} so the
 * generic type never has to be spelled out; {@link com.uxplima.uxmlib.command.Args} reads parsed
 * arguments by name; {@link com.uxplima.uxmlib.command.Sender} unwraps the source to its sender / player;
 * and {@link com.uxplima.uxmlib.command.CommandRegistrar} hides the {@code LifecycleEvents.COMMANDS}
 * registration boilerplate. The Brigadier builder stays the surface — this only removes the ceremony.
 */
@NullMarked
package com.uxplima.uxmlib.command;

import org.jspecify.annotations.NullMarked;
