package com.uxplima.uxmlib.gui.style;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmlib.text.style.StyleTokens;
import com.uxplima.uxmlib.text.style.Theme;

/**
 * Puts the title of a menu tile on the first line of its lore, under a blank name.
 *
 * <p>The client draws an item's display name hard against the top edge of the tooltip and will not put a line
 * above it. A blank name buys that line of air, and the title then reads as the first thing inside the
 * tooltip rather than as its lid; a blank line closes it the same way, so the text sits in a box of air.
 *
 * <p>The blank name is a single space and not an empty component. That is the piece of client behaviour worth
 * remembering: an empty name makes the client fall back to the material's own name, and the tile then says
 * "Ender Eye" where the blank line belongs.
 *
 * <p>A button is not a tile. A page arrow or a filler pane keeps its one-line name and carries no lore at
 * all, so {@link #titled} hands such an item back untouched.
 */
public final class Tiles {

    /** Every lore line is padded one space either side, so no text touches the edge of the tooltip. */
    public static final String PADDING = " ";

    /** The gradient a title takes when the caller names none. */
    private static final String HEADER = "header";

    private Tiles() {}

    /**
     * The name a titled tile carries: one space, never {@link Component#empty()}.
     *
     * <p>A blank name is a value here rather than the absence of one, which matters to anything that reads
     * a name from a file: {@code isBlank} answers true for the name a titled tile is supposed to have, so a
     * reader that treats blank as "not configured" will quietly hand the tile back its material name.
     * Absent and blank have to be different tests.
     */
    public static Component blankName() {
        return Component.text(PADDING);
    }

    /**
     * The lore of a tile: the title line, the lore as it was written, and a blank line to close the box. The
     * result is one component with newlines in it, which the item builder splits into lines.
     *
     * <p>The closing blank is only added when the lore does not already end on one. {@link Lore#build()}
     * closes its own box, so a tile built the usual way would otherwise end on two blank lines and sit a
     * line higher than every other tile.
     */
    public static Component titled(Theme theme, Component title, Component lore) {
        return titled(theme, title, lore, HEADER);
    }

    /** The same, with the title painted across the theme gradient named {@code gradient}. */
    public static Component titled(Theme theme, Component title, Component lore, String gradient) {
        Objects.requireNonNull(theme, "theme");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(lore, "lore");
        Objects.requireNonNull(gradient, "gradient");
        return isBlank(title) ? lore : box(head(theme, title, gradient), lore);
    }

    /**
     * The same, with the title painted with the arc the theme's wheel holds at {@code position}.
     *
     * <p>This is what a menu uses when its tiles should differ from each other and no file should have to
     * name a colour to say so: the caller passes the position of the tile and the wheel decides. A theme
     * with no wheel paints every tile with the header, which is the look a server that never asked for the
     * effect should get.
     */
    public static Component titled(Theme theme, Component title, Component lore, int position) {
        Objects.requireNonNull(theme, "theme");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(lore, "lore");
        return isBlank(title) ? lore : box(head(theme, title, position), lore);
    }

    /**
     * The lore of a tile whose lore is a list of lines rather than one component with newlines in it: the
     * title line, the lines as they were written, and a blank line to close the box.
     *
     * <p>Lore with nothing in it is a button and not a tile. A page arrow or a filler pane carries no lore and
     * keeps the one-line name it was written with, so it comes back untitled, and so does a tile whose title is
     * blank because there is nothing to move. A caller may therefore route every item through this.
     *
     * <p>This is where the list form and the component form part, and they part because their inputs do. An
     * empty list is genuinely no lore, while {@link Component#empty()} is a blank line somebody asked for, so
     * the component form boxes it and this one does not. For the same reason the test here is that the list
     * holds no lines at all and not that every line in it is blank: a list holding one blank line is a caller
     * asking for a blank line, which is the position {@link #blankName()} already takes.
     *
     * <p>The returned list is unmodifiable and is always a new list, including in the two cases where nothing is
     * added. A caller that has to know whether a title was moved therefore cannot compare the result against what
     * it passed in: it asks {@link #isUntitled}, which is the question it actually means and which answers by the
     * same rule this method decides by.
     */
    public static List<Component> titled(Theme theme, Component title, List<Component> lore) {
        return titled(theme, title, lore, HEADER);
    }

    /** The same, with the title painted across the theme gradient named {@code gradient}. */
    public static List<Component> titled(Theme theme, Component title, List<Component> lore, String gradient) {
        Objects.requireNonNull(theme, "theme");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(lore, "lore");
        Objects.requireNonNull(gradient, "gradient");
        return isUntitled(title, lore) ? List.copyOf(lore) : box(head(theme, title, gradient), lore);
    }

    /** The same, with the title painted with the arc the theme's wheel holds at {@code position}. */
    public static List<Component> titled(Theme theme, Component title, List<Component> lore, int position) {
        Objects.requireNonNull(theme, "theme");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(lore, "lore");
        return isUntitled(title, lore) ? List.copyOf(lore) : box(head(theme, title, position), lore);
    }

    /**
     * Whether there is nothing to title: a button carrying no lore, or a tile whose title is blank. This is the rule
     * {@link #titled(Theme, Component, List)} decides by, exposed because the caller that titles an item usually has
     * to make a second decision from the same answer: an item that kept its title needs its name left alone, and one
     * whose title moved into the lore needs {@link #blankName()} instead.
     */
    public static boolean isUntitled(Component title, List<Component> lore) {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(lore, "lore");
        return lore.isEmpty() || isBlank(title);
    }

    /** The list form of the box: the head, the lines, and the closing blank when the last line is not one. */
    private static List<Component> box(Component head, List<Component> lore) {
        List<Component> titled = new ArrayList<>(lore.size() + 2);
        titled.add(head);
        titled.addAll(lore);
        if (!isBlank(lore.get(lore.size() - 1))) {
            titled.add(Component.text(PADDING));
        }
        return List.copyOf(titled);
    }

    /** The title line, the lore, and the blank line that closes the box when the lore does not. */
    private static Component box(Component head, Component lore) {
        Component titled = head.append(Component.newline()).append(lore);
        return endsBlank(lore) ? titled : titled.append(Component.newline()).append(Component.text(PADDING));
    }

    /** The title line on its own, painted with the theme's {@code header} gradient. */
    public static Component head(Theme theme, Component title) {
        return head(theme, title, HEADER);
    }

    /**
     * The title line on its own: the theme's title glyph in the icon colour, then the title, bold and
     * painted across the theme gradient named {@code gradient}.
     *
     * <p>The name is asked for rather than worked out here. A menu of twelve tiles painted with one gradient
     * reads as one heading twelve times, and only the file that knows what the twelve tiles are about can
     * say which of them should look alike: a wardrobe wants a colour per family, a lobby list wants one
     * colour per lobby, and a page of pets wants the same colour on every pet.
     *
     * <p>It paints as well as bolds because a catalog that writes a title writes words and nothing else,
     * which is the right assumption for it to make: this line forces bold, so it owns the look of the line,
     * and a title left unpainted falls back to the client's own lore colour rather than to the theme. A
     * title that arrives already carrying a colour (a lobby name, a rank) keeps it.
     */
    public static Component head(Theme theme, Component title, String gradient) {
        Objects.requireNonNull(theme, "theme");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(gradient, "gradient");
        return line(theme, StyleTokens.paint(theme, title, gradient));
    }

    /** The title line painted with the arc the theme's wheel holds at {@code position}. */
    public static Component head(Theme theme, Component title, int position) {
        Objects.requireNonNull(theme, "theme");
        Objects.requireNonNull(title, "title");
        return line(theme, StyleTokens.paint(theme, title, position));
    }

    /** The glyph, the painted title in bold, and the padding either side of them. */
    private static Component line(Theme theme, Component painted) {
        return Component.text(PADDING)
                .append(Component.text(theme.glyph("title") + PADDING, theme.colour("icon")))
                .append(painted.decoration(TextDecoration.BOLD, true))
                .append(Component.text(PADDING));
    }

    /** Whether {@code lore} already ends on a blank line, so closing it again would double the air. */
    private static boolean endsBlank(Component lore) {
        String plain = PlainTextComponentSerializer.plainText().serialize(lore);
        int lastBreak = plain.lastIndexOf('\n');
        return lastBreak >= 0 && plain.substring(lastBreak + 1).isBlank();
    }

    /** Whether {@code title} would put a title on a tile, or is the blank a titled tile already carries. */
    public static boolean isBlank(Component title) {
        Objects.requireNonNull(title, "title");
        return PlainTextComponentSerializer.plainText().serialize(title).isBlank();
    }
}
