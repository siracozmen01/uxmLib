package com.uxplima.uxmlib.update;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Shared, non-blocking GET plumbing for the release adapters. One {@link HttpClient} (and its executor /
 * connection pool) is reused across every provider so constructing providers does not leak thread pools, the
 * same discipline the Discord webhook client follows. A 2xx body is handed to the provider's parser; any other
 * status, or an I/O failure, collapses to {@link Optional#empty()} so a transient outage never crashes a check.
 */
final class Http {

    private static final HttpClient CLIENT =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private static final String USER_AGENT = "uxmLib-UpdateChecker (+https://github.com/siracozmen01/uxmLib)";

    private Http() {}

    /** GET {@code endpoint}, parse a 2xx body with {@code parser}, and fold any failure to empty. */
    static <T> CompletableFuture<Optional<T>> getJson(URI endpoint, Function<String, Optional<T>> parser) {
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> apply(response, parser))
                .exceptionally(failure -> Optional.empty());
    }

    private static <T> Optional<T> apply(HttpResponse<String> response, Function<String, Optional<T>> parser) {
        if (response.statusCode() / 100 != 2) {
            return Optional.empty();
        }
        return parser.apply(response.body());
    }
}
