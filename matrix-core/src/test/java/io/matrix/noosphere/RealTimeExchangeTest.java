package io.matrix.noosphere;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RealTimeExchangeTest {

    private static FnlPackage pkg(String name, String type) {
        return FnlPackage.builder()
                .name(name).type(type).accuracy(0.95)
                .authorInstanceId("test-node").snapshotHash("hash-" + name)
                .build();
    }

    @Test
    void shouldPublishAndDiscoverPackages() {
        var exchange = new RealTimeExchange("node-a");
        exchange.publish(pkg("test-fnl", "vision"));

        var discovered = exchange.discover("vision");
        assertThat(discovered).hasSize(1);
        assertThat(discovered.getFirst().name()).isEqualTo("test-fnl");
    }

    @Test
    void shouldNotifySubscribers() {
        var exchange = new RealTimeExchange("node-a");
        List<FnlPackage> received = new ArrayList<>();
        exchange.subscribe("fnl:vision", received::add);

        exchange.publish(pkg("test", "vision"));

        assertThat(received).hasSize(1);
        assertThat(received.getFirst().type()).isEqualTo("vision");
    }

    @Test
    void shouldUnsubscribe() {
        var exchange = new RealTimeExchange("node-a");
        List<FnlPackage> received = new ArrayList<>();
        exchange.subscribe("fnl:vision", received::add);
        exchange.unsubscribe("fnl:vision", received::add);

        exchange.publish(pkg("x", "vision"));
        assertThat(received).isEmpty();
    }

    @Test
    void shouldProvideStats() {
        var exchange = new RealTimeExchange("stats-node");
        exchange.publish(pkg("a", "type1"));
        exchange.publish(pkg("b", "type2"));
        exchange.subscribe("fnl:type1", p -> {});

        var stats = exchange.stats();
        assertThat(stats.get("published_count")).isEqualTo(2);
        assertThat(stats.get("channels")).isEqualTo(1);
    }

    @Test
    void shouldFilterByTypePattern() {
        var exchange = new RealTimeExchange("node");
        exchange.publish(pkg("v1", "vision"));
        exchange.publish(pkg("t1", "text"));
        exchange.publish(pkg("v2", "vision_v2"));

        assertThat(exchange.discover("vision")).hasSize(2);
        assertThat(exchange.discover("text")).hasSize(1);
        assertThat(exchange.discover("audio")).isEmpty();
    }
}
