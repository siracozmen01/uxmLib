package com.uxplima.uxmlib.condition.wallet;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * The server's answer to an {@link EconomyBinding}.
 *
 * <p>The plugin has to be there first, and the plugin manager is the only thing this class asks before it
 * names anything. That check is what makes every binding a soft one: a server without the plugin never
 * loads its class, so the class is never missing at run time and the server starts the same either way.
 */
public final class ServerEconomyProviders implements EconomyProviders {

    /** One accessor name per hop. */
    private static final Pattern HOPS = Pattern.compile("\\.");

    private final System.Logger log;

    public ServerEconomyProviders(System.Logger log) {
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public Optional<Object> provider(EconomyBinding binding) {
        Objects.requireNonNull(binding, "binding");
        if (!isPresent(binding.pluginName())) {
            return Optional.empty();
        }
        try {
            Class<?> provider = classOf(binding.pluginName(), binding.providerClass());
            return switch (binding.access()) {
                case SERVICE -> service(provider);
                case STATIC -> chain(provider, Objects.requireNonNull(binding.accessorName()));
                case PLUGIN ->
                    Optional.ofNullable((Object) Bukkit.getPluginManager().getPlugin(binding.pluginName()));
                // The class itself answers. Nothing is fetched, and the reader calls the methods on no
                // object at all.
                case CLASS -> Optional.of(provider);
            };
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unreachable) {
            log.log(
                    System.Logger.Level.WARNING,
                    "The " + binding.pluginName() + " economy is here but could not be reached",
                    unreachable);
            return Optional.empty();
        }
    }

    @Override
    public Optional<Object> service(String pluginName, String className) {
        Objects.requireNonNull(pluginName, "pluginName");
        Objects.requireNonNull(className, "className");
        if (!isPresent(pluginName)) {
            return Optional.empty();
        }
        try {
            return service(classOf(pluginName, className));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unreachable) {
            log.log(
                    System.Logger.Level.WARNING,
                    "The " + pluginName + " plugin is here but " + className + " could not be reached",
                    unreachable);
            return Optional.empty();
        }
    }

    /** Whether one plugin is on the server and running, for a caller that only wants to know. */
    public static boolean isPresent(String pluginName) {
        Objects.requireNonNull(pluginName, "pluginName");
        Plugin found = Bukkit.getPluginManager().getPlugin(pluginName);
        return found != null && found.isEnabled();
    }

    /**
     * A class of another plugin, asked of the plugin that owns it.
     *
     * <p>A plugin of this generation is loaded in a class loader of its own, and it sees another plugin
     * only where it named it in {@code paper-plugin.yml}. A binding may be written by an operator, who can
     * name an economy nobody here has heard of, so no file can carry every name in advance. The owner of a
     * class always sees it, so the owner is asked. A server that loads every plugin together answers
     * exactly the same.
     */
    private static Class<?> classOf(String pluginName, String className) throws ClassNotFoundException {
        Plugin owner = Bukkit.getPluginManager().getPlugin(pluginName);
        ClassLoader loader = owner == null
                ? ServerEconomyProviders.class.getClassLoader()
                : owner.getClass().getClassLoader();
        return Class.forName(className, false, loader);
    }

    private static Optional<Object> service(Class<?> provider) {
        RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration(provider);
        return Optional.ofNullable(registration).map(RegisteredServiceProvider::getProvider);
    }

    /**
     * Follow a chain of methods that take nothing, the first of them static.
     *
     * <p>{@code getInstance.getAPI} is two hops, and more than one economy hides its API that way.
     */
    private static Optional<Object> chain(Class<?> provider, String accessors) throws ReflectiveOperationException {
        Object current = null;
        Class<?> on = provider;
        for (String step : HOPS.splitAsStream(accessors).toList()) {
            Method accessor = on.getMethod(step);
            current = accessor.invoke(current);
            if (current == null) {
                return Optional.empty();
            }
            on = current.getClass();
        }
        return Optional.ofNullable(current);
    }
}
