package com.uxplima.uxmlib.gui;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;

/**
 * Says out loud when flattening a component to plain text loses all of it.
 *
 * <p>A translatable component that no translator holds flattens to the empty string rather than to its key, so
 * a label built from one does not degrade, it disappears. The player sees a blank button and nobody who could
 * fix it hears anything: the same text is correct in a lore line, which keeps the component and lets the client
 * translate it. A blank button that logs is an ordinary bug report. A blank button that does not is a week of
 * somebody's time.
 *
 * <p>The check names the one loss that was measured rather than guessing at a general one. An empty result is
 * only reported when the component actually carried a translatable key, so text that is legitimately empty
 * stays silent.
 */
final class FlattenLoss {

    private static final Logger LOG = Logger.getLogger(GuiText.class.getName());

    /** Report each key once. Keys come from menu files, so the set is small, and it is capped regardless. */
    private static final int REMEMBERED = 256;

    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

    private FlattenLoss() {}

    /**
     * {@code flattened} unchanged, having warned when it is empty and {@code source} carried a translatable key.
     * Never throws and never alters the result: a render that has reached this point still has to finish.
     */
    static String checked(String key, Component source, String flattened) {
        if (!flattened.isEmpty() || !hasTranslatable(source)) {
            return flattened;
        }
        if (REPORTED.size() < REMEMBERED && REPORTED.add(key)) {
            LOG.warning("GuiText.plain lost every word of \"" + key
                    + "\": the implementation returned a translatable component and no translator holds it, so"
                    + " the label is blank rather than wrong. Resolve the text before returning it.");
        }
        return flattened;
    }

    private static boolean hasTranslatable(Component component) {
        if (component instanceof TranslatableComponent) {
            return true;
        }
        for (Component child : component.children()) {
            if (hasTranslatable(child)) {
                return true;
            }
        }
        return false;
    }
}
