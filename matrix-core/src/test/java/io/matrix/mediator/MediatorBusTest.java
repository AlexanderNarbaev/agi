package io.matrix.mediator;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 213 — MediatorBus unit tests. */
class MediatorBusTest {

    @Test
    void emptyBusHasNoSubscribers() {
        var bus = new MediatorBus();
        assertThat(bus.subscriberCount("X")).isZero();
    }

    @Test
    void subscribeAddsSubscriber() {
        var bus = new MediatorBus();
        bus.subscribe("X", (t, p) -> {});
        assertThat(bus.subscriberCount("X")).isEqualTo(1);
    }

    @Test
    void publishNotifiesSubscribers() {
        var bus = new MediatorBus();
        List<String> received = new ArrayList<>();
        bus.subscribe("X", (t, p) -> received.add(p));
        bus.publish("X", "payload1");
        bus.publish("X", "payload2");
        assertThat(received).hasSize(2);
        assertThat(received.get(0)).isEqualTo("payload1");
    }

    @Test
    void multipleSubscribersAllReceive() {
        var bus = new MediatorBus();
        List<String> r1 = new ArrayList<>();
        List<String> r2 = new ArrayList<>();
        bus.subscribe("Y", (t, p) -> r1.add(p));
        bus.subscribe("Y", (t, p) -> r2.add(p));
        bus.publish("Y", "x");
        assertThat(r1).hasSize(1);
        assertThat(r2).hasSize(1);
    }

    @Test
    void deliveryCountTrack() {
        var bus = new MediatorBus();
        bus.subscribe("X", (t, p) -> {});
        bus.publish("X", "a");
        bus.publish("X", "b");
        assertThat(bus.deliveryCount("X")).isEqualTo(2);
    }

    @Test
    void clearResetsDeliveries() {
        var bus = new MediatorBus();
        bus.subscribe("X", (t, p) -> {});
        bus.publish("X", "a");
        bus.clear("X");
        assertThat(bus.deliveryCount("X")).isZero();
    }

    @Test
    void multipleTopicsIndependent() {
        var bus = new MediatorBus();
        List<String> topic1 = new ArrayList<>();
        List<String> topic2 = new ArrayList<>();
        bus.subscribe("t1", (t, p) -> topic1.add(p));
        bus.subscribe("t2", (t, p) -> topic2.add(p));
        bus.publish("t1", "a");
        bus.publish("t2", "b");
        assertThat(topic1).hasSize(1);
        assertThat(topic2).hasSize(1);
        assertThat(topic1.get(0)).isEqualTo("a");
        assertThat(topic2.get(0)).isEqualTo("b");
    }
}
