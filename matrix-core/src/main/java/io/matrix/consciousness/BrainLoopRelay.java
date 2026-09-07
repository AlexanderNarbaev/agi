package io.matrix.consciousness;

/**
 * RUN 248 — BrainLoopRelay (input relay).
 *
 * <p>Simple input queuing with backpressure. Cycles pull from
 * the relay. If relay is empty, returns null.
 *
 * <p>Used to decouple input producers from cycle consumers.
 */
public final class BrainLoopRelay {

    public record Offer(String source, String input, long offeredAtMicros) {}

    private final java.util.concurrent.ArrayBlockingQueue<Offer> queue;
    private long totalOffered = 0;
    private long totalTaken = 0;

    public BrainLoopRelay(int capacity) {
        if (capacity <= 0) capacity = 1;
        this.queue = new java.util.concurrent.ArrayBlockingQueue<>(capacity);
    }

    public synchronized boolean offer(String source, String input) {
        boolean ok = queue.offer(new Offer(source, input,
                System.nanoTime() / 1000L));
        if (ok) totalOffered++;
        return ok;
    }

    public synchronized Offer take() {
        Offer o = queue.poll();
        if (o != null) totalTaken++;
        return o;
    }

    public synchronized int size() { return queue.size(); }
    public synchronized int capacity() { return queue.size() + queue.remainingCapacity(); }
    public synchronized long totalOffered() { return totalOffered; }
    public synchronized long totalTaken() { return totalTaken; }
}
