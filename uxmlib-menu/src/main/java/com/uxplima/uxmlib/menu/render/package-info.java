/**
 * The Bukkit shell that turns a resolved menu spec into the {@code ItemStack}s and inventory the player sees.
 * {@link com.uxplima.uxmlib.menu.render.ItemRenderer} resolves one item's
 * material, name, lore, and decoration against the placeholder registry and the locale catalog; the wider
 * renderer assembles those into an open inventory. It is where the pure spec model meets Bukkit item types, and
 * {@code providers/} is the other, for the tokens that name a third-party item rather than a material. What holds is
 * the negative: {@code spec/} and {@code eval/} stay free of {@code org.bukkit}.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmlib.menu.render;
