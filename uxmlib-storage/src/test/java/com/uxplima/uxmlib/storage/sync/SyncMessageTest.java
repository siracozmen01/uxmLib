package com.uxplima.uxmlib.storage.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** The pure wire codec that frames a (channel, payload) pair so one physical transport can carry many. */
class SyncMessageTest {

    @Test
    void encodeThenDecodeRoundTripsChannelAndPayload() {
        SyncMessage original = new SyncMessage("cache:users", "evict:42");

        SyncMessage back = SyncMessage.decode(SyncMessage.encode(original));

        assertThat(back).isEqualTo(original);
        assertThat(back.channel()).isEqualTo("cache:users");
        assertThat(back.payload()).isEqualTo("evict:42");
    }

    @Test
    void payloadMayContainTheDelimiterAndStillRoundTrips() {
        // The frame splits on the first delimiter only, so a payload that itself contains the delimiter
        // (a nested frame, a colon-bearing token) survives intact.
        SyncMessage original = new SyncMessage("events", "a\nb\nc");

        assertThat(SyncMessage.decode(SyncMessage.encode(original))).isEqualTo(original);
    }

    @Test
    void emptyPayloadRoundTrips() {
        SyncMessage original = new SyncMessage("ping", "");

        assertThat(SyncMessage.decode(SyncMessage.encode(original))).isEqualTo(original);
    }

    @Test
    void encodePrefixesChannelLengthSoTheFrameIsSelfDescribing() {
        // 5 = length of "users"; the frame is "<len>\n<channel><payload>".
        assertThat(SyncMessage.encode(new SyncMessage("users", "hi"))).isEqualTo("5\nusershi");
    }

    @Test
    void decodeRejectsAFrameWithoutALengthHeader() {
        assertThatThrownBy(() -> SyncMessage.decode("no-newline-here")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decodeRejectsANonNumericLengthHeader() {
        assertThatThrownBy(() -> SyncMessage.decode("x\nbody")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decodeRejectsALengthThatOverrunsTheFrame() {
        assertThatThrownBy(() -> SyncMessage.decode("99\nshort")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructorRejectsNulls() {
        assertThatThrownBy(() -> new SyncMessage(nullString(), "p")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SyncMessage("c", nullString())).isInstanceOf(NullPointerException.class);
    }

    /** A typed null so the test never passes a literal null to a {@code @NonNull} parameter. */
    @SuppressWarnings("NullAway")
    private static String nullString() {
        return null;
    }
}
