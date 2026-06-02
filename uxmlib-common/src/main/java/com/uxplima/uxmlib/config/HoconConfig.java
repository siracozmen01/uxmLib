package com.uxplima.uxmlib.config;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.scheduler.TaskHandle;
import org.jspecify.annotations.Nullable;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.serialize.TypeSerializerCollection;

/**
 * A HOCON-backed configuration. Parsed once on {@link #load(Path)} into an {@link AtomicReference} swapped
 * whole on {@link #reload()}, so a reader sees the entire old tree or the entire new one — never half.
 * Dotted paths address nested nodes; subtrees map onto {@code @ConfigSerializable} types via
 * {@link #get(Class)} / {@link #getNode(String, Class, Object)}. A missing file loads empty.
 */
public final class HoconConfig {

    private static final System.Logger LOG = System.getLogger(HoconConfig.class.getName());

    private final Path file;
    private final HoconConfigurationLoader loader;
    private final @Nullable TypeSerializerCollection serializers;
    private final AtomicReference<CommentedConfigurationNode> root;
    private final List<ConfigProperty<?>> properties = new CopyOnWriteArrayList<>();
    private final List<Runnable> reloadListeners = new CopyOnWriteArrayList<>();
    private @Nullable TaskHandle watchTask;

    private HoconConfig(Path file, @Nullable TypeSerializerCollection serializers) {
        this.file = file;
        this.serializers = serializers;
        this.loader = loader(file, serializers);
        this.root = new AtomicReference<>(read(this.loader));
    }

    /** Load {@code file} as HOCON. A non-existent file loads as an empty tree. */
    public static HoconConfig load(Path file) {
        Objects.requireNonNull(file, "file");
        return new HoconConfig(file, null);
    }

    /** Load {@code file} with extra type serializers (see {@link ConfigCodecs#bukkit()}) for value mapping. */
    public static HoconConfig load(Path file, TypeSerializerCollection serializers) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(serializers, "serializers");
        return new HoconConfig(file, serializers);
    }

    /**
     * Seed {@code file} from the bundled classpath resource if absent (the authored, comment-rich
     * template), then load it. An existing file is never clobbered, so user edits survive every upgrade.
     */
    public static HoconConfig loadOrExtract(Path file, String defaultResource, ClassLoader classLoader) {
        Objects.requireNonNull(file, "file");
        ConfigDefaults.extractIfAbsent(file, defaultResource, classLoader);
        return load(file);
    }

    private static HoconConfigurationLoader loader(Path file, @Nullable TypeSerializerCollection serializers) {
        HoconConfigurationLoader.Builder builder =
                HoconConfigurationLoader.builder().path(file).emitComments(true);
        if (serializers != null) {
            builder.defaultOptions(options -> options.serializers(serializers));
        }
        return builder.build();
    }

    /** Re-read the file, swap the tree atomically, refresh bound properties, and run reload listeners. */
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

    /**
     * Auto-reload this config when its file changes on disk, polling every {@code period} through
     * {@code scheduler} (Folia-safe, debounced one poll). {@link #unwatch()} stops it; re-calling replaces.
     */
    public synchronized void watch(Scheduler scheduler, Duration period) {
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(period, "period");
        unwatch();
        watchTask = ConfigWatcher.start(scheduler, file, period, this::reload);
    }

    /** Stop auto-reloading (a no-op if not watching). */
    public synchronized void unwatch() {
        if (watchTask != null) {
            watchTask.cancel();
            watchTask = null;
        }
    }

    /**
     * Upgrade across schema versions: replay only {@code migration} steps newer than the file's recorded
     * {@code config-version}, rewrite the version, and save once if it changed. Returns the new version.
     */
    public synchronized int migrate(ConfigMigration migration) {
        Objects.requireNonNull(migration, "migration");
        return ConfigUpgrade.migrate(currentRoot(), migration, this::save);
    }

    /**
     * Deep-merge a default tree into the live config — adding absent keys, never overwriting a user value
     * — and save once if anything was added. Returns whether anything was written.
     */
    public synchronized boolean mergeDefaults(ConfigurationNode defaults) {
        Objects.requireNonNull(defaults, "defaults");
        return ConfigUpgrade.mergeDefaults(currentRoot(), defaults, this::save);
    }

    /** Deep-merge defaults from a bundled classpath resource. Returns whether anything was written. */
    public boolean mergeDefaults(String resource, ClassLoader classLoader) {
        return mergeDefaults(ConfigDefaults.parseResource(resource, classLoader));
    }

    /** Set the comment shown above {@code path} on the next save, without overwriting a user's own comment. */
    public void commentIfAbsent(String path, String comment) {
        Objects.requireNonNull(comment, "comment");
        currentRoot().node((Object[]) path.split("\\.")).commentIfAbsent(comment);
    }

    /** Atomically write the in-memory tree to the file (temp file + rename), so a crash never half-writes it. */
    public synchronized void save() {
        AtomicFiles.save(file, temp -> loader(temp, serializers), currentRoot());
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
        return Objects.requireNonNullElse(node(path).getString(fallback), fallback);
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

    /** Map the list at {@code path} into a {@code List<T>}; empty when absent. */
    public <T> List<T> getList(String path, Class<T> element) {
        Objects.requireNonNull(element, "element");
        return ConfigSections.list(node(path), path, element);
    }

    /**
     * Map each child of the section at {@code path} onto {@code type}, keyed by child name — for a table
     * of named entries (e.g. {@code kits { starter {...} vip {...} }}). Empty when the section is absent.
     */
    public <T> java.util.Map<String, T> getSection(String path, Class<T> type) {
        Objects.requireNonNull(type, "type");
        return ConfigSections.section(node(path), path, type);
    }

    /**
     * Map the list at {@code path} into a {@code List<T>}, skipping any element that fails to deserialize
     * rather than aborting the whole read. The returned {@link LenientResult} carries the good elements and
     * a {@link ConfigViolation} for each one skipped, so one malformed row never wipes the rest.
     */
    public <T> LenientResult<List<T>> getListLenient(String path, Class<T> element) {
        Objects.requireNonNull(element, "element");
        return ConfigSections.listLenient(node(path), path, element);
    }

    /**
     * Map each child of the section at {@code path} onto {@code type}, skipping any child that fails to
     * deserialize rather than aborting the whole read. See {@link #getSection(String, Class)} for the strict
     * variant; this one keeps every well-formed entry and reports the rest in the {@link LenientResult}.
     */
    public <T> LenientResult<java.util.Map<String, T>> getSectionLenient(String path, Class<T> type) {
        Objects.requireNonNull(type, "type");
        return ConfigSections.sectionLenient(node(path), path, type);
    }

    /** The child key names directly under {@code path} (a section's entries). */
    public List<String> keys(String path) {
        return ConfigSections.keys(node(path));
    }

    /** The live node at the exact key sequence {@code path}, for keys with a dot or list indices. */
    public ConfigurationNode nodeAt(Object... path) {
        Objects.requireNonNull(path, "path");
        return currentRoot().node(path);
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
