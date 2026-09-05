package com.uxplima.uxmlib.menu.property.colour;

import java.util.Objects;

import com.uxplima.uxmlib.menu.MenuKeys;
import org.jspecify.annotations.NullMarked;

/**
 * The catalog keys the colour picker's chrome resolves through: the menu title, the custom-hex button name and
 * its anvil prompt, the clear/default button name, and the back button name. The per-colour swatch names live on
 * {@link ColourSwatch} itself, so this bundle carries only the picker's own buttons. Every string the picker
 * shows is one of these keys; nothing is inlined, so the picker honours the locale pipeline like every other
 * menu.
 *
 * @param title the picker menu title
 * @param customName the custom-hex button name
 * @param customPrompt the anvil hint shown when typing a custom hex colour
 * @param clearName the clear/default button name (resets the property to its "no override" sentinel)
 * @param backName the back button name (returns to the editor without changing the colour)
 */
@NullMarked
public record ColourPickerText(
        String title, String customName, String customPrompt, String clearName, String backName) {

    public ColourPickerText {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(customName, "customName");
        Objects.requireNonNull(customPrompt, "customPrompt");
        Objects.requireNonNull(clearName, "clearName");
        Objects.requireNonNull(backName, "backName");
    }

    /** The shared default chrome, resolving through the engine's own {@link MenuKeys} colour-picker keys. */
    public static ColourPickerText shared() {
        return new ColourPickerText(
                MenuKeys.COLOUR_PICKER_TITLE,
                MenuKeys.COLOUR_PICKER_CUSTOM,
                MenuKeys.COLOUR_PICKER_CUSTOM_PROMPT,
                MenuKeys.COLOUR_PICKER_CLEAR,
                MenuKeys.COLOUR_PICKER_BACK);
    }
}
