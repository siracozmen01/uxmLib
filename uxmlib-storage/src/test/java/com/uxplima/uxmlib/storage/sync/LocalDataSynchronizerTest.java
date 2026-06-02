package com.uxplima.uxmlib.storage.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/** In-memory synchronizer: a publish reaches every subscriber on that channel and no other. */
class LocalDataSynchronizerTest {

    @Test
    void publishReachesSubscribersOnTheSameChannel() {
        LocalDataSynchronizer sync = new LocalDataSynchronizer();
        List<String> seen = new ArrayList<>();
        sync.subscribe("cache", seen::add);

        sync.publish("cache", "evict:1");
        sync.publish("cache", "evict:2");

        assertThat(seen).containsExactly("evict:1", "evict:2");
    }

    @Test
    void publishDoesNotReachSubscribersOnAnotherChannel() {
        LocalDataSynchronizer sync = new LocalDataSynchronizer();
        List<String> cache = new ArrayList<>();
        List<String> events = new ArrayList<>();
        sync.subscribe("cache", cache::add);
        sync.subscribe("events", events::add);

        sync.publish("cache", "only-cache");

        assertThat(cache).containsExactly("only-cache");
        assertThat(events).isEmpty();
    }

    @Test
    void everySubscriberOnAChannelReceivesTheMessage() {
        LocalDataSynchronizer sync = new LocalDataSynchronizer();
        List<String> a = new ArrayList<>();
        List<String> b = new ArrayList<>();
        sync.subscribe("ch", a::add);
        sync.subscribe("ch", b::add);

        sync.publish("ch", "fanout");

        assertThat(a).containsExactly("fanout");
        assertThat(b).containsExactly("fanout");
    }

    @Test
    void publishToAChannelWithNoSubscribersIsANoOp() {
        LocalDataSynchronizer sync = new LocalDataSynchronizer();

        sync.publish("nobody", "msg");
        // no throw, no observable effect
        assertThat(sync.channelCount()).isZero();
    }

    @Test
    void unsubscribeStopsDelivery() {
        LocalDataSynchronizer sync = new LocalDataSynchronizer();
        List<String> seen = new ArrayList<>();
        Subscription handle = sync.subscribe("ch", seen::add);

        sync.publish("ch", "before");
        handle.unsubscribe();
        sync.publish("ch", "after");

        assertThat(seen).containsExactly("before");
    }

    @Test
    void unsubscribeOfOneHandlerLeavesOthersOnTheChannel() {
        LocalDataSynchronizer sync = new LocalDataSynchronizer();
        List<String> a = new ArrayList<>();
        List<String> b = new ArrayList<>();
        Subscription ha = sync.subscribe("ch", a::add);
        sync.subscribe("ch", b::add);

        ha.unsubscribe();
        sync.publish("ch", "msg");

        assertThat(a).isEmpty();
        assertThat(b).containsExactly("msg");
    }

    @Test
    void unsubscribeOfTheLastHandlerDropsTheChannel() {
        LocalDataSynchronizer sync = new LocalDataSynchronizer();
        Subscription handle = sync.subscribe("ch", m -> {});

        assertThat(sync.channelCount()).isEqualTo(1);
        handle.unsubscribe();
        assertThat(sync.channelCount()).isZero();
    }

    @Test
    void closeStopsAllDelivery() {
        LocalDataSynchronizer sync = new LocalDataSynchronizer();
        List<String> seen = new ArrayList<>();
        sync.subscribe("ch", seen::add);

        sync.close();
        sync.publish("ch", "after-close");

        assertThat(seen).isEmpty();
        assertThat(sync.channelCount()).isZero();
    }

    @Test
    void subscribingAfterCloseIsRejected() {
        LocalDataSynchronizer sync = new LocalDataSynchronizer();
        sync.close();

        assertThatThrownBy(() -> sync.subscribe("ch", m -> {})).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aFailingHandlerDoesNotStopOtherHandlers() {
        LocalDataSynchronizer sync = new LocalDataSynchronizer();
        List<String> seen = new ArrayList<>();
        sync.subscribe("ch", m -> {
            throw new IllegalStateException("boom");
        });
        sync.subscribe("ch", seen::add);

        sync.publish("ch", "msg");

        assertThat(seen).containsExactly("msg");
    }

    @Test
    void publicMethodsRejectNulls() {
        LocalDataSynchronizer sync = new LocalDataSynchronizer();

        assertThatThrownBy(() -> sync.publish(nullString(), "m")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> sync.publish("c", nullString())).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> sync.subscribe(nullString(), m -> {})).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> sync.subscribe("c", nullHandler())).isInstanceOf(NullPointerException.class);
    }

    @SuppressWarnings("NullAway")
    private static String nullString() {
        return null;
    }

    @SuppressWarnings("NullAway")
    private static java.util.function.Consumer<String> nullHandler() {
        return null;
    }
}
