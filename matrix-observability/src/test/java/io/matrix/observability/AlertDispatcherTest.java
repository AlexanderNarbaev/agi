package io.matrix.observability;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AlertDispatcherTest {

    @Test
    void testDispatchSendsToAllChannels() {
        AlertDispatcher d = new AlertDispatcher();
        List<String> channel1 = new ArrayList<>();
        List<String> channel2 = new ArrayList<>();
        d.addChannel(new AlertDispatcher.Channel() {
            public String name() { return "ch1"; }
            public void send(AlertDispatcher.Alert a) { channel1.add(a.title()); }
        });
        d.addChannel(new AlertDispatcher.Channel() {
            public String name() { return "ch2"; }
            public void send(AlertDispatcher.Alert a) { channel2.add(a.title()); }
        });

        d.dispatch(AlertDispatcher.Severity.CRITICAL, "Test", "details", "test");
        assertEquals(1, channel1.size());
        assertEquals(1, channel2.size());
        assertEquals(1, d.alertCount());
    }

    @Test
    void testRateLimit() {
        AlertDispatcher d = new AlertDispatcher(new AlertDispatcher.RateLimit(2, Duration.ofMinutes(1)));
        d.addChannel(new AlertDispatcher.Channel() {
            public String name() { return "noop"; }
            public void send(AlertDispatcher.Alert a) {}
        });
        d.dispatch(AlertDispatcher.Severity.INFO, "A", "a", "test");
        d.dispatch(AlertDispatcher.Severity.INFO, "B", "b", "test");
        d.dispatch(AlertDispatcher.Severity.INFO, "C", "c", "test");  // dropped
        assertEquals(2, d.alertCount());
    }

    @Test
    void testChannelFailureDoesNotBlockOthers() {
        AlertDispatcher d = new AlertDispatcher();
        d.addChannel(new AlertDispatcher.Channel() {
            public String name() { return "broken"; }
            public void send(AlertDispatcher.Alert a) { throw new RuntimeException("fail"); }
        });
        d.addChannel(new AlertDispatcher.Channel() {
            public String name() { return "ok"; }
            public void send(AlertDispatcher.Alert a) { /* swallow */ }
        });
        assertDoesNotThrow(() -> d.dispatch(AlertDispatcher.Severity.WARN, "X", "y", "test"));
        assertEquals(1, d.alertCount());
    }

    @Test
    void testRecentAlerts() {
        AlertDispatcher d = new AlertDispatcher();
        for (int i = 0; i < 5; i++) {
            d.dispatch(AlertDispatcher.Severity.INFO, "alert_" + i, "details", "test");
        }
        assertEquals(5, d.alertCount());
        List<AlertDispatcher.Alert> recent = d.recentAlerts(3);
        assertEquals(3, recent.size());
    }

    @Test
    void testAlertRecordFields() {
        AlertDispatcher d = new AlertDispatcher();
        d.dispatch(AlertDispatcher.Severity.CRITICAL, "Test", "details", "src");
        AlertDispatcher.Alert alert = d.recentAlerts(1).get(0);
        assertEquals(AlertDispatcher.Severity.CRITICAL, alert.severity());
        assertEquals("Test", alert.title());
        assertEquals("details", alert.description());
        assertEquals("src", alert.source());
    }

    @Test
    void testChannelCount() {
        AlertDispatcher d = new AlertDispatcher();
        assertEquals(0, d.channelCount());
        d.addChannel(new AlertDispatcher.Channel() {
            public String name() { return "x"; }
            public void send(AlertDispatcher.Alert a) {}
        });
        assertEquals(1, d.channelCount());
    }
}
