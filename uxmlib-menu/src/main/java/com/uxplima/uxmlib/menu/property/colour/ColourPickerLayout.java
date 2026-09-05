package com.uxplima.uxmlib.menu.property.colour;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bukkit.Material;

import com.uxplima.uxmlib.common.Log;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * The geometry of the shared colour-picker sub-menu: the row count, the slots the 16-colour palette is drawn into and
 * the per-swatch material for each of those slots, the custom-hex / clear / back button slots and their icons, and the
 * background filler. Nothing is hardcoded: every slot and material comes from {@code modules/management/gui/colour-
 * picker.conf} (operator copy on disk, then the bundled resource, then the code default), the same disk-first fallback
 * the editor sub-layouts use, so a typo never stops the picker opening.
 *
 * <p>{@code paletteSlots} and {@code paletteIcons} are positional and equal length: the i-th palette swatch is drawn
 * into {@code paletteSlots[i]} with {@code paletteIcons[i]}. A swatch with no configured icon falls back to its {@link
 * ColourSwatch#defaultIcon()} stained-glass pane.
 *
 * @param rows the menu height in rows (1..6)
 * @param paletteSlots the slot each palette swatch is drawn into, in {@link ColourSwatch} order
 * @param paletteIcons the material each palette swatch is drawn with, positional with {@code paletteSlots}
 * @param customSlot the custom-hex button slot
 * @param customIcon the custom-hex button material
 * @param clearSlot the clear/default button slot
 * @param clearIcon the clear/default button material
 * @param backSlot the back button slot
 * @param backIcon the back button material
 * @param filler the background filler material
 */
@NullMarked
public record ColourPickerLayout(
        int rows,
        List<Integer> paletteSlots,
        List<Material> paletteIcons,
        int customSlot,
        Material customIcon,
        int clearSlot,
        Material clearIcon,
        int backSlot,
        Material backIcon,
        Material filler) {

    /** The shared module + conf name the picker layout ships and loads under. */
    public static final String MODULE = "management";

    public static final String NAME = "colour-picker";

    /** The width of an inventory row, the multiplier that turns a declared row count into an addressable slot range. */
    private static final int SLOTS_PER_ROW = 9;

    /** The sentinel a node hands back when its value is not readable as a number: no real slot is this. */
    private static final int NOT_A_NUMBER = Integer.MIN_VALUE;

    public ColourPickerLayout {
        paletteSlots = List.copyOf(Objects.requireNonNull(paletteSlots, "paletteSlots"));
        paletteIcons = List.copyOf(Objects.requireNonNull(paletteIcons, "paletteIcons"));
        if (rows < 1 || rows > 6) {
            throw new IllegalArgumentException("rows must be 1..6, was " + rows);
        }
        if (paletteSlots.isEmpty()) {
            throw new IllegalArgumentException("paletteSlots must not be empty");
        }
        if (paletteSlots.size() != paletteIcons.size()) {
            throw new IllegalArgumentException("paletteSlots and paletteIcons must be the same length");
        }
        Objects.requireNonNull(customIcon, "customIcon");
        Objects.requireNonNull(clearIcon, "clearIcon");
        Objects.requireNonNull(backIcon, "backIcon");
        Objects.requireNonNull(filler, "filler");
    }

    /** The built-in geometry used when no conf is present or a key is missing: one swatch per palette slot. */
    public static ColourPickerLayout codeDefault() {
        List<Integer> slots = List.of(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29);
        List<Material> icons = new ArrayList<>();
        for (ColourSwatch swatch : ColourSwatch.palette()) {
            icons.add(swatch.defaultIcon());
        }
        return new ColourPickerLayout(
                6,
                slots,
                List.copyOf(icons),
                40,
                Material.ANVIL,
                42,
                Material.BARRIER,
                49,
                Material.ARROW,
                Material.BLACK_STAINED_GLASS_PANE);
    }

    /**
     * Resolve the picker layout, preferring an operator's on-disk edit, then the bundled resource, then the code
     * default. A missing file or an unparsable key logs and falls back, so the picker always opens.
     */
    public static ColourPickerLayout load(Path dataFolder, Log log) {
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        ColourPickerLayout codeDefault = codeDefault();
        ConfigurationNode root = root(dataFolder, log);
        if (root == null) {
            return codeDefault;
        }
        int rows = clampRows(root.node("rows").getInt(codeDefault.rows()), codeDefault.rows(), log);
        List<Integer> slots = slotList(root.node("palette-slots"), codeDefault.paletteSlots(), "palette-slots", log);
        List<Material> icons = paletteIcons(root.node("palette-icons"), slots.size(), codeDefault, log);
        int customSlot = slot(root.node("custom-slot"), codeDefault.customSlot(), rows, "custom-slot", log);
        int clearSlot = slot(root.node("clear-slot"), codeDefault.clearSlot(), rows, "clear-slot", log);
        int backSlot = slot(root.node("back-slot"), codeDefault.backSlot(), rows, "back-slot", log);
        Material customIcon = material(root.node("custom-icon").getString(), codeDefault.customIcon(), log);
        Material clearIcon = material(root.node("clear-icon").getString(), codeDefault.clearIcon(), log);
        Material backIcon = material(root.node("back-icon").getString(), codeDefault.backIcon(), log);
        Material filler = material(root.node("filler").getString(), codeDefault.filler(), log);
        return new ColourPickerLayout(
                rows, slots, icons, customSlot, customIcon, clearSlot, clearIcon, backSlot, backIcon, filler);
    }

    /**
     * Resolve the palette icons so the list is always the same length as the slots: a configured material per
     * slot where present, each swatch's default stained-glass pane otherwise. A conf list shorter than the slots
     * simply leaves the tail at the swatch defaults.
     */
    private static List<Material> paletteIcons(
            ConfigurationNode node, int slotCount, ColourPickerLayout codeDefault, Log log) {
        List<ColourSwatch> palette = ColourSwatch.palette();
        List<Material> icons = new ArrayList<>();
        List<? extends ConfigurationNode> configured = node.childrenList();
        for (int i = 0; i < slotCount; i++) {
            Material fallback = i < palette.size() ? palette.get(i).defaultIcon() : codeDefault.filler();
            String raw = i < configured.size() ? configured.get(i).getString() : null;
            icons.add(material(raw, fallback, log));
        }
        return icons;
    }

    private static @Nullable ConfigurationNode root(Path dataFolder, Log log) {
        Path onDisk =
                dataFolder.resolve("modules").resolve(MODULE).resolve("gui").resolve(NAME + ".conf");
        HoconConfigurationLoader loader;
        String origin;
        if (Files.isRegularFile(onDisk)) {
            loader = HoconConfigurationLoader.builder().path(onDisk).build();
            origin = onDisk.toString();
        } else {
            String resource = "modules/" + MODULE + "/gui/" + NAME + ".conf";
            if (ColourPickerLayout.class.getClassLoader().getResource(resource) == null) {
                return null;
            }
            loader = HoconConfigurationLoader.builder()
                    .source(() -> openReader(resource))
                    .build();
            origin = resource;
        }
        try {
            return loader.load();
        } catch (ConfigurateException failure) {
            log.error("failed to load colour picker layout " + origin, failure);
            return null;
        }
    }

    private static int clampRows(int rows, int fallback, Log log) {
        if (rows < 1 || rows > 6) {
            log.warn("colour picker rows {} out of range 1..6, using {}", rows, fallback);
            return fallback;
        }
        return rows;
    }

    private static Material material(@Nullable String raw, Material fallback, Log log) {
        if (raw == null) {
            return fallback;
        }
        Material matched = Material.matchMaterial(raw);
        if (matched == null) {
            log.warn("colour picker material {} is unknown, using {}", raw, fallback);
            return fallback;
        }
        return matched;
    }

    /**
     * The slot list under {@code key}, or the shipped one when the file names none or names one that is not a list of
     * numbers. An entry Configurate cannot read as a number used to come back as zero, so a palette written with
     * words collapsed onto slot zero and the picker still opened looking like something an operator had chosen. A
     * whole list is refused rather than the bad entries dropped, because a palette missing three of its sixteen
     * colours is a worse thing to hand back than the shipped one.
     */
    private static List<Integer> slotList(ConfigurationNode node, List<Integer> fallback, String key, Log log) {
        if (node.virtual() || node.empty()) {
            return fallback;
        }
        List<Integer> values = new ArrayList<>();
        for (ConfigurationNode child : node.childrenList()) {
            int value = child.getInt(NOT_A_NUMBER);
            if (value == NOT_A_NUMBER || value < 0) {
                log.warn("colour picker {} entry {} is not a slot, using the shipped list", key, child.raw());
                return fallback;
            }
            values.add(value);
        }
        return values.isEmpty() ? fallback : values;
    }

    /**
     * One button slot under {@code key}, or the shipped one when the file names something the window cannot address.
     * A slot past the end is simply never drawn, and a button nobody can click is not a button, so it falls back and
     * says so rather than being clamped into a slot the operator did not choose either.
     */
    private static int slot(ConfigurationNode node, int fallback, int rows, String key, Log log) {
        int value = node.getInt(NOT_A_NUMBER);
        if (value == NOT_A_NUMBER) {
            return fallback;
        }
        if (value < 0 || value >= rows * SLOTS_PER_ROW) {
            log.warn("colour picker {} {} is outside the {} row window, using {}", key, value, rows, fallback);
            return fallback;
        }
        return value;
    }

    private static BufferedReader openReader(String resource) throws IOException {
        InputStream in = ColourPickerLayout.class.getClassLoader().getResourceAsStream(resource);
        if (in == null) {
            throw new java.io.FileNotFoundException(resource);
        }
        return new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
    }
}
