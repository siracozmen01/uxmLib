package com.uxplima.uxmlib.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.uxplima.uxmlib.menu.property.colour.ColourSwatch;
import org.junit.jupiter.api.Test;

/**
 * The keys the engine's own windows ask the host's catalog for. They are a list of strings, so nothing here checks
 * behaviour; what it checks is the two mistakes a list of strings invites and a compiler cannot see.
 *
 * <p>The first is a duplicate: two keys that got the same spelling answer with each other's words, and a window
 * quietly says the wrong thing in every language at once. The second is a swatch with no key: the colour picker
 * paints sixteen dyes and every one of them has to be nameable, because a material name is English.
 */
class MenuKeysTest {

    private static List<Field> keyFields() {
        List<Field> fields = new ArrayList<>();
        for (Field field : MenuKeys.class.getDeclaredFields()) {
            if (Modifier.isPublic(field.getModifiers())
                    && Modifier.isStatic(field.getModifiers())
                    && field.getType() == String.class) {
                fields.add(field);
            }
        }
        return fields;
    }

    private static List<String> keys() {
        List<String> values = new ArrayList<>();
        for (Field field : keyFields()) {
            try {
                values.add((String) field.get(null));
            } catch (IllegalAccessException unreachable) {
                throw new AssertionError(field.getName(), unreachable);
            }
        }
        return values;
    }

    @Test
    void noTwoKeysShareASpelling() {
        assertThat(keys()).doesNotHaveDuplicates();
    }

    /** Every key is a catalog key and reads as one, so a host scanning its file can tell whose keys these are. */
    @Test
    void everyKeyIsInTheGuiNamespaceAndIsLowerCase() {
        assertThat(keys()).isNotEmpty().allSatisfy(key -> {
            assertThat(key).startsWith("gui.");
            assertThat(key).isEqualTo(key.toLowerCase(java.util.Locale.ROOT));
            assertThat(key).doesNotContain(" ");
        });
    }

    /**
     * Every swatch the picker paints names a key, and no key is left over. A swatch without one would show a
     * material name, and a key without a swatch would be a line an operator translates that nothing ever asks for.
     */
    @Test
    void everySwatchNamesAKeyAndEveryColourKeyBelongsToASwatch() {
        List<String> swatchKeys =
                Arrays.stream(ColourSwatch.values()).map(ColourSwatch::nameKey).toList();
        List<String> colourKeys =
                keys().stream().filter(key -> key.startsWith("gui.colour.")).toList();

        assertThat(swatchKeys).hasSize(16).doesNotHaveDuplicates();
        assertThat(colourKeys).containsExactlyInAnyOrderElementsOf(swatchKeys);
    }
}
