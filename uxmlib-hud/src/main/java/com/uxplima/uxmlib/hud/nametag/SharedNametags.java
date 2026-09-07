package com.uxplima.uxmlib.hud.nametag;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.jspecify.annotations.Nullable;

/**
 * One registry for one server, whichever plugin loaded first.
 *
 * <p>{@link NametagRegistry} ends the fight between two plugins that both want a player's scoreboard team,
 * but only inside one jar. Every plugin of ours shades uxmLib and relocates it, so
 * {@code com.uxplima.a.libs.uxmlib...NametagRegistry} and {@code com.uxplima.b.libs...NametagRegistry} are two
 * unrelated classes holding two registries and creating two teams. The plugins were composing into one name
 * each, separately, and the second still lost.
 *
 * <p>This settles it the way {@code BackupParticipants} settles a save request: a registration on the server's
 * own service manager, under a type that comes from the boot class loader and is therefore the same class in
 * every jar. The payload crosses as an {@code Object[]} of JDK, Bukkit and Adventure types, all of which every
 * plugin shares because none of them is ever shaded. The registration is marked, so a {@code Function} service
 * somebody registered for a purpose of their own is left alone.
 *
 * <h2>Using it</h2>
 *
 * <pre>{@code
 * Nametags nametags = SharedNametags.claim(this, () -> new NametagRegistry(
 *         new ScoreboardNametagSink(board, getLogger()), getLogger(), " ", scheduler));
 *
 * nametags.contribute(player, NametagContribution.prefix(getName(), priority, prefix));
 *
 * // onDisable
 * nametags.withdraw(getName());
 * SharedNametags.release(this);
 * }</pre>
 *
 * <p>The plugin that builds the registry decides the separator and the colour rule for the whole server, so
 * read both from your own config and expect an operator to set them on whichever plugin loads first. That is
 * the price of one registry, and it is smaller than the price of two.
 *
 * <h2>When the owner goes away</h2>
 *
 * <p>The server unregisters a disabled plugin's services, so an owner that disables takes its registry and its
 * teams with it. The view a second plugin holds notices on its next call and takes the registry over, so the
 * names come back as the plugins contribute again rather than staying gone until a restart. Contributions the
 * old owner held are not carried across: nothing can copy them, because the two registries share no type.
 */
public final class SharedNametags implements Nametags {

    /**
     * The mark that tells our registration apart from any other {@code Function} service on the server, and
     * carries the payload version so a future shape can be recognised rather than guessed at.
     */
    private static final String MARK = "uxmlib:nametag-registry:1:";

    /**
     * The service type the registry is offered under. {@code Function} comes from the boot class loader, so it
     * is the same class in every relocated copy of this library, which is the whole point. The cast is what
     * gives the rest of this class a typed {@code Function<Object[], Object>} instead of a raw one: a class
     * literal cannot carry a type argument, and Bukkit keys a service by its class literal.
     */
    @SuppressWarnings("unchecked")
    private static final Class<Function<Object[], Object>> SERVICE =
            (Class<Function<Object[], Object>>) (Class<?>) Function.class;

    private final String plugin;
    private final Plugin owner;
    private final Supplier<NametagRegistry> ifFirst;
    private @Nullable NametagRegistry local;

    private SharedNametags(Plugin owner, Supplier<NametagRegistry> ifFirst) {
        this.owner = owner;
        this.plugin = owner.getName();
        this.ifFirst = ifFirst;
    }

    /**
     * Join this server's one registry, building it with {@code ifFirst} when nobody has yet.
     *
     * <p>{@code ifFirst} is called at most once and only when this plugin turns out to be the first to load,
     * so a plugin that is not the owner never creates a sink and never touches a scoreboard team.
     */
    public static Nametags claim(Plugin plugin, Supplier<NametagRegistry> ifFirst) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(ifFirst, "ifFirst");
        SharedNametags shared = new SharedNametags(plugin, ifFirst);
        if (registrations().isEmpty()) {
            // Decided at claim time, not at the first contribution, so the owner is the plugin that loaded
            // first rather than the plugin that happened to paint a name first.
            shared.ownRegistry();
        }
        return shared;
    }

    /** Stop offering this plugin's registry to the rest of the server. A plugin's {@code onDisable} calls it. */
    public static void release(Plugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        for (RegisteredServiceProvider<Function<Object[], Object>> registered : registrations()) {
            if (registered.getPlugin().equals(plugin)) {
                Bukkit.getServicesManager().unregister(SERVICE, registered.getProvider());
            }
        }
    }

    /** The plugin whose registry this server is using, for a status line. Empty when nobody has claimed one. */
    public static Optional<String> ownerName() {
        List<RegisteredServiceProvider<Function<Object[], Object>>> ours = registrations();
        return ours.isEmpty() ? Optional.empty() : Optional.of(nameOf(ours.get(0)));
    }

    @Override
    public void contribute(Player player, NametagContribution contribution) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(contribution, "contribution");
        @Nullable Function<Object[], Object> foreign = foreignOwner();
        if (foreign == null) {
            ownRegistry().contribute(player, contribution);
            return;
        }
        call(
                foreign,
                "contribute",
                player,
                contribution.plugin(),
                contribution.priority(),
                contribution.prefix(),
                contribution.suffix(),
                contribution.color());
    }

    @Override
    public void withdraw(Player player, String plugin) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(plugin, "plugin");
        @Nullable Function<Object[], Object> foreign = foreignOwner();
        if (foreign == null) {
            ownRegistry().withdraw(player, plugin);
            return;
        }
        call(foreign, "withdraw-from", player, plugin);
    }

    @Override
    public void withdraw(String plugin) {
        Objects.requireNonNull(plugin, "plugin");
        @Nullable Function<Object[], Object> foreign = foreignOwner();
        if (foreign == null) {
            ownRegistry().withdraw(plugin);
            return;
        }
        call(foreign, "withdraw", plugin);
    }

    @Override
    public void forget(Player player) {
        Objects.requireNonNull(player, "player");
        forget(player.getUniqueId());
    }

    @Override
    public void forget(UUID id) {
        Objects.requireNonNull(id, "id");
        @Nullable Function<Object[], Object> foreign = foreignOwner();
        if (foreign == null) {
            ownRegistry().forget(id);
            return;
        }
        call(foreign, "forget", id);
    }

    @Override
    public ComposedNametag composed(UUID id) {
        Objects.requireNonNull(id, "id");
        @Nullable Function<Object[], Object> foreign = foreignOwner();
        if (foreign == null) {
            return ownRegistry().composed(id);
        }
        return decompose(call(foreign, "composed", id));
    }

    /**
     * Take back what this plugin put on the server.
     *
     * <p>On the owner that is the whole registry. On a plugin that is only a view of somebody else's it is
     * this plugin's own contributions and nothing more, because closing a registry another plugin owns would
     * wipe a name that plugin is still painting.
     */
    @Override
    public void close() {
        @Nullable Function<Object[], Object> foreign = foreignOwner();
        if (foreign == null) {
            NametagRegistry own = local;
            if (own != null) {
                own.close();
            }
            release(owner);
            return;
        }
        call(foreign, "withdraw", plugin);
    }

    /** This plugin's own registry, created and offered to the server the first time it is needed. */
    private NametagRegistry ownRegistry() {
        NametagRegistry own = local;
        if (own == null) {
            own = Objects.requireNonNull(ifFirst.get(), "ifFirst supplied no registry");
            local = own;
        }
        if (mine() == null) {
            Bukkit.getServicesManager().register(SERVICE, new Bridge(plugin, own), owner, ServicePriority.Normal);
        }
        return own;
    }

    /** The registry another plugin owns, or {@code null} when this plugin owns it or nobody does yet. */
    private @Nullable Function<Object[], Object> foreignOwner() {
        for (RegisteredServiceProvider<Function<Object[], Object>> registered : registrations()) {
            if (!nameOf(registered).equals(plugin)) {
                return registered.getProvider();
            }
            return null; // Ours is the first registration, so this plugin is the owner.
        }
        return null;
    }

    /** This plugin's own registration, or {@code null} when it has not made one. */
    private @Nullable Function<Object[], Object> mine() {
        for (RegisteredServiceProvider<Function<Object[], Object>> registered : registrations()) {
            if (nameOf(registered).equals(plugin)) {
                return registered.getProvider();
            }
        }
        return null;
    }

    /** Rebuild a composed name from the parts that crossed the boundary. */
    private static ComposedNametag decompose(@Nullable Object answer) {
        if (!(answer instanceof Object[] parts) || parts.length != 5) {
            return ComposedNametag.compose(List.of(), NametagRegistry.DEFAULT_SEPARATOR);
        }
        List<String> sources = new ArrayList<>();
        if (parts[4] instanceof List<?> raw) {
            for (Object source : raw) {
                sources.add(String.valueOf(source));
            }
        }
        return new ComposedNametag(
                parts[0] instanceof Component prefix ? prefix : Component.empty(),
                parts[1] instanceof Component suffix ? suffix : Component.empty(),
                parts[2] instanceof NamedTextColor color ? color : null,
                parts[3] instanceof String owner ? owner : null,
                sources);
    }

    private static @Nullable Object call(Function<Object[], Object> bridge, @Nullable Object... payload) {
        return bridge.apply(payload);
    }

    /** Every marked registration, in service-manager order, which is registration order at one priority. */
    @SuppressWarnings("unchecked") // Bukkit keys a service by its Class, and Function has no useful argument.
    private static List<RegisteredServiceProvider<Function<Object[], Object>>> registrations() {
        List<RegisteredServiceProvider<Function<Object[], Object>>> ours = new ArrayList<>();
        for (RegisteredServiceProvider<Function<Object[], Object>> registered :
                (List<RegisteredServiceProvider<Function<Object[], Object>>>)
                        (List<?>) List.copyOf(Bukkit.getServicesManager().getRegistrations(Function.class))) {
            if (registered.getProvider().toString().toLowerCase(Locale.ROOT).startsWith(MARK)) {
                ours.add(registered);
            }
        }
        return ours;
    }

    /** The plugin name a marked registration carries, read off the mark rather than off the plugin. */
    private static String nameOf(RegisteredServiceProvider<Function<Object[], Object>> registered) {
        return registered.getProvider().toString().substring(MARK.length());
    }

    /**
     * One plugin's registry, offered to the rest of the server.
     *
     * <p>The verb and every argument are types from the boot class loader, from Bukkit or from Adventure, and
     * none of the three is ever shaded, so both sides of a call see the same classes whatever they relocated.
     * The mark is in {@code toString} because that is the one thing a plain {@code Function} can carry across
     * two class loaders that share no type of ours.
     */
    private record Bridge(String plugin, NametagRegistry registry) implements Function<Object[], Object> {

        @Override
        public @Nullable Object apply(Object[] payload) {
            String verb = String.valueOf(payload[0]);
            switch (verb) {
                case "contribute" -> registry.contribute((Player) payload[1], contribution(payload));
                case "withdraw-from" -> registry.withdraw((Player) payload[1], (String) payload[2]);
                case "withdraw" -> registry.withdraw((String) payload[1]);
                case "forget" -> registry.forget((UUID) payload[1]);
                case "composed" -> {
                    return parts(registry.composed((UUID) payload[1]));
                }
                default -> throw new IllegalArgumentException("unknown nametag registry verb: " + verb);
            }
            return null;
        }

        private static NametagContribution contribution(Object[] payload) {
            return new NametagContribution(
                    (String) payload[2],
                    (Integer) payload[3],
                    (Component) payload[4],
                    (Component) payload[5],
                    (NamedTextColor) payload[6]);
        }

        private static Object[] parts(ComposedNametag name) {
            return new Object[] {name.prefix(), name.suffix(), name.color(), name.colorOwner(), name.colorSources()};
        }

        @Override
        public String toString() {
            return MARK + plugin;
        }
    }
}
