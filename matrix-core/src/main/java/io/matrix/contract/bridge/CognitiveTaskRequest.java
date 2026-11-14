package io.matrix.contract.bridge;

import io.matrix.contract.Correlation;
import io.matrix.contract.Provenance;
import io.matrix.contract.RetryPolicy;

import java.util.List;
import java.util.Map;

/**
 * W53 — a typed request for remote cognitive work.
 *
 * <p><b>Why this record exists.</b> W52's bridge carried <i>knowledge</i>: an observation, a
 * candidate belief, a link to evidence, an established belief. A request to <i>do
 * something</i> is a different thing, and it needs a different contract: a request has a
 * task, a capability, a budget, a deadline and an expected output; a fact has none of
 * those. The mandate is explicit that the protocol must not be "an unrestricted free-form
 * string", so the request is typed, and the natural-language text is one field of a typed
 * record rather than the record itself.</p>
 *
 * <p><b>What the request is allowed to carry.</b> The minimum context the declared task
 * needs, and nothing more: Chain A does <b>not</b> send its memory, its snapshot or its
 * claim store to Chain B. The context map is the declared, bounded context of the task, and
 * the payload limits are part of the request so that the receiver can refuse an oversized
 * request before it reads it.</p>
 *
 * <p><b>Where the authorisation is.</b> Not here. The authorisation of this request is the
 * <b>granted right in the envelope</b> — {@link BridgeCapability#INFERENCE_REQUEST} — and the
 * receiver checks its own grant on every invocation. A request that carried its own grant
 * would be a caller widening its own permission, which is exactly what the mandate
 * forbids. What the request carries is the capability it <i>needs</i>
 * ({@code requiredCapability}); whether it is allowed to ask for it is the receiver's
 * decision, made against its own allowlist.</p>
 *
 * <p><b>The idempotency key.</b> The request id is the key, and the payload hash is bound
 * to it: the same request id with the same payload replays the original result, and the
 * same request id with a different payload is a conflict. The hash is computed over the
 * canonical content of the request, so it is stable across processes.</p>
 *
 * @param protocolVersion   the bridge protocol version the sender speaks
 * @param taskId            the stable identity of the logical task
 * @param requestId         the stable identity of this attempt (the idempotency key)
 * @param correlation       the correlation that ties the task across both processes
 * @param parentEpisodeId   the episode or task this one belongs to
 * @param senderId          the identity of the requesting process and component
 * @param requiredCapability the capability the task needs, resolved through an allowlist
 * @param capabilityVersion the version of that capability the sender expects
 * @param kind              the typed kind of the task
 * @param input             the bounded input of the task; text is one field of it
 * @param context           the minimal declared context of the task
 * @param constraints       the explicit constraints the execution must respect
 * @param outputContract    the contract the result must satisfy
 * @param budgetMillis      the execution budget, in milliseconds
 * @param deadlineMillis    the deadline, measured from the moment the request was issued
 * @param maxPayloadBytes   the largest payload the receiver will accept for this task
 * @param maxRemoteHops     the number of remote hops the task may take (one for W53)
 * @param provenance        where the request came from
 * @param payloadHash       the content hash of the canonical request
 * @param retry             the retry policy of the caller
 */
public record CognitiveTaskRequest(
        int protocolVersion,
        String taskId,
        String requestId,
        Correlation correlation,
        String parentEpisodeId,
        String senderId,
        String requiredCapability,
        String capabilityVersion,
        Kind kind,
        String input,
        Map<String, String> context,
        List<String> constraints,
        String outputContract,
        long budgetMillis,
        long deadlineMillis,
        int maxPayloadBytes,
        int maxRemoteHops,
        Provenance provenance,
        String payloadHash,
        RetryPolicy retry) {

    /** The kinds of remote cognitive task the protocol defines. */
    public enum Kind {
        /** Run the cognitive cycle over the bounded input and report the result. */
        COGNITIVE_INFERENCE;

        /**
         * The kind named on the wire.
         *
         * @param wireName the name
         * @return the kind, or empty when the protocol does not define it
         */
        public static java.util.Optional<Kind> parse(String wireName) {
            if (wireName == null || wireName.isBlank()) {
                return java.util.Optional.empty();
            }
            for (Kind kind : values()) {
                if (kind.name().equals(wireName)) {
                    return java.util.Optional.of(kind);
                }
            }
            return java.util.Optional.empty();
        }
    }

    /** The deadline a request gets when the caller names none. */
    public static final long DEFAULT_DEADLINE_MILLIS = 5_000L;

    /** The execution budget a request gets when the caller names none. */
    public static final long DEFAULT_BUDGET_MILLIS = 5_000L;

    /** The hop bound of the first version of the protocol: one remote hop, no recursion. */
    public static final int MAX_REMOTE_HOPS = 1;

    public CognitiveTaskRequest {
        if (protocolVersion < 1) {
            throw new IllegalArgumentException("the task request must name a protocol version");
        }
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("the task request needs a task id");
        }
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("the task request needs a request id");
        }
        if (correlation == null) {
            throw new IllegalArgumentException("the task request needs a correlation");
        }
        if (senderId == null || senderId.isBlank()) {
            throw new IllegalArgumentException("the task request needs a sender identity");
        }
        if (requiredCapability == null || requiredCapability.isBlank()) {
            throw new IllegalArgumentException("the task request needs the capability it requires");
        }
        if (kind == null) {
            throw new IllegalArgumentException("the task request needs a task kind");
        }
        if (input == null) {
            input = "";
        }
        if (outputContract == null || outputContract.isBlank()) {
            throw new IllegalArgumentException("the task request needs an output contract");
        }
        if (budgetMillis <= 0L) {
            throw new IllegalArgumentException("the task request needs a positive budget");
        }
        if (deadlineMillis <= 0L) {
            throw new IllegalArgumentException("the task request needs a positive deadline");
        }
        if (maxPayloadBytes <= 0) {
            throw new IllegalArgumentException("the task request needs a positive payload limit");
        }
        if (maxRemoteHops < 1 || maxRemoteHops > MAX_REMOTE_HOPS) {
            throw new IllegalArgumentException("the task request may take at most "
                    + MAX_REMOTE_HOPS + " remote hop(s), and it asked for " + maxRemoteHops);
        }
        if (provenance == null) {
            throw new IllegalArgumentException("the task request needs its provenance");
        }
        context = context == null ? Map.of() : Map.copyOf(context);
        constraints = constraints == null ? List.of() : List.copyOf(constraints);
    }

    /**
     * The number of bytes the request's input occupies, in UTF-8.
     *
     * @return the size of the bounded input
     */
    public int inputBytes() {
        return input.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
    }

    /**
     * Whether the request stays inside the payload limit it declared.
     *
     * @return true when the input fits
     */
    public boolean withinPayloadLimit() {
        return inputBytes() <= maxPayloadBytes;
    }
}
