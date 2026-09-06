package com.uxplima.uxmlib.menu.spec;

import java.util.List;
import java.util.Objects;

/**
 * The match rules an {@link ItemDragSpec} validates a dropped cursor item against, carried as plain data so this
 * stays Bukkit-free and plain-JUnit testable. The actual {@code ItemStack} comparison lives runtime-side, in the
 * menu listener; this record only describes what to match. Nothing in {@code spec/} imports {@code org.bukkit}, which
 * is the boundary that keeps it testable, and it is the claim worth making here: six packages in this module do touch
 * Bukkit, so naming the listener as the only one would be false.
 *
 * <p>{@code materials} is a whitelist of uppercase {@code Material} names: an empty list means any material passes.
 * {@code minAmount} is the least stack size the cursor must carry (a value below one is treated as one at match
 * time). {@code nameContains} is a case-insensitive substring the cursor's display name must contain; an empty
 * string skips the name check.
 */
public record ItemRuleSpec(List<String> materials, int minAmount, String nameContains) {

    public ItemRuleSpec {
        materials = List.copyOf(Objects.requireNonNull(materials, "materials"));
        Objects.requireNonNull(nameContains, "nameContains");
    }
}
