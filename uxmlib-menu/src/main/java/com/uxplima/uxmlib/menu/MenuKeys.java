package com.uxplima.uxmlib.menu;

/**
 * The catalog keys the engine's own windows ask for. The engine draws a few windows nobody wrote a file for: the
 * confirm prompt, the page arrows a paged list grows, and the colour picker a colour property opens. Those windows
 * still need words, and the engine has none of its own.
 *
 * <p>So it names a key and asks the host's {@link com.uxplima.uxmlib.gui.GuiText} for it. The host owns the catalog,
 * the operator owns the file behind it, and a server that ships one more language gets these windows in it without
 * anybody touching Java. Nothing here is a user-facing literal; every one of these is a question.
 *
 * <p>A host that has no entry for one of these keys is what {@link com.uxplima.uxmlib.gui.GuiText} already answers
 * for: the engine asks the same way for every key and does not treat its own as a special case.
 */
public final class MenuKeys {

    private MenuKeys() {}

    /** The two button labels the confirm window paints, and the Bedrock modal form needs as words. */
    public static final String CONFIRM_YES = "gui.confirm.yes";

    public static final String CONFIRM_NO = "gui.confirm.no";

    /** The navigation buttons a list grows once it spans more than one page. */
    public static final String PAGE_PREVIOUS = "gui.page.previous";

    public static final String PAGE_NEXT = "gui.page.next";

    /** The colour picker's own chrome: its title and the four buttons that are not a swatch. */
    public static final String COLOUR_PICKER_TITLE = "gui.colour-picker.title";

    public static final String COLOUR_PICKER_CUSTOM = "gui.colour-picker.custom";

    public static final String COLOUR_PICKER_CUSTOM_PROMPT = "gui.colour-picker.custom-prompt";

    public static final String COLOUR_PICKER_CLEAR = "gui.colour-picker.clear";

    public static final String COLOUR_PICKER_BACK = "gui.colour-picker.back";

    /**
     * The sixteen dye names the picker's swatches carry. They are asked for rather than taken from the material
     * name because a material name is English and is not what a player calls a colour.
     */
    public static final String COLOUR_WHITE = "gui.colour.white";

    public static final String COLOUR_ORANGE = "gui.colour.orange";

    public static final String COLOUR_MAGENTA = "gui.colour.magenta";

    public static final String COLOUR_LIGHT_BLUE = "gui.colour.light-blue";

    public static final String COLOUR_YELLOW = "gui.colour.yellow";

    public static final String COLOUR_LIME = "gui.colour.lime";

    public static final String COLOUR_PINK = "gui.colour.pink";

    public static final String COLOUR_GRAY = "gui.colour.gray";

    public static final String COLOUR_LIGHT_GRAY = "gui.colour.light-gray";

    public static final String COLOUR_CYAN = "gui.colour.cyan";

    public static final String COLOUR_PURPLE = "gui.colour.purple";

    public static final String COLOUR_BLUE = "gui.colour.blue";

    public static final String COLOUR_BROWN = "gui.colour.brown";

    public static final String COLOUR_GREEN = "gui.colour.green";

    public static final String COLOUR_RED = "gui.colour.red";

    public static final String COLOUR_BLACK = "gui.colour.black";
}
