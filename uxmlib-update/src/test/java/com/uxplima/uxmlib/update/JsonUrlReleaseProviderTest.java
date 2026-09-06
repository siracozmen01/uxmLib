package com.uxplima.uxmlib.update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.Test;

class JsonUrlReleaseProviderTest {

    private static final URI ENDPOINT = URI.create("https://releases.example.com/latest.json");
    private static final List<String> DEFAULT_FIELDS = List.of("tag_name", "version");

    // A GitHub-shaped body: tag_name plus an explicit html_url page.
    private static final String WITH_TAG_AND_PAGE = """
            {
              "tag_name": "v1.4.0",
              "html_url": "https://example.com/r/releases/tag/v1.4.0"
            }
            """;

    @Test
    void extractsTagNameAndHtmlUrl() {
        var release = JsonUrlReleaseProvider.parseLatest(WITH_TAG_AND_PAGE, DEFAULT_FIELDS, ENDPOINT);
        assertThat(release).isPresent();
        assertThat(release.get().version()).isEqualTo("v1.4.0");
        assertThat(release.get().url()).isEqualTo("https://example.com/r/releases/tag/v1.4.0");
    }

    @Test
    void fallsBackToTheVersionFieldWhenNoTagName() {
        var release = JsonUrlReleaseProvider.parseLatest(
                "{\"version\":\"2.3.1\",\"html_url\":\"https://example.com/v/2.3.1\"}", DEFAULT_FIELDS, ENDPOINT);
        assertThat(release).isPresent();
        assertThat(release.get().version()).isEqualTo("2.3.1");
        assertThat(release.get().url()).isEqualTo("https://example.com/v/2.3.1");
    }

    @Test
    void usesTheEndpointAsThePageWhenHtmlUrlAbsent() {
        var release = JsonUrlReleaseProvider.parseLatest("{\"version\":\"2.3.1\"}", DEFAULT_FIELDS, ENDPOINT);
        assertThat(release).isPresent();
        assertThat(release.get().version()).isEqualTo("2.3.1");
        assertThat(release.get().url()).isEqualTo(ENDPOINT.toString());
    }

    @Test
    void honoursCustomVersionFields() {
        var release = JsonUrlReleaseProvider.parseLatest("{\"latest\":\"9.0.0\"}", List.of("latest"), ENDPOINT);
        assertThat(release).isPresent();
        assertThat(release.get().version()).isEqualTo("9.0.0");
    }

    @Test
    void emptyWhenNoVersionField() {
        assertThat(JsonUrlReleaseProvider.parseLatest(
                        "{\"html_url\":\"https://example.com/r\"}", DEFAULT_FIELDS, ENDPOINT))
                .isEmpty();
    }

    @Test
    void emptyOnMalformedBody() {
        assertThat(JsonUrlReleaseProvider.parseLatest("not json at all", DEFAULT_FIELDS, ENDPOINT))
                .isEmpty();
    }

    @Test
    void rejectsANonWebEndpoint() {
        assertThatThrownBy(() -> new JsonUrlReleaseProvider("ftp://example.com/r.json"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAMalformedEndpoint() {
        assertThatThrownBy(() -> new JsonUrlReleaseProvider("h ttp://bad url"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsEmptyVersionFields() {
        assertThatThrownBy(() -> new JsonUrlReleaseProvider(ENDPOINT, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void keepsTheConfiguredEndpoint() {
        JsonUrlReleaseProvider provider = new JsonUrlReleaseProvider(ENDPOINT);
        assertThat(provider.endpoint()).isEqualTo(ENDPOINT);
    }
}
