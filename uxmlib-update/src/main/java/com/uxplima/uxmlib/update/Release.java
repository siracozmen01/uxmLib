package com.uxplima.uxmlib.update;

import java.net.URI;
import java.util.Objects;

/**
 * One published release as reported by an {@link UpdateProvider}: the release's version string (e.g.
 * {@code "1.4.0"}) and the page a human should open to obtain it. The URL is validated to be an absolute
 * http/https URI at construction so a notification can never surface a bogus or non-web link.
 */
public record Release(String version, String url) {

    public Release {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(url, "url");
        if (version.isBlank()) {
            throw new IllegalArgumentException("version must not be blank");
        }
        requireWebUrl(url);
    }

    private static void requireWebUrl(String url) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (java.net.URISyntaxException malformed) {
            throw new IllegalArgumentException("malformed release URL: " + url, malformed);
        }
        String scheme = uri.getScheme();
        if (uri.getHost() == null || scheme == null || !(scheme.equals("http") || scheme.equals("https"))) {
            throw new IllegalArgumentException("release URL must be an absolute http/https URL: " + url);
        }
    }
}
