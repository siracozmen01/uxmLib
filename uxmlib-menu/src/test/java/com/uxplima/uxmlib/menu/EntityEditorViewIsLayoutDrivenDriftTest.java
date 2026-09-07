package com.uxplima.uxmlib.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Guards the generic editor shell against naming a material. {@link EntityEditorView} draws a module's management
 * screen from the {@link EntityEditorLayout} its caller hands it, which is a file the operator can rewrite. Every
 * icon it paints therefore comes from that layout, and a shipped {@code .conf} that drops a key fails loudly at
 * load rather than falling back to something the shell picked.
 *
 * <p>A material named inside the shell breaks that. The button keeps drawing, the operator's file is no longer the
 * one place the icons live, and a dropped key hides a button with nobody told. This is the test that says so. It
 * reads the shell's source text, because a literal the shell never reaches on the tested path is still a literal.
 */
class EntityEditorViewIsLayoutDrivenDriftTest {

    /** The shell under guard, and the two things it may not carry. */
    private static final String SHELL = "uxmlib-menu/src/main/java/com/uxplima/uxmlib/menu/EntityEditorView.java";

    private static final List<String> FORBIDDEN = List.of("Material.", "import org.bukkit.Material;");

    @Test
    void theEditorShellNamesNoMaterialAndTakesItsIconsFromTheLayout() {
        Path source = repoRoot().resolve(SHELL);
        assertThat(source).as("expected the editor shell at %s", source).exists();

        List<String> found = new ArrayList<>();
        List<String> lines = read(source);
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            for (String forbidden : FORBIDDEN) {
                if (line.contains(forbidden)) {
                    found.add(SHELL + ":" + (index + 1) + " names " + forbidden + " in: " + line.strip());
                }
            }
        }

        assertThat(found)
                .as(
                        "%s takes its materials from the layout, so it may name none itself. "
                                + "A material here is an icon the operator's file no longer owns, and a dropped "
                                + "layout key would hide the button in silence:%n%s",
                        SHELL, String.join(System.lineSeparator(), found))
                .isEmpty();
    }

    private static List<String> read(Path file) {
        try {
            return Files.readAllLines(file);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.exists(dir.resolve("settings.gradle.kts"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("could not locate the repo root (settings.gradle.kts)");
    }
}
