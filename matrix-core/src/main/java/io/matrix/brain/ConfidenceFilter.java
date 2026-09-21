package io.matrix.brain;

/**
 * W489 — Confidence Filter (anti-hallucination).
 * 
 * Filters out low-confidence responses from the LLM.
 * If the model isn't sure, it refuses to answer rather than hallucinating.
 */
public final class ConfidenceFilter {
    
    private final double minConfidence;
    
    public ConfidenceFilter(double minConfidence) {
        this.minConfidence = minConfidence;
    }
    
    public ConfidenceFilter() {
        this(0.3);  // Default: 30% minimum confidence
    }
    
    /**
     * Check if a response should be accepted.
     * Returns null if rejected, or the response if accepted.
     */
    public String filter(String response, double confidence) {
        if (confidence < minConfidence) {
            return null;  // Reject
        }
        return response;  // Accept
    }
    
    /**
     * Get a rejection message for low-confidence responses.
     */
    public String rejectionMessage(double confidence) {
        return "I'm not confident enough to answer this. (confidence: " + 
            String.format("%.1f%%", confidence * 100) + 
            ", minimum: " + String.format("%.1f%%", minConfidence * 100) + ")";
    }
    
    public double getMinConfidence() { return minConfidence; }
}
