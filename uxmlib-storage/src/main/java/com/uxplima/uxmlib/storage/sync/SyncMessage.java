package com.uxplima.uxmlib.storage.sync;

import java.util.Objects;

/**
 * A logical message — a {@code (channel, payload)} pair — together with a pure wire codec. A networked
 * transport such as Redis pub/sub can subscribe a node to a single physical channel and still demultiplex
 * many logical channels from it: the frame carries the channel name inline so {@link #decode(String)} can
 * recover it without any side table.
 *
 * <p>The frame is length-prefixed: {@code "<channelLength>\n<channel><payload>"}. Prefixing the channel's
 * length (rather than delimiting channel from payload) means the payload may contain any character,
 * including the {@code '\n'} separator, and still round-trips. The codec is deterministic and allocation-
 * light, with no dependency on the transport, so it is unit-tested in isolation.
 */
public record SyncMessage(String channel, String payload) {

    private static final char LENGTH_SEPARATOR = '\n';

    public SyncMessage {
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(payload, "payload");
    }

    /** Frame {@code message} into its self-describing wire form. The inverse of {@link #decode(String)}. */
    public static String encode(SyncMessage message) {
        Objects.requireNonNull(message, "message");
        String channel = message.channel();
        return channel.length() + Character.toString(LENGTH_SEPARATOR) + channel + message.payload();
    }

    /**
     * Parse a frame produced by {@link #encode(SyncMessage)} back into its channel and payload.
     *
     * @throws NullPointerException if {@code frame} is {@code null}
     * @throws IllegalArgumentException if {@code frame} is not a well-formed frame
     */
    public static SyncMessage decode(String frame) {
        Objects.requireNonNull(frame, "frame");
        int separator = frame.indexOf(LENGTH_SEPARATOR);
        if (separator < 0) {
            throw new IllegalArgumentException("frame has no length header");
        }
        int channelLength = parseLength(frame.substring(0, separator));
        int channelStart = separator + 1;
        int channelEnd = channelStart + channelLength;
        if (channelEnd > frame.length()) {
            throw new IllegalArgumentException("declared channel length overruns the frame");
        }
        return new SyncMessage(frame.substring(channelStart, channelEnd), frame.substring(channelEnd));
    }

    private static int parseLength(String header) {
        try {
            int length = Integer.parseInt(header);
            if (length < 0) {
                throw new IllegalArgumentException("negative channel length: " + length);
            }
            return length;
        } catch (NumberFormatException cause) {
            throw new IllegalArgumentException("non-numeric channel length: " + header, cause);
        }
    }
}
