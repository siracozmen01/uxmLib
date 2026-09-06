package com.uxplima.uxmlib.menu.property;

import java.util.function.Consumer;

import org.bukkit.entity.Player;

import com.uxplima.uxmlib.gui.input.InputRequest;
import org.jspecify.annotations.NullMarked;

/**
 * The narrow capability an editable property uses to capture a line of text: it hands over a viewer and an
 * {@link InputRequest} (the input-point key the operator's anvil / chat / sign mode is looked up by, the catalog line
 * to show, its placeholders, and an optional pre-fill) and gets exactly one callback back, the typed line on submit
 * or {@code onCancel}. Production wires it as {@code textInput::prompt}; a test wires a synchronous fake.
 *
 * <p>It exists because the concrete seam is a final class with no interface whose cheapest constructor takes two
 * package-private backends, so nothing outside its own package can build one. A property that named the class rather
 * than the capability could not be constructed in a test at all, whatever the test wanted to assert. Only the open
 * path needs a prompt; the value logic beside it does not, and this seam is what lets the two be paid for
 * separately.
 *
 * <p>It is not {@link com.uxplima.uxmlib.menu.runtime.MenuTextPrompt}, which the engine's own {@code input:} step
 * uses, and the two are not one interface because they sit on opposite sides of the text pipeline. This one is asked
 * before resolution: it carries a catalog key and its placeholders, and the seam behind it resolves them for the
 * viewer. That one is asked after: the engine has already rendered the prompt against the open context, placeholders
 * and all, so it carries a finished {@link net.kyori.adventure.text.Component} that nothing may resolve twice.
 *
 * <p>The implementation delivers both callbacks on the viewer's entity thread, as the text-input seam already does,
 * so a property's continuation runs where it can touch the player and reopen its editor.
 */
@NullMarked
@FunctionalInterface
public interface CatalogTextPrompt {

    void prompt(Player viewer, InputRequest request, Consumer<String> onSubmit, Runnable onCancel);
}
