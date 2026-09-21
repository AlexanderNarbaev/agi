package io.matrix.brain;

/**
 * W490 — Confidence-Filtered Brain.
 * 
 * Wraps any BrainCycle with a confidence filter.
 * If the model isn't confident enough, it refuses to answer.
 * 
 * This is the ANTI-HALLUCINATION measure:
 * - High confidence → accept the response
 * - Low confidence → refuse and say "I'm not sure"
 */
public final class ConfidenceFilteredBrain implements BrainCycle {
    
    private final BrainCycle delegate;
    private final ConfidenceFilter filter;
    private long rejectedCount = 0;
    private long acceptedCount = 0;
    
    public ConfidenceFilteredBrain(BrainCycle delegate, ConfidenceFilter filter) {
        this.delegate = delegate;
        this.filter = filter;
    }
    
    public ConfidenceFilteredBrain(BrainCycle delegate) {
        this(delegate, new ConfidenceFilter());
    }
    
    @Override
    public CycleResult cycle(String input) {
        CycleResult raw = delegate.cycle(input);
        
        // Check confidence
        String filtered = filter.filter(raw.reply(), raw.confidence());
        if (filtered == null) {
            rejectedCount++;
            return new CycleResult(
                false,  // not accepted
                "DENY:low_confidence",
                filter.rejectionMessage(raw.confidence()),
                raw.arousal(),
                raw.focusCount(),
                raw.predictionError(),
                raw.confidence(),
                raw.auditIndex(),
                raw.durationMs()
            );
        }
        
        acceptedCount++;
        return raw;
    }
    
    @Override
    public void close() {
        delegate.close();
    }
    
    public long getRejectedCount() { return rejectedCount; }
    public long getAcceptedCount() { return acceptedCount; }
    public double getRejectionRate() {
        long total = rejectedCount + acceptedCount;
        return total > 0 ? (double) rejectedCount / total : 0.0;
    }
    
    public BrainCycle getDelegate() { return delegate; }
}
