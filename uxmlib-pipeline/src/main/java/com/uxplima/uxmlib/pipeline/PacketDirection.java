package com.uxplima.uxmlib.pipeline;

/** Which way a packet is travelling relative to the server. */
public enum PacketDirection {

    /** Server to client (an outbound {@code write} on the channel). */
    OUTBOUND,

    /** Client to server (an inbound {@code channelRead} on the channel). */
    INBOUND
}
