package io.matrix.observability;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * WAVE T-09 — Alert Dispatcher.
 *
 * Routes anomaly events to configured channels (Slack, Email, PagerDuty).
 * T-09 ships in-memory dispatch; T-09.5 wires real Slack/Email webhooks.
 *
 * <p>Severity levels:</p>
 * <ul>
 *   <li>INFO — informational, no action</li>
 *   <li>WARN — investigate within hours</li>
 *   <li>CRITICAL — page on-call immediately</li>
 * </ul>
 *
 * <p><b>CONSTITUTION compliance:</b> No LLM. Pure routing.</p>
 */
public final class AlertDispatcher {

    public enum Severity { INFO, WARN, CRITICAL }

    public record Alert(
        Severity severity,
        String title,
        String description,
        Instant timestamp,
        String source
    ) {}

    public interface Channel {
        String name();
        void send(Alert alert);
    }

    /** Rate limiter: don't send more than maxAlertsPerWindow within window. */
    public record RateLimit(int maxAlertsPerWindow, Duration window) {}

    private final List<Channel> channels = new CopyOnWriteArrayList<>();
    private final List<Alert> history = new ArrayList<>();
    private final RateLimit rateLimit;
    private final java.util.Deque<Instant> recentAlerts = new java.util.concurrent.ConcurrentLinkedDeque<>();

    public AlertDispatcher() {
        this(new RateLimit(10, Duration.ofMinutes(5)));
    }

    public AlertDispatcher(RateLimit rateLimit) {
        this.rateLimit = Objects.requireNonNull(rateLimit, "rateLimit");
    }

    public void addChannel(Channel channel) {
        channels.add(Objects.requireNonNull(channel, "channel"));
    }

    public void dispatch(Severity severity, String title, String description, String source) {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(title, "title");

        Instant now = Instant.now();
        // Rate limit check
        recentAlerts.add(now);
        Instant cutoff = now.minus(rateLimit.window());
        while (!recentAlerts.isEmpty() && recentAlerts.peek().isBefore(cutoff)) {
            recentAlerts.poll();
        }
        if (recentAlerts.size() > rateLimit.maxAlertsPerWindow) {
            return;  // rate-limited, silently drop
        }

        Alert alert = new Alert(severity, title, description, now, source);
        history.add(alert);
        for (Channel channel : channels) {
            try {
                channel.send(alert);
            } catch (Exception e) {
                // Don't fail dispatch if one channel fails
                System.err.println("Channel " + channel.name() + " failed: " + e.getMessage());
            }
        }
    }

    public int alertCount() { return history.size(); }

    public List<Alert> recentAlerts(int n) {
        int from = Math.max(0, history.size() - n);
        return new ArrayList<>(history.subList(from, history.size()));
    }

    public int channelCount() { return channels.size(); }
}
