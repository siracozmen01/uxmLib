package com.uxplima.uxmlib.config;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.serialize.TypeSerializerCollection;

/**
 * A HOCON-backed configuration. The file is parsed once on {@link #load(Path)} and held in an
 * {@link AtomicReference}; {@link #reload()} re-reads it and swaps the reference whole, so a reader sees
 * either the entire old tree or the entire new one — never a half-applied config.
 *
 * <p>Dotted paths ({@code "storage.host"}) address nested nodes for the typed scalar reads. Whole
 * subtrees map onto {@code @ConfigSerializable} types via {@link #get(Class)} / {@link #getNode(String,
 * Class, Object)}, so config can be modelled as records and classes rather than scattered string lookups.
 * A missing file yields an empty tree, so every scalar read returns its fallback.
 */
public final class HoconConfig {

    private static final System.Logger LOG = System.getLogger(HoconConfig.class.getName());

    private final HoconConfigurationLoader loader;
    private final AtomicReference<CommentedConfigurationNode> root;
    private final List<ConfigProperty<?>> properties = new CopyOnWriteArrayList<>();
    private final List<Runnable> reloadListeners = new CopyOnWriteArrayList<>();

    private HoconConfig(HoconConfigurationLoader loader, CommentedConfigurationNode root) {
        this.loader = loader;
        this.root = new AtomicReference<>(root);
    }

    /** Load {@code file} as HOCON. A non-existent file loads as an empty tree. */
    public static HoconConfig load(Path file) {
        Objects.requireNonNull(file, "file");
        HoconConfigurationLoader loader =
                HoconConfigurationLoader.builder().path(file).build();
        return new HoconConfig(loader, read(loader));
    }

    /**
     * Load {@code file} with extra type serializers (see {@link ConfigCodecs#bukkit()}), so subtree and
     * scalar reads can map onto the registered Bukkit value types.
     */
    public static HoconConfig load(Path file, TypeSerializerCollection serializers) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(serializers, "serializers");
        HoconConfigurationLoader loader = HoconConfigurationLoader.builder()
                .path(file)
                .defaultOptions(options -> options.serializers(serializers))
                .build();
        return new HoconConfig(loader, read(loader));
    }

    /**
     * Re-read the file and swap the in-memory tree atomically, then refresh every bound
     * {@link ConfigProperty} (firing change listeners) and run every {@link #onReload} listener.
     */
    public void reload() {
        root.set(read(loader));
        // Isolate each property and listener: one that throws is logged and skipped, so a single bad
        // observer can't stop the rest of a reload from being applied.
        for (ConfigProperty<?> property : properties) {
            try {
                property.refresh();
            } catch (RuntimeException failure) {
                LOG.log(System.Logger.Level.ERROR, "a config property failed to refresh on reload", failure);
            }
        }
        for (Runnable listener : reloadListeners) {
            try {
                listener.run();
            } catch (RuntimeException failure) {
                LOG.log(System.Logger.Level.ERROR, "a config reload listener threw", failure);
            }
        }
    }

    /** Run {@code listener} after each {@link #reload()}. */
    public void onReload(Runnable listener) {
        reloadListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /** Write the current in-memory tree back to the file. Synchronized so saves never overlap. */
    public synchronized void save() {
        try {
            loader.save(currentRoot());
        } catch (ConfigurateException failure) {
            throw new ConfigException("failed to save config", failure);
        }
    }

    /** Save off the calling thread on {@code executor}; the returned future completes when the write ends. */
    public CompletableFuture<Void> saveAsync(Executor executor) {
        Objects.requireNonNull(executor, "executor");
        return CompletableFuture.runAsync(this::save, executor);
    }

    /** A bound int property at {@code path}, refreshed on reload. */
    public ConfigProperty<Integer> intProperty(String path, int fallback) {
        Objects.requireNonNull(path, "path");
        return register(new ConfigProperty<>(() -> getInt(path, fallback)));
    }

    /** A bound boolean property at {@code path}, refreshed on reload. */
    public ConfigProperty<Boolean> boolProperty(String path, boolean fallback) {
        Objects.requireNonNull(path, "path");
        return register(new ConfigProperty<>(() -> getBoolean(path, fallback)));
    }

    /** A bound string property at {@code path}, refreshed on reload. */
    public ConfigProperty<String> stringProperty(String path, String fallback) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(fallback, "fallback");
        return register(new ConfigProperty<>(() -> getString(path, fallback)));
    }

    private <T> ConfigProperty<T> register(ConfigProperty<T> property) {
        properties.add(property);
        return property;
    }

    /** The boolean at {@code path}, or {@code fallback} when absent. */
    public boolean getBoolean(String path, boolean fallback) {
        return node(path).getBoolean(fallback);
    }

    /** The int at {@code path}, or {@code fallback} when absent. */
    public int getInt(String path, int fallback) {
        return node(path).getInt(fallback);
    }

    /** The long at {@code path}, or {@code fallback} when absent. */
    public long getLong(String path, long fallback) {
        return node(path).getLong(fallback);
    }

    /** The double at {@code path}, or {@code fallback} when absent. */
    public double getDouble(String path, double fallback) {
        return node(path).getDouble(fallback);
    }

    /** The string at {@code path}, or {@code fallback} when absent. */
    public String getString(String path, String fallback) {
        Objects.requireNonNull(fallback, "fallback");
        String value = node(path).getString(fallback);
        return value != null ? value : fallback;
    }

    /** The string at {@code path}, empty when absent. */
    public Optional<String> getString(String path) {
        return Optional.ofNullable(node(path).getString());
    }

    /** Map the whole config onto {@code type} (a {@code @ConfigSerializable} record or class). */
    public <T> Optional<T> get(Class<T> type) {
        Objects.requireNonNull(type, "type");
        try {
            return Optional.ofNullable(currentRoot().get(type));
        } catch (SerializationException failure) {
            throw new ConfigException("failed to map config to " + type.getName(), failure);
        }
    }

    /** Map the subtree at {@code path} onto {@code type}, or return {@code fallback} when absent. */
    public <T> T getNode(String path, Class<T> type, T fallback) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(fallback, "fallback");
        try {
            T value = node(path).get(type);
            return value != null ? value : fallback;
        } catch (SerializationException failure) {
            throw new ConfigException("failed to map " + path + " to " + type.getName(), failure);
        }
    }

    /** The raw root node, for callers that need Configurate directly. */
    public CommentedConfigurationNode root() {
        return currentRoot();
    }

    private ConfigurationNode node(String path) {
        Objects.requireNonNull(path, "path");
        Object[] segments = path.split("\\.");
        return currentRoot().node(segments);
    }

    // The reference is seeded in the constructor and only ever swapped with a non-null node, so this is
    // never null at runtime; the requireNonNull tells NullAway what the AtomicReference cannot express.
    private CommentedConfigurationNode currentRoot() {
        return Objects.requireNonNull(root.get(), "root");
    }

    private static CommentedConfigurationNode read(HoconConfigurationLoader loader) {
        try {
            return loader.load();
        } catch (ConfigurateException failure) {
            throw new ConfigException("failed to load config", failure);
        }
    }
}
