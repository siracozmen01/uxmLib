package com.uxplima.uxmlib.gui;

import net.kyori.adventure.text.Component;

/**
 * A plain single-page menu: a grid of slots the caller fills with {@link GuiItem}s. Created through
 * {@link Guis#gui()}.
 */
public final class SimpleGui extends AbstractGui {

    SimpleGui(Component title, int rows) {
        super(title, rows);
    }
}
