package io.matrix.brain;

/**
 * W482 — Common interface for brain cycles.
 * 
 * Both LlmBrainLoopService and LlmBrainLoopRag implement this.
 */
public interface BrainCycle {
    
    /** Result of one cognitive cycle. */
    record CycleResult(
        boolean accepted,
        String action,
        String reply,
        double arousal,
        int focusCount,
        double predictionError,
        double confidence,
        long auditIndex,
        long durationMs
    ) {}
    
    /**
     * Run one cognitive cycle.
     */
    CycleResult cycle(String input);
    
    /**
     * Run cycle with chat-style input.
     */
    default CycleResult chat(String sessionId, String userMessage) {
        return cycle(userMessage);
    }
    
    /**
     * Release resources.
     */
    void close();
}
