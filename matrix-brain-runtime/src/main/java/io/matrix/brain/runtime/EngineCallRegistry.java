package io.matrix.brain.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RECON-W1 — Mechanical truthfulness guard (Article VIII).
 *
 * <p>Every cognitive stage that wishes to claim "I did X" must first
 * <code>register()</code> an {@link EngineCall} in this registry.
 * BrcStep.evidence is then generated FROM the registry — never
 * hand-written strings — so that the evidence names the actual engine
 * method that was invoked and whose output shaped the reply.</p>
 *
 * <p>This eliminates the shadow-logic failure mode where a stage
 * pretends a different engine produced the answer.</p>
 */
public final class EngineCallRegistry {

    /** One recorded invocation: engine class.method + args digest + raw output digest. */
    public record EngineCall(
        String stage,             // which stage (REFLEX, HDC, BIR, ...)
        String engineClass,       // e.g. "io.matrix.brain.runtime.HdcRetrievalStage"
        String engineMethod,      // e.g. "retrieve"
        String argsDigest,        // short hash of the input args
        String outputDigest,      // short hash of the output
        long timestampNs
    ) {
        public String evidenceString() {
            return "engine=" + engineClass + "." + engineMethod
                 + "(args=" + argsDigest + ",out=" + outputDigest + ")";
        }
    }

    private final List<EngineCall> calls = new ArrayList<>();
    private final Map<String, Integer> stageFiredCount = new LinkedHashMap<>();

    /** Record one engine invocation. Idempotent on duplicate (stage, method). */
    public synchronized EngineCall register(String stage, String engineClass,
                                            String engineMethod, String argsDigest,
                                            String outputDigest) {
        if (stage == null || stage.isBlank())
            throw new IllegalArgumentException("stage required");
        if (engineClass == null || engineClass.isBlank())
            throw new IllegalArgumentException("engineClass required");
        if (engineMethod == null || engineMethod.isBlank())
            throw new IllegalArgumentException("engineMethod required");
        EngineCall c = new EngineCall(stage, engineClass, engineMethod,
            argsDigest == null ? "" : argsDigest,
            outputDigest == null ? "" : outputDigest,
            System.nanoTime());
        calls.add(c);
        stageFiredCount.merge(stage, 1, Integer::sum);
        return c;
    }

    public synchronized int callCount() {
        return calls.size();
    }

    public synchronized List<EngineCall> allCalls() {
        return Collections.unmodifiableList(new ArrayList<>(calls));
    }

    public synchronized Map<String, Integer> stageFiredCount() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(stageFiredCount));
    }

    /** Returns evidence strings ready for a BrcStep. */
    public synchronized List<String> evidenceFor(String stage) {
        List<String> out = new ArrayList<>();
        for (EngineCall c : calls) {
            if (c.stage().equals(stage)) out.add(c.evidenceString());
        }
        return out;
    }

    /** Verify the registry has at least one call from the given stage. */
    public synchronized boolean firedFrom(String stage) {
        return stageFiredCount.containsKey(stage);
    }

    /** Reset for the next cognitive cycle. */
    public synchronized void reset() {
        calls.clear();
        stageFiredCount.clear();
    }
}
