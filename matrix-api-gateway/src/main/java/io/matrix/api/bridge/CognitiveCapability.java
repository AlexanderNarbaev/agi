package io.matrix.api.bridge;

import io.matrix.api.brain.BrainCycle;
import io.matrix.contract.bridge.CognitiveTaskRequest;
import io.matrix.contract.bridge.CognitiveTaskResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * W53 — the capabilities chain B exposes to a peer, and the real path each one runs.
 *
 * <p><b>The allowlist is the whole security model of the remote execution.</b> A capability
 * id arrives over the network and is resolved through this map: an id that is not in the
 * map is not served, and there is no path from a name on the wire to a class, a method or a
 * reflection call. The mandate forbids exactly that, and this class is where the refusal
 * lives.</p>
 *
 * <p><b>The first capability is the real cognitive path.</b> {@link #COGNITIVE_INFERENCE}
 * runs the brain's own cycle — the ten-stage pipeline behind
 * {@code ProductionBrainClient.cycle(...)} — and nothing else. It is not a stub, and it is
 * not a re-implementation of the inference: it is the same call the gateway's own
 * {@code /v1/analyze} route makes.</p>
 *
 * <p><b>What the execution reports.</b> Only what the production path exposes: the reply,
 * the confidence, the modulators that fired and the duration. The response's hypothesis,
 * prediction, outcome and supporting-observation fields stay <b>empty</b>, because the
 * production path does not expose them — the mandate is explicit that a remote answer must
 * not carry fabricated reasoning steps, and an empty field is honest where a plausible
 * sentence would not be.</p>
 */
public final class CognitiveCapability {

    /** The capability id of the bounded cognitive inference chain B exposes. */
    public static final String COGNITIVE_INFERENCE = "brain.cognitive-inference";

    /** The version of that capability. */
    public static final String COGNITIVE_INFERENCE_VERSION = "v1";

    /**
     * What one execution produced.
     *
     * @param status          what happened
     * @param result          the result, when there is one
     * @param confidence      the confidence, when the capability reports one
     * @param basis           what the confidence rests on
     * @param trace           the steps the capability actually exposed
     * @param durationMillis  how long the execution took
     * @param traceReference  the reference to the durable execution trace
     * @param effectCommitted whether the execution committed a memory effect
     * @param refusalReason   the reason, when the status is not a result
     */
    public record Execution(
            CognitiveTaskResponse.Status status,
            String result,
            Double confidence,
            CognitiveTaskResponse.ConfidenceBasis basis,
            List<String> trace,
            Long durationMillis,
            String traceReference,
            boolean effectCommitted,
            String refusalReason) {

        public Execution {
            trace = trace == null ? List.of() : List.copyOf(trace);
        }
    }

    /** The execution of one capability. */
    @FunctionalInterface
    public interface Executor {
        /**
         * Runs the capability.
         *
         * @param request the request
         * @return what the execution produced
         * @throws Exception when the execution fails
         */
        Execution execute(CognitiveTaskRequest request) throws Exception;
    }

    /**
     * One served capability: the version it is served at and the path that runs it.
     *
     * @param version  the version this process serves it at
     * @param executor the path that runs it
     */
    private record Served(String version, Executor executor) {
    }

    private final Map<String, Served> capabilities;

    /**
     * The capabilities that were revoked, with the reason.
     *
     * <p><b>Why the revocation is kept and not just deleted.</b> A revoked capability must
     * not be served again, and the reason has to survive for the audit: a caller whose
     * task is refused has to learn <i>why</i>, and "this bridge serves no capability called
     * X" would be a lie — the bridge served it, and then it was withdrawn.</p>
     */
    private final Map<String, String> revoked = new ConcurrentHashMap<>();

    /**
     * A capability set with nothing in it.
     */
    public CognitiveCapability() {
        this.capabilities = Map.of();
    }

    private CognitiveCapability(Map<String, Served> capabilities) {
        this.capabilities = Map.copyOf(capabilities);
    }

    /**
     * The capability set chain B serves: the real cognitive inference.
     *
     * @param brain the brain runtime the gateway holds
     * @return the capabilities
     */
    public static CognitiveCapability standard(BrainCycle brain) {
        Map<String, Served> served = new LinkedHashMap<>();
        served.put(COGNITIVE_INFERENCE, new Served(COGNITIVE_INFERENCE_VERSION, request -> {
            if (brain == null) {
                return new Execution(CognitiveTaskResponse.Status.UNSUPPORTED, null, null, null,
                        List.of(), null, null, false,
                        "the brain runtime is not available in this process");
            }
            String context = request.context() == null || request.context().isEmpty()
                    ? null : String.join("; ",
                            request.context().entrySet().stream()
                                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                                    .toList());
            BrainCycle.CycleResult result = brain.cycle(request.input(), context, null);
            if (result == null) {
                return new Execution(CognitiveTaskResponse.Status.FAILED, null, null, null,
                        List.of(), null, null, false,
                        "the brain cycle returned nothing");
            }
            List<String> trace = result.modulatorsFired() == null
                    ? List.of() : List.copyOf(result.modulatorsFired());
            if (!result.accepted()) {
                return new Execution(CognitiveTaskResponse.Status.REFUSED, null, null, null,
                        trace, result.durationMs(), null, false,
                        "the brain cycle refused the input");
            }
            return new Execution(CognitiveTaskResponse.Status.COMPLETED, result.reply(),
                    result.confidence(), CognitiveTaskResponse.ConfidenceBasis.MEASURED,
                    trace, result.durationMs(), null, false, null);
        }));
        return new CognitiveCapability(served);
    }

    /**
     * Whether this set serves the named capability.
     *
     * @param capabilityId the capability
     * @return true when the capability may be invoked
     */
    public boolean serves(String capabilityId) {
        return capabilityId != null && capabilities.containsKey(capabilityId);
    }

    /**
     * The capabilities this set serves.
     *
     * @return the ids
     */
    public Set<String> served() {
        Set<String> live = new java.util.LinkedHashSet<>(capabilities.keySet());
        live.removeAll(revoked.keySet());
        return Set.copyOf(live);
    }

    /**
     * Withdraws a capability from service.
     *
     * <p><b>What this is for.</b> The mandate asks that a revocation prevent subsequent
     * execution, and it asks that the permissions be checked on <i>every</i> invocation.
     * The check is per invocation here, so a revocation takes effect on the next call:
     * the capability is not executed once more, and the caller is told why.</p>
     *
     * @param capabilityId the capability
     * @param reason       why it was withdrawn
     */
    public void revoke(String capabilityId, String reason) {
        if (capabilityId == null || capabilityId.isBlank()) {
            throw new IllegalArgumentException("the capability id must not be blank");
        }
        if (!capabilities.containsKey(capabilityId)) {
            throw new IllegalArgumentException("this bridge serves no capability called "
                    + capabilityId);
        }
        revoked.put(capabilityId, reason == null || reason.isBlank()
                ? "it was withdrawn" : reason);
    }

    /**
     * The capabilities that were withdrawn, with the reason.
     *
     * @return the revocations
     */
    public Map<String, String> revocations() {
        return Map.copyOf(revoked);
    }

    /**
     * Runs a capability.
     *
     * @param request the request
     * @return what the execution produced; an {@code UNSUPPORTED} execution when the
     *         capability is not in the allowlist
     */
    public Execution execute(CognitiveTaskRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("there is no request to execute");
        }
        String withdrawal = revoked.get(request.requiredCapability());
        if (withdrawal != null) {
            // A withdrawn capability is not served, and the reason says that it was
            // withdrawn rather than that it never existed.
            return new Execution(CognitiveTaskResponse.Status.UNSUPPORTED, null, null, null,
                    List.of(), null, null, false,
                    "the capability " + request.requiredCapability()
                            + " was withdrawn: " + withdrawal);
        }
        Served served = capabilities.get(request.requiredCapability());
        if (served == null) {
            return new Execution(CognitiveTaskResponse.Status.UNSUPPORTED, null, null, null,
                    List.of(), null, null, false,
                    "this bridge serves no capability called " + request.requiredCapability());
        }
        String asked = request.capabilityVersion();
        if (asked != null && !asked.isBlank() && !asked.equals(served.version())) {
            // The task asked for a version this process does not serve. Running it anyway
            // would answer a different question than the one that was asked.
            return new Execution(CognitiveTaskResponse.Status.UNSUPPORTED, null, null, null,
                    List.of(), null, null, false,
                    "this bridge serves " + request.requiredCapability() + " at version "
                            + served.version() + ", and the task asked for " + asked);
        }
        try {
            return served.executor().execute(request);
        } catch (Exception e) {
            return new Execution(CognitiveTaskResponse.Status.FAILED, null, null, null,
                    List.of(), null, null, false,
                    "the execution failed: " + e.getClass().getSimpleName()
                            + (e.getMessage() == null ? "" : ": " + e.getMessage()));
        }
    }
}
