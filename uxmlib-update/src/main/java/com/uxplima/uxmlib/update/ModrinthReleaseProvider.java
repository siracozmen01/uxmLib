package com.uxplima.uxmlib.update;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmlib.common.SemanticVersion;
import org.jspecify.annotations.Nullable;

/**
 * An {@link UpdateProvider} backed by a Modrinth project's version list
 * ({@code GET https://api.modrinth.com/v2/project/{id}/version}). The endpoint's array order is not a
 * guaranteed version order, so the latest is chosen by parsing every element's {@code version_number} into a
 * {@link SemanticVersion} and taking the highest (a published page's {@code id} gives a stable
 * {@code https://modrinth.com/project/{id}/version/{versionId}} human link). Parsed with the real {@link Json}
 * reader; any non-2xx or unparseable response degrades to "no release".
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
        return highest(versions).flatMap(newest -> toRelease(newest, projectId));
    }

    // The endpoint does not promise version order, so pick the element whose version_number parses to the
    // highest SemanticVersion. Elements with an absent or unparseable version_number are skipped.
    private static Optional<Map<?, ?>> highest(List<?> versions) {
        Map<?, ?> best = null;
        SemanticVersion bestVersion = null;
        for (Object element : versions) {
            if (!(element instanceof Map<?, ?> candidate)) {
                continue;
            }
            SemanticVersion parsed = parsedVersion(candidate);
            if (parsed != null && (bestVersion == null || parsed.isNewerThan(bestVersion))) {
                best = candidate;
                bestVersion = parsed;
            }
        }
        return Optional.ofNullable(best);
    }

    private static @Nullable SemanticVersion parsedVersion(Map<?, ?> version) {
        Optional<String> number = Json.string(version, "version_number");
        if (number.isEmpty()) {
            return null;
        }
        try {
            return SemanticVersion.parse(number.get());
        } catch (IllegalArgumentException unparseable) {
            return null;
        }
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
