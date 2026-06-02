package com.uxplima.uxmlib.update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class UpdateCheckerTest {

    private static UpdateProvider providing(@org.jspecify.annotations.Nullable Release release) {
        return () -> CompletableFuture.completedFuture(Optional.ofNullable(release));
    }

    private static Release release(String version) {
        return new Release(version, "https://github.com/o/r/releases/latest");
    }

    @Test
    void outdatedWhenLatestIsNewer() {
        UpdateChecker checker = new UpdateChecker(new InlineScheduler(), providing(release("1.5.0")), "1.4.0");
        UpdateOutcome outcome = checker.check().join();
        assertThat(outcome.status()).isEqualTo(UpdateStatus.OUTDATED);
        assertThat(outcome.release()).map(Release::version).contains("1.5.0");
    }

    @Test
    void upToDateWhenEqual() {
        UpdateChecker checker = new UpdateChecker(new InlineScheduler(), providing(release("1.4.0")), "1.4.0");
        assertThat(checker.check().join().status()).isEqualTo(UpdateStatus.UP_TO_DATE);
    }

    @Test
    void devBuildWhenRunningAheadOfPublished() {
        UpdateChecker checker = new UpdateChecker(new InlineScheduler(), providing(release("1.3.0")), "1.4.0");
        assertThat(checker.check().join().status()).isEqualTo(UpdateStatus.DEV_BUILD);
    }

    @Test
    void failedWhenProviderHasNoRelease() {
        UpdateChecker checker = new UpdateChecker(new InlineScheduler(), providing(null), "1.4.0");
        UpdateOutcome outcome = checker.check().join();
        assertThat(outcome.status()).isEqualTo(UpdateStatus.FAILED);
        assertThat(outcome.release()).isEmpty();
    }

    @Test
    void failedWhenLatestVersionUnparseable() {
        UpdateChecker checker = new UpdateChecker(new InlineScheduler(), providing(release("not-a-version")), "1.4.0");
        assertThat(checker.check().join().status()).isEqualTo(UpdateStatus.FAILED);
    }

    @Test
    void announceOnceFiresConsumerOnlyForFirstOutdatedResult() {
        AtomicInteger announced = new AtomicInteger();
        UpdateChecker checker = new UpdateChecker(new InlineScheduler(), providing(release("1.5.0")), "1.4.0");
        checker.checkAndAnnounce(outcome -> announced.incrementAndGet()).join();
        checker.checkAndAnnounce(outcome -> announced.incrementAndGet()).join();
        assertThat(announced).hasValue(1);
    }

    @Test
    void announceFiresAgainForAStrictlyNewerLaterRelease() {
        AtomicInteger announced = new AtomicInteger();
        MutableProvider provider = new MutableProvider(release("1.5.0"));
        UpdateChecker checker = new UpdateChecker(new InlineScheduler(), provider, "1.4.0");

        checker.checkAndAnnounce(outcome -> announced.incrementAndGet()).join();
        checker.checkAndAnnounce(outcome -> announced.incrementAndGet()).join(); // same version: no re-announce
        provider.set(release("1.6.0"));
        checker.checkAndAnnounce(outcome -> announced.incrementAndGet()).join(); // newer: announce again

        assertThat(announced).hasValue(2);
    }

    @Test
    void announceDoesNotFireForAnOlderLaterRelease() {
        AtomicInteger announced = new AtomicInteger();
        MutableProvider provider = new MutableProvider(release("1.6.0"));
        UpdateChecker checker = new UpdateChecker(new InlineScheduler(), provider, "1.4.0");

        checker.checkAndAnnounce(outcome -> announced.incrementAndGet()).join();
        provider.set(release("1.5.0"));
        checker.checkAndAnnounce(outcome -> announced.incrementAndGet()).join(); // older than announced: skip

        assertThat(announced).hasValue(1);
    }

    // A provider whose current release can be swapped between checks, so a recurring poll can be simulated.
    private static final class MutableProvider implements UpdateProvider {
        private volatile Release current;

        MutableProvider(Release initial) {
            this.current = initial;
        }

        void set(Release next) {
            this.current = next;
        }

        @Override
        public CompletableFuture<Optional<Release>> latest() {
            return CompletableFuture.completedFuture(Optional.of(current));
        }
    }

    @Test
    void announceDoesNotFireWhenUpToDate() {
        AtomicInteger announced = new AtomicInteger();
        UpdateChecker checker = new UpdateChecker(new InlineScheduler(), providing(release("1.4.0")), "1.4.0");
        checker.checkAndAnnounce(outcome -> announced.incrementAndGet()).join();
        assertThat(announced).hasValue(0);
    }

    @Test
    void lastOutcomeStartsEmptyThenReflectsCheck() {
        UpdateChecker checker = new UpdateChecker(new InlineScheduler(), providing(release("1.5.0")), "1.4.0");
        assertThat(checker.lastOutcome()).isEmpty();
        checker.check().join();
        assertThat(checker.lastOutcome()).map(UpdateOutcome::status).contains(UpdateStatus.OUTDATED);
    }

    @Test
    void rejectsUnparseableCurrentVersion() {
        assertThatThrownBy(() -> new UpdateChecker(new InlineScheduler(), providing(null), "garbage"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
