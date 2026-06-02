package com.uxplima.uxmlib.gui.input;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

/**
 * The native, packet-free sign backend for {@link PlayerInput}. It places a transient sign block, opens its
 * editor for the player, and restores the original block when the typed lines come back through
 * {@link org.bukkit.event.block.SignChangeEvent SignChangeEvent}. {@code openSign} needs a real
 * {@link Sign} block-state, so the block is briefly written to the world and immediately restored — no NMS,
 * no packets.
 *
 * <p>One instance per {@link PlayerInput}; the per-player saved-block map lives here, not statically.
 */
final class SignPrompt {

    private final Map<UUID, Saved> placed = new ConcurrentHashMap<>();

    /** Place a transient sign at {@code player}'s feet, fill its front with {@code prompt}, and open it. */
    void open(Player player, Component prompt) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(prompt, "prompt");
        Location at = Objects.requireNonNull(player.getLocation(), "location");
        Block block = at.getBlock();
        placed.put(player.getUniqueId(), new Saved(block.getLocation(), block.getBlockData()));
        block.setType(Material.OAK_SIGN, false);
        if (block.getState() instanceof Sign sign) {
            applyPromptLines(sign, prompt);
            sign.update(true, false);
            player.openSign(sign, Side.FRONT);
        }
    }

    /** Restore the block this prompt replaced for {@code player}, if any is still pending. */
    void restore(Player player) {
        Objects.requireNonNull(player, "player");
        Saved saved = placed.remove(player.getUniqueId());
        if (saved != null) {
            Block block = saved.location.getBlock();
            block.setBlockData(saved.data, false);
        }
    }

    private static void applyPromptLines(Sign sign, Component prompt) {
        // Line 0 carries the prompt; the player types on the remaining lines. The editor still lets them
        // overwrite line 0, so the join in PlayerInput keeps whatever they leave.
        List<Component> lines =
                new ArrayList<>(List.of(prompt, Component.empty(), Component.empty(), Component.empty()));
        for (int i = 0; i < 4; i++) {
            sign.getSide(Side.FRONT).line(i, lines.get(i));
        }
    }

    private record Saved(Location location, BlockData data) {
        Saved {
            Objects.requireNonNull(location, "location");
            Objects.requireNonNull(data, "data");
        }
    }
}
