package com.uxplima.uxmlib.update;

import net.kyori.adventure.text.Component;

/**
 * The sentence a plugin says when a newer build of it exists.
 *
 * <p>{@link UpdateNotifier} decides <em>when</em> to speak: it polls off-thread, it announces a distinct
 * newer release once rather than once per poll, and it gates the on-join notice behind a permission. What is
 * said is this, and it is required rather than defaulted.
 *
 * <p>The reason it is required is that a version notice is one of the few lines a plugin sends that has no
 * entry in its own message file, so a library that shipped one would put an English sentence, in colours
 * nobody chose, into a plugin that translates everything else. The wording, the colours and the link belong
 * to the consumer. {@link Release#url()} is there for a caller that wants the release page on a click.
 *
 * <pre>{@code
 * UpdateAnnouncement announcement = (name, current, release) -> messages.render(
 *         MyKeys.UPDATE_AVAILABLE,
 *         Text.placeholder("name", name),
 *         Text.placeholder("current", current),
 *         Text.placeholder("latest", release.version()))
 *     .clickEvent(ClickEvent.openUrl(release.url()));
 * }</pre>
 */
@FunctionalInterface
public interface UpdateAnnouncement {

    /**
     * The component shown to an operator, in the console and on join.
     *
     * @param pluginName the running plugin's name
     * @param currentVersion the version that is running
     * @param release the newer release that was found
     */
    Component notification(String pluginName, String currentVersion, Release release);
}
