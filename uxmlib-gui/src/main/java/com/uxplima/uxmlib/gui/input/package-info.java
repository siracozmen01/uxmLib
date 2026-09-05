/**
 * Asking a player to type a line of text, on two floors.
 *
 * <p>{@link com.uxplima.uxmlib.gui.input.PlayerInput} is the mechanism: it opens one native prompt, an
 * {@link com.uxplima.uxmlib.gui.input.InputType#ANVIL anvil}, a
 * {@link com.uxplima.uxmlib.gui.input.InputType#CHAT chat} line or a transient
 * {@link com.uxplima.uxmlib.gui.input.InputType#SIGN sign}, and delivers whatever came back through one
 * callback as an {@link com.uxplima.uxmlib.gui.input.InputResult}. It chooses nothing.
 *
 * <p>{@link com.uxplima.uxmlib.gui.input.TextInput} is the seam above it: it reads an operator's per-key
 * configuration from {@link com.uxplima.uxmlib.gui.input.InputSettings}, picks the backend, adds a Bedrock
 * form for a Bedrock client and a native dialog where the server supports one, applies the cancel-word
 * policy, and hands a call site a single answer. A call site names neither a backend nor a screen.
 *
 * <p>The division is worth stating because it is where a defect lived. Policy belongs to the upper floor
 * alone: a cancel word applied by the mechanism would arrive already decided, and a word an operator had
 * removed would still abort a prompt with nothing to say why. So the seam builds its
 * {@code PlayerInput} with
 * {@link com.uxplima.uxmlib.gui.input.PlayerInput#withoutCancelKeyword(org.bukkit.plugin.Plugin)}.
 */
@NullMarked
package com.uxplima.uxmlib.gui.input;

import org.jspecify.annotations.NullMarked;
