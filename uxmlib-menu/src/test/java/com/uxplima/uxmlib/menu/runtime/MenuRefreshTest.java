package com.uxplima.uxmlib.menu.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;

import org.bukkit.entity.Player;

import com.uxplima.uxmlib.menu.spec.MenuSpec;
import com.uxplima.uxmlib.menu.spec.MenuSpecLoader;
import com.uxplima.uxmlib.menu.support.SameThreadScheduler;
import com.uxplima.uxmlib.scheduler.TaskHandle;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * The one repeating task an open menu may own. Two properties matter and neither is visible from the menu itself: a
 * menu that never asked to refresh must cost nothing after its open, and a menu that did must hand its cancel handle
 * to the holder, because the holder is what a close and a quit reach. A timer nobody can cancel is a leak that only
 * shows up on a server that has been running for a week.
 */
class MenuRefreshTest {

    /**
     * The one hop {@link SameThreadScheduler} refuses, taken here on purpose and recorded rather than run. A refresh
     * timer must not fire inline: running it would redraw during the open that started it.
     */
    private static final class RecordingScheduler extends SameThreadScheduler {

        private int timers;

        @Nullable private Duration delay;

        @Nullable private Duration period;

        @Nullable private Consumer<TaskHandle> task;

        private int cancels;

        private final TaskHandle handle = new TaskHandle() {

            @Override
            public void cancel() {
                cancels++;
            }

            @Override
            public boolean isCancelled() {
                return cancels > 0;
            }
        };

        @Override
        public TaskHandle globalTimer(Duration delay, Duration period, Consumer<TaskHandle> task) {
            timers++;
            this.delay = delay;
            this.period = period;
            this.task = task;
            return handle;
        }
    }

    private Player viewer;

    private RecordingScheduler scheduler;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        viewer = MockBukkit.getMock().addPlayer();
        scheduler = new RecordingScheduler();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private MenuHolder holderFor(String conf) {
        MenuSpec spec = new MenuSpecLoader().parse(conf);
        return new MenuHolder("m", spec, MenuContext.of(viewer, null, 0));
    }

    @Test
    void aMenuThatNeverAskedToRefreshStartsNoTask() {
        MenuRefresh.start(holderFor("rows = 1"), scheduler, () -> {
            throw new AssertionError("a static menu must not redraw");
        });

        assertThat(scheduler.timers).isZero();
    }

    @Test
    void refreshWrittenAsDisabledIsTheSameAsNotWritingItAtAll() {
        MenuRefresh.start(holderFor("rows = 1\nrefresh { enabled = false, interval-ticks = 20 }"), scheduler, () -> {});

        assertThat(scheduler.timers).isZero();
    }

    @Test
    void anEnabledRefreshRunsAtItsOwnCadenceInBothTheDelayAndThePeriod() {
        MenuRefresh.start(holderFor("rows = 1\nrefresh { enabled = true, interval-ticks = 20 }"), scheduler, () -> {});

        assertThat(scheduler.timers).isEqualTo(1);
        assertThat(scheduler.period).isEqualTo(Duration.ofSeconds(1));
        assertThat(scheduler.delay)
                .as("the first redraw waits one whole period, so the open itself is not drawn twice")
                .isEqualTo(scheduler.period);
    }

    @Test
    void theRedrawIsTheCallersAndTheEngineOnlySchedulesIt() {
        int[] redraws = {0};
        MenuRefresh.start(
                holderFor("rows = 1\nrefresh { enabled = true, interval-ticks = 1 }"), scheduler, () -> redraws[0]++);

        assertThat(redraws[0])
                .as("scheduling is not running: the task must not fire during the open")
                .isZero();

        Consumer<TaskHandle> task = Objects.requireNonNull(scheduler.task);
        task.accept(scheduler.handle);
        task.accept(scheduler.handle);

        assertThat(redraws[0]).isEqualTo(2);
    }

    @Test
    void theHandleIsParkedOnTheHolderSoAClosingMenuCanCancelIt() {
        MenuHolder holder = holderFor("rows = 1\nrefresh { enabled = true, interval-ticks = 20 }");
        MenuRefresh.start(holder, scheduler, () -> {});

        holder.cancelRefresh();

        assertThat(scheduler.cancels).isEqualTo(1);
    }

    @Test
    void aStaticMenuLeavesTheHolderWithNothingToCancel() {
        MenuHolder holder = holderFor("rows = 1");
        MenuRefresh.start(holder, scheduler, () -> {});

        holder.cancelRefresh();

        assertThat(scheduler.cancels).isZero();
    }
}
