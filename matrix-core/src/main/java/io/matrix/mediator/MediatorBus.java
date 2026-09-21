package io.matrix.mediator;

import java.util.ArrayList;
import java.util.List;

/**
 * RUN 213 — MediatorBus (in-process message bus).
 *
 * <p>Simple publish-subscribe channel for in-process mediator
 * messages. Components subscribe to topics; producers publish;
 * subscribers get notified deterministically.
 */
public final class MediatorBus {

    public interface Subscriber {
        void receive(String topic, String payload);
    }

    private final List<TopicChannel> channels = new ArrayList<>();

    private static class TopicChannel {
        final String topic;
        final List<Subscriber> subs = new ArrayList<>();
        final List<String> deliveries = new ArrayList<>();
        TopicChannel(String t) { this.topic = t; }
    }

    private synchronized TopicChannel channel(String topic) {
        for (TopicChannel c : channels) {
            if (c.topic.equals(topic)) return c;
        }
        TopicChannel c = new TopicChannel(topic);
        channels.add(c);
        return c;
    }

    public synchronized void subscribe(String topic, Subscriber s) {
        channel(topic).subs.add(s);
    }

    public synchronized void publish(String topic, String payload) {
        TopicChannel c = channel(topic);
        c.deliveries.add(payload);
        for (Subscriber s : c.subs) {
            s.receive(topic, payload);
        }
    }

    public synchronized int subscriberCount(String topic) {
        return channel(topic).subs.size();
    }

    public synchronized int deliveryCount(String topic) {
        return channel(topic).deliveries.size();
    }

    public synchronized void clear(String topic) {
        channel(topic).deliveries.clear();
    }
}
