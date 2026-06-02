package com.uxplima.uxmlib.hologram.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bukkit.Color;
import org.bukkit.entity.Display;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmlib.hologram.Appearance;
import com.uxplima.uxmlib.hologram.HologramSpec;
import com.uxplima.uxmlib.hologram.Transform;
import com.uxplima.uxmlib.text.Text;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

/**
 * Reads a {@link HologramSpec} from a HOCON/Configurate node, so a server owner can lay out and re-skin a
 * hologram in a config file while code spawns it. The node carries a {@code lines} list (MiniMessage) and
 * an optional {@code appearance} table with {@code billboard}, {@code seeThrough}, {@code glow} (hex),
 * {@code background} (hex), {@code lineWidth}, {@code textShadow}, {@code viewRange}, {@code scale}, and
 * {@code rotation}. Spawn the result with {@link com.uxplima.uxmlib.hologram.Holograms} or a {@link com.uxplima.uxmlib.hologram.HologramManager}.
 *
 * <pre>{@code
 * lines = [ "<gold>Spawn", "<gray>Welcome!" ]
 * appearance {
 *   billboard = CENTER
 *   glow = "#ff5555"
 *   scale = 1.5
 * }
 * }</pre>
 */
public final class HologramConfig {

    private HologramConfig() {}

    /** Build a spec from {@code node}; throws {@link IllegalArgumentException} on a malformed value. */
    public static HologramSpec load(ConfigurationNode node) {
        Objects.requireNonNull(node, "node");
        List<Component> lines = readLines(node.node("lines"));
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("a hologram config needs at least one line");
        }
        return new HologramSpec(lines, readAppearance(node.node("appearance")));
    }

    /**
     * Write {@code spec} back into {@code node} in the same shape {@link #load} reads, so an admin's
     * edit-on-disk round-trips: {@code load(save(spec)) } reconstructs an equal spec. Lines are serialized
     * back to MiniMessage and only non-default appearance fields are emitted, keeping the file tidy.
     */
    public static void save(HologramSpec spec, ConfigurationNode node) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(node, "node");
        try {
            List<String> lines = new ArrayList<>();
            for (Component line : spec.lines()) {
                lines.add(Text.serialize(line));
            }
            node.node("lines").setList(String.class, lines);
            writeAppearance(spec.appearance(), node.node("appearance"));
        } catch (SerializationException malformed) {
            throw new IllegalArgumentException("could not serialize hologram spec", malformed);
        }
    }

    private static void writeAppearance(Appearance look, ConfigurationNode node) throws SerializationException {
        node.node("billboard").set(look.billboard().name());
        node.node("seeThrough").set(look.seeThrough());
        node.node("textShadow").set(look.textShadow());
        if (look.glow() != null) {
            node.node("glow").set(hex(look.glow()));
        }
        if (look.background() != null) {
            node.node("background").set(hex(look.background()));
        }
        if (look.lineWidth() != null) {
            node.node("lineWidth").set(look.lineWidth());
        }
        if (look.viewRange() != null) {
            node.node("viewRange").set(look.viewRange());
        }
        writeTransform(look.transform(), node);
    }

    private static void writeTransform(@org.jspecify.annotations.Nullable Transform transform, ConfigurationNode node)
            throws SerializationException {
        if (transform != null) {
            node.node("scale").set(transform.scaleX());
            node.node("rotation").set(transform.yawDegrees());
        }
    }

    private static String hex(Color color) {
        return String.format("#%06x", color.asRGB() & 0xffffff);
    }

    private static List<Component> readLines(ConfigurationNode linesNode) {
        List<Component> lines = new ArrayList<>();
        try {
            List<String> raw = linesNode.getList(String.class);
            if (raw != null) {
                for (String line : raw) {
                    lines.add(Text.mini(line));
                }
            }
        } catch (SerializationException malformed) {
            throw new IllegalArgumentException("hologram 'lines' must be a list of strings", malformed);
        }
        return lines;
    }

    private static Appearance readAppearance(ConfigurationNode node) {
        Appearance appearance = Appearance.DEFAULT;
        if (node.virtual()) {
            return appearance;
        }
        appearance = appearance
                .withBillboard(billboard(node.node("billboard").getString("CENTER")))
                .withSeeThrough(node.node("seeThrough").getBoolean(false))
                .withTextShadow(node.node("textShadow").getBoolean(false));
        appearance = applyColors(appearance, node);
        appearance = applyNumbers(appearance, node);
        return appearance;
    }

    private static Appearance applyColors(Appearance appearance, ConfigurationNode node) {
        String glow = node.node("glow").getString();
        if (glow != null) {
            appearance = appearance.withGlow(color(glow));
        }
        String background = node.node("background").getString();
        if (background != null) {
            appearance = appearance.withBackground(color(background));
        }
        return appearance;
    }

    private static Appearance applyNumbers(Appearance appearance, ConfigurationNode node) {
        if (!node.node("lineWidth").virtual()) {
            appearance = appearance.withLineWidth(node.node("lineWidth").getInt());
        }
        if (!node.node("viewRange").virtual()) {
            appearance = appearance.withViewRange((float) node.node("viewRange").getDouble());
        }
        boolean hasScale = !node.node("scale").virtual();
        boolean hasRotation = !node.node("rotation").virtual();
        if (hasScale || hasRotation) {
            float scale = (float) node.node("scale").getDouble(1.0);
            float yaw = (float) node.node("rotation").getDouble(0.0);
            appearance = appearance.withTransform(new Transform(scale, scale, scale, yaw));
        }
        return appearance;
    }

    private static Display.Billboard billboard(String raw) {
        try {
            return Display.Billboard.valueOf(raw.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException("unknown billboard: " + raw, unknown);
        }
    }

    private static Color color(String hex) {
        String cleaned = hex.startsWith("#") ? hex.substring(1) : hex;
        try {
            return Color.fromRGB(Integer.parseInt(cleaned, 16));
        } catch (IllegalArgumentException bad) {
            throw new IllegalArgumentException("invalid colour in hologram config: " + hex, bad);
        }
    }
}
