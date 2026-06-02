package com.uxplima.uxmlib.gui.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmlib.gui.Guis;
import com.uxplima.uxmlib.gui.InteractionModifier;
import com.uxplima.uxmlib.gui.SimpleGui;
import com.uxplima.uxmlib.gui.item.GuiAction;
import com.uxplima.uxmlib.gui.item.GuiItem;
import com.uxplima.uxmlib.item.ItemBuilder;
import com.uxplima.uxmlib.text.Text;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

/**
 * Builds a {@link SimpleGui} from a HOCON/Configurate node, so a server owner can lay out and re-skin a
 * menu in a config file while code keeps the behaviour. The node carries a {@code title}, a {@code rows}
 * count, an optional {@code locks} list (interaction modifiers the menu lets through), a {@code mask} (a
 * list of nine-character rows), and an {@code items} table whose keys are the mask characters; each item
 * gives a {@code material}, optional MiniMessage {@code name} and {@code lore}, and an optional
 * {@code action} resolved from a {@link MenuActions} registry.
 *
 * <pre>{@code
 * title = "<gold>Shop"
 * rows = 3
 * mask = [ "XXXXXXXXX", "X   C   X", "XXXXXXXXX" ]
 * items {
 *   X { material = "GRAY_STAINED_GLASS_PANE", name = " " }
 *   C { material = "BARRIER", name = "<red>Close", action = "close" }
 * }
 * }</pre>
 *
 * <p>An item may instead declare an ordered {@code states} map keyed by a state name; each state carries an
 * icon spec and a named {@code condition} resolved from a {@link MenuConditions} registry. The first state
 * whose condition passes for the viewer renders (this maps onto a {@link GuiItem.Stateful} item), so the
 * same slot can look and behave differently per player — pass the conditions registry to
 * {@link #load(ConfigurationNode, MenuActions, MenuConditions)}.
 *
 * <pre>{@code
 * items {
 *   S {
 *     states {
 *       online  { condition = "is-online",  material = "LIME_DYE", name = "<green>Online" }
 *       offline { condition = "always",     material = "GRAY_DYE", name = "<gray>Offline" }
 *     }
 *   }
 * }
 * }</pre>
 */
public final class MenuConfig {

    private MenuConfig() {}

    /** Build a menu from {@code node}, wiring item actions through {@code actions}; no multi-state items. */
    public static SimpleGui load(ConfigurationNode node, MenuActions actions) {
        return load(node, actions, new MenuConditions());
    }

    /**
     * Build a menu from {@code node}, wiring click behaviour through {@code actions} and state conditions
     * through {@code conditions}. An item that declares a {@code states} map renders the first state whose
     * named condition passes for the viewer; a {@code locks} list lets the named interactions through.
     */
    public static SimpleGui load(ConfigurationNode node, MenuActions actions, MenuConditions conditions) {
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(actions, "actions");
        Objects.requireNonNull(conditions, "conditions");
        Component title = Text.mini(node.node("title").getString(""));
        int rows = node.node("rows").getInt(determineRows(node));
        SimpleGui gui = Guis.gui().title(title).rows(rows).build();
        applyLocks(gui, node.node("locks"));
        Map<Character, GuiItem> legend = readLegend(node.node("items"), actions, conditions);
        gui.filler().pattern(readMask(node.node("mask")), legend);
        return gui;
    }

    private static void applyLocks(SimpleGui gui, ConfigurationNode locksNode) {
        for (String raw : readStringList(locksNode)) {
            gui.allow(parseModifier(raw));
        }
    }

    private static InteractionModifier parseModifier(String raw) {
        try {
            return InteractionModifier.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException("unknown interaction lock in menu config: " + raw, unknown);
        }
    }

    private static int determineRows(ConfigurationNode node) {
        int maskRows = node.node("mask").childrenList().size();
        return Math.min(6, Math.max(1, maskRows));
    }

    private static List<String> readMask(ConfigurationNode maskNode) {
        List<String> mask = new ArrayList<>();
        for (ConfigurationNode row : maskNode.childrenList()) {
            mask.add(row.getString(""));
        }
        return mask;
    }

    private static Map<Character, GuiItem> readLegend(
            ConfigurationNode itemsNode, MenuActions actions, MenuConditions conditions) {
        Map<Character, GuiItem> legend = new HashMap<>();
        for (Map.Entry<Object, ? extends ConfigurationNode> entry :
                itemsNode.childrenMap().entrySet()) {
            String key = String.valueOf(entry.getKey());
            if (key.isEmpty()) {
                continue;
            }
            legend.put(key.charAt(0), readItem(entry.getValue(), actions, conditions));
        }
        return legend;
    }

    private static GuiItem readItem(ConfigurationNode itemNode, MenuActions actions, MenuConditions conditions) {
        ConfigurationNode statesNode = itemNode.node("states");
        if (!statesNode.empty()) {
            return readStateful(statesNode, actions, conditions);
        }
        ItemStack icon = buildIcon(itemNode);
        GuiAction action = resolveAction(itemNode, actions);
        return action == GuiAction.None.INSTANCE ? GuiItem.display(icon) : new GuiItem.Static(icon, action);
    }

    private static GuiItem readStateful(ConfigurationNode statesNode, MenuActions actions, MenuConditions conditions) {
        List<MenuConditions.NamedState> states = new ArrayList<>();
        for (Map.Entry<Object, ? extends ConfigurationNode> entry :
                statesNode.childrenMap().entrySet()) {
            String name = String.valueOf(entry.getKey());
            ConfigurationNode stateNode = entry.getValue();
            String conditionName = stateNode.node("condition").getString("always");
            states.add(new MenuConditions.NamedState(
                    name, conditions.require(conditionName), buildIcon(stateNode), resolveAction(stateNode, actions)));
        }
        return MenuConditions.statefulOf(states);
    }

    private static GuiAction resolveAction(ConfigurationNode node, MenuActions actions) {
        String actionName = node.node("action").getString();
        if (actionName == null) {
            return GuiAction.None.INSTANCE;
        }
        java.util.function.Consumer<org.bukkit.event.inventory.InventoryClickEvent> action = actions.get(actionName);
        if (action == null) {
            throw new IllegalArgumentException("unknown menu action: " + actionName);
        }
        return new GuiAction.Run(action);
    }

    private static ItemStack buildIcon(ConfigurationNode itemNode) {
        Material material = parseMaterial(itemNode.node("material").getString("STONE"));
        ItemBuilder builder = ItemBuilder.of(material);
        String name = itemNode.node("name").getString();
        if (name != null) {
            builder.name(Text.mini(name));
        }
        List<Component> lore = readLore(itemNode.node("lore"));
        if (!lore.isEmpty()) {
            builder.lore(lore);
        }
        int amount = itemNode.node("amount").getInt(1);
        if (amount > 1) {
            builder.amount(amount);
        }
        return builder.build();
    }

    private static List<Component> readLore(ConfigurationNode loreNode) {
        List<Component> lore = new ArrayList<>();
        for (String line : readStringList(loreNode)) {
            lore.add(Text.mini(line));
        }
        return lore;
    }

    private static List<String> readStringList(ConfigurationNode node) {
        try {
            List<String> values = node.getList(String.class);
            return values == null ? List.of() : values;
        } catch (SerializationException notAList) {
            // A scalar where a list is expected is simply treated as absent.
            return List.of();
        }
    }

    private static Material parseMaterial(String raw) {
        Material material = Material.matchMaterial(raw);
        if (material == null) {
            throw new IllegalArgumentException("unknown material in menu config: " + raw);
        }
        return material;
    }
}
