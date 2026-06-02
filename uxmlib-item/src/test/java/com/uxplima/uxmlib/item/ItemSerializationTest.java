package com.uxplima.uxmlib.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class ItemSerializationTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // MockBukkit's serializeAsBytes round-trip is not byte-faithful for Adventure component metadata, so
    // these tests assert what it reliably preserves — material and amount. Full-component fidelity rides
    // on Paper's native serialization, which is exercised on a real server.

    @Test
    void roundTripsTypeAndAmountThroughBytes() {
        // A stackable material so amount > 1 is valid (a sword max-stacks at 1).
        ItemStack original = ItemBuilder.of(Material.DIAMOND).amount(3).build();

        ItemStack restored = ItemSerialization.fromBytes(ItemSerialization.toBytes(original));

        assertThat(restored.getType()).isEqualTo(Material.DIAMOND);
        assertThat(restored.getAmount()).isEqualTo(3);
    }

    @Test
    void roundTripsTypeAndAmountThroughBase64() {
        ItemStack original = ItemBuilder.of(Material.PAPER).amount(7).build();

        String encoded = ItemSerialization.toBase64(original);
        ItemStack restored = ItemSerialization.fromBase64(encoded);

        assertThat(restored.getType()).isEqualTo(Material.PAPER);
        assertThat(restored.getAmount()).isEqualTo(7);
    }

    @Test
    void rejectsInvalidBase64WithAClearError() {
        assertThatThrownBy(() -> ItemSerialization.fromBase64("this is not base64 !!!"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBytesThatAreNotASerializedItem() {
        assertThatThrownBy(() -> ItemSerialization.fromBytes(new byte[] {1, 2, 3, 4}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void stampsAMagicHeaderOnTheBytes() {
        byte[] bytes = ItemSerialization.toBytes(ItemBuilder.of(Material.STONE).build());

        // The first four bytes are the "UXMI" magic; the fifth is the format version.
        assertThat(new byte[] {bytes[0], bytes[1], bytes[2], bytes[3]})
                .isEqualTo("UXMI".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        assertThat(bytes[4]).isEqualTo((byte) 1);
    }

    @Test
    void readsAHeaderlessLegacyBlobForBackCompat() {
        ItemStack original = ItemBuilder.of(Material.GOLD_INGOT).amount(5).build();

        // A blob produced before the header existed: raw Paper bytes, no magic.
        byte[] legacy = original.serializeAsBytes();
        ItemStack restored = ItemSerialization.fromBytes(legacy);

        assertThat(restored.getType()).isEqualTo(Material.GOLD_INGOT);
        assertThat(restored.getAmount()).isEqualTo(5);
    }

    @Test
    void readsAHeaderlessLegacyBase64ForBackCompat() {
        ItemStack original = ItemBuilder.of(Material.EMERALD).amount(2).build();

        String legacy = java.util.Base64.getEncoder().encodeToString(original.serializeAsBytes());
        ItemStack restored = ItemSerialization.fromBase64(legacy);

        assertThat(restored.getType()).isEqualTo(Material.EMERALD);
        assertThat(restored.getAmount()).isEqualTo(2);
    }

    @Test
    void exposesTheStampedDataVersionFromAHeaderedBlob() {
        byte[] bytes = ItemSerialization.toBytes(ItemBuilder.of(Material.STONE).build());

        assertThat(ItemSerialization.dataVersionOf(bytes)).isPresent();
    }

    @Test
    void reportsNoStampedDataVersionForALegacyBlob() {
        byte[] legacy = ItemBuilder.of(Material.STONE).build().serializeAsBytes();

        assertThat(ItemSerialization.dataVersionOf(legacy)).isEmpty();
    }

    @Test
    void roundTripsThroughGzippedBytes() {
        ItemStack original = ItemBuilder.of(Material.DIAMOND).amount(4).build();

        byte[] compressed = ItemSerialization.toCompressedBytes(original);
        ItemStack restored = ItemSerialization.fromCompressedBytes(compressed);

        assertThat(restored.getType()).isEqualTo(Material.DIAMOND);
        assertThat(restored.getAmount()).isEqualTo(4);
    }

    @Test
    void roundTripsThroughGzippedBase64() {
        ItemStack original = ItemBuilder.of(Material.PAPER).amount(9).build();

        String encoded = ItemSerialization.toCompressedBase64(original);
        ItemStack restored = ItemSerialization.fromCompressedBase64(encoded);

        assertThat(restored.getType()).isEqualTo(Material.PAPER);
        assertThat(restored.getAmount()).isEqualTo(9);
    }

    @Test
    void rejectsCompressedBytesThatAreNotGzip() {
        assertThatThrownBy(() -> ItemSerialization.fromCompressedBytes(new byte[] {1, 2, 3, 4}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAHeaderWithAnUnknownFormatVersion() {
        byte[] good = ItemSerialization.toBytes(ItemBuilder.of(Material.STONE).build());
        byte[] tampered = good.clone();
        tampered[4] = (byte) 99; // a format version this reader does not understand

        assertThatThrownBy(() -> ItemSerialization.fromBytes(tampered)).isInstanceOf(IllegalArgumentException.class);
    }
}
