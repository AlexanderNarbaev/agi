package io.matrix.contract.bridge;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * W51 — the chain A side of the bridge (BRIDGE-2 … BRIDGE-9).
 *
 * <p><b>What this class is.</b> It receives a message, decides whether it satisfies the
 * contract, writes the effect exactly once, writes the audit record, and answers. It does
 * not open a database, does not name a database path and does not import anything from the
 * store: the write goes through {@link BridgeSink}, and the implementation of that interface
 * is the one piece that belongs to chain A. That is what lets this package be read by a
 * chain B client without pulling chain A in with it.</p>
 *
 * <p><b>The order of the checks is the contract.</b> Every check exists because a requirement
 * names it, and the order is chosen so that the cheapest and most decisive check comes
 * first:</p>
 * <ol>
 *   <li><b>the protocol and the version</b> (BRIDGE-1, BRIDGE-2) — a message from another
 *       protocol, or from a version this reader does not know, is refused before anything
 *       else is looked at;</li>
 *   <li><b>the capability</b> (BRIDGE-5) — the kind is in the clear, so a message that asks
 *       for a right this bridge did not grant is refused without its body being parsed;</li>
 *   <li><b>the signature</b> (BRIDGE-3) — nothing untrusted is parsed before it is
 *       authenticated;</li>
 *   <li><b>the payload</b> (BRIDGE-4, BRIDGE-5) — it must be readable, its kind must agree
 *       with the envelope's, and it must carry the provenance of the delivery;</li>
 *   <li><b>the budget</b> (BRIDGE-9) — the last gate before the store is touched;</li>
 *   <li><b>the effect</b> — and only then is the sink called.</li>
 * </ol>
 *
 * <p><b>Why the idempotency is consulted before the checks.</b> BRIDGE-8 promises that "a
 * caller that retries after a timeout gets the outcome of the first attempt, not a second
 * observation". A retry that was refused for an exhausted quota must get that refusal back
 * rather than a fresh attempt, and a retry must not consume a second slot of the budget. So
 * a request id that has already been decided is answered from the registry, and the checks
 * are not run again.</p>
 *
 * <p><b>Why a reused request id with a different payload is refused.</b> A request id is the
 * idempotency key. Two different messages under one key are not a retry; they are a
 * conflict, and answering the second with the first's outcome would hide a sender that
 * disagrees with itself. The conflict is refused and audited.</p>
 *
 * <p><b>Why the clock is a parameter.</b> Nothing here reads the wall clock. The instant of
 * the decision is supplied by the caller, so the same calls produce the same decisions and
 * the same records, in this process and in any other. The time-dependent parts of the
 * protocol — the rate window and the daily ceiling — are counted from the same instant.</p>
 */
public final class BridgeReceiver {

    private final BridgeSigner verifier;
    private final Set<BridgeCapability> granted;
    private final BridgeBudget budget;
    private final BridgeIdempotency idempotency;
    private final BridgeSink sink;
    private final BridgePayloadCodec codec;
    /**
     * The audit log of the receiver.
     *
     * <p><b>Why a queue and not a copy-on-write list.</b> A copy-on-write list copies its
     * whole backing array on EVERY append, so an append costs O(n) in the number of records
     * already written and a chain of n records costs O(n²) in total. The measurement of
     * 2026-11-08 saw exactly that: the bytes allocated per call grew by 17 635 B between a
     * chain of 1 000 and a chain of 10 000 records, which is the copy, not the work. A
     * concurrent queue appends in constant time and copies nothing, and the snapshot the
     * caller receives is still a copy — {@link #auditLog()} hands out a list that does not
     * change under the caller.</p>
     */
    private final Queue<BridgeAuditRecord> auditLog = new ConcurrentLinkedQueue<>();
    private final AtomicLong auditSequence = new AtomicLong();

    /**
     * A receiver with the given parts.
     *
     * @param verifier    the signer that verifies incoming messages; {@code null} means that
     *                    no call can be trusted and every message is refused (BRIDGE-3)
     * @param granted     the rights this bridge grants to its senders (BRIDGE-5)
     * @param budget      the budget enforced before the store is touched (BRIDGE-9)
     * @param idempotency the registry that makes the bridge idempotent per request id
     * @param sink        the port through which an accepted message reaches the store
     */
    public BridgeReceiver(BridgeSigner verifier, Set<BridgeCapability> granted, BridgeBudget budget,
                           BridgeIdempotency idempotency, BridgeSink sink) {
        this(verifier, granted, budget, idempotency, sink, new BridgePayloadCodec());
    }

    /**
     * A receiver with the given parts and an explicit codec.
     *
     * @param verifier    the signer that verifies incoming messages
     * @param granted     the rights this bridge grants to its senders
     * @param budget      the budget enforced before the store is touched
     * @param idempotency the registry that makes the bridge idempotent per request id
     * @param sink        the port through which an accepted message reaches the store
     * @param codec       the codec that reads and writes the payload
     */
    public BridgeReceiver(BridgeSigner verifier, Set<BridgeCapability> granted, BridgeBudget budget,
                           BridgeIdempotency idempotency, BridgeSink sink, BridgePayloadCodec codec) {
        if (budget == null) {
            throw new IllegalArgumentException("a bridge receiver needs a budget");
        }
        if (idempotency == null) {
            throw new IllegalArgumentException("a bridge receiver needs an idempotency registry");
        }
        if (sink == null) {
            throw new IllegalArgumentException("a bridge receiver needs a sink");
        }
        if (codec == null) {
            throw new IllegalArgumentException("a bridge receiver needs a payload codec");
        }
        this.verifier = verifier;
        this.granted = granted == null ? Set.of() : Set.copyOf(granted);
        this.budget = budget;
        this.idempotency = idempotency;
        this.sink = sink;
        this.codec = codec;
    }

    /**
     * The rights this bridge grants (BRIDGE-5).
     *
     * @return the granted rights
     */
    public Set<BridgeCapability> grantedCapabilities() {
        return granted;
    }

    /**
     * The audit chain this bridge has written (BRIDGE-7).
     *
     * <p>A copy, so that a caller cannot change the chain by changing the list it was
     * handed.</p>
     *
     * @return the records, oldest first
     */
    public List<BridgeAuditRecord> auditLog() {
        return List.copyOf(auditLog);
    }

    /**
     * The outcome of one message (BRIDGE-2 … BRIDGE-9).
     *
     * @param envelope   the message
     * @param nowMillis  the instant of the delivery, in epoch milliseconds
     * @return the decision
     */
    public BridgeDecision receive(BridgeEnvelope envelope, long nowMillis) {
        return receiveAnswer(envelope, nowMillis).decision();
    }

    /**
     * W52 — the answer to a message: the decision, and what the receiver did with it.
     *
     * <p>The effect is carried through the idempotency registry, so a message that arrives
     * twice answers with the FIRST answer — the same decision and the same effect — and
     * does nothing the second time (BRIDGE-8). An effect that a retry could produce twice
     * would break the property the retry exists to protect.</p>
     *
     * @param envelope   the message
     * @param nowMillis  the instant of the delivery, in epoch milliseconds
     * @return the answer
     */
    public BridgeAnswer receiveAnswer(BridgeEnvelope envelope, long nowMillis) {
        if (envelope == null) {
            throw new IllegalArgumentException("there is no message to receive");
        }
        Optional<BridgeIdempotency.Outcome> known = idempotency.find(envelope.requestId());
        if (known.isPresent()) {
            return replay(envelope, known.get(), nowMillis);
        }
        String digest = envelope.payloadDigest();
        BridgeIdempotency.Outcome outcome = idempotency.firstOutcome(envelope.requestId(), digest,
                () -> {
                    BridgeAnswer answer = evaluate(envelope, nowMillis);
                    auditLog.add(BridgeAuditRecord.of(answer.decision(), envelope, at(nowMillis)));
                    return new BridgeIdempotency.Outcome(answer.decision(), digest, answer.outcome());
                });
        return new BridgeAnswer(outcome.decision(), outcome.effect());
    }

    /**
     * The outcome of a delivery that carries several messages (BRIDGE-9).
     *
     * <p>The size of the batch is checked before anything is written, so an oversized batch
     * has no effect at all. A message whose request id was already decided is answered from
     * the registry even then: a retry must not become a refusal because it arrived in a large
     * batch (BRIDGE-8).</p>
     *
     * @param batch      the messages
     * @param nowMillis  the instant of the delivery, in epoch milliseconds
     * @return one decision per message, in the order of the batch
     */
    public List<BridgeDecision> receiveBatch(List<BridgeEnvelope> batch, long nowMillis) {
        if (batch == null || batch.isEmpty()) {
            return List.of();
        }
        boolean oversized = batch.size() > budget.maxBatch();
        List<BridgeDecision> decisions = new ArrayList<>(batch.size());
        for (BridgeEnvelope envelope : batch) {
            if (!oversized) {
                decisions.add(receive(envelope, nowMillis));
                continue;
            }
            Optional<BridgeIdempotency.Outcome> known = idempotency.find(envelope.requestId());
            if (known.isPresent()) {
                decisions.add(replay(envelope, known.get(), nowMillis).decision());
                continue;
            }
            decisions.add(refuseOnce(envelope, nowMillis, BridgeRefusalReason.BATCH_TOO_LARGE,
                    "the batch carries " + batch.size() + " messages and the limit is "
                            + budget.maxBatch()));
        }
        return List.copyOf(decisions);
    }

    /**
     * The answer to a message that has already been decided (BRIDGE-8).
     *
     * <p>Same request id, same payload: the first outcome, and no second effect. Same
     * request id, different payload: a conflict, refused and audited.</p>
     */
    private BridgeAnswer replay(BridgeEnvelope envelope, BridgeIdempotency.Outcome known,
                                 long nowMillis) {
        if (known.payloadDigest().equals(envelope.payloadDigest())) {
            return new BridgeAnswer(known.decision(), known.effect());
        }
        String auditId = nextAuditId(envelope.requestId());
        BridgeDecision decision = BridgeDecision.refused(envelope.requestId(), auditId,
                BridgeRefusalReason.REQUEST_ID_CONFLICT,
                "the request id already carries a different payload");
        auditLog.add(BridgeAuditRecord.of(decision, envelope, at(nowMillis)));
        return BridgeAnswer.of(decision);
    }

    /**
     * The answer to a message that is refused before the checks run (BRIDGE-6, BRIDGE-9).
     *
     * <p>The refusal is registered as the outcome of the request id, so that a retry of the
     * same call gets the same answer rather than a second attempt.</p>
     */
    private BridgeDecision refuseOnce(BridgeEnvelope envelope, long nowMillis,
                                      BridgeRefusalReason reason, String detail) {
        String digest = envelope.payloadDigest();
        BridgeIdempotency.Outcome outcome = idempotency.firstOutcome(envelope.requestId(), digest,
                () -> {
                    String auditId = nextAuditId(envelope.requestId());
                    BridgeDecision decision =
                            BridgeDecision.refused(envelope.requestId(), auditId, reason, detail);
                    auditLog.add(BridgeAuditRecord.of(decision, envelope, at(nowMillis)));
                    return BridgeIdempotency.Outcome.of(decision, digest);
                });
        return outcome.decision();
    }

    /**
     * The decision on a message that has not been decided yet.
     *
     * <p>The order of the checks is the one this class's documentation gives, and every
     * check is a requirement of the contract. The budget is last, because BRIDGE-9 asks for
     * the budget to be enforced "before the store is touched" and not before the message is
     * understood: a bridge that spent a slot of its budget on a forged message would be
     * letting an unauthenticated sender exhaust the quota of an honest one.</p>
     */
    private BridgeAnswer evaluate(BridgeEnvelope envelope, long nowMillis) {
        String auditId = nextAuditId(envelope.requestId());

        // BRIDGE-1 and BRIDGE-2 — the protocol and its version.
        if (!BridgeProtocol.NAME.equals(envelope.protocol())) {
            return refusedAnswer(auditId, envelope, BridgeRefusalReason.UNKNOWN_PROTOCOL,
                    "the message names the protocol " + envelope.protocol());
        }
        if (!BridgeProtocol.supportedVersion(envelope.version())) {
            return refusedAnswer(auditId, envelope, BridgeRefusalReason.UNSUPPORTED_VERSION,
                    "the message carries the version " + envelope.version()
                            + " and this reader speaks " + BridgeProtocol.VERSION);
        }

        // BRIDGE-5 — the right the sender asks for must be one this bridge granted.
        if (!envelope.kind().grantedBy(granted)) {
            return refusedAnswer(auditId, envelope, BridgeRefusalReason.CAPABILITY_NOT_GRANTED,
                    "the bridge did not grant " + envelope.kind());
        }

        // BRIDGE-3 — the call is authenticated, or it is refused.
        if (verifier == null) {
            return refusedAnswer(auditId, envelope, BridgeRefusalReason.NO_SECRET_CONFIGURED,
                    "no verification secret is configured, so nothing can be trusted");
        }
        if (!envelope.verify(verifier)) {
            return refusedAnswer(auditId, envelope, BridgeRefusalReason.SIGNATURE_INVALID,
                    "the signature does not verify under the configured secret");
        }

        // BRIDGE-4 and BRIDGE-5 — the payload is readable, agrees with the envelope and
        // carries the provenance of the delivery.
        BridgePayload payload;
        try {
            payload = codec.decode(envelope.payload());
        } catch (BridgeProtocolException e) {
            return refusedAnswer(auditId, envelope, e.reason(), e.getMessage());
        }
        if (payload.kind() != envelope.kind()) {
            return refusedAnswer(auditId, envelope, BridgeRefusalReason.PAYLOAD_KIND_MISMATCH,
                    "the payload carries " + payload.kind() + " and the envelope declares "
                            + envelope.kind());
        }
        if (!payload.hasProvenance()) {
            return refusedAnswer(auditId, envelope, BridgeRefusalReason.MISSING_PROVENANCE,
                    "the delivery names neither its origin nor its instant");
        }

        // BRIDGE-9 — the budget is enforced before the store is touched.
        BridgeBudget.Verdict verdict = budget.check(envelope, 1, nowMillis);
        if (!verdict.allowed()) {
            return refusedAnswer(auditId, envelope, verdict.reason(), verdict.detail());
        }

        // The effect. At most once per request id, because the idempotency registry runs
        // this supplier at most once for a request id (BRIDGE-8). The sink may report what
        // it did, and that report travels back with the answer (W52).
        io.matrix.contract.Outcome effect = sink.accept(envelope);
        return new BridgeAnswer(BridgeDecision.accepted(envelope.requestId(), auditId), effect);
    }

    /**
     * A refusal, wrapped in the answer the sender receives.
     *
     * @param auditId  the audit id of the refusal
     * @param envelope the refused message
     * @param reason   the reason the contract names
     * @param detail   what the receiver says about it
     * @return the answer
     */
    private BridgeAnswer refusedAnswer(String auditId, BridgeEnvelope envelope,
                                        BridgeRefusalReason reason, String detail) {
        return BridgeAnswer.of(refused(auditId, envelope, reason, detail));
    }

    /**
     * A refusal, built with the reason the contract names.
     */
    private BridgeDecision refused(String auditId, BridgeEnvelope envelope,
                                   BridgeRefusalReason reason, String detail) {
        return BridgeDecision.refused(envelope.requestId(), auditId, reason, detail);
    }

    /**
     * The identifier of the next audit record.
     */
    private String nextAuditId(String requestId) {
        return BridgeAuditRecord.auditIdFor(requestId, auditSequence.incrementAndGet());
    }

    /**
     * The instant of a decision, as the record carries it.
     *
     * <p>UTC, so that the record of the same call is the same string on every host.</p>
     */
    private String at(long nowMillis) {
        return Instant.ofEpochMilli(nowMillis).toString();
    }
}
