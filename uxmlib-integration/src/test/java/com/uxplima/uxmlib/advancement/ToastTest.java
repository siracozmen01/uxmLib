package com.uxplima.uxmlib.advancement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Tests the immutable {@link Toast} spec and its JSON serialisation. Runs under MockBukkit because resolving
 * a {@link Material} key and (for the {@code ItemStack} overload) constructing an item both go through the
 * server registry; the Gson component serialiser stands alone but is exercised here too.
 */
class ToastTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void serialisesIconKeyTitleAndFrameIntoTheAdvancementJson() {
        Toast toast = Toast.builder()
                .icon(Material.DIAMOND)
                .title(Component.text("Welcome"))
                .frame(AdvancementFrame.CHALLENGE)
                .build();
        String json = toast.toJson();
        assertThat(json)
                .contains("\"icon\":{\"id\":\"minecraft:diamond\"}")
                .contains("\"frame\":\"challenge\"")
                .contains("Welcome")
                .contains("\"trigger\":\"minecraft:impossible\"");
    }

    @Test
    void descriptionDefaultsToEmptyAndFrameToTask() {
        Toast toast =
                Toast.builder().icon(Material.STONE).title(Component.text("Hi")).build();
        assertThat(toast.description()).isEqualTo(Component.empty());
        assertThat(toast.frame()).isEqualTo(AdvancementFrame.TASK);
        assertThat(toast.toJson()).contains("\"frame\":\"task\"");
    }

    @Test
    void iconFromItemStackUsesOnlyTheMaterial() {
        Toast toast = Toast.builder()
                .icon(new ItemStack(Material.GOLDEN_APPLE, 5))
                .title(Component.text("Hi"))
                .build();
        assertThat(toast.icon()).isEqualTo(Material.GOLDEN_APPLE);
        assertThat(toast.toJson()).contains("\"id\":\"minecraft:golden_apple\"");
    }

    @Test
    void buildRequiresIconAndTitle() {
        assertThatThrownBy(() -> Toast.builder().title(Component.text("Hi")).build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("icon");
        assertThatThrownBy(() -> Toast.builder().icon(Material.STONE).build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("title");
    }

    @Test
    void unboundBuilderCannotShow() {
        // Toast.builder() has no Toasts service behind it, so show(player) must refuse rather than NPE on a
        // null service. The bound path (Toasts#builder) is exercised by the wiring smoke test.
        Toast.Builder builder = Toast.builder().icon(Material.STONE).title(Component.text("Hi"));
        PlayerMock player = server.addPlayer();
        assertThatThrownBy(() -> builder.show(player))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unbound");
    }
}
