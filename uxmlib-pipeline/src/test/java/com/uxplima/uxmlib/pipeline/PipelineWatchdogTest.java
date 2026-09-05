package com.uxplima.uxmlib.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;

import org.junit.jupiter.api.Test;

class PipelineWatchdogTest {

    private final PipelineWatchdog watchdog = new PipelineWatchdog("uxmlib_packet", "decoder");

    @Test
    void inPlaceWhenHandlerSitsDirectlyAfterAnchor() {
        List<String> names = List.of("splitter", "decoder", "uxmlib_packet", "packet_handler");
        assertThat(watchdog.evaluate(names)).isEqualTo(PipelineWatchdog.Decision.IN_PLACE);
    }

    @Test
    void reorderWhenAnotherHandlerSlippedBetweenAnchorAndOurs() {
        // An anti-cheat injected "ac_handler" right after the decoder, pushing ours one slot down.
        List<String> names = List.of("splitter", "decoder", "ac_handler", "uxmlib_packet", "packet_handler");
        PipelineWatchdog.Decision decision = watchdog.evaluate(names);
        assertThat(decision).isEqualTo(PipelineWatchdog.Decision.REORDER);
        assertThat(decision.needsReorder()).isTrue();
    }

    @Test
    void reorderWhenOursIsBeforeTheAnchor() {
        List<String> names = List.of("uxmlib_packet", "splitter", "decoder", "packet_handler");
        assertThat(watchdog.evaluate(names)).isEqualTo(PipelineWatchdog.Decision.REORDER);
    }

    @Test
    void missingWhenOurHandlerIsNotPresent() {
        List<String> names = List.of("splitter", "decoder", "packet_handler");
        PipelineWatchdog.Decision decision = watchdog.evaluate(names);
        assertThat(decision).isEqualTo(PipelineWatchdog.Decision.MISSING);
        assertThat(decision.needsReorder()).isFalse();
    }

    @Test
    void anchorGoneWhenAnchorIsAbsentButOursIsPresent() {
        List<String> names = List.of("splitter", "uxmlib_packet", "packet_handler");
        assertThat(watchdog.evaluate(names)).isEqualTo(PipelineWatchdog.Decision.ANCHOR_GONE);
    }

    @Test
    void exposesItsConfiguredNames() {
        assertThat(watchdog.handlerName()).isEqualTo("uxmlib_packet");
        assertThat(watchdog.anchorName()).isEqualTo("decoder");
    }

    @Test
    void blankNamesAreRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> new PipelineWatchdog(" ", "decoder"));
        assertThatIllegalArgumentException().isThrownBy(() -> new PipelineWatchdog("ours", ""));
    }
}
