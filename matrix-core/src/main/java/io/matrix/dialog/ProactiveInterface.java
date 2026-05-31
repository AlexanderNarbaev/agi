package io.matrix.dialog;

import io.matrix.mediator.DriverState;
import io.matrix.mediator.DriverType;

import java.util.ArrayList;
import java.util.List;

/**
 * Monitors driver states and decides when to initiate proactive dialog.
 *
 * <p>Conditions for initiation:
 * <ul>
 * <li>High D_social — system "misses" interaction</li>
 * <li>High D_curiosity + interesting internal finding</li>
 * <li>Anomaly detected — warn user</li>
 * <li>Evolution milestone reached — celebrate with user</li>
 * </ul>
 *
 * <p>Ref: L4_Mediator.md §6.1
 */
public class ProactiveInterface {

    public enum InitiationReason {
        SOCIAL_NEED("Feeling social — long time no chat"),
        CURIOSITY_FINDING("Discovered something interesting"),
        ANOMALY_DETECTED("Detected a potential issue"),
        MILESTONE_REACHED("Evolution milestone achieved");

        private final String description;

        InitiationReason(String description) {
            this.description = description;
        }

        public String description() { return description; }
    }

    public record InitiationDecision(
            boolean shouldInitiate,
            InitiationReason reason,
            String suggestedMessage
    ) {
        public static InitiationDecision no() {
            return new InitiationDecision(false, null, null);
        }

        public static InitiationDecision yes(InitiationReason reason, String message) {
            return new InitiationDecision(true, reason, message);
        }
    }

    private final List<String> interactionHistory = new ArrayList<>();
    private int ignoredCount;
    private long lastInteractionTime;
    private boolean proactiveEnabled = true;

    /**
     * Evaluates driver states and decides whether to initiate dialog.
     */
    public InitiationDecision evaluate(List<DriverState> drivers, List<String> recentDiscoveries,
                                        List<String> recentAnomalies, List<String> milestones) {
        if (!proactiveEnabled) return InitiationDecision.no();

        for (DriverState driver : drivers) {
            if (driver.type() == DriverType.CURIOSITY && driver.isHigh()) {
                if (!recentDiscoveries.isEmpty()) {
                    String discovery = recentDiscoveries.get(0);
                    return InitiationDecision.yes(
                            InitiationReason.CURIOSITY_FINDING,
                            "I've discovered something interesting: " + discovery
                                    + ". Would you like to hear about it?");
                }
            }
        }

        if (!recentAnomalies.isEmpty()) {
            String anomaly = recentAnomalies.get(0);
            return InitiationDecision.yes(
                    InitiationReason.ANOMALY_DETECTED,
                    "I've detected an anomaly: " + anomaly
                            + ". Would you like me to investigate?");
        }

        if (!milestones.isEmpty()) {
            String milestone = milestones.get(0);
            return InitiationDecision.yes(
                    InitiationReason.MILESTONE_REACHED,
                    "I've reached a milestone: " + milestone + "!");
        }

        long idleMs = System.currentTimeMillis() - lastInteractionTime;
        if (idleMs > 60_000) {
            return InitiationDecision.yes(
                    InitiationReason.SOCIAL_NEED,
                    "It's been a while since we last talked. How are you doing?");
        }

        return InitiationDecision.no();
    }

    public void recordInteraction(String message) {
        interactionHistory.add(message);
        lastInteractionTime = System.currentTimeMillis();
        ignoredCount = 0;
    }

    public void recordIgnored() {
        ignoredCount++;
        if (ignoredCount > 3) {
            proactiveEnabled = false;
        }
    }

    public void enable() { proactiveEnabled = true; }
    public void disable() { proactiveEnabled = false; }
    public boolean isEnabled() { return proactiveEnabled; }
    public int ignoredCount() { return ignoredCount; }
    public List<String> interactionHistory() { return List.copyOf(interactionHistory); }
}
