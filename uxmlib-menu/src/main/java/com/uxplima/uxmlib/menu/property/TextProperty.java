package com.uxplima.uxmlib.menu.property;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import com.uxplima.uxmlib.gui.input.InputRequest;
import com.uxplima.uxmlib.scheduler.Scheduler;
import org.jspecify.annotations.NullMarked;

/**
 * A property whose click opens a {@link CatalogTextPrompt} prompt (anvil or chat, per the operator's per-key config), validates
 * the typed line, and hands the accepted value to a setter. The prompt's hint is a catalog line; the validator turns
 * the raw text into the accepted value or rejects it (an empty {@link Optional}), so a module can trim, length-check,
 * or pattern-match without the framework knowing the rules. An accepted value is written through the caller's setter
 * off the tick thread via the shared {@link Scheduler}; on a rejected submit and on cancel the editor is redrawn so the
 * viewer lands back where they were.
 *
 * <p>The {@code inputKey} identifies this field to the input config: every text field in an entity editor shares the
 * one {@code editor.text-field} key, so an operator flips all editor text fields to chat (or anvil) with a single
 * override. The {@link CatalogTextPrompt} seam already hops the callback onto the viewer's region thread and handles the cancel
 * keywords, so this property only validates and sets.
 */
@NullMarked
public final class TextProperty implements EditableProperty {

    private final String inputKey;
    private final String label;
    private final String promptHint;
    private final Material icon;
    private final Supplier<String> current;
    private final Function<String, Optional<String>> validator;
    private final Consumer<String> setter;
    private final CatalogTextPrompt textPrompt;
    private final Scheduler scheduler;

    public TextProperty(
            String inputKey,
            String label,
            String promptHint,
            Material icon,
            Supplier<String> current,
            Function<String, Optional<String>> validator,
            Consumer<String> setter,
            CatalogTextPrompt textPrompt,
            Scheduler scheduler) {
        this.inputKey = Objects.requireNonNull(inputKey, "inputKey");
        this.label = Objects.requireNonNull(label, "label");
        this.promptHint = Objects.requireNonNull(promptHint, "promptHint");
        this.icon = Objects.requireNonNull(icon, "icon");
        this.current = Objects.requireNonNull(current, "current");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.setter = Objects.requireNonNull(setter, "setter");
        this.textPrompt = Objects.requireNonNull(textPrompt, "textPrompt");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public String label() {
        return label;
    }

    @Override
    public Material icon() {
        return icon;
    }

    @Override
    public String valueLore(Player viewer) {
        Objects.requireNonNull(viewer, "viewer");
        return current.get();
    }

    @Override
    public void onClick(PropertyClick click) {
        Objects.requireNonNull(click, "click");
        textPrompt.prompt(
                click.viewer(), InputRequest.of(inputKey, promptHint), raw -> applyInput(click, raw), click.reopen());
    }

    /**
     * Apply a submitted line: validate it, and on acceptance write it through the setter off-thread and redraw; on
     * rejection redraw without writing. The prompt callback delegates here, and it is public so the behaviour is
     * reachable without opening a live prompt.
     *
     * <p>Reachable used not to mean covered: the property named the concrete text-input seam, a final class with no
     * interface whose cheapest constructor takes two package-private backends, so nothing outside that package could
     * build one and this method could not be reached from a test at all. It now names {@link CatalogTextPrompt}, the
     * capability it actually uses, which a test satisfies with a lambda.
     */
    public void applyInput(PropertyClick click, String raw) {
        Objects.requireNonNull(click, "click");
        Objects.requireNonNull(raw, "raw");
        Optional<String> accepted = validator.apply(raw);
        if (accepted.isEmpty()) {
            click.reopen().run();
            return;
        }
        String value = accepted.get();
        scheduler.async(() -> {
            setter.accept(value);
            scheduler.entity(click.viewer(), click.reopen());
        });
    }
}
