package com.uxplima.uxmlib.discord;

/**
 * Maps a raw Discord webhook HTTP status code to a human-readable message and a few decision flags, so a
 * caller or log line is meaningful instead of an opaque {@code int}. A {@code sendContent}/{@code sendEmbed}
 * future yields the status code; wrap it with {@link #of(int)} to explain it.
 *
 * <p>The known codes follow Discord's webhook contract: {@code 204} is a successful delivery (Discord
 * returns no body), {@code 400} is a malformed payload, {@code 401}/{@code 403} are authentication/permission
 * failures, {@code 404} is an unknown or deleted webhook, and {@code 429} is a rate-limit. Any other code is
 * classified by its HTTP family (2xx delivered, 5xx server error, else unexpected).
 */
public record WebhookStatus(int status) {

    /** Wrap a raw HTTP status code. */
    public static WebhookStatus of(int status) {
        return new WebhookStatus(status);
    }

    /** Whether Discord accepted and delivered the message (any 2xx; {@code 204} is the normal success). */
    public boolean delivered() {
        return status >= 200 && status < 300;
    }

    /** Whether the message was rate-limited and may be retried after a back-off ({@code 429}). */
    public boolean rateLimited() {
        return status == 429;
    }

    /** Whether the failure is an authentication or permission problem ({@code 401}/{@code 403}). */
    public boolean authError() {
        return status == 401 || status == 403;
    }

    /** Whether Discord reported a problem with the request itself (any 4xx). */
    public boolean clientError() {
        return status >= 400 && status < 500;
    }

    /** Whether Discord itself failed (any 5xx) — the send may be worth retrying later. */
    public boolean serverError() {
        return status >= 500 && status < 600;
    }

    /** A short human-readable explanation of this status, suitable for a log line or an admin message. */
    public String message() {
        return switch (status) {
            case 204 -> "delivered";
            case 200 -> "delivered (with body)";
            case 400 -> "bad payload: Discord rejected the message body";
            case 401 -> "unauthorized: the webhook token is missing or invalid";
            case 403 -> "forbidden: the webhook lacks permission to post here";
            case 404 -> "unknown webhook: the URL is wrong or the webhook was deleted";
            case 429 -> "rate-limited: too many requests, retry after the back-off";
            default -> defaultMessage();
        };
    }

    private String defaultMessage() {
        if (delivered()) {
            return "delivered (status " + status + ")";
        }
        if (serverError()) {
            return "Discord server error (status " + status + "), retry later";
        }
        if (clientError()) {
            return "request rejected (status " + status + ")";
        }
        return "unexpected status " + status;
    }
}
