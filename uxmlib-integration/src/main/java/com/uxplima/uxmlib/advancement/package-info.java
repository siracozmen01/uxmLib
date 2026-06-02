/**
 * A transient toast / advancement-grant API. {@link com.uxplima.uxmlib.advancement.Toast} pops a one-shot
 * advancement toast for any icon without leaving a persistent advancement behind: it registers a synthetic
 * advancement through the native Bukkit data-pack loader, awards its single impossible-trigger criterion to
 * fire the toast, then revokes and removes it a moment later. {@link com.uxplima.uxmlib.advancement.Advancements}
 * grants and revokes already-registered advancements. No packets, no per-version NMS.
 */
@NullMarked
package com.uxplima.uxmlib.advancement;

import org.jspecify.annotations.NullMarked;
