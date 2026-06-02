package com.uxplima.uxmlib.discord;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Posts messages to a Discord channel through an incoming-webhook URL. Sends are non-blocking — the
 * returned future completes with the HTTP status — so a caller never blocks a server thread on network I/O.
 */
public final class DiscordWebhook {

    // One client (and its connection pool / executor) shared by every webhook, so constructing webhooks
    // per config reload doesn't leak thread pools. The client itself holds no per-endpoint state.
    private static final HttpClient HTTP =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    // Discord asks every client to identify itself; a generic JDK user-agent risks being throttled or blocked.
    private static final String USER_AGENT = "uxmLib-DiscordWebhook (+https://github.com/siracozmen01/uxmLib)";

    // Suppress every mention by default so a message body can never mass-ping @everyone/@here or a role.
    static final String ALLOWED_MENTIONS_NONE = "\"allowed_mentions\":{\"parse\":[]}";

    // A 429 may arrive without a usable Retry-After; back off a fixed beat rather than hammering Discord.
    private static final Duration DEFAULT_RETRY_DELAY = Duration.ofSeconds(1);

    // One retry on a rate-limit keeps a transient 429 from dropping the message without risking a storm.
    private static final int MAX_RETRIES = 1;

    private final URI endpoint;

    /** @param url the channel's incoming-webhook URL (an absolute http/https URL) */
    public DiscordWebhook(String url) {
        Objects.requireNonNull(url, "url");
        URI uri;
        try {
            uri = new URI(url);
        } catch (java.net.URISyntaxException malformed) {
            throw new IllegalArgumentException("malformed webhook URL: " + url, malformed);
        }
        String scheme = uri.getScheme();
        if (uri.getHost() == null || scheme == null || !(scheme.equals("http") || scheme.equals("https"))) {
            throw new IllegalArgumentException("webhook URL must be an absolute http/https URL: " + url);
        }
        this.endpoint = uri;
    }

    /**
     * Post a plain-content message. Never blocks; the future yields the Discord HTTP status code (a 204
     * means delivered).
     */
    public CompletableFuture<Integer> sendContent(String content) {
        Objects.requireNonNull(content, "content");
        return post(contentBody(content));
    }

    /** Post an embed. Never blocks; the future yields the Discord HTTP status code (a 204 means delivered). */
    public CompletableFuture<Integer> sendEmbed(DiscordEmbed embed) {
        Objects.requireNonNull(embed, "embed");
        return post(embedBody(embed));
    }

    /**
     * Post up to ten embeds in one message (Discord's per-message cap). Never blocks; the future yields the
     * Discord HTTP status code. Rejects an empty or over-cap list before any network call.
     */
    public CompletableFuture<Integer> sendEmbeds(List<DiscordEmbed> embeds) {
        Objects.requireNonNull(embeds, "embeds");
        if (embeds.isEmpty()) {
            throw new IllegalArgumentException("at least one embed is required");
        }
        if (embeds.size() > WebhookMessage.EMBEDS_MAX) {
            throw new IllegalArgumentException("a message carries at most " + WebhookMessage.EMBEDS_MAX + " embeds");
        }
        // Discord rejects a message whose embeds' combined text exceeds 6000 characters, even when each embed
        // is individually valid. Surface that as a clear exception here rather than a 400 after the round-trip.
        List<String> overBudget = EmbedLimits.messageViolations(embeds);
        if (!overBudget.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", overBudget));
        }
        return post(embedsBody(embeds));
    }

    /**
     * Post a fully-built {@link WebhookMessage} (content, embeds, identity overrides). Never blocks; the
     * future yields the Discord HTTP status code. The message is already validated at {@link
     * WebhookMessage.Builder#build()}.
     */
    public CompletableFuture<Integer> send(WebhookMessage message) {
        Objects.requireNonNull(message, "message");
        return post(message.body());
    }

    /**
     * Like {@link #sendContent(String)} but the future yields a {@link WebhookStatus}, which explains the
     * raw HTTP code (delivered / bad payload / auth / unknown webhook / rate-limited). Kept separate from the
     * {@code Integer}-returning methods so existing callers do not break.
     */
    public CompletableFuture<WebhookStatus> sendContentDetailed(String content) {
        return sendContent(content).thenApply(WebhookStatus::of);
    }

    /** Like {@link #sendEmbed(DiscordEmbed)} but the future yields an explained {@link WebhookStatus}. */
    public CompletableFuture<WebhookStatus> sendEmbedDetailed(DiscordEmbed embed) {
        return sendEmbed(embed).thenApply(WebhookStatus::of);
    }

    /** Like {@link #send(WebhookMessage)} but the future yields an explained {@link WebhookStatus}. */
    public CompletableFuture<WebhookStatus> sendDetailed(WebhookMessage message) {
        return send(message).thenApply(WebhookStatus::of);
    }

    /** The JSON request body for a plain-content message. Package-private so the encoding is testable. */
    static String contentBody(String content) {
        return "{\"content\":" + jsonString(content) + "," + ALLOWED_MENTIONS_NONE + "}";
    }

    /** The JSON request body for an embed. Package-private so the encoding is testable. */
    static String embedBody(DiscordEmbed embed) {
        return embedsBody(List.of(embed));
    }

    /** The JSON request body for an embed array. Package-private so the encoding is testable. */
    static String embedsBody(List<DiscordEmbed> embeds) {
        return "{\"embeds\":" + EmbedJson.encodeArray(embeds) + "," + ALLOWED_MENTIONS_NONE + "}";
    }

    /** Send a ready JSON body, retrying once off-thread on a 429 per the rate-limit headers Discord returns. */
    private CompletableFuture<Integer> post(String body) {
        return send(body, MAX_RETRIES);
    }

    private CompletableFuture<Integer> send(String body, int retriesLeft) {
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .header("Content-Type", "application/json")
                .header("User-Agent", USER_AGENT)
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenCompose(response -> handle(body, retriesLeft, response));
    }

    private CompletableFuture<Integer> handle(String body, int retriesLeft, HttpResponse<String> response) {
        int status = response.statusCode();
        if (!shouldRetry(status, retriesLeft)) {
            return CompletableFuture.completedFuture(status);
        }
        Duration delay = retryDelay(response.headers().firstValue("Retry-After"), Optional.of(response.body()));
        // A pure off-thread delay, not Bukkit scheduling: the resend fires on the common pool once the wait
        // elapses, so a server thread is never parked waiting on a rate-limit window.
        Executor later =
                CompletableFuture.delayedExecutor(delay.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        return CompletableFuture.completedFuture((Void) null)
                .thenComposeAsync(ignored -> send(body, retriesLeft - 1), later);
    }

    /** Whether a status code warrants a retry while the retry budget is not yet spent. Pure and testable. */
    static boolean shouldRetry(int status, int retriesLeft) {
        return status == 429 && retriesLeft > 0;
    }

    /**
     * The delay before re-sending a rate-limited request: the {@code Retry-After} header (whole seconds) if
     * present, else the JSON body's {@code retry_after} (fractional seconds), else a short default. Pure and
     * testable; never negative.
     */
    static Duration retryDelay(Optional<String> retryAfterHeader, Optional<String> responseBody) {
        Optional<Duration> fromHeader = retryAfterHeader.flatMap(DiscordWebhook::parseSeconds);
        if (fromHeader.isPresent()) {
            return fromHeader.get();
        }
        return responseBody.flatMap(DiscordWebhook::parseRetryAfterJson).orElse(DEFAULT_RETRY_DELAY);
    }

    private static Optional<Duration> parseSeconds(String seconds) {
        try {
            double value = Double.parseDouble(seconds.trim());
            return value < 0 ? Optional.empty() : Optional.of(Duration.ofMillis((long) (value * 1000)));
        } catch (NumberFormatException notANumber) {
            return Optional.empty();
        }
    }

    private static Optional<Duration> parseRetryAfterJson(String json) {
        int key = json.indexOf("\"retry_after\"");
        if (key < 0) {
            return Optional.empty();
        }
        int colon = json.indexOf(':', key);
        if (colon < 0) {
            return Optional.empty();
        }
        int end = colon + 1;
        while (end < json.length() && "-0123456789.".indexOf(json.charAt(end)) >= 0) {
            end++;
        }
        return parseSeconds(json.substring(colon + 1, end));
    }

    static String jsonString(String raw) {
        StringBuilder out = new StringBuilder(raw.length() + 2);
        out.append('"');
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    // Any other control character below 0x20 is illegal raw in JSON; emit a \\uXXXX escape.
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
        return out.toString();
    }
}
