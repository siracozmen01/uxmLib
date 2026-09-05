package com.uxplima.uxmlib.text.style;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * Where a theme is read from: a file a suite of plugins shares, and a plugin's own file on top of it.
 *
 * <p>A server that runs several plugins from one author expects one look, and sixteen copies of the same file
 * is not that: it is sixteen chances to drift and sixteen files to edit to change one colour. So a suite may
 * put the theme in one folder beside the plugins, at {@link #shared(Path, String)}, and every plugin of the
 * suite reads it. A plugin's own file wins key by key, because a server that wants one plugin to read
 * differently should not have to give up the shared file to get it.
 *
 * <p><b>The name of that folder is the caller's, and this library holds none.</b> Which plugins form a suite,
 * and what the folder they share is called, is a convention of one estate: a library that named a folder would
 * be sending every consumer to read a file in a directory they never created and share with plugins they have
 * never heard of. So the name is a parameter. A plugin with no suite around it passes {@link #own(Path)} and
 * reads its own folder, which is the answer for a plugin that stands alone.
 */
public final class ThemeFiles {

    private static final String FILE = "theme.conf";

    private ThemeFiles() {}

    /**
     * The theme file of the folder called {@code folder}, worked out from the data folder of a plugin that
     * sits beside it: {@code plugins/<folder>/theme.conf}.
     *
     * <p>{@code folder} is the name a suite of plugins agrees on. Nothing in this library knows it or
     * suggests one.
     *
     * @throws IllegalArgumentException when {@code folder} is blank, which would resolve to the plugins
     *     directory itself and read a theme belonging to nobody
     */
    public static Path shared(Path dataFolder, String folder) {
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(folder, "folder");
        if (folder.isBlank()) {
            throw new IllegalArgumentException("folder must not be blank");
        }
        Path plugins = dataFolder.toAbsolutePath().getParent();
        Path root = plugins != null ? plugins : dataFolder;
        return root.resolve(folder).resolve(FILE);
    }

    /** The plugin's own theme file, in its own data folder: {@code plugins/<plugin>/theme.conf}. */
    public static Path own(Path dataFolder) {
        Objects.requireNonNull(dataFolder, "dataFolder");
        return dataFolder.resolve(FILE);
    }

    /**
     * The theme in {@code shared}, with {@code own} applied on top of it.
     *
     * <p>A file that is not there is not an error: a server with neither file gets the shipped defaults, and a
     * server with only the shared file gets it everywhere.
     *
     * @throws ConfigurateException when a file exists and cannot be read, which an operator has to see
     * @throws IllegalArgumentException when a file holds something that is not a colour
     */
    public static Theme load(Path shared, Path own) throws ConfigurateException {
        Objects.requireNonNull(shared, "shared");
        Objects.requireNonNull(own, "own");
        ConfigurationNode merged = read(own);
        merged.mergeFrom(read(shared));
        return merged.empty() ? Theme.defaults() : Theme.from(merged);
    }

    /**
     * The theme in one file, with nothing beneath it. This is what a plugin that stands alone wants: it has
     * one theme file, in its own folder, and no suite to share one with.
     *
     * @throws ConfigurateException when the file exists and cannot be read
     * @throws IllegalArgumentException when the file holds something that is not a colour
     */
    public static Theme load(Path file) throws ConfigurateException {
        Objects.requireNonNull(file, "file");
        ConfigurationNode node = read(file);
        return node.empty() ? Theme.defaults() : Theme.from(node);
    }

    private static ConfigurationNode read(Path file) throws ConfigurateException {
        if (!Files.isRegularFile(file)) {
            return CommentedConfigurationNode.root();
        }
        return HoconConfigurationLoader.builder().path(file).build().load();
    }
}
