package com.uxplima.uxmlib.gui;

import java.util.Objects;

/**
 * Layout helpers for a chest-style {@link Gui}: fill the border, a row, a column, a rectangle, or every
 * empty slot, without writing the slot arithmetic by hand. Obtain one with {@link Gui#filler()}. Every
 * method assumes a nine-wide grid (a chest menu); on a non-chest {@link GuiType} menu only {@link #fill}
 * and {@link #fillEmpty} are meaningful. Rows and columns are 1-indexed to match {@link Gui#set(int, int,
 * GuiItem)}.
 */
public final class GuiFiller {

    private static final int WIDTH = 9;

    private final Gui gui;

    GuiFiller(Gui gui) {
        this.gui = Objects.requireNonNull(gui, "gui");
    }

    /** Put {@code item} in every slot, overwriting what is there. */
    public GuiFiller fill(GuiItem item) {
        Objects.requireNonNull(item, "item");
        for (int slot = 0; slot < gui.size(); slot++) {
            gui.set(slot, item);
        }
        return this;
    }

    /** Put {@code item} in every slot that is currently empty. */
    public GuiFiller fillEmpty(GuiItem item) {
        Objects.requireNonNull(item, "item");
        for (int slot = 0; slot < gui.size(); slot++) {
            if (gui.getItem(slot) == null) {
                gui.set(slot, item);
            }
        }
        return this;
    }

    /** Put {@code item} around the outer edge of the grid (top and bottom rows, first and last columns). */
    public GuiFiller fillBorder(GuiItem item) {
        Objects.requireNonNull(item, "item");
        int rows = rows();
        if (rows < 1) {
            return this;
        }
        for (int col = 1; col <= WIDTH; col++) {
            gui.set(1, col, item);
            gui.set(rows, col, item);
        }
        for (int row = 1; row <= rows; row++) {
            gui.set(row, 1, item);
            gui.set(row, WIDTH, item);
        }
        return this;
    }

    /** Put {@code item} across every slot of 1-indexed {@code row}. */
    public GuiFiller fillRow(int row, GuiItem item) {
        Objects.requireNonNull(item, "item");
        for (int col = 1; col <= WIDTH; col++) {
            gui.set(row, col, item);
        }
        return this;
    }

    /** Put {@code item} down every slot of 1-indexed {@code col}. */
    public GuiFiller fillColumn(int col, GuiItem item) {
        Objects.requireNonNull(item, "item");
        int rows = rows();
        for (int row = 1; row <= rows; row++) {
            gui.set(row, col, item);
        }
        return this;
    }

    /** Put {@code item} in the inclusive rectangle from ({@code row1},{@code col1}) to ({@code row2},{@code col2}). */
    public GuiFiller fillRect(int row1, int col1, int row2, int col2, GuiItem item) {
        Objects.requireNonNull(item, "item");
        int topRow = Math.min(row1, row2);
        int bottomRow = Math.max(row1, row2);
        int leftCol = Math.min(col1, col2);
        int rightCol = Math.max(col1, col2);
        for (int row = topRow; row <= bottomRow; row++) {
            for (int col = leftCol; col <= rightCol; col++) {
                gui.set(row, col, item);
            }
        }
        return this;
    }

    private int rows() {
        return gui.size() / WIDTH;
    }
}
