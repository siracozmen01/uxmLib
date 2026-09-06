package com.uxplima.uxmlib.config;

import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * The files a jar carries under one directory, listed rather than named.
 *
 * <p>A plugin that writes its defaults on first run has to know what it ships. Naming them in an array works
 * until somebody adds a file and forgets the array, and then the file is in the jar and never on the disk.
 * Reading the directory is the same answer with nothing to forget.
 *
 * <p>It answers in a jar, which is what a server runs, and in an exploded directory, which is what a test
 * and an IDE run.
 */
public final class ClasspathFiles {

    private ClasspathFiles() {}

    /**
     * The names of the files directly under {@code directory} on {@code loader}, sorted, without the
     * directory itself and without anything deeper.
     *
     * <p>A directory that is not on the class loader holds nothing and is not an error: a plugin that ships
     * no file of that kind is a plugin with nothing to write.
     */
    public static List<String> list(ClassLoader loader, String directory) {
        Objects.requireNonNull(loader, "loader");
        Objects.requireNonNull(directory, "directory");
        URL url = loader.getResource(directory);
        if (url == null) {
            return List.of();
        }
        List<String> names =
                switch (url.getProtocol()) {
                    case "file" -> inDirectory(url, directory);
                    case "jar" -> inJar(url, directory);
                    default ->
                        throw new ConfigException(
                                "cannot list " + directory + ": unsupported resource protocol " + url.getProtocol());
                };
        return names.stream().sorted().toList();
    }

    private static List<String> inDirectory(URL url, String directory) {
        try (Stream<Path> entries = Files.list(Path.of(url.toURI()))) {
            return entries.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .toList();
        } catch (IOException | URISyntaxException unreadable) {
            throw new ConfigException("cannot list " + directory, unreadable);
        }
    }

    private static List<String> inJar(URL url, String directory) {
        try {
            JarURLConnection connection = (JarURLConnection) url.openConnection();
            connection.setUseCaches(false);
            String entry = connection.getEntryName();
            if (entry == null) {
                return List.of();
            }
            // A multi-release jar hands back the name with its trailing slash and a plain one hands it back
            // without. Appending one unconditionally builds a prefix that matches nothing, and the caller
            // reads that empty list as a jar with no files in it.
            String prefix = entry.endsWith("/") ? entry : entry + "/";
            return namesUnder(connection, prefix);
        } catch (IOException unreadable) {
            throw new ConfigException("cannot list " + directory, unreadable);
        }
    }

    private static List<String> namesUnder(JarURLConnection connection, String prefix) throws IOException {
        List<String> names = new ArrayList<>();
        try (JarFile jar = connection.getJarFile()) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (!name.startsWith(prefix)) {
                    continue;
                }
                String simple = name.substring(prefix.length());
                if (!simple.isEmpty() && simple.indexOf('/') < 0) {
                    names.add(simple);
                }
            }
        }
        return names;
    }
}
