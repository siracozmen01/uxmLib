package com.uxplima.uxmlib.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Smoke test for the pipeline injector. MockBukkit cannot supply a real Netty channel, so the contract under
 * test is graceful failure: against a mock player every operation returns its "could not act" value (false /
 * MISSING) and none of them throws. The actual splice/eject/reorder is exercised on a live server only.
 */
class PacketPipelineSmokeTest {

    private ServerMock server;
    private PacketPipeline pipeline;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        pipeline = new PacketPipeline(
                new ChannelResolver(), new PacketListenerRegistry(), "uxmlib_packet", PacketPipelineSmokeTest::noSink);
    }

    // A no-op fault sink: no packet flows in these smoke tests (the channel is never resolved), so the sink is
    // never invoked; it only satisfies the constructor's non-null contract.
    private static void noSink(Throwable fault) {}

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void injectOnAMockPlayerReturnsFalseWithoutThrowing() {
        PlayerMock player = server.addPlayer();
        assertThat(pipeline.inject(player)).isFalse();
    }

    @Test
    void ejectAndIsInjectedAreSafeWhenNothingWasInjected() {
        PlayerMock player = server.addPlayer();
        assertThat(pipeline.eject(player)).isFalse();
        assertThat(pipeline.isInjected(player)).isFalse();
    }

    @Test
    void reorderOnAnUnreachableChannelReportsMissing() {
        PlayerMock player = server.addPlayer();
        assertThat(pipeline.reorder(player)).isEqualTo(PipelineWatchdog.Decision.MISSING);
    }

    @Test
    void noOperationThrowsAgainstAMockPlayer() {
        PlayerMock player = server.addPlayer();
        assertThatCode(() -> {
                    pipeline.inject(player);
                    pipeline.reorder(player);
                    pipeline.isInjected(player);
                    pipeline.eject(player);
                })
                .doesNotThrowAnyException();
    }

    @Test
    void handlerNameIsExposed() {
        assertThat(pipeline.handlerName()).isEqualTo("uxmlib_packet");
    }

    @Test
    void constructorRejectsBlankHandlerNameAndNullArgs() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PacketPipeline(
                        new ChannelResolver(), new PacketListenerRegistry(), "  ", PacketPipelineSmokeTest::noSink));
        assertThatNullPointerException()
                .isThrownBy(() ->
                        new PacketPipeline(new ChannelResolver(), new PacketListenerRegistry(), "ours", nullSink()));
    }

    @SuppressWarnings("NullAway") // intentionally feeds null to assert the constructor guard fires.
    private static Consumer<Throwable> nullSink() {
        return null;
    }
}
