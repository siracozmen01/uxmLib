package com.uxplima.uxmlib.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.UUID;

import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

/**
 * Exercises the real pipeline splice/eject/reorder against an {@link EmbeddedChannel}: the path MockBukkit
 * cannot reach because it has no Netty channel. A foreign {@code "decoder"} handler stands in for the vanilla
 * anchor; the package-private channel seam ({@code inject}/{@code eject}/{@code reorder} on a {@link
 * io.netty.channel.Channel}) lets these drive the netty mutation directly.
 *
 * <p>The reorder cases pin the fix for the regression where a concurrent anchor disappearance left our handler
 * permanently dropped: after a failed re-anchor the handler must still be present in the pipeline.
 */
class PacketPipelineNettyTest {

    private static final String HANDLER = "uxmlib_packet";
    private static final String ANCHOR = PacketPipeline.DEFAULT_ANCHOR;
    private final UUID owner = UUID.randomUUID();

    private PacketPipeline newPipeline() {
        return new PacketPipeline(new ChannelResolver(), new PacketListenerRegistry(), HANDLER, fault -> {});
    }

    private static EmbeddedChannel channelWithDecoder() {
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addLast(ANCHOR, new ChannelInboundHandlerAdapter());
        return channel;
    }

    @Test
    void injectSplicesOurHandlerImmediatelyAfterTheAnchor() {
        PacketPipeline pipeline = newPipeline();
        EmbeddedChannel channel = channelWithDecoder();

        assertThat(pipeline.inject(channel, owner)).isTrue();

        assertThat(channel.pipeline().get(HANDLER)).isNotNull();
        int anchor = channel.pipeline().names().indexOf(ANCHOR);
        assertThat(channel.pipeline().names().get(anchor + 1)).isEqualTo(HANDLER);
    }

    @Test
    void injectReturnsFalseAndAddsNothingWhenTheAnchorIsAbsent() {
        PacketPipeline pipeline = newPipeline();
        EmbeddedChannel channel = new EmbeddedChannel(); // no "decoder"

        assertThat(pipeline.inject(channel, owner)).isFalse();
        assertThat(channel.pipeline().get(HANDLER)).isNull();
    }

    @Test
    void reinjectIsIdempotentAndDoesNotDoubleAdd() {
        PacketPipeline pipeline = newPipeline();
        EmbeddedChannel channel = channelWithDecoder();

        assertThat(pipeline.inject(channel, owner)).isTrue();
        assertThat(pipeline.inject(channel, owner)).isTrue();

        assertThat(channel.pipeline().names().stream().filter(HANDLER::equals)).hasSize(1);
    }

    @Test
    void ejectRemovesOurHandlerAndIsSafeToRepeat() {
        PacketPipeline pipeline = newPipeline();
        EmbeddedChannel channel = channelWithDecoder();
        pipeline.inject(channel, owner);

        assertThat(pipeline.eject(channel)).isTrue();
        assertThat(channel.pipeline().get(HANDLER)).isNull();
        assertThat(pipeline.eject(channel)).isFalse();
    }

    @Test
    void reorderMovesOurHandlerBackDirectlyAfterTheAnchor() {
        PacketPipeline pipeline = newPipeline();
        EmbeddedChannel channel = channelWithDecoder();
        pipeline.inject(channel, owner);
        // A foreign plugin splices a handler between the anchor and ours, pushing ours out of position.
        channel.pipeline().addAfter(ANCHOR, "foreign", new ChannelInboundHandlerAdapter());

        assertThat(pipeline.reorder(channel)).isEqualTo(PipelineWatchdog.Decision.REORDER);

        int anchor = channel.pipeline().names().indexOf(ANCHOR);
        assertThat(channel.pipeline().names().get(anchor + 1)).isEqualTo(HANDLER);
    }

    @Test
    void reorderIsAnInPlaceNoOpWhenAlreadyDirectlyAfterTheAnchor() {
        PacketPipeline pipeline = newPipeline();
        EmbeddedChannel channel = channelWithDecoder();
        pipeline.inject(channel, owner);

        assertThat(pipeline.reorder(channel)).isEqualTo(PipelineWatchdog.Decision.IN_PLACE);
        assertThat(channel.pipeline().get(HANDLER)).isNotNull();
    }

    @Test
    void reorderNeverLeavesOurHandlerDroppedWhenTheAnchorVanishesMidMove() {
        // Reproduces the drop bug: a plugin deletes the anchor in the window between our remove and re-add.
        // The reanchor() seam lets the test delete "decoder" at exactly that instant, so the subsequent
        // addAfter throws NoSuchElementException. Before the fix this left our handler permanently dropped;
        // the recovery path must now re-insert it at the tail so interception stays active.
        EmbeddedChannel channel = channelWithDecoder();
        PacketPipeline pipeline =
                new PacketPipeline(new ChannelResolver(), new PacketListenerRegistry(), HANDLER, fault -> {}) {
                    @Override
                    void reanchor(io.netty.channel.ChannelPipeline p, PacketInterceptor handler) {
                        p.remove(ANCHOR); // concurrent plugin removed the anchor mid-reorder
                        super.reanchor(p, handler); // throws NoSuchElementException -> recovery must run
                    }
                };
        pipeline.inject(channel, owner);
        channel.pipeline().addAfter(ANCHOR, "foreign", new ChannelInboundHandlerAdapter());

        assertThatCode(() -> pipeline.reorder(channel)).doesNotThrowAnyException();

        assertThat(channel.pipeline().get(HANDLER)).isNotNull();
        assertThat(channel.pipeline().get(ANCHOR)).isNull();
    }

    @Test
    void reorderReportsAnchorGoneAndKeepsOurHandlerWhenTheAnchorIsAlreadyAbsent() {
        PacketPipeline pipeline = newPipeline();
        EmbeddedChannel channel = channelWithDecoder();
        pipeline.inject(channel, owner);
        channel.pipeline().remove(ANCHOR);

        assertThat(pipeline.reorder(channel)).isEqualTo(PipelineWatchdog.Decision.ANCHOR_GONE);
        assertThat(channel.pipeline().get(HANDLER)).isNotNull();
    }
}
