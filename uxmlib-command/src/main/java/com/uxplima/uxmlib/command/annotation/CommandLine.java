package com.uxplima.uxmlib.command.annotation;

import com.uxplima.uxmlib.text.message.MessageKey;

/**
 * Every line the command layer says on its own behalf, as a catalog key.
 *
 * <p>The command layer produces text that a plugin's own catalog would not otherwise hold: the help page,
 * the rejection of a bad argument, the cooldown notice. Left alone that text is the English
 * {@link CommandMessages} defaults, painted in vanilla colours, which makes it the one screen in a plugin
 * that is not the plugin's. These keys put it back in the catalog: they are read by
 * {@link CommandMessages#fromCatalogue}, they are translated with everything else, and the consumer's own
 * palette owns them.
 *
 * <p>Each path is the one the plugins already ship, so a language file written before this enum existed
 * keeps working.
 *
 * <p><b>The defaults below are plain MiniMessage in the vanilla colours, and they are meant to be
 * replaced.</b> A default has to render at a player on its own, because a consumer may take this module
 * and wire no style layer at all, and MiniMessage leaves a tag it does not know as literal text: a default
 * written in a style layer's own vocabulary reaches that player as the characters of the tag rather than
 * as a sentence. So what ships here is the plainest thing that reads correctly, matching the components
 * {@link CommandMessages#english()} builds, and it decides no look.
 *
 * <p>A plugin with a style layer writes its own template at these paths, in its own tokens, in its own
 * language files. That is where a palette belongs: the file the plugin ships, not the jar the library
 * ships. The catalog wins over the default for every key it holds, so supplying them is one line each and
 * needs nothing from this enum.
 *
 * @see CommandMessages#fromCatalogue
 */
public enum CommandLine implements MessageKey {

    /** A player-only command was run from the console. */
    PLAYER_ONLY("command.player-only", "<red>Only a player can run this command."),

    /** An argument was rejected, with no reason to give. */
    INVALID_VALUE("command.invalid-value", "<red>Invalid value '<input>' for <argument>."),

    /** An argument was rejected and the resolver said why. */
    INVALID_VALUE_WHY("command.invalid-value-why", "<red>Invalid value '<input>' for <argument>: <reason>"),

    /** An argument was rejected because it is not one of a known set. */
    NOT_ONE_OF("command.not-one-of", "<red>Invalid value '<input>' for <argument>: expected one of <allowed>."),

    /** An argument was rejected with a detail but no argument name. */
    INVALID_ARGUMENT("command.invalid-argument", "<red><detail>"),

    /** An argument was rejected with nothing to say about it. */
    BAD_ARGUMENT("command.bad-argument", "<red>Invalid argument."),

    /** The handler threw. */
    INTERNAL_ERROR("command.internal-error", "<red>An internal error occurred while running this command."),

    /** The sender must wait before running this again. */
    ON_COOLDOWN("command.on-cooldown", "<red>You must wait <time> before using this again."),

    /** The first line of a help page. */
    HELP_HEADER("command.help-header", "<yellow>/<command> help (<page>/<pages>)"),

    /** The command part of a help line. */
    HELP_COMMAND("command.help-command", "<white><command>"),

    /** What stands between a command and its description. */
    HELP_SEPARATOR("command.help-separator", "<gray> - "),

    /** The description part of a help line. */
    HELP_DESCRIPTION("command.help-description", "<gray><description>"),

    /** The hover on a help line, which writes the command into the chat box when clicked. */
    HELP_FILL_HINT("command.help-fill-hint", "Click to fill in this command"),

    /** The hover on a page button. */
    HELP_PAGE_HINT("command.help-page-hint", "Page <page>");

    private final String path;
    private final String defaultTemplate;

    CommandLine(String path, String defaultTemplate) {
        this.path = path;
        this.defaultTemplate = defaultTemplate;
    }

    @Override
    public String path() {
        return path;
    }

    @Override
    public String defaultTemplate() {
        return defaultTemplate;
    }
}
