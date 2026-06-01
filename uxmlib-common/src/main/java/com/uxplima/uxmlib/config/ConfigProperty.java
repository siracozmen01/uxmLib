package com.uxplima.uxmlib.config;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A typed handle to one config value. {@link #get()} returns the current value, cached so reads are
 * cheap; when the owning {@link HoconConfig} reloads, the handle re-reads, and if the value changed every
 * {@link #onChange(Consumer)} listener fires with the new value. Obtain one from {@code HoconConfig}'s
 * {@code *Property} methods rather than constructing it directly.
 */
public final class ConfigProperty<T> {

    private static final System.Logger LOG = System.getLogger(ConfigProperty.class.getName());

    private final Supplier<T> reader;
    private final List<Consumer<T>> listeners = new CopyOnWriteArrayList<>();
    private volatile T value;

    ConfigProperty(Supplier<T> reader) {
        this.reader = Objects.requireNonNull(reader, "reader");
        this.value = reader.get();
    }

    /** The current value. */
    public T get() {
        return value;
    }

    /** Register a listener invoked with the new value whenever it changes on reload. Returns this. */
    public ConfigProperty<T> onChange(Consumer<T> listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
        return this;
    }

    /** Re-read from the config; fire listeners if the value changed. Called by {@link HoconConfig#reload()}. */
    void refresh() {
        T fresh = reader.get();
        if (!Objects.equals(fresh, value)) {
            value = fresh;
            for (Consumer<T> listener : listeners) {
                // One throwing listener must not stop the others from seeing the change.
                try {
                    listener.accept(fresh);
                } catch (RuntimeException failure) {
                    LOG.log(System.Logger.Level.ERROR, "a config-property change listener threw", failure);
                }
            }
        }
    }
}
