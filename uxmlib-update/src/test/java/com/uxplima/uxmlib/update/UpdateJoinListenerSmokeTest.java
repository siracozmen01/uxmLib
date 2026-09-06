package com.uxplima.uxmlib.update;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Smoke test for the on-join wiring against a real (mock) Paper server: register the listener, fire a join, and
 * confirm a permission-holder is told about an outdated build while a player without the node is not. The pure
 * comparison and message construction are covered by their own unit tests; this asserts the Bukkit plumbing
 * (permission gate + component delivery) holds together.
 */
class UpdateJoinListenerSmokeTest {

    private static final String PERMISSION = "uxmlib.update.notify";
    private static final Release NEWER = new Release("1.5.0", "https://github.com/o/r/releases/latest");

    /** A consumer's own line. The library ships none, so a test has to bring one exactly as a plugin does. */
    private static final UpdateAnnouncement ANNOUNCEMENT =
            (name, current, release) -> Component.text(name + " " + current + " -> " + release.version())
                    .clickEvent(ClickEvent.openUrl(release.url()));

    private ServerMock server;
    private Plugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private UpdateChecker warmCheckerReporting(Release latest) {
        UpdateProvider provider = () -> CompletableFuture.completedFuture(Optional.of(latest));
        UpdateChecker checker = new UpdateChecker(new InlineScheduler(), provider, "1.4.0");
        checker.check().join(); // warm the cache so the join path reads a resolved OUTDATED outcome
        return checker;
    }

    @Test
    void permissionHolderIsNotifiedOnJoin() {
        UpdateChecker checker = warmCheckerReporting(NEWER);
        server.getPluginManager()
                .registerEvents(new UpdateJoinListener(checker, "uxmLib", PERMISSION, ANNOUNCEMENT), plugin);

        PlayerMock player = server.addPlayer();
        player.addAttachment(plugin, PERMISSION, true);
        server.getPluginManager().callEvent(new org.bukkit.event.player.PlayerJoinEvent(player, Component.empty()));

        Component message = player.nextComponentMessage();
        assertThat(message).isNotNull();
        assertThat(com.uxplima.uxmlib.text.Text.plain(message)).contains("1.5.0");
    }

    @Test
    void playerWithoutPermissionIsNotNotified() {
        UpdateChecker checker = warmCheckerReporting(NEWER);
        server.getPluginManager()
                .registerEvents(new UpdateJoinListener(checker, "uxmLib", PERMISSION, ANNOUNCEMENT), plugin);

        PlayerMock player = server.addPlayer();
        server.getPluginManager().callEvent(new org.bukkit.event.player.PlayerJoinEvent(player, Component.empty()));

        assertThat(player.nextComponentMessage()).isNull();
    }

    @Test
    void coldCacheDoesNotNotifyButQueuesACheck() {
        // No prior check: the listener must not message on this join, only warm the cache for the next one.
        UpdateProvider provider = () -> CompletableFuture.completedFuture(Optional.of(NEWER));
        UpdateChecker checker = new UpdateChecker(new InlineScheduler(), provider, "1.4.0");
        server.getPluginManager()
                .registerEvents(new UpdateJoinListener(checker, "uxmLib", PERMISSION, ANNOUNCEMENT), plugin);

        PlayerMock player = server.addPlayer();
        player.addAttachment(plugin, PERMISSION, true);
        server.getPluginManager().callEvent(new org.bukkit.event.player.PlayerJoinEvent(player, Component.empty()));

        assertThat(player.nextComponentMessage()).isNull();
        // The warm-cache check ran inline, so the outcome is now available for a subsequent join.
        assertThat(checker.lastOutcome()).map(UpdateOutcome::status).contains(UpdateStatus.OUTDATED);
    }

    /**
     * The whole of what this seam has to guarantee: the component the consumer built is the component the
     * player receives, click event included. The library adds no word and no colour of its own to it.
     */
    @Test
    void thePlayerReceivesExactlyTheAnnouncementTheConsumerBuilt() {
        UpdateChecker checker = warmCheckerReporting(NEWER);
        server.getPluginManager()
                .registerEvents(new UpdateJoinListener(checker, "uxmLib", PERMISSION, ANNOUNCEMENT), plugin);

        PlayerMock player = server.addPlayer();
        player.addAttachment(plugin, PERMISSION, true);
        server.getPluginManager().callEvent(new org.bukkit.event.player.PlayerJoinEvent(player, Component.empty()));

        assertThat(player.nextComponentMessage()).isEqualTo(ANNOUNCEMENT.notification("uxmLib", "1.4.0", NEWER));
    }

    /** The values handed to the announcement are the running name, the running version and the new release. */
    @Test
    void theAnnouncementIsToldWhichVersionsAreInPlay() {
        String[] seen = new String[3];
        UpdateAnnouncement recording = (name, current, release) -> {
            seen[0] = name;
            seen[1] = current;
            seen[2] = release.version();
            return Component.text("x");
        };
        UpdateChecker checker = warmCheckerReporting(NEWER);
        server.getPluginManager()
                .registerEvents(new UpdateJoinListener(checker, "uxmTags", PERMISSION, recording), plugin);

        PlayerMock player = server.addPlayer();
        player.addAttachment(plugin, PERMISSION, true);
        server.getPluginManager().callEvent(new org.bukkit.event.player.PlayerJoinEvent(player, Component.empty()));

        assertThat(seen).containsExactly("uxmTags", "1.4.0", "1.5.0");
    }
}
