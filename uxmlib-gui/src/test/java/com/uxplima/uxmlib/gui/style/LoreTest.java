package com.uxplima.uxmlib.gui.style;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmlib.text.style.Theme;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;

/** The lore shape: the order of the blocks, the air between them, and glyphs that come from the theme. */
class LoreTest {

    private final Theme theme = TestThemes.withGlyphs();

    @Test
    void theBlocksComeOutInTheOrderTheShapeAllows() {
        Component lore = Lore.of(theme)
                .crumb(Component.text("Cosmetic"))
                .description(Component.text("About"), Component.text("What it does"))
                .details(Component.text("Details"))
                .row(Component.text("Owned"), Component.text("12"))
                .action(Component.text("Click to wear"))
                .build();

        List<String> lines = lines(lore);
        assertThat(lines.get(0)).contains("Cosmetic");
        assertThat(lines).anySatisfy(line -> assertThat(line).contains("✎").contains("About"));
        assertThat(lines).anySatisfy(line -> assertThat(line).contains("≡").contains("Details"));
        assertThat(lines)
                .anySatisfy(
                        line -> assertThat(line).contains("•").contains("Owned").contains("12"));
        assertThat(lines.get(lines.size() - 2)).contains("→").contains("Click to wear");
    }

    @Test
    void aBlockNobodyFilledInTakesNoSpace() {
        Component lore = Lore.of(theme)
                .description(Component.text("About"), Component.text("Text"))
                .build();

        assertThat(lines(lore)).hasSize(3); // the header, its line, and the blank that closes the box
    }

    @Test
    void aBlankLineSeparatesTwoBlocks() {
        Component lore = Lore.of(theme)
                .crumb(Component.text("Cosmetic"))
                .action(Component.text("Click"))
                .build();

        assertThat(lines(lore).get(1)).isBlank();
    }

    /** A description a translator wrote over two lines stays two lines. */
    @Test
    void aMultiLineDescriptionKeepsTheBreaksTheTranslatorWrote() {
        Component text = Component.text("first").append(Component.newline()).append(Component.text("second"));

        Component lore =
                Lore.of(theme).description(Component.text("About"), text).build();

        assertThat(lines(lore)).hasSize(4);
        assertThat(lines(lore).get(1)).isEqualTo("    first ");
        assertThat(lines(lore).get(2)).isEqualTo("    second ");
    }

    /** A sentence too long for a tooltip is broken, and nothing of it is lost. */
    @Test
    void aLongDescriptionIsWrapped() {
        String sentence = "It throws a hook where you look, and pulls you to it.";

        Component lore = Lore.of(theme)
                .description(Component.text("About"), Component.text(sentence))
                .build();

        List<String> written = lines(lore).stream()
                .filter(line -> !line.isBlank() && !line.contains("✎"))
                .map(String::trim)
                .toList();

        assertThat(written).hasSizeGreaterThan(1);
        assertThat(written).allSatisfy(line -> assertThat(line.length()).isLessThanOrEqualTo(34));
        assertThat(String.join(" ", written)).isEqualTo(sentence);
    }

    /** A greedy wrap leaves one word alone on the last line. The second pass spreads them out. */
    @Test
    void aWrappedDescriptionDoesNotLeaveOneWordOnTheLastLine() {
        Component lore = Lore.of(theme)
                .description(
                        Component.text("About"),
                        Component.text("A sheep of every colour runs off, and then it goes bang."))
                .build();

        List<String> written = lines(lore).stream()
                .filter(line -> !line.isBlank() && !line.contains("✎"))
                .map(String::trim)
                .toList();

        assertThat(written).hasSize(2);
        assertThat(written.get(written.size() - 1).split(" ")).hasSizeGreaterThan(1);
    }

    /** A short description is left exactly as it was written. */
    @Test
    void aShortDescriptionIsNotTouched() {
        Component lore = Lore.of(theme)
                .description(Component.text("About"), Component.text("What it does"))
                .build();

        assertThat(lines(lore)).anySatisfy(line -> assertThat(line).isEqualTo("    What it does "));
    }

    @Test
    void theGlyphsComeFromTheThemeSoAServerCanChangeThem() throws ConfigurateException {
        ConfigurationNode node = CommentedConfigurationNode.root();
        node.node("glyphs", "row").set("-");
        Lore lore = Lore.of(Theme.from(node));

        Component built = lore.details(Component.text("Details"))
                .row(Component.text("Owned"), Component.text("12"))
                .build();

        assertThat(lines(built)).anySatisfy(line -> assertThat(line).contains("- Owned"));
    }

    /**
     * The column: a breadcrumb, a description line and a bullet all start where the header's words start.
     * A header spends a space, its glyph and a space, which for the shipped glyphs is four spaces' worth.
     */
    @Test
    void everythingUnderAHeaderLinesUpWithItsWords() {
        Component lore = Lore.of(theme)
                .crumb(Component.text("Cosmetic"))
                .description(Component.text("About"), Component.text("What it does"))
                .details(Component.text("Details"))
                .row(Component.text("Owned"), Component.text("12"))
                .build();

        List<String> lines = lines(lore);
        assertThat(lines.get(0)).isEqualTo("    Cosmetic ");
        assertThat(lines).anySatisfy(line -> assertThat(line).isEqualTo("    What it does "));
        assertThat(lines).anySatisfy(line -> assertThat(line).isEqualTo("    • Owned 12 "));
        assertThat(lines).anySatisfy(line -> assertThat(line).isEqualTo(" ≡ Details "));
    }

    /** The indent is measured, so a wider glyph moves the column instead of leaving the text behind. */
    @Test
    void aWiderGlyphMovesTheColumnWithIt() throws ConfigurateException {
        ConfigurationNode node = CommentedConfigurationNode.root();
        node.node("glyphs", "details").set("Æ"); // 10 pixels, where the shipped one is 7

        Component lore = Lore.of(Theme.from(node))
                .details(Component.text("Details"))
                .row(Component.text("Owned"), Component.text("12"))
                .build();

        assertThat(lines(lore)).anySatisfy(line -> assertThat(line).isEqualTo("     • Owned 12 "));
    }

    /** Lore an operator wrote in a config file lands in the same column as lore built here. */
    @Test
    void writtenLinesTakeTheShapeOfTheRest() {
        Component lore = Lore.of(theme)
                .lines(List.of(Component.text("A hat."), Component.empty(), Component.text("Worn on the head.")))
                .build();

        List<String> lines = lines(lore);
        assertThat(lines.get(0)).isEqualTo("    A hat. ");
        assertThat(lines.get(1)).isBlank();
        assertThat(lines.get(2)).isEqualTo("    Worn on the head. ");
        assertThat(lines.get(3)).isBlank();
    }

    /** A file that already closes its own box does not get two blank lines when it moves onto this. */
    @Test
    void blankLinesAtEitherEndOfAWrittenBlockAreDropped() {
        Component lore = Lore.of(theme)
                .lines(List.of(Component.empty(), Component.text("A hat."), Component.text(" ")))
                .build();

        assertThat(lines(lore)).containsExactly("    A hat. ", " ");
    }

    @Test
    void aWrittenBlockSplitsOnTheBreaksItWasWrittenWith() {
        Component written = Component.text("first").append(Component.newline()).append(Component.text("second"));

        assertThat(lines(Lore.of(theme).lines(written).build())).containsExactly("    first ", "    second ", " ");
    }

    /** A file that draws its own furniture keeps it: no column is applied, only the pad and the air. */
    @Test
    void verbatimLinesKeepTheGeometryTheyWereWrittenWith() {
        Component lore = Lore.of(theme)
                .verbatim(List.of(
                        Component.text(" ◆ Server Selector"),
                        Component.text("    Navigation"),
                        Component.text(" "),
                        Component.text(" → Right click to open")))
                .build();

        assertThat(lines(lore))
                .containsExactly(" ◆ Server Selector ", "    Navigation ", " ", " → Right click to open ", " ");
    }

    /** A file that already closes its own box does not close it twice on the way through. */
    @Test
    void verbatimDropsTheBlankLinesAFileClosesItselfWith() {
        Component lore = Lore.of(theme)
                .verbatim(List.of(Component.text(" ◆ Title"), Component.text(" ")))
                .build();

        assertThat(lines(lore)).containsExactly(" ◆ Title ", " ");
    }

    /** The last thing a tile says needs air under it, or the text reads as cut off by the tooltip edge. */
    @Test
    void theLoreEndsOnABlankLine() {
        Component lore = Lore.of(theme).crumb(Component.text("Cosmetic")).build();

        List<String> lines = lines(lore);
        assertThat(lines.get(lines.size() - 1)).isBlank();
        assertThat(lines.get(lines.size() - 2)).contains("Cosmetic");
    }

    @Test
    void anEmptyLoreStaysEmptyRatherThanGrowingALine() {
        assertThat(Lore.of(theme).build()).isEqualTo(Component.empty());
    }

    private static List<String> lines(Component lore) {
        String plain = PlainTextComponentSerializer.plainText().serialize(lore);
        return List.of(plain.split("\n", -1));
    }

    /**
     * The wrap width is a taste rather than a rule, so a caller may state its own and the sentences follow
     * it. A tooltip that has to sit beside a wider window is the reason to have the setting at all.
     */
    @Test
    void aCallerMayStateItsOwnDescriptionWidth() {
        Theme theme = TestThemes.withGlyphs();
        Component sentence = Component.text("A line of light that follows your feet everywhere you walk.");

        List<String> narrow = lines(Lore.of(theme)
                .width(16)
                .description(Component.text("Description"), sentence)
                .build());
        List<String> wide = lines(Lore.of(theme)
                .width(60)
                .description(Component.text("Description"), sentence)
                .build());

        assertThat(narrow).hasSizeGreaterThan(wide.size());
    }

    /** A width nobody could write a sentence into is a defect in the caller, not a tooltip of empty lines. */
    @Test
    void aWidthThatIsNotPositiveIsRefused() {
        assertThatThrownBy(() -> Lore.of(TestThemes.withGlyphs()).width(0)).isInstanceOf(IllegalArgumentException.class);
    }
}
