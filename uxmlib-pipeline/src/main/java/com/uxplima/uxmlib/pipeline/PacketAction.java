package com.uxplima.uxmlib.pipeline;

/**
 * A listener's verdict on a packet. {@link #PASS} lets it continue through the pipeline; {@link #CANCEL}
 * drops it (the interceptor will not forward it on). The registry folds many listeners' verdicts together:
 * any single {@code CANCEL} cancels the packet, mirroring a veto.
 */
public enum PacketAction {

    /** Let the packet continue unchanged. */
    PASS,

    /** Drop the packet; it is not forwarded further along the pipeline. */
    CANCEL;

    /** {@code true} if this verdict cancels the packet. */
    public boolean cancels() {
        return this == CANCEL;
    }
}
