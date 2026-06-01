package com.uxplima.uxmlib.gui;

/**
 * A single class of inventory interaction a {@link Gui} can choose to allow. By default a menu cancels
 * every interaction, so its items cannot be taken, replaced, or dragged out. Allowing a modifier lets
 * that one class of interaction through — for instance a storage menu allows {@link #ITEM_TAKE} and
 * {@link #ITEM_PLACE} so a player can move real items in and out, while a button menu allows none.
 */
public enum InteractionModifier {

    /** Taking an item out of a menu slot. */
    ITEM_TAKE,

    /** Putting an item into a menu slot. */
    ITEM_PLACE,

    /** Swapping the cursor item with a menu slot's item. */
    ITEM_SWAP,

    /** Dropping an item (Q / drop key) while the menu is open. */
    ITEM_DROP
}
