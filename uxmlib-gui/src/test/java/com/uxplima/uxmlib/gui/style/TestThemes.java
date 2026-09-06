package com.uxplima.uxmlib.gui.style;

import com.uxplima.uxmlib.text.style.Theme;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;

/**
 * The theme these tests draw with.
 *
 * <p>The library ships no glyph of its own, because furniture is taste and a library holds none. A menu that
 * wants a marked description line names the character in its theme file, so a test of that geometry has to
 * name it too, exactly as a consumer does.
 */
final class TestThemes {

    private TestThemes() {}

    /** A theme that names the glyphs a tile and a tooltip are drawn with. */
    static Theme withGlyphs() {
        try {
            ConfigurationNode node = CommentedConfigurationNode.root();
            node.node("glyphs", "separator").set("▶");
            node.node("glyphs", "title").set("◆");
            node.node("glyphs", "description").set("✎");
            node.node("glyphs", "details").set("≡");
            node.node("glyphs", "row").set("•");
            node.node("glyphs", "status").set("•");
            node.node("glyphs", "action").set("→");
            return Theme.from(node);
        } catch (ConfigurateException failure) {
            throw new IllegalStateException("the test theme did not build", failure);
        }
    }
}
