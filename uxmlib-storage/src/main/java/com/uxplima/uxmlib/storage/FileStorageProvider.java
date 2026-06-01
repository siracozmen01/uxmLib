package com.uxplima.uxmlib.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * A {@link StorageProvider} backed by one HOCON file per aggregate under a directory — the flat-file
 * alternative to SQL, so a small plugin needs no database. Each aggregate is mapped through Configurate
 * object-mapping (so {@code T} is a {@code @ConfigSerializable} type), written to {@code <id>.conf}, and
 * read back by id. No JDBC, no pool. Reads/writes block on disk, so wrap them off-thread for hot paths.
 */
public final class FileStorageProvider<I, T> implements StorageProvider<I, T> {

    private final Path directory;
    private final Class<T> type;
    private final Function<T, I> idOf;

    public FileStorageProvider(Path directory, Class<T> type, Function<T, I> idOf) {
        this.directory = Objects.requireNonNull(directory, "directory");
        this.type = Objects.requireNonNull(type, "type");
        this.idOf = Objects.requireNonNull(idOf, "idOf");
        try {
            Files.createDirectories(directory);
        } catch (IOException failure) {
            throw new StorageException("could not create storage directory " + directory, failure);
        }
    }

    @Override
    public Optional<T> findById(I id) {
        Objects.requireNonNull(id, "id");
        Path file = fileFor(id);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(loader(file).load().get(type));
        } catch (ConfigurateException failure) {
            throw new StorageException("could not read " + file, failure);
        }
    }

    @Override
    public List<T> findAll() {
        List<T> all = new ArrayList<>();
        try (Stream<Path> files = Files.list(directory)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".conf")).toList()) {
                T value = loader(file).load().get(type);
                if (value != null) {
                    all.add(value);
                }
            }
        } catch (IOException failure) {
            // ConfigurateException is an IOException subclass, so this covers a malformed file too.
            throw new StorageException("could not list storage directory " + directory, failure);
        }
        return all;
    }

    @Override
    public void save(T entity) {
        Objects.requireNonNull(entity, "entity");
        Path file = fileFor(idOf.apply(entity));
        HoconConfigurationLoader loader = loader(file);
        try {
            CommentedConfigurationNode node = loader.createNode();
            node.set(type, entity);
            loader.save(node);
        } catch (ConfigurateException failure) {
            throw new StorageException("could not write " + file, failure);
        }
    }

    @Override
    public boolean deleteById(I id) {
        Objects.requireNonNull(id, "id");
        try {
            return Files.deleteIfExists(fileFor(id));
        } catch (IOException failure) {
            throw new StorageException("could not delete entry " + id, failure);
        }
    }

    private Path fileFor(I id) {
        return directory.resolve(id + ".conf");
    }

    private static HoconConfigurationLoader loader(Path file) {
        return HoconConfigurationLoader.builder().path(file).build();
    }
}
