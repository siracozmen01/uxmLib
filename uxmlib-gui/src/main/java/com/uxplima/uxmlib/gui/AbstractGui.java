package com.uxplima.uxmlib.gui;

import java.time.Duration;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
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

    private Component title;
    private final int size;
    private final @Nullable GuiType type;
    private final Map<Integer, GuiItem> items = new HashMap<>();
    private @Nullable Inventory inventory;
    private @Nullable Consumer<InventoryCloseEvent> closeHandler;
    private @Nullable Consumer<InventoryOpenEvent> openHandler;
    private @Nullable Consumer<InventoryClickEvent> defaultClickHandler;
    private @Nullable Consumer<InventoryClickEvent> outsideClickHandler;
    private final Set<InteractionModifier> allowed = EnumSet.noneOf(InteractionModifier.class);
    private long ticks;
    private @Nullable Duration autoRefresh;
    private long refreshEveryTicks = 1L;
    private GuiSound sounds = GuiSound.NONE;
    // Set while updateTitle rebuilds the inventory, so the internal close/reopen does not look like a real
    // close or open to the user's handlers, sounds, or the tick registry.
    private boolean reopening;

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
            GuiRender.writeSlot(inventory, this, slot, item);
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
        GuiRegistry.onOpen(this);
        if (reopening) {
            return; // an internal title-change reopen, not a user-visible open
        }
        sounds.playOpen(event.getPlayer());
        Consumer<InventoryOpenEvent> handler = openHandler;
        if (handler != null) {
            handler.accept(event);
        }
    }

    /** Whether this menu has animated or auto-refresh content and is currently being viewed. */
    boolean needsTicking() {
        return (autoRefresh != null || hasAnimatedContent())
                && inventory != null
                && !inventory.getViewers().isEmpty();
    }

    final boolean hasAnimatedContent() {
        return items.values().stream().anyMatch(GuiItem.Animated.class::isInstance);
    }

    @Override
    public void open(HumanEntity viewer) {
        Objects.requireNonNull(viewer, "viewer");
        Inventory inv = getInventory();
        // Resolve dynamic/stateful/animated items for this specific viewer before showing the menu.
        if (viewer instanceof org.bukkit.entity.Player player) {
            GuiRender.renderAll(inv, this, items, player);
        }
        viewer.openInventory(inv);
    }

    @Override
    public void close(HumanEntity viewer) {
        GuiRender.close(inventory, Objects.requireNonNull(viewer, "viewer"));
    }

    @Override
    public void closeAll() {
        GuiRender.closeAll(inventory);
    }

    @Override
    public void updateTitle(Component newTitle) {
        Objects.requireNonNull(newTitle, "title");
        this.title = newTitle;
        Inventory old = inventory;
        if (old == null) {
            return; // not built yet; the new title will be used when it is created
        }
        // Bukkit fixes a title at creation, so rebuild the inventory and reopen it for current viewers.
        // Guard the close/reopen so it doesn't fire the user's open/close handlers or the open sound.
        this.inventory = null;
        this.reopening = true;
        try {
            GuiRender.reopen(old, getInventory());
        } finally {
            this.reopening = false;
        }
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        boolean hitItem =
                GuiClick.route(this, inventory, items, allowed, defaultClickHandler, outsideClickHandler, event);
        if (hitItem) {
            sounds.playClick(event.getWhoClicked());
        }
    }

    /** Set the click/open feedback sounds for this menu. */
    final void sounds(GuiSound newSounds) {
        this.sounds = Objects.requireNonNull(newSounds, "sounds");
    }

    @Override
    public void handleClose(InventoryCloseEvent event) {
        if (reopening) {
            return; // the close half of an internal title-change reopen
        }
        // Stop ticking once the last viewer leaves (getViewers still includes the closing player here,
        // so one-or-fewer means this close empties the menu).
        if (inventory != null && inventory.getViewers().size() <= 1) {
            GuiRegistry.onClose(this);
        }
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
        GuiRender.renderAll(inv, this, items, GuiRender.firstViewer(inv));
    }

    @Override
    public long ticks() {
        return ticks;
    }

    /**
     * Re-resolve every item for the current viewer and rewrite the open inventory in place. Note: a menu
     * with dynamic/stateful/animated content is single-viewer — open one instance per player (a navigator
     * does this for you). With a shared inventory only the static items are correct for every viewer.
     */
    @Override
    public void refresh() {
        Inventory inv = inventory;
        if (inv != null) {
            render(inv);
        }
    }

    /**
     * Advance the animation clock and, on the configured interval, re-render only the changeable items.
     * Static slots are left untouched, and an unchanged icon is not rewritten, so the tick path does the
     * least work it can.
     */
    final void tick() {
        ticks++;
        Inventory inv = inventory;
        if (inv == null || ticks % refreshEveryTicks != 0) {
            return;
        }
        Player viewer = GuiRender.firstViewer(inv);
        if (viewer != null) {
            GuiRender.renderDynamic(inv, this, items, viewer);
        }
    }

    /** Set how often this menu re-renders while open ({@code null} = every tick, for animations). */
    final void autoRefresh(@Nullable Duration interval) {
        this.autoRefresh = interval;
        this.refreshEveryTicks = interval == null ? 1L : Math.max(1L, interval.toMillis() / 50L);
    }

    private void checkSlot(int slot) {
        if (slot < 0 || slot >= size) {
            throw new IllegalArgumentException("slot must be 0.." + (size - 1));
        }
    }
}
