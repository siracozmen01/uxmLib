package com.uxplima.uxmlib.menu.runtime;

import java.util.function.Consumer;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The narrow capability the menu engine's {@code input:} step uses to capture a line of text without depending on the
 * concrete text-input seam: it hands over a viewer, an input-point key (the operator's per-key anvil/chat/sign mode is
 * looked up by it), a resolved prompt label and an optional pre-fill, and gets exactly one callback back: the typed
 * line on submit, or {@code onCancel}. Production wires it over {@code TextInput.promptResolved}, and a test can
 * satisfy it with a lambda. Defined here so {@code MenuListener} stays decoupled from the input package, which is a
 * final class with no interface that nothing outside its own package can construct.
 *
 * <p>It is not {@link com.uxplima.uxmlib.menu.property.CatalogTextPrompt}, which an editable property uses, and the
 * two are not one interface because they sit on opposite sides of the text pipeline. This one is asked after
 * resolution: the engine has already rendered the prompt against the open context, placeholders and all, so the
 * {@link Component} it carries is finished and nothing may resolve it twice. That one is asked before: it carries a
 * catalog key and its placeholders for the seam behind it to resolve.
 *
 * <p>The implementation is responsible for delivering both callbacks on the viewer's entity thread, exactly as the
 * text-input seam already does, so the engine's continuation runs where it can safely touch the player and reopen a
 * menu.
 */
@NullMarked
public interface MenuTextPrompt {

    void prompt(
            Player viewer,
            String key,
            Component prompt,
            @Nullable String initialText,
            Consumer<String> onSubmit,
            Runnable onCancel);
}
