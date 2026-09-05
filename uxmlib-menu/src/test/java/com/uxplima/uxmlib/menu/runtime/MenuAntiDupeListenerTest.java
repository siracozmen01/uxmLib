package com.uxplima.uxmlib.menu.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmlib.common.Log;
import com.uxplima.uxmlib.menu.render.MenuItemMark;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * The safety net behind the cancel-all invariant. Nothing should ever put a menu tile in a real inventory, so every
 * assertion here is about a state the engine believes cannot happen, which is exactly why it needs testing: the paths
 * that produce it are a plugin conflict, a crash with a menu open, or a shutdown that writes the player file.
 *
 * <p>Two halves matter equally and only one of them is about dupes. A marked tile must go, or an escaped display item
 * is duplicable. An unmarked item must stay, or the sweep is itself an item-eating bug in every inventory on the
 * server. The second is the one that would end a server, so the tests carry genuine items alongside marked ones
 * rather than sweeping an inventory that holds nothing else.
 */
class MenuAntiDupeListenerTest {

    /** Records what the sweep reported, so a silent sweep can be told from a busy one. */
    private static final class RecordingLog implements Log {

        final List<String> debug = new ArrayList<>();

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {
            debug.add(message);
        }
    }

    private final RecordingLog log = new RecordingLog();

    private MenuAntiDupeListener listener;

    private Player player;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        player = MockBukkit.getMock().addPlayer();
        listener = new MenuAntiDupeListener(log);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /** A display copy of the kind the renderer builds: a real material carrying the engine's mark. */
    private static ItemStack markedTile() {
        return MenuItemMark.mark(new ItemStack(Material.GRAY_STAINED_GLASS_PANE));
    }

    /** Something a player owns. Nothing the engine does marks one of these. */
    private static ItemStack genuine(Material material) {
        return new ItemStack(material, 5);
    }

    private void close() {
        listener.onClose(new InventoryCloseEvent(player.getOpenInventory()));
    }

    private void join() {
        listener.onJoin(new PlayerJoinEvent(player, Component.empty()));
    }

    // -- what the sweep removes ----------------------------------------------------------------------------

    @Test
    void aMarkedTileInThePlayersOwnInventoryIsRemovedOnClose() {
        player.getInventory().setItem(3, markedTile());

        close();

        assertThat(player.getInventory().getItem(3)).isNull();
    }

    @Test
    void aMarkedTileIsRemovedOnJoinToo() {
        player.getInventory().setItem(3, markedTile());

        join();

        assertThat(player.getInventory().getItem(3)).isNull();
    }

    /** A menu open at shutdown can persist a tile into any slot, so every slot the sweep reads must be cleared. */
    @Test
    void everyStorageSlotIsSwept() {
        player.getInventory().setItem(0, markedTile());
        player.getInventory().setItem(17, markedTile());
        player.getInventory().setItem(35, markedTile());

        close();

        assertThat(player.getInventory().getItem(0)).isNull();
        assertThat(player.getInventory().getItem(17)).isNull();
        assertThat(player.getInventory().getItem(35)).isNull();
    }

    /** The armour and off-hand slots are part of the same contents array, and a tile there is as duplicable. */
    @Test
    void theOffHandIsSweptAsWellAsTheMainInventory() {
        player.getInventory().setItemInOffHand(markedTile());

        close();

        ItemStack offHand = player.getInventory().getItemInOffHand();
        assertThat(offHand == null ? Material.AIR : offHand.getType()).isEqualTo(Material.AIR);
    }

    @Test
    void severalMarkedTilesAreAllRemovedRatherThanTheFirstOne() {
        player.getInventory().setItem(1, markedTile());
        player.getInventory().setItem(2, markedTile());
        player.getInventory().setItem(3, markedTile());

        close();

        assertThat(player.getInventory().getItem(1)).isNull();
        assertThat(player.getInventory().getItem(2)).isNull();
        assertThat(player.getInventory().getItem(3)).isNull();
    }

    // -- what the sweep must never touch -------------------------------------------------------------------

    /**
     * The half that would end a server. A sweep that removed an unmarked item would eat a real inventory on every
     * menu close, so the genuine items are asserted present and unchanged rather than merely non-null.
     */
    @Test
    void genuineItemsAreLeftExactlyAsTheyWere() {
        player.getInventory().setItem(0, genuine(Material.DIAMOND));
        player.getInventory().setItem(1, genuine(Material.OAK_LOG));

        close();

        assertThat(player.getInventory().getItem(0)).isEqualTo(genuine(Material.DIAMOND));
        assertThat(player.getInventory().getItem(1)).isEqualTo(genuine(Material.OAK_LOG));
    }

    /** The mixed case is the real one: an escaped tile sitting among things the player owns. */
    @Test
    void anEscapedTileIsTakenAndItsNeighboursAreNot() {
        player.getInventory().setItem(0, genuine(Material.DIAMOND));
        player.getInventory().setItem(1, markedTile());
        player.getInventory().setItem(2, genuine(Material.OAK_LOG));

        close();

        assertThat(player.getInventory().getItem(0)).isEqualTo(genuine(Material.DIAMOND));
        assertThat(player.getInventory().getItem(1)).isNull();
        assertThat(player.getInventory().getItem(2)).isEqualTo(genuine(Material.OAK_LOG));
    }

    /**
     * An item of the very material the engine paints its filler with is still a player's item when it is unmarked.
     * The mark is the whole test, not the material, and this is the assertion that says the two are not confused.
     */
    @Test
    void anUnmarkedItemOfATileMaterialIsAPlayersItemAndStays() {
        player.getInventory().setItem(0, genuine(Material.GRAY_STAINED_GLASS_PANE));

        close();

        assertThat(player.getInventory().getItem(0)).isEqualTo(genuine(Material.GRAY_STAINED_GLASS_PANE));
    }

    /** The window being closed is discarded anyway, and sweeping it would be the engine tidying its own rubbish. */
    @Test
    void theClosingWindowItselfIsNotSwept() {
        org.bukkit.inventory.Inventory window = Bukkit.createInventory(null, 27);
        window.setItem(4, markedTile());
        player.openInventory(window);

        close();

        assertThat(window.getItem(4)).as("the menu window is left alone").isNotNull();
    }

    // -- what it says while doing it -----------------------------------------------------------------------

    /** The ordinary case is every menu close on the server, so it must be silent or the log is the dupe. */
    @Test
    void aSweepThatFindsNothingSaysNothing() {
        player.getInventory().setItem(0, genuine(Material.DIAMOND));

        close();

        assertThat(log.debug).isEmpty();
    }

    /** A marked tile in a real inventory is abnormal, so each one is reported for an operator to trace. */
    @Test
    void eachRemovalIsReportedOnce() {
        player.getInventory().setItem(1, markedTile());
        player.getInventory().setItem(2, markedTile());

        close();

        assertThat(log.debug).hasSize(2);
        assertThat(log.debug).allMatch(line -> line.contains("menu_antidupe_stripped"));
    }
}
