package com.uxplima.uxmlib.gui.input;

/**
 * Which backend a {@link PlayerInput} request uses to capture a line of text. All three are native (no NMS,
 * no packets): {@link #ANVIL} reuses the rename field of a vanilla anvil, {@link #CHAT} captures the
 * player's next chat message, and {@link #SIGN} opens a transient sign and reads the typed lines.
 */
public enum InputType {
    ANVIL,
    CHAT,
    SIGN
}
