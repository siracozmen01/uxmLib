package com.uxplima.uxmlib.text.style;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.spongepowered.configurate.ConfigurateException;

/** One theme for the server, and what a plugin's own file may still say on top of it. */
class ThemeFilesTest {

    @Test
    void theSharedFileSitsBesideThePluginsThatReadIt(@TempDir Path root) {
        Path dataFolder = root.resolve("plugins").resolve("uxmTags");

        assertThat(ThemeFiles.shared(dataFolder, "aSuiteFolder"))
                .isEqualTo(root.resolve("plugins").resolve("aSuiteFolder").resolve("theme.conf"));
    }

    /**
     * The folder is the caller's word, not the library's. This is the whole of the change: a library that
     * named one would send every consumer to read a file in a directory they never created.
     */
    @Test
    void theSharedFolderIsWhateverTheCallerNames(@TempDir Path root) {
        Path dataFolder = root.resolve("plugins").resolve("aPlugin");

        assertThat(ThemeFiles.shared(dataFolder, "one")).isNotEqualTo(ThemeFiles.shared(dataFolder, "another"));
        assertThat(ThemeFiles.shared(dataFolder, "one"))
                .isEqualTo(root.resolve("plugins").resolve("one").resolve("theme.conf"));
    }

    /** A blank folder resolves to the plugins directory itself, which is a theme belonging to nobody. */
    @Test
    void aBlankFolderIsRefused(@TempDir Path root) {
        Path dataFolder = root.resolve("plugins").resolve("aPlugin");

        assertThatThrownBy(() -> ThemeFiles.shared(dataFolder, " ")).isInstanceOf(IllegalArgumentException.class);
    }

    /** A plugin that stands alone has one file, in its own folder, and no suite to share one with. */
    @Test
    void aPluginsOwnFileSitsInItsOwnFolder(@TempDir Path root) {
        Path dataFolder = root.resolve("plugins").resolve("aPlugin");

        assertThat(ThemeFiles.own(dataFolder)).isEqualTo(dataFolder.resolve("theme.conf"));
    }

    @Test
    void neitherFileMeansTheShippedDefaults(@TempDir Path root) throws ConfigurateException {
        Theme theme = ThemeFiles.load(root.resolve("aSuite/theme.conf"), root.resolve("aPlugin/theme.conf"));

        assertThat(theme.hex("accent")).isEqualTo(Theme.defaults().hex("accent"));
    }

    /** One file and nothing beneath it, for the plugin that has no suite around it. */
    @Test
    void oneFileIsReadOnItsOwn(@TempDir Path root) throws IOException, ConfigurateException {
        Path file = write(root, "own.conf", "roles { accent = \"#ff0000\" }\n");

        assertThat(ThemeFiles.load(file).hex("accent")).isEqualTo("#ff0000");
        assertThat(ThemeFiles.load(root.resolve("missing.conf")).hex("accent"))
                .isEqualTo(Theme.defaults().hex("accent"));
    }

    @Test
    void theSharedFileIsReadWhenAPluginHasNoneOfItsOwn(@TempDir Path root) throws IOException, ConfigurateException {
        Path shared = write(root, "shared.conf", "palette { sky = \"#48cae4\" }\nroles { accent = sky }\n");

        Theme theme = ThemeFiles.load(shared, root.resolve("missing.conf"));

        assertThat(theme.hex("accent")).isEqualTo("#48cae4");
    }

    @Test
    void aPluginsOwnFileWinsKeyByKey(@TempDir Path root) throws IOException, ConfigurateException {
        Path shared = write(root, "shared.conf", "roles { accent = \"#48cae4\", value = \"#ffe66d\" }\n");
        Path own = write(root, "own.conf", "roles { accent = \"#ff0000\" }\n");

        Theme theme = ThemeFiles.load(shared, own);

        assertThat(theme.hex("accent")).isEqualTo("#ff0000");
        assertThat(theme.hex("value")).isEqualTo("#ffe66d");
    }

    private static Path write(Path root, String name, String content) throws IOException {
        Path file = root.resolve(name);
        Files.writeString(file, content);
        return file;
    }
}
