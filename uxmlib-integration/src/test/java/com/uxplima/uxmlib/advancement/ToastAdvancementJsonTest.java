package com.uxplima.uxmlib.advancement;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Pure tests of the synthetic-advancement JSON — no Bukkit, no Adventure. */
class ToastAdvancementJsonTest {

    @Test
    void buildsDisplayAndImpossibleCriterion() {
        String json = ToastAdvancementJson.build(
                "minecraft:diamond", "{\"text\":\"Hi\"}", "{\"text\":\"\"}", AdvancementFrame.GOAL);
        assertThat(json)
                .isEqualTo("{\"display\":{\"icon\":{\"id\":\"minecraft:diamond\"},\"title\":{\"text\":\"Hi\"}"
                        + ",\"description\":{\"text\":\"\"},\"frame\":\"goal\",\"show_toast\":true"
                        + ",\"announce_to_chat\":false,\"hidden\":true}"
                        + ",\"criteria\":{\"uxmlib_toast\":{\"trigger\":\"minecraft:impossible\"}}}");
    }

    @Test
    void frameTokenIsTheLowerCaseName() {
        assertThat(ToastAdvancementJson.build("minecraft:stone", "{}", "{}", AdvancementFrame.TASK))
                .contains("\"frame\":\"task\"");
        assertThat(ToastAdvancementJson.build("minecraft:stone", "{}", "{}", AdvancementFrame.CHALLENGE))
                .contains("\"frame\":\"challenge\"");
    }

    @Test
    void announceToChatStaysOffAndEntryStaysHidden() {
        String json = ToastAdvancementJson.build("minecraft:apple", "{}", "{}", AdvancementFrame.TASK);
        assertThat(json)
                .contains("\"announce_to_chat\":false")
                .contains("\"hidden\":true")
                .contains("\"show_toast\":true");
    }

    @Test
    void escapesControlCharactersInTheIconId() {
        // The icon id is server-resolved so it is normally clean, but a stray control char must not break the
        // JSON: a bare 0x01 becomes \\u0001 and a quote is backslash-escaped.
        String iconId = "a" + (char) 0x01 + "\"b";
        assertThat(ToastAdvancementJson.build(iconId, "{}", "{}", AdvancementFrame.TASK))
                .contains("\"id\":\"a\\u0001\\\"b\"");
    }

    @Test
    void embedsTitleAndDescriptionComponentJsonVerbatim() {
        String title = "{\"text\":\"Welcome\",\"color\":\"gold\"}";
        String desc = "{\"text\":\"Line two\"}";
        String json = ToastAdvancementJson.build("minecraft:book", title, desc, AdvancementFrame.TASK);
        assertThat(json).contains("\"title\":" + title).contains("\"description\":" + desc);
    }
}
