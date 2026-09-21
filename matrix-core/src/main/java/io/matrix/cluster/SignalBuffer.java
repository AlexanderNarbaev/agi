package io.matrix.cluster;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Thread-safe signal buffer for incoming and outgoing neuron signals.
 */
public class SignalBuffer {

    private final ConcurrentLinkedQueue<Signal> queue = new ConcurrentLinkedQueue<>();
    private final int capacity;

    public SignalBuffer(int capacity) {
        this.capacity = capacity;
    }

    public boolean push(Signal signal) {
        if (queue.size() >= capacity) {
            return false;
        }
        return queue.offer(signal);
    }

    public Signal poll() {
        return queue.poll();
    }

    public int drainTo(java.util.Collection<Signal> target) {
        int count = 0;
        Signal s;
        while ((s = queue.poll()) != null) {
            target.add(s);
            count++;
        }
        return count;
    }

    public int size() {
        return queue.size();
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    public int capacity() {
        return capacity;
    }
}
