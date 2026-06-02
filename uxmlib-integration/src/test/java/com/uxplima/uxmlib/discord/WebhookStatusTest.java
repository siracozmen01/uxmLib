package com.uxplima.uxmlib.discord;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Pure mapping of Discord webhook HTTP status codes to flags and human-readable messages. */
class WebhookStatusTest {

    @Test
    void deliveredStatusesAreFlaggedAndExplained() {
        WebhookStatus ok = WebhookStatus.of(204);
        assertThat(ok.delivered()).isTrue();
        assertThat(ok.rateLimited()).isFalse();
        assertThat(ok.clientError()).isFalse();
        assertThat(ok.serverError()).isFalse();
        assertThat(ok.message()).isEqualTo("delivered");
    }

    @Test
    void badPayloadIsAClientErrorWithAClearMessage() {
        WebhookStatus bad = WebhookStatus.of(400);
        assertThat(bad.delivered()).isFalse();
        assertThat(bad.clientError()).isTrue();
        assertThat(bad.message()).contains("bad payload");
    }

    @Test
    void authFailuresAreFlagged() {
        assertThat(WebhookStatus.of(401).authError()).isTrue();
        assertThat(WebhookStatus.of(403).authError()).isTrue();
        assertThat(WebhookStatus.of(401).message()).contains("unauthorized");
        assertThat(WebhookStatus.of(403).message()).contains("forbidden");
        assertThat(WebhookStatus.of(404).authError()).isFalse();
    }

    @Test
    void unknownWebhookIsExplained() {
        WebhookStatus notFound = WebhookStatus.of(404);
        assertThat(notFound.clientError()).isTrue();
        assertThat(notFound.message()).contains("unknown webhook");
    }

    @Test
    void rateLimitedIsFlaggedAndExplained() {
        WebhookStatus limited = WebhookStatus.of(429);
        assertThat(limited.rateLimited()).isTrue();
        assertThat(limited.clientError()).isTrue();
        assertThat(limited.message()).contains("rate-limited");
    }

    @Test
    void serverErrorsAreFlaggedForRetry() {
        WebhookStatus down = WebhookStatus.of(503);
        assertThat(down.serverError()).isTrue();
        assertThat(down.delivered()).isFalse();
        assertThat(down.message()).contains("server error");
    }

    @Test
    void anUnexpectedCodeStillGetsASaneMessage() {
        assertThat(WebhookStatus.of(100).message()).contains("unexpected");
        assertThat(WebhookStatus.of(200).delivered()).isTrue();
        assertThat(WebhookStatus.of(201).message()).contains("delivered");
    }
}
