package io.matrix.operator;

import java.time.ZonedDateTime;
import java.util.List;

public class MatrixClusterStatus {

    private String phase = "Pending";
    private int activeNeurons;
    private int frozenNeurons;
    private double messagesPerSecond;
    private String lastSnapshot;
    private List<Condition> conditions;

    public String getPhase() { return phase; }
    public void setPhase(String phase) { this.phase = phase; }
    public int getActiveNeurons() { return activeNeurons; }
    public void setActiveNeurons(int activeNeurons) { this.activeNeurons = activeNeurons; }
    public int getFrozenNeurons() { return frozenNeurons; }
    public void setFrozenNeurons(int frozenNeurons) { this.frozenNeurons = frozenNeurons; }
    public double getMessagesPerSecond() { return messagesPerSecond; }
    public void setMessagesPerSecond(double messagesPerSecond) { this.messagesPerSecond = messagesPerSecond; }
    public String getLastSnapshot() { return lastSnapshot; }
    public void setLastSnapshot(String lastSnapshot) { this.lastSnapshot = lastSnapshot; }
    public List<Condition> getConditions() { return conditions; }
    public void setConditions(List<Condition> conditions) { this.conditions = conditions; }

    public static class Condition {
        private String type;
        private String status;
        private String reason;
        private String message;
        private String lastTransitionTime;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getLastTransitionTime() { return lastTransitionTime; }
        public void setLastTransitionTime(String lastTransitionTime) { this.lastTransitionTime = lastTransitionTime; }
    }
}
