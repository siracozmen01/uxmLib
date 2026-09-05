package com.uxplima.uxmlib.text.style;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

import com.uxplima.uxmlib.text.Text;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;

/** The token pass: a role becomes a colour, a prefix becomes a word plus a separator, a foreign tag survives. */
class StyleTokensTest {

    private final Theme theme = Theme.defaults();

    @Test
    void aColourTokenBecomesTheColourTheThemeHoldsForThatRole() {
        assertThat(StyleTokens.expand("<body>hello</body>", theme, true)).isEqualTo("<color:#ffffff>hello</color>");
    }

    @Test
    void aTagThatIsNotATokenIsLeftForMiniMessage() {
        assertThat(StyleTokens.expand("<b><player></b>", theme, true)).isEqualTo("<b><player></b>");
    }

    @Test
    void aCategoryPrefixIsTheWordInBoldThenTheSeparator() throws ConfigurateException {
        ConfigurationNode node = CommentedConfigurationNode.root();
        node.node("glyphs", "separator").set("▶");

        assertThat(StyleTokens.expand("<tag:'HOME'>", Theme.from(node), true))
                .isEqualTo("<b><color:#55ffff>ʜᴏᴍᴇ</color></b> <color:#555555>▶</color>");
    }

    /** The library draws no separator, so a theme that names none writes the word and nothing after it. */
    @Test
    void aCategoryPrefixIsTheWordAloneUntilTheFileNamesASeparator() {
        assertThat(StyleTokens.expand("<tag:'HOME'>", theme, true))
                .isEqualTo("<b><color:#55ffff>ʜᴏᴍᴇ</color></b> <color:#555555></color>");
    }

    @Test
    void aCategoryTheThemeColoursDifferentlyKeepsItsOwnColour() throws ConfigurateException {
        ConfigurationNode node = CommentedConfigurationNode.root();
        node.node("prefix", "categories", "shop").set("good");

        assertThat(StyleTokens.expand("<tag:'shop'>", Theme.from(node), false)).contains("<color:#55ff55>shop</color>");
    }

    /** A category the file says nothing about reads in the accent, which is a prefix and not a mistake. */
    @Test
    void aCategoryNobodyColouredReadsInTheAccent() {
        assertThat(StyleTokens.expand("<tag:'shop'>", theme, false)).contains("<color:#55ffff>shop</color>");
    }

    @Test
    void aDenialReadsInTheFailureColourWhicheverFeatureRaisedIt() {
        assertThat(StyleTokens.expand("<etag:'ERROR'>", theme, true)).startsWith("<b><color:#ff5555>ᴇʀʀᴏʀ</color></b>");
    }

    @Test
    void aHeaderIsBoldAndInTheAccentColour() {
        assertThat(StyleTokens.expand("<h:'REWARDS'>", theme, true)).isEqualTo("<b><color:#55ffff>ʀᴇᴡᴀʀᴅꜱ</color></b>");
    }

    /** A theme that names a header gradient paints the header across it; nothing else changes. */
    @Test
    void aHeaderTakesTheThemesGradientWhenThereIsOne() throws ConfigurateException {
        Theme gradient = themeWithHeaderStops("#48cae4", "#6c8dfb");

        assertThat(StyleTokens.expand("<h:'REWARDS'>", gradient, true))
                .isEqualTo("<gradient:#48cae4:#6c8dfb><b>ʀᴇᴡᴀʀᴅꜱ</b></gradient>");
    }

    /** One stop is a flat colour, which is how an operator switches the effect off. */
    @Test
    void aSingleStopIsAFlatColour() throws ConfigurateException {
        Theme flat = themeWithHeaderStops("#ff0000");

        assertThat(StyleTokens.expand("<h:'REWARDS'>", flat, false)).isEqualTo("<b><color:#ff0000>REWARDS</color></b>");
    }

    private static Theme themeWithHeaderStops(String... stops) throws ConfigurateException {
        ConfigurationNode node = CommentedConfigurationNode.root();
        node.node("gradients", "header").setList(String.class, List.of(stops));
        return Theme.from(node);
    }

    @Test
    void aHeaderMayNameAGradientOfItsOwn() throws ConfigurateException {
        Theme toned = themeWithGradients();

        assertThat(StyleTokens.expand("<h:'REWARDS':mint>", toned, false))
                .isEqualTo("<gradient:#4ecca3:#48cae4><b>REWARDS</b></gradient>");
    }

    /** A gradient nobody named is a spelling mistake, and the header it falls back to is still readable. */
    @Test
    void aGradientTheThemeDoesNotKnowFallsBackToTheHeader() throws ConfigurateException {
        Theme many = themeWithGradients();

        assertThat(StyleTokens.expand("<h:'REWARDS':moss>", many, false))
                .isEqualTo("<gradient:#ffe66d:#ff6b8b><b>REWARDS</b></gradient>");
    }

    /** A title is painted with the gradient the caller named, and with nothing else. */
    @Test
    void aTitleTakesTheGradientItWasGiven() throws ConfigurateException {
        Theme many = themeWithGradients();

        assertThat(gradientOf(StyleTokens.paint(many, Component.text("Emotes"), "mint")))
                .isEqualToIgnoringCase("<gradient:#4ecca3:#48cae4>");
    }

    /**
     * The point of naming one: a menu is as many colours as it has subjects.
     *
     * <p>Two tiles look alike only when the file asked them to, so this is the whole of what a menu needs
     * from the library and the library never has to guess it.
     */
    @Test
    void aMenuOfTitlesUsesTheGradientsItNames() throws ConfigurateException {
        Theme many = themeWithGradients();
        List<String> names = List.of("strawberry", "peach", "buttercup", "mint", "aqua", "periwinkle", "lavender");

        Set<String> used = new LinkedHashSet<>();
        for (String name : names) {
            used.add(gradientOf(StyleTokens.paint(many, Component.text("Trails"), name)));
        }

        assertThat(used).hasSize(names.size());
    }

    /** A caller that names the header, or names nothing, gets the header. */
    @Test
    void theHeaderIsWhatAnUnnamedTitleTakes() throws ConfigurateException {
        Theme gradient = themeWithHeaderStops("#48cae4", "#6c8dfb");

        assertThat(serialize(StyleTokens.paint(gradient, Component.text("Emotes"), "header")))
                .isEqualTo(serialize(StyleTokens.header(gradient, Component.text("Emotes"))));
    }

    /** A title that arrived painted was painted on purpose: a lobby name, a rank, a player's own tag. */
    @Test
    void aTitleThatCarriesAColourKeepsIt() throws ConfigurateException {
        Theme many = themeWithGradients();
        Component painted = Component.text("Emotes").color(TextColor.fromHexString("#123456"));

        assertThat(StyleTokens.paint(many, painted, "mint")).isEqualTo(painted);
    }

    /** A header, and seven neighbouring pairs of the pastel palette in the order the wheel runs. */
    private static Theme themeWithGradients() throws ConfigurateException {
        ConfigurationNode node = CommentedConfigurationNode.root();
        node.node("gradients", "header").setList(String.class, List.of("#ffe66d", "#ff6b8b"));
        node.node("gradients", "strawberry").setList(String.class, List.of("#ff6b8b", "#ffa07a"));
        node.node("gradients", "peach").setList(String.class, List.of("#ffa07a", "#ffe66d"));
        node.node("gradients", "buttercup").setList(String.class, List.of("#ffe66d", "#4ecca3"));
        node.node("gradients", "mint").setList(String.class, List.of("#4ecca3", "#48cae4"));
        node.node("gradients", "aqua").setList(String.class, List.of("#48cae4", "#6c8dfb"));
        node.node("gradients", "periwinkle").setList(String.class, List.of("#6c8dfb", "#b388ff"));
        node.node("gradients", "lavender").setList(String.class, List.of("#b388ff", "#ff6b8b"));
        return Theme.from(node);
    }

    private static String serialize(Component component) {
        return Text.serialize(component);
    }

    /** The gradient tag a painted title opens with, which is the colour and not the words inside it. */
    private static String gradientOf(Component title) {
        String written = serialize(title);
        int end = written.indexOf('>');
        return end < 0 ? written : written.substring(0, end + 1);
    }

    @Test
    void aPrefixWithNoLabelIsADefectRatherThanAnEmptyLine() {
        assertThatThrownBy(() -> StyleTokens.expand("<tag:''> hello", theme, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("label");
    }

    @Test
    void aRoleTheServerInventedIsATokenLikeAnyOther() throws Exception {
        ConfigurationNode node = CommentedConfigurationNode.root();
        node.node("roles", "premium").set("#b388ff");

        assertThat(StyleTokens.expand("<premium>VIP</premium>", Theme.from(node), true))
                .isEqualTo("<color:#b388ff>VIP</color>");
    }

    @Test
    void aHeaderMayNameARoleInsteadOfAGradient() throws Exception {
        ConfigurationNode node = CommentedConfigurationNode.root();
        node.node("roles", "value").set("#ffe66d");

        assertThat(StyleTokens.expand("<h:'SHOP':value>", Theme.from(node), false))
                .isEqualTo("<b><color:#ffe66d>SHOP</color></b>");
    }

    @Test
    void aPositionPaintsWithTheArcTheWheelHolds() throws Exception {
        ConfigurationNode node = CommentedConfigurationNode.root();
        node.node("wheel").setList(String.class, java.util.List.of("#ff6b8b", "#4ecca3", "#48cae4"));
        Theme wheeled = Theme.from(node);

        String first = Text.serialize(StyleTokens.paint(wheeled, Component.text("SHOP"), 0));
        String second = Text.serialize(StyleTokens.paint(wheeled, Component.text("SHOP"), 1));

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void aHeaderMayNameAWheelPositionInsteadOfAName() throws Exception {
        ConfigurationNode node = CommentedConfigurationNode.root();
        node.node("wheel").setList(String.class, java.util.List.of("#ff6b8b", "#4ecca3", "#48cae4"));
        Theme wheeled = Theme.from(node);

        assertThat(StyleTokens.expand("<h:'SHOP':1>", wheeled, false))
                .isEqualTo("<gradient:#4ecca3:#48cae4><b>SHOP</b></gradient>");
    }

    @Test
    void aWheelPositionWrapsSoALongMenuKeepsWorking() throws Exception {
        ConfigurationNode node = CommentedConfigurationNode.root();
        node.node("wheel").setList(String.class, java.util.List.of("#ff6b8b", "#4ecca3", "#48cae4"));
        Theme wheeled = Theme.from(node);

        assertThat(StyleTokens.expand("<h:'SHOP':4>", wheeled, false))
                .isEqualTo(StyleTokens.expand("<h:'SHOP':1>", wheeled, false));
    }

    @Test
    void aNamePaintsAcrossTheWholeWheelWithoutBold() throws Exception {
        ConfigurationNode node = CommentedConfigurationNode.root();
        node.node("wheel").setList(String.class, java.util.List.of("#ff6b8b", "#4ecca3", "#48cae4"));
        Theme wheeled = Theme.from(node);

        assertThat(StyleTokens.expand("<g:'UXM Network':wheel>", wheeled, false))
                .isEqualTo("<gradient:#ff6b8b:#4ecca3:#48cae4>UXM Network</gradient>");
    }

    @Test
    void aNameKeepsItsOwnLettersWhereAHeaderIsWrittenInSmallCapitals() throws Exception {
        ConfigurationNode node = CommentedConfigurationNode.root();
        node.node("roles", "value").set("#ffe66d");
        Theme flat = Theme.from(node);

        assertThat(StyleTokens.expand("<g:'UXM Network':value>", flat, true))
                .isEqualTo("<color:#ffe66d>UXM Network</color>");
    }

    @Test
    void aThemeWithNoWheelPaintsANameWithTheHeader() {
        assertThat(StyleTokens.expand("<g:'UXM Network':wheel>", theme, false))
                .isEqualTo(StyleTokens.expand("<g:'UXM Network'>", theme, false));
    }

    @Test
    void aThemeWithNoWheelPaintsAPositionWithTheHeader() {
        assertThat(Text.serialize(StyleTokens.paint(theme, Component.text("SHOP"), 3)))
                .isEqualTo(Text.serialize(StyleTokens.header(theme, Component.text("SHOP"))));
    }
}
