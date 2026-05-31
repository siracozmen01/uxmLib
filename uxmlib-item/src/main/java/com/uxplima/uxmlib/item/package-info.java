/**
 * Item construction. {@link com.uxplima.uxmlib.item.ItemBuilder} is a fluent builder over Paper's
 * {@code ItemStack} (name and lore as Adventure components, enchantments, flags, attributes, durability,
 * persistent data, and player-head skins via {@link com.uxplima.uxmlib.item.SkullData}). Enchantments and
 * attributes are resolved by key through {@link com.uxplima.uxmlib.item.Items}, since Paper 1.21 made them
 * registry entries rather than static constants. Built on the native 1.21+ API — no reflection.
 */
@NullMarked
package com.uxplima.uxmlib.item;

import org.jspecify.annotations.NullMarked;
