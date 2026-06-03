package com.uxplima.uxmlib.update;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import com.uxplima.uxmlib.common.SemanticVersion;
import com.uxplima.uxmlib.scheduler.Scheduler;
import org.jspecify.annotations.Nullable;

/**
 * Compares the running build against an {@link UpdateProvider}'s latest release and remembers the result. The
 * provider's fetch is dispatched through {@link Scheduler#async} so the network call never touches a server
 * thread; the comparison is pure. {@link #checkAndAnnounce} guarantees the announce callback fires at most once
 * (the announce-once dedupe the established checkers use) so the console is not spammed by the recurring timer.
 *
 * <p>This type holds no Bukkit state and never self-downloads — v1 is notify-only. The clickable on-join
 * surface is layered on top by {@link UpdateNotifier}/{@link UpdateJoinListener}, which read {@link #lastOutcome}.
 */
public final class UpdateChecker {

    private final Scheduler scheduler;
    private final UpdateProvider provider;
    private final SemanticVersion current;
    private final AtomicReference<@Nullable UpdateOutcome> lastOutcome = new AtomicReference<>();
    // The highest version already announced, or null if nothing has been announced yet. A later, strictly newer
    // release is announced again; the same (or an older) outdated build is not re-announced by the recurring timer.
    private final AtomicReference<@Nullable SemanticVersion> lastAnnounced = new AtomicReference<>();

    /**
     * @param scheduler the library scheduler; the fetch runs on its async pool
     * @param provider the release source to compare against
     * @param currentVersion the running build's version (typically {@link UxmLibVersion#VERSION})
     */
    public UpdateChecker(Scheduler scheduler, UpdateProvider provider, String currentVersion) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.provider = Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(currentVersion, "currentVersion");
        this.current = SemanticVersion.parse(currentVersion);
    }

    /** The version this checker treats as the running build. */
    public SemanticVersion currentVersion() {
        return current;
    }

    /** The most recent check's outcome, or empty if no check has completed yet. */
    public Optional<UpdateOutcome> lastOutcome() {
        return Optional.ofNullable(lastOutcome.get());
    }

    /**
     * Run one check off-thread. The returned future completes with the derived {@link UpdateOutcome} and the
     * result is stored in {@link #lastOutcome}. Never blocks a caller thread.
     */
    public CompletableFuture<UpdateOutcome> check() {
        CompletableFuture<UpdateOutcome> result = new CompletableFuture<>();
        scheduler.async(() -> fetchInto(result));
        return result;
    }

    // Runs on the scheduler's async thread. Bridges the provider's own future into {@code result}: the whenComplete
    // stage's own future is intentionally not returned because every outcome (including failure) is funnelled into
    // {@code result}, which the caller already holds — nothing is lost by dropping the bridge future.
    @SuppressWarnings("FutureReturnValueIgnored")
    private void fetchInto(CompletableFuture<UpdateOutcome> result) {
        provider.latest()
                .thenApply(this::evaluate)
                .exceptionally(failure -> UpdateOutcome.failed())
                .whenComplete((outcome, failure) -> {
                    UpdateOutcome resolved = outcome != null ? outcome : UpdateOutcome.failed();
                    lastOutcome.set(resolved);
                    result.complete(resolved);
                });
    }

    /**
     * Run one check and, when it reports {@link UpdateStatus#OUTDATED} for a version newer than the last one
     * announced, invoke {@code onOutdated}. The same outdated build is not re-announced by the recurring timer,
     * but a later, strictly newer release seen in the same process is announced again.
     */
    public CompletableFuture<UpdateOutcome> checkAndAnnounce(Consumer<UpdateOutcome> onOutdated) {
        Objects.requireNonNull(onOutdated, "onOutdated");
        return check().thenApply(outcome -> {
            if (outcome.isOutdated() && shouldAnnounce(outcome)) {
                onOutdated.accept(outcome);
            }
            return outcome;
        });
    }

    // True (and latches the version as announced) only when this outcome's release is strictly newer than the
    // last version already announced. Unparseable versions never re-announce; the CAS keeps the gate race-free.
    private boolean shouldAnnounce(UpdateOutcome outcome) {
        SemanticVersion published =
                outcome.release().map(release -> parseOrNull(release.version())).orElse(null);
        if (published == null) {
            return false;
        }
        SemanticVersion previous;
        do {
            previous = lastAnnounced.get();
            if (previous != null && !published.isNewerThan(previous)) {
                return false;
            }
        } while (!lastAnnounced.compareAndSet(previous, published));
        return true;
    }

    private static @Nullable SemanticVersion parseOrNull(String version) {
        try {
            return SemanticVersion.parse(version);
        } catch (IllegalArgumentException unparseable) {
            return null;
        }
    }

    private UpdateOutcome evaluate(Optional<Release> latest) {
        if (latest.isEmpty()) {
            return UpdateOutcome.failed();
        }
        Release release = latest.get();
        SemanticVersion published;
        try {
            published = SemanticVersion.parse(release.version());
        } catch (IllegalArgumentException unparseable) {
            return UpdateOutcome.failed();
        }
        return new UpdateOutcome(statusFor(published), Optional.of(release));
    }

    private UpdateStatus statusFor(SemanticVersion published) {
        if (published.isNewerThan(current)) {
            return UpdateStatus.OUTDATED;
        }
        if (current.isNewerThan(published)) {
            return UpdateStatus.DEV_BUILD;
        }
        return UpdateStatus.UP_TO_DATE;
    }
}
