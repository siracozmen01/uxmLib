package com.uxplima.uxmlib.storage.migration;

import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import com.uxplima.uxmlib.storage.StorageException;

/**
 * Discovers {@code V<version>__<description>.sql} migrations on the classpath and turns them into ordered
 * {@link Migration}s for {@link MigrationRunner}. Resources are found both in an exploded directory (the
 * {@code file:} URL an IDE or test run sees) and inside a jar (the {@code jar:} URL a shaded plugin sees at
 * runtime), so the same call works in development and in production.
 *
 * <p>Dialect-specific overrides are expressed with two directories — a generic one plus a backend one — and
 * {@link #overlay(List, List)} merges them so the backend file wins for any version it redefines:
 *
 * <pre>{@code
 * List<Migration> migrations = MigrationResources.load(loader, "db/migration");
 * if (sql.dialect() == Dialect.POSTGRES) {
 *     migrations = MigrationResources.overlay(migrations, MigrationResources.load(loader, "db/migration_pg"));
 * }
 * new MigrationRunner(database).apply(migrations);
 * }</pre>
 */
public final class MigrationResources {

    private MigrationResources() {}

    /**
     * Load every {@code V*.sql} migration directly under {@code directory} on {@code classLoader}, ordered by
     * ascending version. A missing directory yields an empty list (so an absent dialect overlay is a no-op);
     * two files declaring the same version is an error.
     */
    public static List<Migration> load(ClassLoader classLoader, String directory) {
        Objects.requireNonNull(classLoader, "classLoader");
        Objects.requireNonNull(directory, "directory");
        List<Migration> migrations = new ArrayList<>();
        Set<Integer> versions = new HashSet<>();
        for (String fileName : listNames(classLoader, directory)) {
            Optional<MigrationFile> parsed = MigrationFile.parse(fileName);
            if (parsed.isEmpty()) {
                continue;
            }
            MigrationFile file = parsed.get();
            if (!versions.add(file.version())) {
                throw new IllegalStateException("duplicate migration version " + file.version() + " in " + directory);
            }
            String sql = read(classLoader, directory + "/" + fileName);
            migrations.add(new Migration(file.version(), file.description(), sql));
        }
        migrations.sort(Comparator.comparingInt(Migration::version));
        return List.copyOf(migrations);
    }

    /**
     * Return {@code base} with every migration in {@code overrides} replacing the {@code base} migration of the
     * same version (and adding any new version), ordered by ascending version. Used to lay a dialect-specific
     * directory over a generic one.
     */
    public static List<Migration> overlay(List<Migration> base, List<Migration> overrides) {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(overrides, "overrides");
        TreeMap<Integer, Migration> byVersion = new TreeMap<>();
        for (Migration migration : base) {
            byVersion.put(migration.version(), migration);
        }
        for (Migration migration : overrides) {
            byVersion.put(migration.version(), migration);
        }
        return List.copyOf(byVersion.values());
    }

    private static List<String> listNames(ClassLoader classLoader, String directory) {
        URL url = classLoader.getResource(directory);
        if (url == null) {
            return List.of();
        }
        return switch (url.getProtocol()) {
            case "file" -> listFileNames(url, directory);
            case "jar" -> listJarNames(url, directory);
            default -> throw new IllegalStateException(
                    "unsupported resource protocol '" + url.getProtocol() + "' for migrations in " + directory);
        };
    }

    private static List<String> listFileNames(URL url, String directory) {
        try (Stream<Path> entries = Files.list(Path.of(url.toURI()))) {
            return entries.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .toList();
        } catch (IOException | URISyntaxException failure) {
            throw new StorageException("could not list migrations in " + directory, failure);
        }
    }

    private static List<String> listJarNames(URL url, String directory) {
        try {
            JarURLConnection connection = (JarURLConnection) url.openConnection();
            connection.setUseCaches(false);
            String prefix = connection.getEntryName();
            if (prefix == null) {
                return List.of();
            }
            List<String> names = new ArrayList<>();
            try (JarFile jar = connection.getJarFile()) {
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    String entryName = entries.nextElement().getName();
                    if (!entryName.startsWith(prefix + "/")) {
                        continue;
                    }
                    String simple = entryName.substring(prefix.length() + 1);
                    if (!simple.isEmpty() && simple.indexOf('/') < 0) {
                        names.add(simple);
                    }
                }
            }
            return names;
        } catch (IOException failure) {
            throw new StorageException("could not list migrations in " + directory, failure);
        }
    }

    private static String read(ClassLoader classLoader, String resource) {
        try (InputStream in = classLoader.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("migration resource not readable: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new StorageException("could not read migration " + resource, failure);
        }
    }
}
