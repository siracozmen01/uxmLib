package com.uxplima.uxmlib.storage.sync;

/**
 * A handle to one {@link DataSynchronizer#subscribe subscription}. Call {@link #unsubscribe()} to stop
 * delivery to that one handler without disturbing other subscribers on the same channel. Unsubscribing
 * twice is a no-op.
 */
@FunctionalInterface
public interface Subscription {

    /** Stop delivering messages to the handler this handle represents. Idempotent. */
    void unsubscribe();
}
