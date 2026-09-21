package io.matrix.observability;

import java.util.Objects;

/**
 * WAVE T-09 — Single Prometheus metric.
 *
 * Holds a name, value, type (counter/gauge/histogram), and optional labels.
 * Thread-safe access via synchronized increment.
 */
public final class PrometheusMetric {

    public enum Type { COUNTER, GAUGE, HISTOGRAM }

    public final String name;
    public final Type type;
    public final String help;
    private volatile double value;

    public PrometheusMetric(String name, Type type, String help) {
        this.name = Objects.requireNonNull(name, "name");
        this.type = Objects.requireNonNull(type, "type");
        this.help = Objects.requireNonNull(help, "help");
        this.value = 0.0;
    }

    public synchronized void increment() {
        if (type != Type.COUNTER && type != Type.GAUGE) {
            throw new UnsupportedOperationException("Cannot increment " + type);
        }
        value += 1.0;
    }

    public synchronized void incrementBy(double delta) {
        if (type != Type.COUNTER && type != Type.GAUGE) {
            throw new UnsupportedOperationException("Cannot increment " + type);
        }
        value += delta;
    }

    public synchronized void set(double newValue) {
        value = newValue;
    }

    public double getValue() { return value; }

    public String render() {
        return String.format("# HELP %s %s%n# TYPE %s %s%n%s %s%n",
            name, help, name, type.toString().toLowerCase(), name, value);
    }

    @Override
    public String toString() {
        return name + "{" + type + "}=" + value;
    }
}
