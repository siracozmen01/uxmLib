package com.uxplima.uxmlib.pipeline;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * The self-healing reorder decision, factored out as pure logic so it is unit-testable without a live Netty
 * channel. After other plugins (anti-cheats, proxies) splice their own handlers in, ours may no longer sit
 * directly after its anchor, which breaks interception order. This class answers a single question over a
 * snapshot of the pipeline's handler names: <em>does our handler still sit immediately after its anchor, and
 * if not, what move restores it?</em>
 *
 * <p>The technique is modelled on HamsterAPI's index-free {@code checkAndReorderHandlers} (MIT) and
 * re-implemented from scratch: compare {@code names.indexOf(ours)} against {@code names.indexOf(anchor) + 1}.
 * The caller applies the returned {@link Decision} against the real pipeline ({@code remove} then
 * {@code addAfter}); keeping the decision separate from the netty mutation is what makes the rule testable.
 */
public final class PipelineWatchdog {

    private final String handlerName;
    private final String anchorName;

    /**
     * @param handlerName the name our handler is registered under in the pipeline
     * @param anchorName the name of the stable vanilla handler ours must sit immediately after (e.g.
     *     {@code "decoder"})
     */
    public PipelineWatchdog(String handlerName, String anchorName) {
        this.handlerName = requireNonBlank(handlerName, "handlerName");
        this.anchorName = requireNonBlank(anchorName, "anchorName");
    }

    public String handlerName() {
        return handlerName;
    }

    public String anchorName() {
        return anchorName;
    }

    /**
     * Decide what (if anything) to do given the live ordered list of pipeline handler names.
     *
     * @param names the pipeline's handler names in order (as {@code ChannelPipeline.names()} returns them)
     * @return the move the caller should apply
     */
    public Decision evaluate(List<String> names) {
        Objects.requireNonNull(names, "names");
        int ours = names.indexOf(handlerName);
        if (ours < 0) {
            return Decision.MISSING;
        }
        int anchor = names.indexOf(anchorName);
        if (anchor < 0) {
            return Decision.ANCHOR_GONE;
        }
        return ours == anchor + 1 ? Decision.IN_PLACE : Decision.REORDER;
    }

    private static String requireNonBlank(@Nullable String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    /** The outcome of an {@link #evaluate(List)} pass. */
    public enum Decision {

        /** Our handler is directly after its anchor; nothing to do. */
        IN_PLACE,

        /** Our handler is present but out of position; remove it and re-add immediately after the anchor. */
        REORDER,

        /** Our handler is not in the pipeline (e.g. ejected, or never injected); the caller should re-inject. */
        MISSING,

        /** The anchor handler is gone (an unexpected pipeline shape); the caller should skip and warn. */
        ANCHOR_GONE;

        /** {@code true} if the caller must move the handler back into position. */
        public boolean needsReorder() {
            return this == REORDER;
        }
    }
}
