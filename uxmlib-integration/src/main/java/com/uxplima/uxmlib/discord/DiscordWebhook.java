package com.uxplima.uxmlib.discord;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Posts messages to a Discord channel through an incoming-webhook URL. Sends are non-blocking — the
 * returned future completes with the HTTP status — so a caller never blocks a server thread on network I/O.
 */
public final class DiscordWebhook {

    // One client (and its connection pool / executor) shared by every webhook, so constructing webhooks
    // per config reload doesn't leak thread pools. The client itself holds no per-endpoint state.
    private static final HttpClient HTTP =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

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
        String body = contentBody(content);
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.discarding()).thenApply(HttpResponse::statusCode);
    }

    /** Post an embed. Never blocks; the future yields the Discord HTTP status code (a 204 means delivered). */
    public CompletableFuture<Integer> sendEmbed(DiscordEmbed embed) {
        Objects.requireNonNull(embed, "embed");
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(embedBody(embed), StandardCharsets.UTF_8))
                .build();
        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.discarding()).thenApply(HttpResponse::statusCode);
    }

    /** The JSON request body for a plain-content message. Package-private so the encoding is testable. */
    static String contentBody(String content) {
        return "{\"content\":" + jsonString(content) + "}";
    }

    /** The JSON request body for an embed. Package-private so the encoding is testable. */
    static String embedBody(DiscordEmbed embed) {
        return "{\"embeds\":[" + EmbedJson.encode(embed) + "]}";
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
