package com.uxplima.uxmlib.item;

import java.util.Optional;

import org.bukkit.inventory.ItemStack;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A whole item written down as one line of text. Anywhere a configuration file lets an operator name an item, it
 * usually names a material and nothing else. Sometimes that is not enough: the operator wants the item they are
 * holding, with its name, its lore, its enchantments, and every component on it. This is the token that carries
 * one, so a file can hold either a material name or a captured item in the same field.
 *
 * <p>The token is {@link #PREFIX} followed by the item's Base64 form. The prefix is what tells the two apart, and
 * it is why this class exists: a reader that has to decide whether a string is a material or an item must ask one
 * question in one place, or the writer and the reader will drift apart.
 *
 * <p>Reading draws a line the two failures would otherwise blur. "This string was never a token" is an ordinary
 * negative and comes back empty. "This string says it is a token and will not read" is damage in an operator's
 * file, and it is raised rather than swallowed, because both look identical from outside: an item that quietly is
 * not there. This class knows the difference and nothing else, so it says which one happened and lets the caller,
 * which knows the file and the key, decide what that is worth.
 */
@NullMarked
public final class SerializedItems {

    /** The prefix that marks a serialized item, as opposed to a plain material name. */
    public static final String PREFIX = "b64:";

    private SerializedItems() {}

    /** Whether {@code token} is a serialized item rather than a material name; a null token is not. */
    public static boolean isSerialized(@Nullable String token) {
        return token != null && token.startsWith(PREFIX);
    }

    /** Write {@code item}, with everything on it, as a {@code b64:} token. */
    public static String encode(ItemStack item) {
        return PREFIX + ItemSerialization.toBase64(item);
    }

    /**
     * Read a {@code b64:} token back into its item.
     *
     * <p>Empty means one thing only: {@code token} is not a token. That is an ordinary answer, and it is what every
     * plain material name that comes past here gets.
     *
     * <p>A string that carries the prefix and will not read is a different answer, so it is not folded into the same
     * one. That is somebody's damaged configuration, or a token written by a newer version than this reader, and both
     * of them show up as an item that quietly is not there. This method states it and the caller decides what to do,
     * because the caller is what knows which file and which key the token came from and holds somewhere to say it.
     * A renderer catches and falls back, a config loader may well refuse to start.
     *
     * <p>A token written by an older tool, whose payload carries no version header, still reads: that is
     * {@link ItemSerialization#fromBase64} keeping faith with what is already in operators' files.
     *
     * @throws IllegalArgumentException if {@code token} carries the prefix but its payload will not read
     */
    public static Optional<ItemStack> decode(String token) {
        if (!isSerialized(token)) {
            return Optional.empty();
        }
        return Optional.of(ItemSerialization.fromBase64(token.substring(PREFIX.length())));
    }
}
