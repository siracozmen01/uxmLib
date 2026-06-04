package com.uxplima.uxmlib.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * A hot-reloadable, typed-record view of a single HOCON file. Generic on a {@code @ConfigSerializable}
 * record/class {@code T}: the whole file maps onto one cached {@code T} snapshot, swapped atomically on
 * {@link #reload()}. The complement to {@link HoconConfig} (which queries individual nodes) for the common
 * "load a whole section into a record and hot-reload it" shape — the mapped value is cached so {@link
 * #current()} is a cheap field read, not a re-deserialization per call.
 *
 * <p>Fail-safe reload: {@link #reload()} re-reads and swaps on success; on failure it throws {@link
 * ConfigException} and KEEPS the prior snapshot, so {@link #current()} always returns a valid value. A
 * missing (or unreadable) file on construction yields the {@code defaultIfMissing} value.
 */
public final class RecordConfig<T> {

    private final Path file;
    private final Class<T> type;
    private final Supplier<T> defaultIfMissing;
    private final AtomicReference<T> snapshot;
    // The file mtime captured at the most recent successful load; compared against the live mtime by
    // isModifiedSinceLoad(). Null until the first successful load (e.g. file missing on construction).
    private final AtomicReference<@Nullable FileTime> mtimeAtLoad = new AtomicReference<>();

    /**
     * @param file the HOCON file to read
     * @param type the {@code @ConfigSerializable} record/class the whole file maps onto
     * @param defaultIfMissing the value to use when the file is absent (or unreadable) on construction
     */
    public RecordConfig(Path file, Class<T> type, Supplier<T> defaultIfMissing) {
        this.file = Objects.requireNonNull(file, "file");
        this.type = Objects.requireNonNull(type, "type");
        this.defaultIfMissing = Objects.requireNonNull(defaultIfMissing, "defaultIfMissing");
        this.snapshot = new AtomicReference<>(loadOrDefault());
    }

    /** The active snapshot — the most recently loaded {@code T} (or the default when the file was absent). */
    public T current() {
        return Objects.requireNonNull(snapshot.get(), "snapshot");
    }

    /**
     * Re-read the file, swap the snapshot, and return the new value. On failure throws {@link
     * ConfigException} and retains the prior snapshot, so {@link #current()} stays valid.
     */
    public T reload() {
        T loaded = load();
        snapshot.set(loaded);
        return loaded;
    }

    /**
     * Re-read and parse the file WITHOUT swapping the active snapshot, returning the parsed value — a dry-run
     * for "validate before reload" flows. Throws {@link ConfigException} on a parse failure; {@link
     * #current()} is unaffected regardless of outcome.
     */
    public T dryRun() {
        return load();
    }

    /**
     * Whether the file's modification time is strictly later than the mtime captured at the most recent
     * successful load. False when the file is missing, its mtime cannot be read, or nothing has loaded yet —
     * never a false positive.
     */
    public boolean isModifiedSinceLoad() {
        FileTime live = readMtime();
        FileTime stored = mtimeAtLoad.get();
        if (live == null || stored == null) {
            return false;
        }
        return live.toMillis() > stored.toMillis();
    }

    /**
     * Parse a record from an explicit path with no instance / snapshot / mtime state. A missing file yields
     * {@code defaultIfMissing.get()} (an absent file is not a parse failure); a real parse failure throws
     * {@link ConfigException}. For validating an arbitrary on-disk candidate (e.g. a backup) before adopting it.
     */
    public static <T> T loadFrom(Path file, Class<T> type, Supplier<T> defaultIfMissing) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(defaultIfMissing, "defaultIfMissing");
        if (!Files.exists(file)) {
            return defaultIfMissing.get();
        }
        return parse(file, type);
    }

    private T loadOrDefault() {
        if (!Files.exists(file)) {
            return defaultIfMissing.get();
        }
        try {
            return load();
        } catch (ConfigException malformed) {
            // First-load failure is not surfaced here (the constructor must produce a valid current()); fall
            // back to defaults — a later reload() surfaces the error.
            return defaultIfMissing.get();
        }
    }

    private T load() {
        T value = parse(file, type);
        // Capture mtime AFTER a successful load so isModifiedSinceLoad stays false until the next edit. A
        // failed load (parse threw) leaves the prior mtime untouched — correct: the prior snapshot still holds.
        mtimeAtLoad.set(readMtime());
        return value;
    }

    private static <T> T parse(Path file, Class<T> type) {
        try {
            HoconConfigurationLoader loader =
                    HoconConfigurationLoader.builder().path(file).build();
            CommentedConfigurationNode root = loader.load();
            T value = root.get(type);
            if (value == null) {
                throw new ConfigException("config root of " + file + " deserialized to null");
            }
            return value;
        } catch (ConfigurateException malformed) {
            throw new ConfigException("failed to load " + file + ": " + malformed.getMessage(), malformed);
        }
    }

    private @Nullable FileTime readMtime() {
        try {
            return Files.getLastModifiedTime(file);
        } catch (IOException unreadable) {
            // Read failure (file missing, permissions, etc.) — the caller treats this as "no signal".
            return null;
        }
    }
}
