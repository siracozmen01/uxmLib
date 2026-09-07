package com.uxplima.uxmlib.condition.action;

/**
 * Thrown when a {@link CostAction} cannot be paid: the wallet refused the withdrawal, or the item store did
 * not hold enough. Nothing was taken when this is thrown, and an {@link ActionList} that throws it stops
 * there, so the rewards behind an unpaid cost never run.
 *
 * <p>It is unchecked because a cost failure is a run-time state of the world, not a branch every call site
 * should be forced to write out. Failing loudly is deliberate: a take that quietly did nothing and let the
 * reward run is how a shop gives its stock away.
 */
public class ActionCostException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** A failure naming the cost that could not be met. */
    public ActionCostException(String message) {
        super(message);
    }
}
