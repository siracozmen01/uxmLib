package com.uxplima.uxmlib.update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class GitHubReleaseProviderTest {

    // A trimmed but real-shaped GitHub /releases/latest response.
    private static final String SAMPLE =
            """
            {
              "url": "https://api.github.com/repos/o/r/releases/123",
              "html_url": "https://github.com/o/r/releases/tag/v1.4.0",
              "tag_name": "v1.4.0",
              "name": "1.4.0",
              "draft": false,
              "prerelease": false,
              "assets": []
            }
            """;

    @Test
    void extractsVersionAndUrlFromTagAndHtmlUrl() {
        var release = GitHubReleaseProvider.parseLatest(SAMPLE);
        assertThat(release).isPresent();
        assertThat(release.get().version()).isEqualTo("v1.4.0");
        assertThat(release.get().url()).isEqualTo("https://github.com/o/r/releases/tag/v1.4.0");
    }

    @Test
    void emptyWhenNoTagName() {
        assertThat(GitHubReleaseProvider.parseLatest("{\"html_url\":\"https://x.test/r\"}"))
                .isEmpty();
    }

    @Test
    void emptyOnMalformedBody() {
        assertThat(GitHubReleaseProvider.parseLatest("not json at all")).isEmpty();
    }

    @Test
    void rejectsBlankCoordinates() {
        assertThatThrownBy(() -> new GitHubReleaseProvider("", "repo")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildsTheExpectedApiEndpoint() {
        GitHubReleaseProvider provider = new GitHubReleaseProvider("owner", "repo");
        assertThat(provider.endpoint().toString()).isEqualTo("https://api.github.com/repos/owner/repo/releases/latest");
    }
}
