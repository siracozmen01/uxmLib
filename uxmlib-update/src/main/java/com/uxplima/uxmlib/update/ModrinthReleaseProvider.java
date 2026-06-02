package com.uxplima.uxmlib.update;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.jspecify.annotations.Nullable;

/**
 * An {@link UpdateProvider} backed by a Modrinth project's version list
 * ({@code GET https://api.modrinth.com/v2/project/{id}/version}). Modrinth returns the versions newest-first,
 * so the first array element is the latest; its {@code version_number} is the version and a stable
 * {@code https://modrinth.com/project/{id}/version/{versionId}} page is the human link. Parsed with the real
 * {@link Json} reader; any non-2xx or unparseable response degrades to "no release".
 */
public final class ModrinthReleaseProvider implements UpdateProvider {

    private final String projectId;
    private final URI endpoint;

    /** @param projectId the Modrinth project slug or id */
    public ModrinthReleaseProvider(String projectId) {
        Objects.requireNonNull(projectId, "projectId");
        if (projectId.isBlank()) {
            throw new IllegalArgumentException("projectId must not be blank");
        }
        this.projectId = projectId;
        this.endpoint = URI.create("https://api.modrinth.com/v2/project/" + projectId + "/version");
    }

    @Override
    public CompletableFuture<Optional<Release>> latest() {
        return Http.getJson(endpoint, body -> parseLatest(body, projectId));
    }

    /** The API endpoint this provider queries. Exposed for wiring/diagnostics and tested. */
    public URI endpoint() {
        return endpoint;
    }

    /** Pull the newest {@link Release} out of a Modrinth version-list body. Pure and tested; never throws. */
    static Optional<Release> parseLatest(String body, String projectId) {
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(projectId, "projectId");
        @Nullable Object root;
        try {
            root = Json.parse(body);
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
        if (!(root instanceof List<?> versions) || versions.isEmpty()) {
            return Optional.empty();
        }
        if (!(versions.get(0) instanceof Map<?, ?> newest)) {
            return Optional.empty();
        }
        return toRelease(newest, projectId);
    }

    private static Optional<Release> toRelease(Map<?, ?> newest, String projectId) {
        Optional<String> version = Json.string(newest, "version_number");
        Optional<String> versionId = Json.string(newest, "id");
        if (version.isEmpty() || versionId.isEmpty()) {
            return Optional.empty();
        }
        String url = "https://modrinth.com/project/" + projectId + "/version/" + versionId.get();
        try {
            return Optional.of(new Release(version.get(), url));
        } catch (IllegalArgumentException rejected) {
            return Optional.empty();
        }
    }
}
