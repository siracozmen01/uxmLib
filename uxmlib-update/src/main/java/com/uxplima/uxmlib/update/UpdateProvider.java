package com.uxplima.uxmlib.update;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * A source of "the latest published release" — a GitHub repository, a Modrinth project, etc. The single seam
 * the {@link UpdateChecker} talks to, so a server owner can point the checker at any release host and tests
 * can inject a stub. Implementations must be non-blocking: {@link #latest()} returns a future and performs its
 * I/O off the calling thread (the live HTTP adapters use {@link java.net.http.HttpClient#sendAsync}).
 *
 * <p>The future yields {@link Optional#empty()} when the source has no usable release (HTTP error, empty list,
 * unparseable body) rather than failing, so a transient outage degrades to "no update found", never a crash.
 */
@FunctionalInterface
public interface UpdateProvider {

    /** The latest release this source advertises, or empty if none could be determined. Never blocks. */
    CompletableFuture<Optional<Release>> latest();
}
