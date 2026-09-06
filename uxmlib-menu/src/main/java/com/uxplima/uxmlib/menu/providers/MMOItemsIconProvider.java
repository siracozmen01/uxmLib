package com.uxplima.uxmlib.menu.providers;

import java.util.Optional;

import org.bukkit.Server;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmlib.common.Log;
import org.jspecify.annotations.Nullable;

/**
 * Resolves an {@code mmoitems:<TYPE>:<ID>} spec to its MMOItems item. MMOItems is keyed by a type plus an id, so the
 * bare id here is itself {@code <TYPE>:<ID>} (split on the first colon); a spec missing either half is malformed and
 * yields no item. The item is built through the singleton {@code net.Indyuce.mmoitems.MMOItems} (its public static
 * {@code plugin} field), whose {@code getItem(String type, String id)} returns the built {@link ItemStack} (or {@code
 * null} for an unknown type/id).
 *
 * <p>No {@code net.Indyuce} type is named here (the SDK is reached only by string class-name through reflection) so a
 * server without MMOItems loads none of its classes and the present-guard in {@link ReflectiveItemProvider} short-
 * circuits before any lookup runs.
 */
final class MMOItemsIconProvider extends ReflectiveItemProvider {

    private static final String PLUGIN_NAME = "MMOItems";
    private static final String PREFIX = "mmoitems:";
    private static final String MMOITEMS_CLASS = "net.Indyuce.mmoitems.MMOItems";

    MMOItemsIconProvider(Server server, Log log) {
        super(PLUGIN_NAME, PREFIX, server, log);
    }

    /** The two halves an {@code mmoitems:} value carries, both already trimmed. */
    record TypeAndId(String type, String id) {}

    /**
     * Split an {@code mmoitems:} value into its type and its id. Both halves are judged after trimming rather than
     * by where the colon sits, because an operator may write {@code SWORD : CUTLASS} and a half that is only spaces
     * is as absent as a half that is not there. Empty when either half is missing, which the caller turns into no
     * item at all.
     *
     * <p>Kept out of {@link #lookup} so the one piece of this class an operator can get wrong is readable without
     * the plugin: everything else here is reflection that needs MMOItems on the server to run at all.
     */
    static Optional<TypeAndId> split(String value) {
        int separator = value.indexOf(':');
        if (separator < 0) {
            return Optional.empty();
        }
        String type = value.substring(0, separator).trim();
        String id = value.substring(separator + 1).trim();
        if (type.isEmpty() || id.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new TypeAndId(type, id));
    }

    @Override
    protected @Nullable ItemStack lookup(String id) throws ReflectiveOperationException {
        TypeAndId parts = split(id).orElse(null);
        if (parts == null) {
            return null;
        }
        Class<?> mmoItems = Class.forName(MMOITEMS_CLASS);
        Object plugin = mmoItems.getField("plugin").get(null);
        return (ItemStack)
                mmoItems.getMethod("getItem", String.class, String.class).invoke(plugin, parts.type(), parts.id());
    }
}
