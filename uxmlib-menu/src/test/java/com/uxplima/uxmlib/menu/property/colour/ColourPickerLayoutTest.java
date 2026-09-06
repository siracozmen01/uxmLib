package com.uxplima.uxmlib.menu.property.colour;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;

import com.uxplima.uxmlib.common.Log;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * The picker's geometry, and what a bad line in the operator's file costs. The loader's contract is that the picker
 * always opens, so every bad value degrades to the shipped one. Whether the operator is told is the thing worth
 * asserting, because a degraded window looks exactly like a window they asked for.
 */
class ColourPickerLayoutTest {

    /** A log that keeps its lines so a test can ask whether the operator was told, not just what was drawn. */
    private static final class RecordingLog implements Log {

        private final List<String> warnings = new ArrayList<>();

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {
            StringBuilder line = new StringBuilder(message);
            for (Object arg : args) {
                line.append(' ').append(arg);
            }
            warnings.add(line.toString());
        }

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }

    private final RecordingLog log = new RecordingLog();

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static Path conf(Path dataFolder, String body) throws Exception {
        Path file = dataFolder
                .resolve("modules")
                .resolve(ColourPickerLayout.MODULE)
                .resolve("gui")
                .resolve(ColourPickerLayout.NAME + ".conf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, body);
        return file;
    }

    // -- the shipped geometry ---------------------------------------------------------------------------------

    @Test
    void theCodeDefaultHasOneSlotAndOneIconPerSwatch() {
        ColourPickerLayout layout = ColourPickerLayout.codeDefault();

        assertThat(layout.paletteSlots()).hasSize(ColourSwatch.palette().size());
        assertThat(layout.paletteIcons()).hasSize(ColourSwatch.palette().size());
    }

    @Test
    void theCodeDefaultDrawsEachSwatchWithItsOwnPane() {
        assertThat(ColourPickerLayout.codeDefault().paletteIcons())
                .containsExactlyElementsOf(ColourSwatch.palette().stream()
                        .map(ColourSwatch::defaultIcon)
                        .toList());
    }

    @Test
    void theCodeDefaultsButtonsDoNotSitOnTopOfEachOtherOrOnASwatch() {
        ColourPickerLayout layout = ColourPickerLayout.codeDefault();
        List<Integer> taken = new ArrayList<>(layout.paletteSlots());
        taken.add(layout.customSlot());
        taken.add(layout.clearSlot());
        taken.add(layout.backSlot());

        assertThat(taken).doesNotHaveDuplicates();
        assertThat(taken).allSatisfy(slot -> assertThat(slot).isBetween(0, layout.rows() * 9 - 1));
    }

    // -- what a file changes ----------------------------------------------------------------------------------

    @Test
    void aMissingFileLeavesEveryValueAtItsShippedOne(@TempDir Path dir) {
        assertThat(ColourPickerLayout.load(dir, log)).isEqualTo(ColourPickerLayout.codeDefault());
        assertThat(log.warnings).isEmpty();
    }

    @Test
    void aFileChangesTheValuesItNamesAndLeavesTheRest(@TempDir Path dir) throws Exception {
        conf(dir, "rows = 3\nback-icon = FEATHER\n");

        ColourPickerLayout layout = ColourPickerLayout.load(dir, log);

        assertThat(layout.rows()).isEqualTo(3);
        assertThat(layout.backIcon()).isEqualTo(Material.FEATHER);
        assertThat(layout.customIcon())
                .as("a key the file does not name keeps the shipped value")
                .isEqualTo(ColourPickerLayout.codeDefault().customIcon());
    }

    @Test
    void anOperatorsPaletteSlotsAreTakenInTheOrderTheyWroteThem(@TempDir Path dir) throws Exception {
        conf(dir, "palette-slots = [3, 1, 2]\n");

        assertThat(ColourPickerLayout.load(dir, log).paletteSlots()).containsExactly(3, 1, 2);
    }

    /** The icon list is padded to the slot count, so a short list leaves the tail at the swatch defaults. */
    @Test
    void anIconListShorterThanTheSlotsLeavesTheTailOnTheSwatchDefaults(@TempDir Path dir) throws Exception {
        conf(dir, "palette-slots = [1, 2, 3]\npalette-icons = [\"DIRT\"]\n");

        assertThat(ColourPickerLayout.load(dir, log).paletteIcons())
                .containsExactly(
                        Material.DIRT,
                        ColourSwatch.palette().get(1).defaultIcon(),
                        ColourSwatch.palette().get(2).defaultIcon());
    }

    @Test
    void moreSlotsThanSwatchesFallsBackToTheFillerForTheExtras(@TempDir Path dir) throws Exception {
        String slots = "palette-slots = [" + String.join(", ", java.util.Collections.nCopies(17, "1")) + "]\n";
        conf(dir, slots);

        List<Material> icons = ColourPickerLayout.load(dir, log).paletteIcons();

        assertThat(icons).hasSize(17);
        assertThat(icons.get(16)).isEqualTo(ColourPickerLayout.codeDefault().filler());
    }

    // -- what a bad line costs, and whether it is said out loud -----------------------------------------------

    @Test
    void aRowCountOutsideOneToSixFallsBackAndSaysSo(@TempDir Path dir) throws Exception {
        conf(dir, "rows = 9\n");

        ColourPickerLayout layout = ColourPickerLayout.load(dir, log);

        assertThat(layout.rows()).isEqualTo(ColourPickerLayout.codeDefault().rows());
        assertThat(log.warnings).anyMatch(line -> line.contains("rows"));
    }

    @Test
    void anUnknownMaterialFallsBackAndSaysSo(@TempDir Path dir) throws Exception {
        conf(dir, "back-icon = NOT_A_REAL_BLOCK\n");

        ColourPickerLayout layout = ColourPickerLayout.load(dir, log);

        assertThat(layout.backIcon()).isEqualTo(ColourPickerLayout.codeDefault().backIcon());
        assertThat(log.warnings).anyMatch(line -> line.contains("NOT_A_REAL_BLOCK"));
    }

    /**
     * A slot that is not a number reads as zero through Configurate, so a whole palette can collapse onto slot zero
     * and the picker still opens looking deliberate. The loader warns for a bad row count and a bad material and was
     * silent here, which is the one bad value that produces a plausible window rather than an obviously shipped one.
     */
    @Test
    void aSlotThatIsNotANumberFallsBackAndSaysSo(@TempDir Path dir) throws Exception {
        conf(dir, "palette-slots = [\"left\", \"middle\"]\n");

        ColourPickerLayout layout = ColourPickerLayout.load(dir, log);

        assertThat(layout.paletteSlots())
                .as("two swatches stacked on slot zero is not a layout anybody wrote on purpose")
                .isEqualTo(ColourPickerLayout.codeDefault().paletteSlots());
        assertThat(log.warnings).anyMatch(line -> line.contains("palette-slots"));
    }

    @Test
    void aButtonSlotPastTheEndOfTheWindowFallsBackAndSaysSo(@TempDir Path dir) throws Exception {
        conf(dir, "rows = 1\ncustom-slot = 40\n");

        ColourPickerLayout layout = ColourPickerLayout.load(dir, log);

        assertThat(layout.customSlot())
                .as("a slot the window cannot address is never drawn, and a button nobody can click is not a button")
                .isEqualTo(ColourPickerLayout.codeDefault().customSlot());
        assertThat(log.warnings).anyMatch(line -> line.contains("custom-slot"));
    }

    @Test
    void aNegativeButtonSlotFallsBackAndSaysSo(@TempDir Path dir) throws Exception {
        conf(dir, "back-slot = -1\n");

        ColourPickerLayout layout = ColourPickerLayout.load(dir, log);

        assertThat(layout.backSlot()).isEqualTo(ColourPickerLayout.codeDefault().backSlot());
        assertThat(log.warnings).anyMatch(line -> line.contains("back-slot"));
    }

    // -- the record's own guards ------------------------------------------------------------------------------

    private static ColourPickerLayout layout(int rows, List<Integer> slots, List<Material> icons) {
        return new ColourPickerLayout(
                rows,
                slots,
                icons,
                40,
                Material.ANVIL,
                42,
                Material.BARRIER,
                49,
                Material.ARROW,
                Material.BLACK_STAINED_GLASS_PANE);
    }

    @Test
    void aPaletteWithMoreSlotsThanIconsIsRefusedBecauseTheTwoAreReadPositionally() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> layout(6, List.of(1, 2), List.of(Material.DIRT)))
                .withMessageContaining("same length");
    }

    @Test
    void aPaletteWithNoSlotsIsRefusedBecauseAPickerWithNoSwatchesIsNotAPicker() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> layout(6, List.of(), List.of()))
                .withMessageContaining("paletteSlots must not be empty");
    }

    @Test
    void aWindowThatIsNotOneToSixRowsIsRefused() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> layout(0, List.of(1), List.of(Material.DIRT)))
                .withMessageContaining("rows must be 1..6");
    }

    /**
     * The three button keys read through Configurate with a sentinel default, and an absent key reads as that same
     * sentinel. So the loader has to ask whether the key is there before it asks what number it holds: without that,
     * a word where a slot belongs takes the silent path that an omitted key is supposed to take, and the operator
     * is told nothing.
     */
    @Test
    void aButtonSlotThatIsNotANumberIsSaidOutLoudAndNotTreatedAsAnOmittedKey(@TempDir Path dir) throws Exception {
        conf(dir, "custom-slot = left\n");

        ColourPickerLayout layout = ColourPickerLayout.load(dir, log);

        assertThat(layout.customSlot())
                .isEqualTo(ColourPickerLayout.codeDefault().customSlot());
        assertThat(log.warnings).anySatisfy(line -> assertThat(line).contains("custom-slot", "left"));
    }

    @Test
    void aButtonKeyTheFileLeavesOutIsNotWarnedAbout(@TempDir Path dir) throws Exception {
        conf(dir, "rows = 6\n");

        ColourPickerLayout layout = ColourPickerLayout.load(dir, log);

        assertThat(layout.customSlot())
                .isEqualTo(ColourPickerLayout.codeDefault().customSlot());
        assertThat(log.warnings).isEmpty();
    }

    /**
     * A palette slot past the end of the window draws nothing at all, and the swatch it carried is simply missing
     * from a picker that still opens and still looks deliberate. The list falls back whole rather than losing the
     * one entry, because the slots are positional against the icons: dropping one shifts every colour after it.
     */
    @Test
    void aPaletteSlotPastTheEndOfTheWindowRefusesTheWholeList(@TempDir Path dir) throws Exception {
        conf(dir, "rows = 3\npalette-slots = [1, 2, 60]\n");

        ColourPickerLayout layout = ColourPickerLayout.load(dir, log);

        assertThat(layout.paletteSlots())
                .containsExactlyElementsOf(ColourPickerLayout.codeDefault().paletteSlots());
        assertThat(log.warnings).anySatisfy(line -> assertThat(line).contains("palette-slots", "60"));
    }
}
