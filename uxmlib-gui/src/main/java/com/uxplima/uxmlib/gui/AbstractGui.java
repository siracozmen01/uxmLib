package com.uxplima.uxmlib.gui;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;

import net.kyori.adventure.text.Component;

import org.jspecify.annotations.Nullable;

/**
 * Shared menu behaviour: holds the slot→item map, lazily builds the backing inventory (so {@code this}
 * never escapes a constructor), renders items into it, and routes clicks. Clicks inside the menu are
 * cancelled before the slot action runs, so an unconfigured menu cannot leak items.
 */
abstract class AbstractGui implements Gui {

    private final Component title;
    private final int size;
    private final @Nullable GuiType type;
    private final Map<Integer, GuiItem> items = new HashMap<>();
    private @Nullable Inventory inventory;
    private @Nullable Consumer<InventoryCloseEvent> closeHandler;
    private @Nullable Consumer<InventoryOpenEvent> openHandler;
    private @Nullable Consumer<InventoryClickEvent> defaultClickHandler;
    private @Nullable Consumer<InventoryClickEvent> outsideClickHandler;
    private final Set<InteractionModifier> allowed = EnumSet.noneOf(InteractionModifier.class);

    AbstractGui(Component title, int rows) {
        this.title = Objects.requireNonNull(title, "title");
        if (rows < 1 || rows > 6) {
            throw new IllegalArgumentException("rows must be 1..6");
        }
        this.size = rows * 9;
        this.type = null;
    }

    AbstractGui(Component title, GuiType type) {
        this.title = Objects.requireNonNull(title, "title");
        this.type = Objects.requireNonNull(type, "type");
        this.size = type.size();
    }

    @Override
    public final Component title() {
        return title;
    }

    @Override
    public final int size() {
        return size;
    }

    @Override
    public void set(int slot, GuiItem item) {
        Objects.requireNonNull(item, "item");
        checkSlot(slot);
        items.put(slot, item);
        if (inventory != null) {
            inventory.setItem(slot, item.item());
        }
    }

    @Override
    public void set(int row, int col, GuiItem item) {
        Objects.requireNonNull(item, "item");
        if (row < 1 || col < 1 || col > 9) {
            throw new IllegalArgumentException("row must be >= 1 and col must be 1..9");
        }
        set((row - 1) * 9 + (col - 1), item);
    }

    @Override
    public void addItem(GuiItem... newItems) {
        Objects.requireNonNull(newItems, "items");
        int slot = 0;
        for (GuiItem item : newItems) {
            Objects.requireNonNull(item, "item");
            while (slot < size && items.containsKey(slot)) {
                slot++;
            }
            if (slot >= size) {
                return; // menu is full; the rest are silently dropped, matching addItem semantics
            }
            set(slot, item);
            slot++;
        }
    }

    @Override
    public @Nullable GuiItem getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public GuiFiller filler() {
        return new GuiFiller(this);
    }

    @Override
    public Gui allow(InteractionModifier modifier) {
        allowed.add(Objects.requireNonNull(modifier, "modifier"));
        return this;
    }

    @Override
    public Gui disallow(InteractionModifier modifier) {
        allowed.remove(Objects.requireNonNull(modifier, "modifier"));
        return this;
    }

    @Override
    public boolean allows(InteractionModifier modifier) {
        return allowed.contains(Objects.requireNonNull(modifier, "modifier"));
    }

    @Override
    public void remove(int slot) {
        checkSlot(slot);
        items.remove(slot);
        if (inventory != null) {
            inventory.clear(slot);
        }
    }

    @Override
    public void clear() {
        items.clear();
        if (inventory != null) {
            inventory.clear();
        }
    }

    @Override
    public void onClose(Consumer<InventoryCloseEvent> handler) {
        this.closeHandler = Objects.requireNonNull(handler, "handler");
    }

    @Override
    public void onOpen(Consumer<InventoryOpenEvent> handler) {
        this.openHandler = Objects.requireNonNull(handler, "handler");
    }

    @Override
    public void onDefaultClick(Consumer<InventoryClickEvent> handler) {
        this.defaultClickHandler = Objects.requireNonNull(handler, "handler");
    }

    @Override
    public void onOutsideClick(Consumer<InventoryClickEvent> handler) {
        this.outsideClickHandler = Objects.requireNonNull(handler, "handler");
    }

    @Override
    public void handleOpen(InventoryOpenEvent event) {
        Consumer<InventoryOpenEvent> handler = openHandler;
        if (handler != null) {
            handler.accept(event);
        }
    }

    @Override
    public void open(HumanEntity viewer) {
        Objects.requireNonNull(viewer, "viewer");
        viewer.openInventory(getInventory());
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        // Cancel unless this class of interaction has been explicitly allowed, so an unconfigured menu
        // never leaks items but a storage-style menu can opt taking/placing back in.
        if (!isAllowed(event)) {
            event.setCancelled(true);
        }
        Inventory clicked = event.getClickedInventory();
        if (clicked == null) {
            // A click outside the inventory window entirely (the grey border area).
            Consumer<InventoryClickEvent> outside = outsideClickHandler;
            if (outside != null) {
                outside.accept(event);
            }
            return;
        }
        if (!clicked.equals(inventory)) {
            return;
        }
        GuiItem item = items.get(event.getSlot());
        if (item != null) {
            item.action().accept(event);
            return;
        }
        Consumer<InventoryClickEvent> fallback = defaultClickHandler;
        if (fallback != null) {
            fallback.accept(event);
        }
    }

    private boolean isAllowed(InventoryClickEvent event) {
        if (allowed.isEmpty()) {
            return false;
        }
        InteractionModifier modifier = modifierFor(event.getAction());
        return modifier != null && allowed.contains(modifier);
    }

    private static @Nullable InteractionModifier modifierFor(InventoryAction action) {
        return switch (action) {
            case PICKUP_ALL,
                    PICKUP_HALF,
                    PICKUP_SOME,
                    PICKUP_ONE,
                    MOVE_TO_OTHER_INVENTORY,
                    COLLECT_TO_CURSOR -> InteractionModifier.ITEM_TAKE;
            case PLACE_ALL, PLACE_SOME, PLACE_ONE -> InteractionModifier.ITEM_PLACE;
            case SWAP_WITH_CURSOR, HOTBAR_SWAP, HOTBAR_MOVE_AND_READD -> InteractionModifier.ITEM_SWAP;
            case DROP_ALL_SLOT, DROP_ONE_SLOT, DROP_ALL_CURSOR, DROP_ONE_CURSOR -> InteractionModifier.ITEM_DROP;
            default -> null;
        };
    }

    @Override
    public void handleClose(InventoryCloseEvent event) {
        Consumer<InventoryCloseEvent> handler = closeHandler;
        if (handler != null) {
            handler.accept(event);
        }
    }

    @Override
    public final Inventory getInventory() {
        Inventory inv = inventory;
        if (inv == null) {
            inv = type == null
                    ? Bukkit.createInventory(this, size, title)
                    : Bukkit.createInventory(this, type.inventoryType(), title);
            inventory = inv;
            render(inv);
        }
        return inv;
    }

    /** The item map, for subclasses that render derived content (e.g. pagination). */
    final Map<Integer, GuiItem> items() {
        return items;
    }

    /** The live inventory if it has been built, else {@code null}. */
    final @Nullable Inventory liveInventory() {
        return inventory;
    }

    private void render(Inventory inv) {
        for (Map.Entry<Integer, GuiItem> entry : items.entrySet()) {
            inv.setItem(entry.getKey(), entry.getValue().item());
        }
    }

    private void checkSlot(int slot) {
        if (slot < 0 || slot >= size) {
            throw new IllegalArgumentException("slot must be 0.." + (size - 1));
        }
    }
}
