package com.uxplima.uxmlib.update;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.jspecify.annotations.Nullable;

/**
 * An {@link UpdateProvider} backed by a GitHub repository's latest release
 * ({@code GET https://api.github.com/repos/{owner}/{repo}/releases/latest}). The release version is the
 * {@code tag_name}; the human page is {@code html_url}. The body is parsed with the real {@link Json} reader,
 * not a substring scan, and any non-2xx or unparseable response degrades to "no release".
 */
public final class GitHubReleaseProvider implements UpdateProvider {

    private final URI endpoint;

    /**
     * @param owner the repository owner (user or organisation)
     * @param repo the repository name
     */
    public GitHubReleaseProvider(String owner, String repo) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(repo, "repo");
        if (owner.isBlank() || repo.isBlank()) {
            throw new IllegalArgumentException("owner and repo must not be blank");
        }
        this.endpoint = URI.create("https://api.github.com/repos/" + owner + "/" + repo + "/releases/latest");
    }

    @Override
    public CompletableFuture<Optional<Release>> latest() {
        return Http.getJson(endpoint, GitHubReleaseProvider::parseLatest);
    }

    /** The API endpoint this provider queries. Exposed for wiring/diagnostics and tested. */
    public URI endpoint() {
        return endpoint;
    }

    /** Pull a {@link Release} out of a GitHub releases/latest body. Pure and tested; never throws. */
    static Optional<Release> parseLatest(String body) {
        Objects.requireNonNull(body, "body");
        @Nullable Object root;
        try {
            root = Json.parse(body);
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
        Optional<String> tag = Json.string(root, "tag_name");
        Optional<String> url = Json.string(root, "html_url");
        if (tag.isEmpty() || url.isEmpty()) {
            return Optional.empty();
        }
        return toRelease(tag.get(), url.get());
    }

    private static Optional<Release> toRelease(String version, String url) {
        try {
            return Optional.of(new Release(version, url));
        } catch (IllegalArgumentException rejected) {
            return Optional.empty();
        }
    }
}
