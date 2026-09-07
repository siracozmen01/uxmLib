package com.uxplima.uxmlib.packet.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;

import org.bukkit.plugin.Plugin;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The installer's choreography, as far as a mock server can be driven.
 *
 * <p>MockBukkit gives no real Netty channel, so the contract under test is the same one the pipeline's own
 * smoke tests hold: a mock player is left uninjected and nothing throws. What is checked on top of that is
 * the choreography itself, that a join schedules the delayed reorder pass against the player who joined, and
 * that install and uninstall are safe to call around it.
 */
class ItemViewsSmokeTest {

    private ServerMock server;
    private Plugin plugin;
    private final FakeScheduler scheduler = new FakeScheduler();

    @BeforeEach
    void startServer() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin("ItemViewsHost");
    }

    @AfterEach
    void stopServer() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("the handler is named after the plugin that installed it")
    void theHandlerIsNamedAfterThePlugin() {
        ItemViews views = views();

        assertThat(views.handlerName()).isEqualTo("uxmlib-item-view-itemviewshost");
    }

    @Test
    @DisplayName("a join asks for the injection and schedules the reorder pass for that player")
    void aJoinSchedulesTheReorderPass() {
        ItemViews views = views();
        views.install();

        PlayerMock joined = server.addPlayer();

        assertThat(scheduler.delayed()).hasSize(1);
        assertThat(scheduler.delayed().get(0).entity()).isSameAs(joined);
        assertThat(scheduler.delayed().get(0).delay()).isEqualTo(Duration.ofSeconds(2));
        assertThat(views.isInjected(joined))
                .describedAs("a mock player has no netty channel, so the inject fails softly")
                .isFalse();
    }

    @Test
    @DisplayName("the reorder pass on a player with no channel does nothing and throws nothing")
    void theReorderPassIsSafeWithoutAChannel() {
        ItemViews views = views();
        views.install();
        server.addPlayer();

        assertThatCode(scheduler::runDelayed).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("install and uninstall are safe on a server with players on it")
    void installAndUninstallAreSafe() {
        ItemViews views = views();
        server.addPlayer();

        assertThatCode(() -> {
                    views.install();
                    views.uninstall();
                })
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("after uninstall a join is no longer followed")
    void afterUninstallAJoinIsIgnored() {
        ItemViews views = views();
        views.install();
        views.uninstall();

        server.addPlayer();

        assertThat(scheduler.delayed()).isEmpty();
    }

    private ItemViews views() {
        return new ItemViews(plugin, scheduler, new FakeItemPackets(), (viewer, real) -> real);
    }
}
