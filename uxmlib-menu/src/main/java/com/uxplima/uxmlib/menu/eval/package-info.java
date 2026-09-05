/**
 * Pure evaluation logic for the menu engine, slot priority layering and pagination, that turns a parsed spec
 * into the concrete items a single rendered page should show. Like the {@code spec} model it stays free of
 * {@code org.bukkit} and {@code net.minecraft} so the layering and paging rules can be unit-tested in isolation;
 * that boundary is held by {@code ArchitectureTest.theMenuModelTouchesNoPlatform}, the same rule that fences the
 * {@code spec} model.
 */
@NullMarked
package com.uxplima.uxmlib.menu.eval;

import org.jspecify.annotations.NullMarked;
