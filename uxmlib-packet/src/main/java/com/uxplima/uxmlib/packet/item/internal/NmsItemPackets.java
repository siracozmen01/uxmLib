package com.uxplima.uxmlib.packet.item.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;

import org.bukkit.craftbukkit.inventory.CraftItemStack;

import com.uxplima.uxmlib.packet.item.ItemPackets;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket;
import net.minecraft.network.protocol.game.ClientboundSetCursorItemPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.jspecify.annotations.Nullable;

/**
 * The Mojang-mapped half of the item view: the five clientbound packets that carry an item a client draws a
 * tooltip for, read apart and built again around the items the view handed back.
 *
 * <p>The five are the whole inventory surface: the single slot, the whole container, the item on the cursor,
 * the player-inventory slot the server sets directly, and a villager's trade list. A packet not on that list
 * is not touched, and two are left off on purpose:
 *
 * <ul>
 *   <li>{@code ClientboundSetEquipmentPacket}, the armour and the held item on somebody else's body. It
 *       carries a private sanitise flag with no getter, and Paper reads that flag to decide how much of the
 *       item it hides from the client for anti-cheat. Rebuilding the packet would have to guess it, and
 *       guessing it wrong turns Paper's item obfuscation off without saying so. Worn items draw no tooltip
 *       anyway, so there is nothing to read there and nothing lost by leaving it alone.
 *   <li>Anything that carries an item inside entity metadata, an item frame or a dropped item. Those draw a
 *       name at most, never lore, so a view would cost work on every metadata packet and change nothing a
 *       player reads.
 * </ul>
 *
 * <p>A trade's <em>result</em> goes through the view and its <em>cost</em> does not. A cost is a component
 * predicate the client matches items against, not a tooltip, and rewriting one would make a trade look
 * unpayable.
 *
 * <p>Nothing here mutates a packet in place. A server packet is written to more than one connection when more
 * than one player watches the same container, so editing one would hand every viewer the first viewer's copy.
 * Every rewrite builds a new packet and leaves the original untouched.
 */
public final class NmsItemPackets implements ItemPackets {

    @Override
    public boolean carriesItems(Object packet) {
        Objects.requireNonNull(packet, "packet");
        return packet instanceof ClientboundContainerSetSlotPacket
                || packet instanceof ClientboundContainerSetContentPacket
                || packet instanceof ClientboundSetPlayerInventoryPacket
                || packet instanceof ClientboundSetCursorItemPacket
                || packet instanceof ClientboundMerchantOffersPacket;
    }

    @Override
    public @Nullable Object withItems(Object packet, UnaryOperator<org.bukkit.inventory.ItemStack> shown) {
        Objects.requireNonNull(packet, "packet");
        Objects.requireNonNull(shown, "shown");
        if (packet instanceof ClientboundContainerSetSlotPacket slot) {
            return rebuild(slot, shown);
        }
        if (packet instanceof ClientboundContainerSetContentPacket content) {
            return rebuild(content, shown);
        }
        if (packet instanceof ClientboundSetPlayerInventoryPacket inventory) {
            return rebuild(inventory, shown);
        }
        if (packet instanceof ClientboundSetCursorItemPacket cursor) {
            return rebuild(cursor, shown);
        }
        if (packet instanceof ClientboundMerchantOffersPacket offers) {
            return rebuild(offers, shown);
        }
        return null;
    }

    private static @Nullable Object rebuild(
            ClientboundContainerSetSlotPacket packet, UnaryOperator<org.bukkit.inventory.ItemStack> shown) {
        @Nullable ItemStack view = shown(packet.getItem(), shown);
        if (view == null) {
            return null;
        }
        return new ClientboundContainerSetSlotPacket(
                packet.getContainerId(), packet.getStateId(), packet.getSlot(), view);
    }

    private static @Nullable Object rebuild(
            ClientboundContainerSetContentPacket packet, UnaryOperator<org.bukkit.inventory.ItemStack> shown) {
        List<ItemStack> items = packet.items();
        @Nullable List<ItemStack> drawn = null;
        for (int slot = 0; slot < items.size(); slot++) {
            @Nullable ItemStack view = shown(items.get(slot), shown);
            if (view == null) {
                continue;
            }
            if (drawn == null) {
                drawn = new ArrayList<>(items);
            }
            drawn.set(slot, view);
        }
        @Nullable ItemStack carried = shown(packet.carriedItem(), shown);
        if (drawn == null && carried == null) {
            return null;
        }
        return new ClientboundContainerSetContentPacket(
                packet.containerId(),
                packet.stateId(),
                drawn == null ? items : drawn,
                carried == null ? packet.carriedItem() : carried);
    }

    private static @Nullable Object rebuild(
            ClientboundSetPlayerInventoryPacket packet, UnaryOperator<org.bukkit.inventory.ItemStack> shown) {
        @Nullable ItemStack view = shown(packet.contents(), shown);
        return view == null ? null : new ClientboundSetPlayerInventoryPacket(packet.slot(), view);
    }

    private static @Nullable Object rebuild(
            ClientboundSetCursorItemPacket packet, UnaryOperator<org.bukkit.inventory.ItemStack> shown) {
        @Nullable ItemStack view = shown(packet.contents(), shown);
        return view == null ? null : new ClientboundSetCursorItemPacket(view);
    }

    private static @Nullable Object rebuild(
            ClientboundMerchantOffersPacket packet, UnaryOperator<org.bukkit.inventory.ItemStack> shown) {
        MerchantOffers offers = packet.getOffers();
        @Nullable MerchantOffers drawn = null;
        for (int index = 0; index < offers.size(); index++) {
            MerchantOffer offer = offers.get(index);
            @Nullable ItemStack view = shown(offer.getResult(), shown);
            if (view == null) {
                continue;
            }
            if (drawn == null) {
                drawn = new MerchantOffers();
                drawn.addAll(offers);
            }
            drawn.set(index, withResult(offer, view));
        }
        if (drawn == null) {
            return null;
        }
        return new ClientboundMerchantOffersPacket(
                packet.getContainerId(),
                drawn,
                packet.getVillagerLevel(),
                packet.getVillagerXp(),
                packet.showProgress(),
                packet.canRestock());
    }

    /**
     * The same offer with a different result. Every field is carried over by hand because the constructor
     * that takes a result takes only some of them: a trade rebuilt with a lost discount, a lost demand or a
     * lost experience reward is a trade whose price changed, which is not something a display may do.
     */
    private static MerchantOffer withResult(MerchantOffer offer, ItemStack result) {
        MerchantOffer drawn = new MerchantOffer(
                offer.getItemCostA(),
                offer.getItemCostB(),
                result,
                offer.getUses(),
                offer.getMaxUses(),
                offer.getXp(),
                offer.getPriceMultiplier(),
                offer.getDemand());
        drawn.rewardExp = offer.shouldRewardExp();
        drawn.setSpecialPriceDiff(offer.getSpecialPriceDiff());
        drawn.ignoreDiscounts = offer.ignoreDiscounts;
        return drawn;
    }

    /**
     * One item through the view. Returns {@code null} when nothing changed, which is what lets the caller
     * forward the original packet rather than build a copy of it.
     */
    private static @Nullable ItemStack shown(ItemStack carried, UnaryOperator<org.bukkit.inventory.ItemStack> shown) {
        if (carried.isEmpty()) {
            return null;
        }
        org.bukkit.inventory.ItemStack real = CraftItemStack.asBukkitCopy(carried);
        org.bukkit.inventory.ItemStack view = Objects.requireNonNull(shown.apply(real), "view");
        return view.equals(real) ? null : CraftItemStack.asNMSCopy(view);
    }
}
