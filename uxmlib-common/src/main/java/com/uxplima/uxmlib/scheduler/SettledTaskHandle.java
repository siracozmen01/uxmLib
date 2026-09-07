package com.uxplima.uxmlib.scheduler;

/**
 * The {@link TaskHandle} for work the scheduler settled without the server: it either ran inline because the
 * plugin was past {@code onDisable} and this thread already owned the work, or it was refused because no
 * thread here could run it safely. Either way there is no {@code ScheduledTask} behind it and nothing to
 * cancel.
 */
enum SettledTaskHandle implements TaskHandle {

    /** The task ran to completion on the calling thread, so it is finished rather than cancelled. */
    RAN(false),

    /** The task never started, which is what {@link TaskHandle#isCancelled()} reports as cancelled. */
    REFUSED(true);

    private final boolean cancelled;

    SettledTaskHandle(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public void cancel() {
        // Nothing is pending: the task has already run or was never started.
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }
}
