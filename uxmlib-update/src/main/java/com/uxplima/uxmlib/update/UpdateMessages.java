package com.uxplima.uxmlib.update;

import java.util.Objects;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;

import com.uxplima.uxmlib.text.Text;

/**
 * Builds the "a newer version is available" message. The body is plain MiniMessage through {@link Text} (no
 * legacy colour codes); the release link is a clickable {@link ClickEvent#openUrl} segment so an op can open
 * the download page in one click. Pure and Bukkit-free so the component shape is unit-testable without a server.
 */
final class UpdateMessages {

    private UpdateMessages() {}

    /**
     * The notification component for an outdated build.
     *
     * @param pluginName the human name to show (e.g. {@code "uxmLib"})
     * @param currentVersion the running build's version
     * @param release the newer release to advertise
     */
    static Component notification(String pluginName, String currentVersion, Release release) {
        Objects.requireNonNull(pluginName, "pluginName");
        Objects.requireNonNull(currentVersion, "currentVersion");
        Objects.requireNonNull(release, "release");
        Component prefix = Text.mini(
                "<gray>[<aqua><name></aqua>]</gray> <yellow>A new version is available:</yellow> "
                        + "<gray><current></gray> <dark_gray>-></dark_gray> <green><latest></green>",
                Text.placeholder("name", pluginName),
                Text.placeholder("current", currentVersion),
                Text.placeholder("latest", release.version()));
        Component link = Text.mini("<aqua><underlined>[Open release page]</underlined></aqua>")
                .clickEvent(ClickEvent.openUrl(release.url()));
        return prefix.appendSpace().append(link);
    }
}
