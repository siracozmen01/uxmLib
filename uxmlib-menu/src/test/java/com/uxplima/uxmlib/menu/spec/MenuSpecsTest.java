package com.uxplima.uxmlib.menu.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.uxplima.uxmlib.common.Log;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Where a menu spec comes from, in order: the operator's copy on disk, the copy bundled in the jar, and an empty
 * window as the last resort. The order is the whole of it, and so is the promise that no step of it aborts a
 * module's wiring: a server whose operator saved a broken file still starts, with a window and a line in the log.
 *
 * <p>Every fallback is therefore asserted twice: on the spec that comes back, and on the log line, because a
 * fallback that happens silently is indistinguishable from a file that loaded.
 */
class MenuSpecsTest {

    private static final String BUNDLED = "menus/bundled-test.conf";

    /** Records what was said and at which level, so a silent fallback is a failing test rather than a passing one. */
    private static final class Recording implements Log {

        private final List<String> lines = new ArrayList<>();

        @Override
        public void info(String message, Object... args) {
            lines.add("info " + message);
        }

        @Override
        public void warn(String message, Object... args) {
            lines.add("warn " + format(message, args));
        }

        @Override
        public void error(String message, Throwable cause) {
            lines.add("error " + message);
        }

        @Override
        public void debug(String message, Object... args) {
            lines.add("debug " + message);
        }

        private static String format(String message, Object... args) {
            String out = message;
            for (Object arg : args) {
                out = out.replaceFirst("\\{}", String.valueOf(arg));
            }
            return out;
        }
    }

    private final Recording log = new Recording();

    private static void write(Path file, String body) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, body);
    }

    // -- the order --------------------------------------------------------------------------------------------

    /** The operator's copy is the point of the search, so it wins whenever it is there and readable. */
    @Test
    void theOperatorsCopyOnDiskWinsOverTheBundledOne(@TempDir Path dataFolder) throws IOException {
        write(dataFolder.resolve(BUNDLED), "rows = 6");

        MenuSpec spec = MenuSpecs.loadOrBundled(BUNDLED, dataFolder, 3, log);

        assertThat(spec.rows()).isEqualTo(6);
        assertThat(log.lines).isEmpty();
    }

    /** No copy on disk is the ordinary first run, not a failure, so the bundled one is used without a word. */
    @Test
    void noCopyOnDiskIsTheOrdinaryCaseAndSaysNothing(@TempDir Path dataFolder) {
        MenuSpec spec = MenuSpecs.loadOrBundled(BUNDLED, dataFolder, 3, log);

        assertThat(spec.rows()).isEqualTo(4);
        assertThat(spec.title()).isEqualTo("the bundled one");
        assertThat(log.lines).isEmpty();
    }

    /**
     * A broken copy on disk is the operator's own edit and the one case where they need to be told. The window
     * still opens, from the bundled copy, and the message names the file they have to go and fix.
     */
    @Test
    void aBrokenCopyOnDiskFallsBackToTheBundledOneAndNamesTheFile(@TempDir Path dataFolder) throws IOException {
        Path onDisk = dataFolder.resolve(BUNDLED);
        write(onDisk, "rows = 99");

        MenuSpec spec = MenuSpecs.loadOrBundled(BUNDLED, dataFolder, 3, log);

        assertThat(spec.rows()).as("the bundled copy, not the broken one").isEqualTo(4);
        assertThat(log.lines).singleElement().asString().startsWith("error ").contains(onDisk.toString());
    }

    /** A directory where the file should be is not a copy of anything, so the search moves on rather than failing. */
    @Test
    void aDirectoryWhereTheFileShouldBeIsNotACopy(@TempDir Path dataFolder) throws IOException {
        Files.createDirectories(dataFolder.resolve(BUNDLED));

        MenuSpec spec = MenuSpecs.loadOrBundled(BUNDLED, dataFolder, 3, log);

        assertThat(spec.rows()).isEqualTo(4);
        assertThat(log.lines)
                .as("a directory is not a broken file and needs no telling off")
                .isEmpty();
    }

    // -- the last resort --------------------------------------------------------------------------------------

    /**
     * A resource missing from the jar is a packaging fault nobody can fix from a config file, so it is a warning
     * naming the resource, and the module still wires: an empty window of the rows the caller asked for.
     */
    @Test
    void aResourceMissingFromTheJarLeavesAnEmptyWindowAndNamesTheResource(@TempDir Path dataFolder) {
        MenuSpec spec = MenuSpecs.loadOrBundled("menus/nothing-ships-this.conf", dataFolder, 5, log);

        assertThat(spec.rows()).isEqualTo(5);
        assertThat(spec.items()).isEmpty();
        assertThat(spec.title()).isEmpty();
        assertThat(log.lines).singleElement().asString().startsWith("warn ").contains("menus/nothing-ships-this.conf");
    }

    /** The last-resort window is a window: it has rows, it has no items, and nothing in it can fail to draw. */
    @Test
    void theLastResortWindowIsEmptyRatherThanHalfBuilt(@TempDir Path dataFolder) {
        MenuSpec spec = MenuSpecs.loadOrBundled("menus/nothing-ships-this.conf", dataFolder, 1, log);

        assertThat(spec.rows()).isEqualTo(1);
        assertThat(spec.items()).isEmpty();
        assertThat(spec.openActions()).isEmpty();
        assertThat(spec.closeActions()).isEmpty();
        assertThat(spec.openRequirement()).isEmpty();
        assertThat(spec.contents()).isEmpty();
    }

    /**
     * A row count no window can have is refused at the call rather than on the path that uses it. That path runs
     * only when both files are missing, so a wrong value would otherwise wire cleanly in development and fail on a
     * server whose jar lost the resource.
     */
    @Test
    void aRowCountNoWindowCanHaveIsRefusedAtTheCall(@TempDir Path dataFolder) throws IOException {
        write(dataFolder.resolve(BUNDLED), "rows = 6");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> MenuSpecs.loadOrBundled(BUNDLED, dataFolder, 0, log))
                .withMessageContaining("fallbackRows");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> MenuSpecs.loadOrBundled(BUNDLED, dataFolder, 7, log))
                .withMessageContaining("fallbackRows");
    }
}
