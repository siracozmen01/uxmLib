package com.uxplima.uxmlib.update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.Test;

class JsonTest {

    private static Object parse(String text) {
        return Objects.requireNonNull(Json.parse(text), "parse result");
    }

    @Test
    void parsesFlatObject() {
        Object value = parse("{\"a\":\"x\",\"b\":1,\"c\":true,\"d\":null}");
        assertThat(value).isInstanceOf(Map.class);
        Map<?, ?> map = (Map<?, ?>) value;
        assertThat(map.get("a")).isEqualTo("x");
        assertThat(map.get("b")).isEqualTo(1.0d);
        assertThat(map.get("c")).isEqualTo(Boolean.TRUE);
        assertThat(map.get("d")).isNull();
    }

    @Test
    void parsesNestedArraysAndObjects() {
        Map<?, ?> map = (Map<?, ?>) parse("{\"items\":[{\"id\":1},{\"id\":2}]}");
        List<?> items = (List<?>) Objects.requireNonNull(map.get("items"));
        assertThat(items).hasSize(2);
        Map<?, ?> second = (Map<?, ?>) Objects.requireNonNull(items.get(1));
        assertThat(second.get("id")).isEqualTo(2.0d);
    }

    @Test
    void decodesStringEscapes() {
        Object value = Json.parse("\"a\\\"b\\n\\u0041\"");
        assertThat(value).isEqualTo("a\"b\nA");
    }

    @Test
    void parsesNegativeAndExponentNumbers() {
        Map<?, ?> map = (Map<?, ?>) parse("{\"n\":-12.5e2}");
        assertThat((Double) map.get("n")).isEqualTo(-1250.0d);
    }

    @Test
    void ignoresWhitespaceBetweenTokens() {
        Map<?, ?> map = (Map<?, ?>) parse("  {  \"k\" :  \"v\"  } ");
        assertThat(map.get("k")).isEqualTo("v");
    }

    @Test
    void rejectsTrailingGarbage() {
        assertThatThrownBy(() -> Json.parse("{}garbage")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnterminatedString() {
        assertThatThrownBy(() -> Json.parse("\"oops")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAdversariallyDeepNestingWithoutOverflowing() {
        // A few thousand levels of nesting must surface as an IllegalArgumentException the providers catch, not
        // a StackOverflowError that bypasses that catch.
        String deep = "[".repeat(5_000) + "]".repeat(5_000);
        assertThatThrownBy(() -> Json.parse(deep))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nesting too deep");
    }

    @Test
    void parsesNestingUpToTheDepthLimit() {
        // 64 levels is allowed; the guard rejects only what exceeds it.
        String atLimit = "[".repeat(64) + "]".repeat(64);
        assertThat(Json.parse(atLimit)).isInstanceOf(List.class);
    }

    @Test
    void reportsUnexpectedCharacterForANonValueStartAsIllegalArgument() {
        // A bare ':' can never start a value; the number error path reports it cleanly as an
        // IllegalArgumentException rather than letting any other exception type leak out.
        assertThatThrownBy(() -> Json.parse(":"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unexpected character ':'");
    }

    @Test
    void extractsTopLevelStringField() {
        String body = "{\"tag_name\":\"v1.4.0\",\"html_url\":\"https://example.test/r\"}";
        Map<?, ?> map = (Map<?, ?>) parse(body);
        assertThat(Json.string(map, "tag_name")).contains("v1.4.0");
        assertThat(Json.string(map, "html_url")).contains("https://example.test/r");
        assertThat(Json.string(map, "missing")).isEmpty();
    }
}
