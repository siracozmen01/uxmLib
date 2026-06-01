/**
 * Annotation-driven commands. Annotate a handler class with
 * {@link com.uxplima.uxmlib.command.annotation.annotations.Command}, its methods with
 * {@link com.uxplima.uxmlib.command.annotation.annotations.Subcommand}, parameters with
 * {@link com.uxplima.uxmlib.command.annotation.annotations.Arg}, and optionally
 * {@link com.uxplima.uxmlib.command.annotation.annotations.Permission} (the annotation types live in
 * {@link com.uxplima.uxmlib.command.annotation.annotations}); then
 * {@link com.uxplima.uxmlib.command.annotation.AnnotatedCommands#register} reflects over it and builds
 * the Brigadier tree for you — no hand-wiring of nodes. The underlying {@code Cmd}/{@code Sender} facade
 * stays available for cases the annotations do not cover.
 */
@NullMarked
package com.uxplima.uxmlib.command.annotation;

import org.jspecify.annotations.NullMarked;
