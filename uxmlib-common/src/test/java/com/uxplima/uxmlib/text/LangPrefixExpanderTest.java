package com.uxplima.uxmlib.text;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.Map;

import org.junit.jupiter.api.Test;

@org.jspecify.annotations.NullUnmarked
class LangPrefixExpanderTest {

    private static final Map<String, String> PREFIXES = Map.of("server", "[S] ", "staff", "<red>[Staff]</red> ");

    @Test
    void marker_hit_prepends_resolved_prefix() {
        assertThat(LangPrefixExpander.expand("prefix:server Hello world", PREFIXES))
                .isEqualTo("[S] Hello world");
    }

    @Test
    void key_runs_until_a_separator_then_prefix_resolves() {
        // The key is read up to the first non-key character; a space ends it.
        assertThat(LangPrefixExpander.expand("prefix:staff hi", PREFIXES)).isEqualTo("<red>[Staff]</red> hi");
    }

    @Test
    void unknown_key_strips_marker_only() {
        // Unknown key: the "prefix:<key>" marker is removed, the body remains.
        assertThat(LangPrefixExpander.expand("prefix:unknown body", PREFIXES)).isEqualTo("body");
    }

    @Test
    void no_marker_passes_through_verbatim() {
        assertThat(LangPrefixExpander.expand("just a message", PREFIXES)).isEqualTo("just a message");
    }

    @Test
    void marker_not_at_start_is_left_alone() {
        assertThat(LangPrefixExpander.expand("say prefix:server now", PREFIXES)).isEqualTo("say prefix:server now");
    }

    @Test
    void empty_prefixes_map_strips_marker() {
        assertThat(LangPrefixExpander.expand("prefix:server hi", Map.of())).isEqualTo("hi");
    }

    @Test
    void rejects_null_template() {
        assertThatNullPointerException().isThrownBy(() -> LangPrefixExpander.expand(null, PREFIXES));
    }

    @Test
    void rejects_null_prefixes() {
        assertThatNullPointerException().isThrownBy(() -> LangPrefixExpander.expand("x", null));
    }
}
