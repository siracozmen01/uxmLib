package com.uxplima.uxmlib.storage.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Pure round-trip checks for the {@link Codecs} helpers: the {@link EnumSet}/bitmask pair, the
 * Mojang-ordered UUID/{@code int[4]} pair, and the byte-format auto-detect.
 */
class CodecsTest {

    enum Flag {
        READ,
        WRITE,
        EXECUTE,
        DELETE
    }

    @Test
    void enumSetEncodesEachOrdinalAsABit() {
        int mask = Codecs.enumSetToBitmask(EnumSet.of(Flag.READ, Flag.EXECUTE));
        // READ is ordinal 0 (bit 1), EXECUTE is ordinal 2 (bit 4).
        assertThat(mask).isEqualTo(0b101);
    }

    @Test
    void emptyEnumSetIsZeroAndBack() {
        assertThat(Codecs.enumSetToBitmask(EnumSet.noneOf(Flag.class))).isZero();
        assertThat(Codecs.bitmaskToEnumSet(0, Flag.class)).isEmpty();
    }

    @Test
    void enumSetRoundTripsThroughTheBitmask() {
        EnumSet<Flag> original = EnumSet.of(Flag.WRITE, Flag.DELETE);
        int mask = Codecs.enumSetToBitmask(original);
        assertThat(Codecs.bitmaskToEnumSet(mask, Flag.class)).isEqualTo(original);
    }

    @Test
    void bitmaskDecodeIgnoresBitsWithNoConstant() {
        // Only bits 0..3 map to a Flag; a stray high bit must not produce a phantom or throw.
        EnumSet<Flag> decoded = Codecs.bitmaskToEnumSet(0b1_0000 | 0b0010, Flag.class);
        assertThat(decoded).containsExactly(Flag.WRITE);
    }

    @Test
    void bitmaskRejectsAnEnumWiderThanThirtyTwoConstants() {
        assertThatIllegalArgumentException().isThrownBy(() -> Codecs.bitmaskToEnumSet(1, Wide.class));
    }

    @Test
    void uuidEncodesInMojangOrder() {
        UUID id = new UUID(0x0011223344556677L, 0x8899AABBCCDDEEFFL);
        int[] ints = Codecs.uuidToIntArray(id);
        assertThat(ints).containsExactly(0x00112233, 0x44556677, 0x8899AABB, 0xCCDDEEFF);
    }

    @Test
    void uuidRoundTripsThroughTheIntArray() {
        UUID id = UUID.randomUUID();
        assertThat(Codecs.uuidFromIntArray(Codecs.uuidToIntArray(id))).isEqualTo(id);
    }

    @Test
    void uuidFromIntArrayRejectsAWrongLength() {
        assertThatIllegalArgumentException().isThrownBy(() -> Codecs.uuidFromIntArray(new int[] {1, 2, 3}));
    }

    @Test
    void detectsSixteenRawBytesAsBinary() {
        byte[] raw = new byte[16];
        assertThat(Codecs.detectUuidBytes(raw)).isEqualTo(Codecs.ByteForm.BINARY);
    }

    @Test
    void detectsHyphenatedTextAsText() {
        byte[] text = UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);
        assertThat(Codecs.detectUuidBytes(text)).isEqualTo(Codecs.ByteForm.TEXT);
    }

    @Test
    void detectsAnUnrecognisedLengthAsUnknown() {
        assertThat(Codecs.detectUuidBytes(new byte[] {1, 2, 3})).isEqualTo(Codecs.ByteForm.UNKNOWN);
    }

    @Test
    @SuppressWarnings("NullAway") // intentionally passes null to assert the requireNonNull guard fires
    void detectIsRobustAgainstNull() {
        assertThatNullPointerException().isThrownBy(() -> Codecs.detectUuidBytes(null));
    }

    /** An enum with more than 32 constants, to exercise the bitmask width guard. */
    enum Wide {
        C00,
        C01,
        C02,
        C03,
        C04,
        C05,
        C06,
        C07,
        C08,
        C09,
        C10,
        C11,
        C12,
        C13,
        C14,
        C15,
        C16,
        C17,
        C18,
        C19,
        C20,
        C21,
        C22,
        C23,
        C24,
        C25,
        C26,
        C27,
        C28,
        C29,
        C30,
        C31,
        C32
    }
}
