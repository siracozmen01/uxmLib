package com.uxplima.uxmlib.gui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/** Covers animated-item frame selection driven by the menu's tick clock. */
class AnimatedItemTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static List<ItemStack> frames() {
        return List.of(new ItemStack(Material.RED_WOOL), new ItemStack(Material.GREEN_WOOL));
    }

    @Test
    void showsTheFirstFrameAtTickZero() {
        SimpleGui gui = Guis.gui().rows(1).build();
        gui.set(0, GuiItem.animated(frames(), Duration.ofMillis(50)));

        Player player = MockBukkit.getMock().addPlayer();
        gui.open(player);

        assertThat(java.util.Objects.requireNonNull(gui.getInventory().getItem(0))
                        .getType())
                .isEqualTo(Material.RED_WOOL);
    }

    @Test
    void advancesFrameAfterTicks() {
        SimpleGui gui = Guis.gui().rows(1).build();
        gui.set(0, GuiItem.animated(frames(), Duration.ofMillis(50))); // one frame per tick

        Player player = MockBukkit.getMock().addPlayer();
        gui.open(player);

        gui.tick(); // ticks -> 1, second frame
        assertThat(java.util.Objects.requireNonNull(gui.getInventory().getItem(0))
                        .getType())
                .isEqualTo(Material.GREEN_WOOL);

        gui.tick(); // ticks -> 2, wraps back to first frame
        assertThat(java.util.Objects.requireNonNull(gui.getInventory().getItem(0))
                        .getType())
                .isEqualTo(Material.RED_WOOL);
    }

    @Test
    void hasAnimatedContentIsDetected() {
        SimpleGui plain = Guis.gui().rows(1).build();
        plain.set(0, GuiItem.display(new ItemStack(Material.STONE)));
        assertThat(plain.hasAnimatedContent()).isFalse();

        SimpleGui animated = Guis.gui().rows(1).build();
        animated.set(0, GuiItem.animated(frames(), Duration.ofMillis(100)));
        assertThat(animated.hasAnimatedContent()).isTrue();
    }

    @Test
    void rejectsEmptyFrames() {
        assertThatThrownBy(() -> GuiItem.animated(List.of(), Duration.ofMillis(50)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void autoRefreshIntervalIsHonoured() {
        // A 200ms interval = every 4 ticks; ticks 1-3 must not re-render, tick 4 must.
        SimpleGui gui = Guis.gui().rows(1).autoRefresh(Duration.ofMillis(200)).build();
        int[] renders = {0};
        // A dynamic item that counts how often it is resolved.
        gui.set(0, GuiItem.dynamic(ctx -> {
            renders[0]++;
            return new ItemStack(Material.STONE);
        }));
        Player player = MockBukkit.getMock().addPlayer();
        gui.open(player); // initial render resolves once
        int afterOpen = renders[0];

        gui.tick(); // tick 1 -> gated out
        gui.tick(); // tick 2 -> gated out
        gui.tick(); // tick 3 -> gated out
        assertThat(renders[0]).isEqualTo(afterOpen); // no extra resolves yet
        gui.tick(); // tick 4 -> renders
        assertThat(renders[0]).isGreaterThan(afterOpen);
    }

    @Test
    void tickDoesNotRewriteStaticSlots() {
        SimpleGui gui = Guis.gui().rows(1).build();
        ItemStack stoneStack = new ItemStack(Material.STONE);
        gui.set(0, GuiItem.display(stoneStack));
        gui.set(1, GuiItem.animated(frames(), Duration.ofMillis(50)));
        Player player = MockBukkit.getMock().addPlayer();
        gui.open(player);

        ItemStack beforeStatic = gui.getInventory().getItem(0);
        gui.tick();
        // The static slot's stack is left as-is (renderDynamic skips Static items).
        assertThat(gui.getInventory().getItem(0)).isEqualTo(beforeStatic);
    }
}
