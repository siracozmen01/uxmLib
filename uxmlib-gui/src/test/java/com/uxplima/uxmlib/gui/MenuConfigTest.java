package com.uxplima.uxmlib.gui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.bukkit.Material;

import com.uxplima.uxmlib.gui.config.MenuActions;
import com.uxplima.uxmlib.gui.config.MenuConditions;
import com.uxplima.uxmlib.gui.config.MenuConfig;
import com.uxplima.uxmlib.gui.item.GuiItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/** Covers building a menu from a HOCON config node, including mask layout and named actions. */
class MenuConfigTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static CommentedConfigurationNode parse(String hocon) throws Exception {
        return HoconConfigurationLoader.builder()
                .source(() -> new java.io.BufferedReader(new java.io.StringReader(hocon)))
                .build()
                .load();
    }

    @Test
    void buildsAMaskedMenuFromConfig() throws Exception {
        String hocon =
                """
                title = "<gold>Shop"
                rows = 3
                mask = [ "XXXXXXXXX", "X       X", "XXXXXXXXX" ]
                items {
                  X { material = "GRAY_STAINED_GLASS_PANE", name = " " }
                }
                """;
        SimpleGui gui = MenuConfig.load(parse(hocon), new MenuActions());

        assertThat(gui.size()).isEqualTo(27);
        assertThat(gui.getItem(0)).isNotNull(); // corner from the border mask
        assertThat(gui.getItem(13)).isNull(); // interior space -> untouched
        assertThat(gui.getItem(26)).isNotNull();
    }

    @Test
    void wiresAClickActionFromTheRegistry() throws Exception {
        String hocon =
                """
                rows = 1
                mask = [ "C        " ]
                items {
                  C { material = "BARRIER", name = "<red>Close", action = "close" }
                }
                """;
        boolean[] custom = {false};
        MenuActions actions = new MenuActions().register("close", e -> custom[0] = true);
        SimpleGui gui = MenuConfig.load(parse(hocon), actions);

        GuiItem item = gui.getItem(0);
        assertThat(item).isInstanceOf(GuiItem.Static.class);
        // The 'C' slot is a button wired to the named action.
        var player = MockBukkit.getMock().addPlayer();
        var view = java.util.Objects.requireNonNull(player.openInventory(gui.getInventory()));
        var event = new org.bukkit.event.inventory.InventoryClickEvent(
                view,
                org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER,
                0,
                org.bukkit.event.inventory.ClickType.LEFT,
                org.bukkit.event.inventory.InventoryAction.PICKUP_ALL);
        gui.handleClick(event);
        assertThat(custom[0]).isTrue();
    }

    @Test
    void readsMaterialAndLore() throws Exception {
        String hocon =
                """
                rows = 1
                mask = [ "D        " ]
                items {
                  D { material = "DIAMOND", name = "<aqua>Gem", lore = [ "<gray>line one", "<gray>line two" ] }
                }
                """;
        SimpleGui gui = MenuConfig.load(parse(hocon), new MenuActions());

        GuiItem item = gui.getItem(0);
        assertThat(item).isInstanceOf(GuiItem.Static.class);
        var icon = ((GuiItem.Static) java.util.Objects.requireNonNull(item)).item();
        assertThat(icon.getType()).isEqualTo(Material.DIAMOND);
        assertThat(java.util.Objects.requireNonNull(icon.getItemMeta()).lore()).hasSize(2);
    }

    @Test
    void rejectsAnUnknownAction() throws Exception {
        String hocon =
                """
                rows = 1
                mask = [ "B        " ]
                items {
                  B { material = "STONE", action = "does_not_exist" }
                }
                """;
        assertThatThrownBy(() -> MenuConfig.load(parse(hocon), new MenuActions()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAnUnknownMaterial() throws Exception {
        String hocon =
                """
                rows = 1
                mask = [ "B        " ]
                items {
                  B { material = "NOT_A_REAL_MATERIAL" }
                }
                """;
        assertThatThrownBy(() -> MenuConfig.load(parse(hocon), new MenuActions()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildsAMultiStateItemAndRendersTheFirstMatchingState() throws Exception {
        String hocon =
                """
                rows = 1
                mask = [ "S        " ]
                items {
                  S {
                    states {
                      vip     { condition = "starts-a", material = "EMERALD",  name = "<green>VIP" }
                      default { condition = "always",   material = "REDSTONE", name = "<gray>Member" }
                    }
                  }
                }
                """;
        MenuConditions conditions = new MenuConditions()
                .register("starts-a", ctx -> ctx.viewer().getName().startsWith("A"));
        SimpleGui gui = MenuConfig.load(parse(hocon), new MenuActions(), conditions);

        GuiItem item = gui.getItem(0);
        assertThat(item).isInstanceOf(GuiItem.Stateful.class);

        var alex = MockBukkit.getMock().addPlayer("Alex");
        gui.open(alex);
        assertThat(java.util.Objects.requireNonNull(gui.getInventory().getItem(0))
                        .getType())
                .isEqualTo(Material.EMERALD);

        var steve = MockBukkit.getMock().addPlayer("Steve");
        gui.open(steve);
        assertThat(java.util.Objects.requireNonNull(gui.getInventory().getItem(0))
                        .getType())
                .isEqualTo(Material.REDSTONE);
    }

    @Test
    void multiStateItemWiresPerStateActions() throws Exception {
        String hocon =
                """
                rows = 1
                mask = [ "S        " ]
                items {
                  S {
                    states {
                      only { condition = "always", material = "BARRIER", action = "close" }
                    }
                  }
                }
                """;
        boolean[] clicked = {false};
        MenuActions actions = new MenuActions().register("close", e -> clicked[0] = true);
        SimpleGui gui = MenuConfig.load(parse(hocon), actions, new MenuConditions());

        var player = MockBukkit.getMock().addPlayer();
        var view = java.util.Objects.requireNonNull(player.openInventory(gui.getInventory()));
        var event = new org.bukkit.event.inventory.InventoryClickEvent(
                view,
                org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER,
                0,
                org.bukkit.event.inventory.ClickType.LEFT,
                org.bukkit.event.inventory.InventoryAction.PICKUP_ALL);
        gui.handleClick(event);
        assertThat(clicked[0]).isTrue();
    }

    @Test
    void rejectsAnUnknownConditionInAState() throws Exception {
        String hocon =
                """
                rows = 1
                mask = [ "S        " ]
                items {
                  S { states { a { condition = "ghost", material = "STONE" } } }
                }
                """;
        assertThatThrownBy(() -> MenuConfig.load(parse(hocon), new MenuActions(), new MenuConditions()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void appliesPerMenuTypeLocksFromConfig() throws Exception {
        String hocon =
                """
                rows = 1
                locks = [ "ITEM_TAKE", "ITEM_PLACE" ]
                mask = [ "X        " ]
                items {
                  X { material = "STONE" }
                }
                """;
        SimpleGui gui = MenuConfig.load(parse(hocon), new MenuActions());

        assertThat(gui.allows(InteractionModifier.ITEM_TAKE)).isTrue();
        assertThat(gui.allows(InteractionModifier.ITEM_PLACE)).isTrue();
        assertThat(gui.allows(InteractionModifier.ITEM_DROP)).isFalse();
    }
}
