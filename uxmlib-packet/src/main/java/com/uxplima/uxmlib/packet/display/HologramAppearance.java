package com.uxplima.uxmlib.packet.display;

import java.util.Objects;

import org.bukkit.Color;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;

import org.joml.Vector3f;

/**
 * How a {@link PacketHologram} looks: the text-display knobs a viewer's client applies, held as an immutable
 * value so the driver and its tests need no server.
 *
 * <p>The vocabulary is Bukkit's own ({@link Display.Billboard}, {@link TextDisplay.TextAlignment},
 * {@link Color}) and not an enum of ours, so a plugin that moves an entity hologram onto packets keeps the
 * words it already wrote for {@code Holograms.builder()}.
 *
 * <p>{@code com.uxplima.uxmlib.nametag.Appearance} is the entity-riding twin of this record and the two are
 * deliberately separate. That one carries the line-of-sight fade a nametag needs and rides a player; this one
 * is anchored in the world and fades for nobody. Sharing one record would mean moving the nametag types down
 * into this module, which renames types uxmEssentials already imports.
 *
 * @param billboard how the text turns to face the viewer
 * @param background the colour behind the glyphs; alpha 0 is a transparent panel
 * @param textShadow whether the glyphs cast a drop shadow
 * @param seeThrough whether the text draws through solid blocks
 * @param alignment how several lines are justified against each other
 * @param lineWidth the width in pixels at which the client wraps a line
 * @param viewRange the multiplier on the distance at which a client still draws the text
 * @param translation the offset from the anchor, applied before the billboard turn
 * @param scale the per-axis size of the text
 * @param textOpacity how opaque the glyphs are, 0 to 255
 */
public record HologramAppearance(
        Display.Billboard billboard,
        Color background,
        boolean textShadow,
        boolean seeThrough,
        TextDisplay.TextAlignment alignment,
        int lineWidth,
        float viewRange,
        Vector3f translation,
        Vector3f scale,
        int textOpacity) {

    /** Fully opaque glyphs, which is what a hologram wants unless an operator asks for a ghost. */
    public static final int FULL_OPACITY = 255;

    /** The transparent panel a hologram carries when nobody asked for a background. */
    public static final Color NO_BACKGROUND = Color.fromARGB(0, 0, 0, 0);

    public HologramAppearance {
        Objects.requireNonNull(billboard, "billboard");
        Objects.requireNonNull(background, "background");
        Objects.requireNonNull(alignment, "alignment");
        Objects.requireNonNull(translation, "translation");
        Objects.requireNonNull(scale, "scale");
        if (textOpacity < 0 || textOpacity > 255) {
            throw new IllegalArgumentException("textOpacity must be 0-255, was " + textOpacity);
        }
        if (lineWidth < 0) {
            throw new IllegalArgumentException("lineWidth must be >= 0, was " + lineWidth);
        }
        if (viewRange < 0f) {
            throw new IllegalArgumentException("viewRange must be >= 0, was " + viewRange);
        }
        // Defensive copies: Vector3f is mutable, so a caller cannot reach in and change a stored appearance.
        translation = new Vector3f(translation);
        scale = new Vector3f(scale);
    }

    /**
     * The plain look: text that always faces the viewer, no background panel, no shadow, hidden behind
     * blocks, centred, wrapped at 200 pixels, default view range, no offset, unit scale, fully opaque.
     */
    public static HologramAppearance defaults() {
        return new HologramAppearance(
                Display.Billboard.CENTER,
                NO_BACKGROUND,
                false,
                false,
                TextDisplay.TextAlignment.CENTER,
                200,
                1.0f,
                new Vector3f(0f, 0f, 0f),
                new Vector3f(1f, 1f, 1f),
                FULL_OPACITY);
    }

    @Override
    public Vector3f translation() {
        // Hand back a copy so the stored value stays immutable.
        return new Vector3f(translation);
    }

    @Override
    public Vector3f scale() {
        return new Vector3f(scale);
    }

    /** The background as packed ARGB, which is what the metadata field takes. */
    public int backgroundArgb() {
        return background.asARGB();
    }

    /** The same look with another billboard mode. */
    public HologramAppearance withBillboard(Display.Billboard value) {
        return new HologramAppearance(
                value,
                background,
                textShadow,
                seeThrough,
                alignment,
                lineWidth,
                viewRange,
                translation,
                scale,
                textOpacity);
    }

    /** The same look with another background panel. */
    public HologramAppearance withBackground(Color value) {
        return new HologramAppearance(
                billboard,
                value,
                textShadow,
                seeThrough,
                alignment,
                lineWidth,
                viewRange,
                translation,
                scale,
                textOpacity);
    }

    /** The same look with the drop shadow switched on or off. */
    public HologramAppearance withTextShadow(boolean value) {
        return new HologramAppearance(
                billboard,
                background,
                value,
                seeThrough,
                alignment,
                lineWidth,
                viewRange,
                translation,
                scale,
                textOpacity);
    }

    /** The same look with drawing through blocks switched on or off. */
    public HologramAppearance withSeeThrough(boolean value) {
        return new HologramAppearance(
                billboard,
                background,
                textShadow,
                value,
                alignment,
                lineWidth,
                viewRange,
                translation,
                scale,
                textOpacity);
    }

    /** The same look with another justification. */
    public HologramAppearance withAlignment(TextDisplay.TextAlignment value) {
        return new HologramAppearance(
                billboard,
                background,
                textShadow,
                seeThrough,
                value,
                lineWidth,
                viewRange,
                translation,
                scale,
                textOpacity);
    }

    /** The same look with another wrap width. */
    public HologramAppearance withLineWidth(int value) {
        return new HologramAppearance(
                billboard,
                background,
                textShadow,
                seeThrough,
                alignment,
                value,
                viewRange,
                translation,
                scale,
                textOpacity);
    }

    /** The same look with another view-range multiplier. */
    public HologramAppearance withViewRange(float value) {
        return new HologramAppearance(
                billboard,
                background,
                textShadow,
                seeThrough,
                alignment,
                lineWidth,
                value,
                translation,
                scale,
                textOpacity);
    }

    /** The same look offset from the anchor by {@code (x, y, z)}. */
    public HologramAppearance withTranslation(float x, float y, float z) {
        return new HologramAppearance(
                billboard,
                background,
                textShadow,
                seeThrough,
                alignment,
                lineWidth,
                viewRange,
                new Vector3f(x, y, z),
                scale,
                textOpacity);
    }

    /** The same look scaled uniformly. */
    public HologramAppearance withScale(float factor) {
        return withScale(factor, factor, factor);
    }

    /** The same look scaled per axis. */
    public HologramAppearance withScale(float x, float y, float z) {
        return new HologramAppearance(
                billboard,
                background,
                textShadow,
                seeThrough,
                alignment,
                lineWidth,
                viewRange,
                translation,
                new Vector3f(x, y, z),
                textOpacity);
    }

    /** The same look at another glyph opacity, 0 to 255. */
    public HologramAppearance withTextOpacity(int value) {
        return new HologramAppearance(
                billboard,
                background,
                textShadow,
                seeThrough,
                alignment,
                lineWidth,
                viewRange,
                translation,
                scale,
                value);
    }
}
