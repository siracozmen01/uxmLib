package com.uxplima.uxmlib.update;

/**
 * The outcome of a single update check. Mirrors the small status enum the established checkers expose, so a
 * caller can log or branch without re-deriving the comparison.
 */
public enum UpdateStatus {

    /** The running build matches (or exceeds) the latest published release. */
    UP_TO_DATE,

    /** A newer release than the running build is available. */
    OUTDATED,

    /** The running build is newer than anything published (a local/dev build). */
    DEV_BUILD,

    /** The check could not complete (network error, empty/unparseable source). */
    FAILED
}
