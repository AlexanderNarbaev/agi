package io.matrix.consciousness;

/**
 * RUN 305 — CycleLoadBalancer (load balancing).
 *
 * <p>Distributes cycles across multiple handlers based on
 * current load (arousal).
 */
public final class CycleLoadBalancer {

    public record Handler(int id, double currentLoad) {}

    private final int handlerCount;
    private final double[] load;

    public CycleLoadBalancer(int handlerCount) {
        this.handlerCount = handlerCount;
        this.load = new double[handlerCount];
    }

    /** Select handler with lowest load. Returns handler id. */
    public synchronized int select() {
        int minIdx = 0;
        double minLoad = load[0];
        for (int i = 1; i < handlerCount; i++) {
            if (load[i] < minLoad) {
                minLoad = load[i];
                minIdx = i;
            }
        }
        return minIdx;
    }

    public synchronized void updateLoad(int handlerId, double newLoad) {
        if (handlerId >= 0 && handlerId < handlerCount) {
            load[handlerId] = newLoad;
        }
    }

    public synchronized int handlerCount() { return handlerCount; }
    public synchronized double load(int handlerId) { return load[handlerId]; }
}
