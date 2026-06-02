/**
 * Unified player text input. {@link com.uxplima.uxmlib.gui.input.PlayerInput} requests a line of text from a
 * player and delivers it through one callback as an {@link com.uxplima.uxmlib.gui.input.InputResult},
 * regardless of which native backend ({@link com.uxplima.uxmlib.gui.input.InputType#ANVIL anvil},
 * {@link com.uxplima.uxmlib.gui.input.InputType#CHAT chat} or
 * {@link com.uxplima.uxmlib.gui.input.InputType#SIGN sign}) captured it.
 */
@NullMarked
package com.uxplima.uxmlib.gui.input;

import org.jspecify.annotations.NullMarked;
