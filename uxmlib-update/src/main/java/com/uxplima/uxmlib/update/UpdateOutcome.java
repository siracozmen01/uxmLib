package com.uxplima.uxmlib.update;

import java.util.Objects;
import java.util.Optional;

/**
 * The result of one update check: the derived {@link UpdateStatus} and, when the source reported one, the
 * latest {@link Release}. A consumer reads {@link #status()} to log/branch and {@link #release()} to surface
 * the clickable link. {@code release()} is present whenever the source returned a parseable release, including
 * the {@link UpdateStatus#UP_TO_DATE} and {@link UpdateStatus#DEV_BUILD} cases; it is empty only when the
 * check {@link UpdateStatus#FAILED}.
 */
public record UpdateOutcome(UpdateStatus status, Optional<Release> release) {

    public UpdateOutcome {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(release, "release");
    }

    /** A failed check carries no release. */
    public static UpdateOutcome failed() {
        return new UpdateOutcome(UpdateStatus.FAILED, Optional.empty());
    }

    /** Whether a newer release than the running build is available. */
    public boolean isOutdated() {
        return status == UpdateStatus.OUTDATED;
    }
}
