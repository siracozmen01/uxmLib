package com.uxplima.uxmlib.hud;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Coalescing is observable through the {@link FakeScheduler}: many {@code mark} calls for the same key within
 * one tick arm a single flush and render the key once; marks made from inside the flush are skipped by the
 * re-entrancy guard so a renderer that touches the batch cannot feed back into the same flush.
 */
class UpdateBatchTest {

    private FakeScheduler scheduler;
    private List<String> rendered;
    private UpdateBatch<String> batch;

    @BeforeEach
    void setUp() {
        scheduler = new FakeScheduler();
        rendered = new ArrayList<>();
        batch = new UpdateBatch<>(scheduler, rendered::add);
    }

    @Test
    void threeMarksOfOneKeyInATickFlushItOnce() {
        batch.mark("sb");
        batch.mark("sb");
        batch.mark("sb");

        assertThat(scheduler.laters()).isEqualTo(1);
        scheduler.runLater();

        assertThat(rendered).containsExactly("sb");
    }

    @Test
    void distinctKeysEachRenderOncePerFlush() {
        batch.mark("a");
        batch.mark("b");
        batch.mark("a");

        scheduler.runLater();
        assertThat(rendered).containsExactlyInAnyOrder("a", "b");
        assertThat(scheduler.laters()).isEqualTo(1);
    }

    @Test
    void aMarkAfterAFlushArmsAFreshFlush() {
        batch.mark("a");
        scheduler.runLater();
        batch.mark("a");

        assertThat(scheduler.laters()).isEqualTo(2);
        scheduler.runLater();
        assertThat(rendered).containsExactly("a", "a");
    }

    @Test
    void reentrantMarkFromInsideTheFlushIsSkipped() {
        // A renderer that re-marks the same key must not cause it to render twice in this flush.
        List<String> seen = new ArrayList<>();
        feedbackRef = new UpdateBatch<>(scheduler, key -> {
            seen.add(key);
            feedback().mark(key); // re-entrant call during the flush
        });
        feedback().mark("x");
        scheduler.runLater();

        assertThat(seen).containsExactly("x");
        // No new flush was armed by the re-entrant mark.
        assertThat(scheduler.laters()).isEqualTo(1);
    }

    @Test
    void emptyFlushRendersNothing() {
        // Arming then a stray flush with nothing pending renders nothing and does not throw.
        batch.mark("a");
        scheduler.runLater();
        rendered.clear();
        scheduler.runLater();
        assertThat(rendered).isEmpty();
    }

    // Field so the re-entrant renderer above can reach the batch under construction.
    private @org.jspecify.annotations.Nullable UpdateBatch<String> feedbackRef;

    private UpdateBatch<String> feedback() {
        return java.util.Objects.requireNonNull(feedbackRef);
    }
}
