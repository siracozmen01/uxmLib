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

    private final HttpClient http;
    private final URI endpoint;

    /** @param url the channel's incoming-webhook URL */
    public DiscordWebhook(String url) {
        this.endpoint = URI.create(Objects.requireNonNull(url, "url"));
        this.http =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
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
        return http.sendAsync(request, HttpResponse.BodyHandlers.discarding()).thenApply(HttpResponse::statusCode);
    }

    /** Post an embed. Never blocks; the future yields the Discord HTTP status code (a 204 means delivered). */
    public CompletableFuture<Integer> sendEmbed(DiscordEmbed embed) {
        Objects.requireNonNull(embed, "embed");
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(embedBody(embed), StandardCharsets.UTF_8))
                .build();
        return http.sendAsync(request, HttpResponse.BodyHandlers.discarding()).thenApply(HttpResponse::statusCode);
    }

    /** The JSON request body for a plain-content message. Package-private so the encoding is testable. */
    static String contentBody(String content) {
        return "{\"content\":" + jsonString(content) + "}";
    }

    /** The JSON request body for an embed. Package-private so the encoding is testable. */
    static String embedBody(DiscordEmbed embed) {
        StringBuilder embedJson = new StringBuilder("{\"title\":")
                .append(jsonString(embed.title()))
                .append(",\"description\":")
                .append(jsonString(embed.description()));
        embed.colorValue().ifPresent(color -> embedJson.append(",\"color\":").append(color.intValue()));
        embedJson.append('}');
        return "{\"embeds\":[" + embedJson + "]}";
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
                default -> out.append(c);
            }
        }
        out.append('"');
        return out.toString();
    }
}
