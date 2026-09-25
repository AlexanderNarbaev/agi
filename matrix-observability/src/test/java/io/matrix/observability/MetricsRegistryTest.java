package io.matrix.observability;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MetricsRegistryTest {

    @Test
    void testRegisterAndRender() {
        MetricsRegistry registry = new MetricsRegistry();
        PrometheusMetric counter = registry.registerCounter("test_total", "Test");
        counter.increment();
        counter.increment();
        assertEquals(2.0, counter.getValue());

        String rendered = registry.renderAll();
        assertTrue(rendered.contains("test_total"));
        assertTrue(rendered.contains("# TYPE test_total counter"));
    }

    @Test
    void testGaugeSet() {
        MetricsRegistry registry = new MetricsRegistry();
        PrometheusMetric gauge = registry.registerGauge("active_users", "Active users");
        gauge.set(42.0);
        assertEquals(42.0, gauge.getValue());
        gauge.set(100.0);
        assertEquals(100.0, gauge.getValue());
    }

    @Test
    void testRegisterSameNameTwiceReturnsSame() {
        MetricsRegistry registry = new MetricsRegistry();
        PrometheusMetric a = registry.registerCounter("dup", "first");
        PrometheusMetric b = registry.registerCounter("dup", "second");
        assertSame(a, b);
        assertEquals(1, registry.size());
    }

    @Test
    void testIncrementBy() {
        MetricsRegistry registry = new MetricsRegistry();
        PrometheusMetric c = registry.registerCounter("batch_total", "Batch");
        c.incrementBy(5.0);
        c.incrementBy(2.5);
        assertEquals(7.5, c.getValue());
    }

    @Test
    void testIncrementHistogramThrows() {
        MetricsRegistry registry = new MetricsRegistry();
        PrometheusMetric h = registry.registerHistogram("latency_ms", "Latency");
        assertThrows(UnsupportedOperationException.class, h::increment);
    }

    @Test
    void testRenderIncludesAllRegistered() {
        MetricsRegistry registry = new MetricsRegistry();
        registry.registerCounter("a", "first");
        registry.registerCounter("b", "second");
        String rendered = registry.renderAll();
        assertTrue(rendered.contains("a"));
        assertTrue(rendered.contains("b"));
    }
}
