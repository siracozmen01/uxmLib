package com.uxplima.uxmlib.update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ModrinthReleaseProviderTest {

    // A trimmed /project/{id}/version response: Modrinth returns an array, newest first.
    private static final String SAMPLE =
            """
            [
              {
                "name": "1.4.0",
                "version_number": "1.4.0",
                "id": "abc123",
                "project_id": "proj",
                "version_type": "release"
              },
              {
                "name": "1.3.9",
                "version_number": "1.3.9",
                "id": "old456",
                "project_id": "proj",
                "version_type": "release"
              }
            ]
            """;

    @Test
    void extractsFirstVersionNumberAndBuildsProjectUrl() {
        var release = ModrinthReleaseProvider.parseLatest(SAMPLE, "proj");
        assertThat(release).isPresent();
        assertThat(release.get().version()).isEqualTo("1.4.0");
        assertThat(release.get().url()).isEqualTo("https://modrinth.com/project/proj/version/abc123");
    }

    @Test
    void emptyOnEmptyArray() {
        assertThat(ModrinthReleaseProvider.parseLatest("[]", "proj")).isEmpty();
    }

    @Test
    void emptyOnMalformedBody() {
        assertThat(ModrinthReleaseProvider.parseLatest("{bad", "proj")).isEmpty();
    }

    @Test
    void rejectsBlankProjectId() {
        assertThatThrownBy(() -> new ModrinthReleaseProvider("  ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildsTheExpectedApiEndpoint() {
        ModrinthReleaseProvider provider = new ModrinthReleaseProvider("my-plugin");
        assertThat(provider.endpoint().toString()).isEqualTo("https://api.modrinth.com/v2/project/my-plugin/version");
    }
}
