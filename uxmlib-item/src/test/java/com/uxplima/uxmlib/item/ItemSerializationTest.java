package com.uxplima.uxmlib.item;

import static org.assertj.core.api.Assertions.assertThat;

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
}
