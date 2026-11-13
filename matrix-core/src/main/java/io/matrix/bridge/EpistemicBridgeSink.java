package io.matrix.bridge;

import io.matrix.contract.Belief;
import io.matrix.contract.Observation;
import io.matrix.contract.Provenance;
import io.matrix.contract.bridge.BridgeCapability;
import io.matrix.contract.bridge.BridgeEnvelope;
import io.matrix.contract.bridge.BridgePayload;
import io.matrix.contract.bridge.BridgePayloadCodec;
import io.matrix.contract.bridge.BridgeSink;
import io.matrix.contract.bridge.CognitiveTaskReport;
import io.matrix.knowledge.Claim;
import io.matrix.knowledge.ClaimStatus;
import io.matrix.knowledge.ClaimWarrant;
import io.matrix.knowledge.EvidenceRelation;
import io.matrix.knowledge.KnowledgeChanges;
import io.matrix.knowledge.KnowledgeStore;
import io.matrix.knowledge.KnowledgeWorkbench;
import io.matrix.knowledge.Source;
import io.matrix.knowledge.SourceKind;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.function.Supplier;

/**
 * W51 — where an accepted bridged message reaches the epistemic store.
 *
 * <p><b>Everything a bridged record claims about itself is a CANDIDATE.</b> The store's own
 * status vocabulary says that a claim extracted from a source and not yet validated by a
 * procedure is a candidate, and that is exactly what a report from another process is. The
 * sink therefore writes {@link ClaimStatus#CANDIDATE} and never anything else. It cannot
 * promote, and it has no code that could.</p>
 *
 * <p><b>The source is a report, not an authority.</b> The source of a bridged record is a
 * {@link SourceKind#SECONDARY_REPORT} — a report of something else — with an authority that
 * says "nobody has checked it" and a freshness that says "it was made just now". The
 * provenance the message carried travels with it: the origin, the instant and the sender.</p>
 *
 * <p><b>The identity of a bridged claim is derived from the request id.</b> This is what makes
 * the effect durable-idempotent (BRIDGE-8): a message that arrives twice asks for a claim
 * that already exists, and the sink recognises it and writes nothing. The check is against
 * the store, so it survives the death of the process — which an in-memory registry would
 * not.</p>
 *
 * <p><b>Nothing else is touched.</b> The sink adds a source, a candidate claim and an
 * evidence link. It never changes the status of an existing claim, never changes a
 * confidence, never changes a goal, a safety policy, a disposition or an adjudication.
 * BRIDGE-5 is the whole of the write surface.</p>
 */
final class EpistemicBridgeSink implements BridgeSink {

    private final KnowledgeWorkbench workbench;
    private final String workspaceId;
    private final Supplier<String> clock;
    private final BridgePayloadCodec codec = new BridgePayloadCodec();

    EpistemicBridgeSink(KnowledgeWorkbench workbench, String workspaceId, Supplier<String> clock) {
        this.workbench = workbench;
        this.workspaceId = workspaceId;
        this.clock = clock;
    }

    @Override
    public io.matrix.contract.Outcome accept(BridgeEnvelope envelope) {
        BridgePayload payload = codec.decode(envelope.payload());
        Provenance provenance = payload.provenance();
        String now = clock.get();
        switch (payload.kind()) {
            case OBSERVATION -> writeObservation(envelope, payload.observation(), provenance, now);
            case CANDIDATE -> writeCandidate(envelope, payload.candidate(), provenance, now);
            case EVIDENCE_LINK -> writeEvidenceLink(envelope, payload.evidenceLink(), provenance, now);
            case INFERENCE_RESPONSE -> writeTaskReport(envelope, payload.taskReport(),
                    provenance, now);
            default -> throw new IllegalStateException("the bridge cannot carry "
                    + payload.kind());
        }
        // The delivery direction has nothing to report back: the decision says the message
        // was accepted, and the effect is in the epistemic store, which the sender cannot
        // and must not read.
        return null;
    }

    private void writeObservation(BridgeEnvelope envelope, Observation observation,
                                  Provenance provenance, String now) {
        if (observation == null) {
            return;
        }
        String claimId = claimIdFor(envelope.requestId());
        if (alreadyApplied(claimId)) {
            return;
        }
        String sourceId = sourceIdFor(envelope.requestId());
        String proposition = propositionOf(observation);
        Source source = new Source(sourceId, workspaceId, SourceKind.SECONDARY_REPORT,
                "the report of " + envelope.senderId(), locatorOf(envelope, provenance),
                BridgeService.BRIDGED_REPORT_AUTHORITY, BridgeService.BRIDGED_REPORT_FRESHNESS,
                proposition, now, digestOf(proposition), null, null, "text");
        Claim claim = new Claim(claimId, workspaceId, conceptIdFor(envelope), proposition,
                scopeOf(provenance), null, ClaimStatus.CANDIDATE, 0.0, 1, sourceId,
                "reported by " + envelope.senderId(), "not validated", ClaimWarrant.NONE, null,
                now, now);
        io.matrix.knowledge.Evidence link = new io.matrix.knowledge.Evidence(
                evidenceIdFor(envelope.requestId()), workspaceId, claimId, sourceId,
                EvidenceRelation.SUPPORTS, proposition, BridgeService.BRIDGED_OBSERVATION_QUALITY,
                now);
        workbench.storeForValidation().applyChanges(workspaceId,
                new KnowledgeChanges(List.of(source), List.of(claim), List.<io.matrix.knowledge.Evidence>of(link),
                        List.of(), List.of()));
    }

    private void writeCandidate(BridgeEnvelope envelope, Belief candidate, Provenance provenance,
                                 String now) {
        if (candidate == null) {
            return;
        }
        String claimId = claimIdFor(envelope.requestId());
        if (alreadyApplied(claimId)) {
            return;
        }
        String sourceId = sourceIdFor(envelope.requestId());
        Source source = new Source(sourceId, workspaceId, SourceKind.SECONDARY_REPORT,
                "the report of " + envelope.senderId(), locatorOf(envelope, provenance),
                BridgeService.BRIDGED_REPORT_AUTHORITY, BridgeService.BRIDGED_REPORT_FRESHNESS,
                candidate.proposition(), now, digestOf(candidate.proposition()), null, null,
                "text");
        Claim claim = new Claim(claimId, workspaceId, conceptIdFor(envelope),
                candidate.proposition(), null, null, ClaimStatus.CANDIDATE, 0.0, 1, sourceId,
                "reported by " + envelope.senderId(), "not validated", ClaimWarrant.NONE, null,
                now, now);
        workbench.storeForValidation().applyChanges(workspaceId,
                new KnowledgeChanges(List.of(source), List.of(claim), List.of(), List.of(), List.of()));
    }

    private void writeEvidenceLink(BridgeEnvelope envelope, io.matrix.contract.Evidence link,
                                   Provenance provenance, String now) {
        if (link == null) {
            return;
        }
        String claimId = claimIdFor(envelope.requestId());
        if (alreadyApplied(claimId)) {
            return;
        }
        String sourceId = sourceIdFor(envelope.requestId());
        Source source = new Source(sourceId, workspaceId, SourceKind.SECONDARY_REPORT,
                "the report of " + envelope.senderId(), locatorOf(envelope, provenance),
                BridgeService.BRIDGED_REPORT_AUTHORITY, BridgeService.BRIDGED_REPORT_FRESHNESS,
                link.excerpt(), now, digestOf(link.excerpt()), null, null, "text");
        io.matrix.knowledge.Evidence evidence = new io.matrix.knowledge.Evidence(
                evidenceIdFor(envelope.requestId()), workspaceId, link.subjectId(), sourceId,
                EvidenceRelation.SUPPORTS, link.excerpt(), BridgeService.BRIDGED_OBSERVATION_QUALITY,
                now);
        workbench.storeForValidation().applyChanges(workspaceId,
                new KnowledgeChanges(List.of(source), List.<Claim>of(),
                        List.<io.matrix.knowledge.Evidence>of(evidence), List.of(), List.of()));
    }

    /**
     * W53 — writes the secondary report of a remote cognitive task.
     *
     * <p><b>Why the report is a candidate, like every other bridged record.</b> A report
     * from another process is a claim about something, made by somebody who has not been
     * checked here. It is written as a candidate and never as an established fact, however
     * confident the peer was: a confidence basis of {@code DECLARED} or {@code MEASURED}
     * is not independent evidence, and the store's own vocabulary says a candidate is a
     * candidate until a procedure validates it.</p>
     *
     * <p><b>Why the relation is the report's, not the sink's.</b> The research track on
     * cooperative reasoning found the defect this fixes: the sink used to write
     * {@code SUPPORTS} unconditionally, so a remote report that <i>denied</i> an
     * established belief could only ever arrive as support for a new candidate and never
     * reached the refutation path. The report now carries the relation, and the sink
     * writes what the peer actually said — {@code CONTRADICTS} included.</p>
     *
     * <p><b>Where a contradiction attaches.</b> If the report names the claim it is about
     * and that claim exists, the evidence attaches to that claim: the contradiction is
     * then inspectable against the belief it denies, which is the only way a downstream
     * assessor can see it. If the claim is unknown, the report is written as a candidate
     * about the proposition it names, and the evidence attaches to that candidate — the
     * report is still recorded, and nothing is invented.</p>
     *
     * <p><b>The lineage stays visible.</b> The records the peer derived the report from
     * travel in the claim's notes, so that the reader can tell a proposition that came
     * back from the peer from one the peer found on its own. The same information
     * returning is not a second, independent observation of it.</p>
     */
    private void writeTaskReport(BridgeEnvelope envelope, CognitiveTaskReport report,
                                 Provenance provenance, String now) {
        if (report == null) {
            return;
        }
        String claimId = claimIdFor(envelope.requestId());
        if (alreadyApplied(claimId)) {
            return;
        }
        String sourceId = sourceIdFor(envelope.requestId());
        Source source = new Source(sourceId, workspaceId, SourceKind.SECONDARY_REPORT,
                "the remote cognitive report of " + envelope.senderId(),
                locatorOf(envelope, provenance),
                BridgeService.BRIDGED_REPORT_AUTHORITY, BridgeService.BRIDGED_REPORT_FRESHNESS,
                report.subjectProposition(), now, digestOf(report.subjectProposition()),
                null, null, "text");
        Claim claim = new Claim(claimId, workspaceId, conceptIdFor(envelope),
                report.subjectProposition(), scopeOf(provenance), null, ClaimStatus.CANDIDATE,
                0.0, 1, sourceId, notesOf(report), "not validated",
                ClaimWarrant.NONE, null, now, now);
        EvidenceRelation relation = relationOf(report);
        io.matrix.knowledge.Evidence evidence = new io.matrix.knowledge.Evidence(
                evidenceIdFor(envelope.requestId()), workspaceId, claimId, sourceId, relation,
                report.subjectProposition(), BridgeService.BRIDGED_OBSERVATION_QUALITY, now);
        workbench.storeForValidation().applyChanges(workspaceId,
                new KnowledgeChanges(List.of(source), List.of(claim),
                        List.<io.matrix.knowledge.Evidence>of(evidence), List.of(), List.of()));
    }

    /**
     * The relation the peer stated, translated into the store's vocabulary.
     *
     * <p>A report that says neither — {@code INDEPENDENT} — is recorded as
     * {@code CONTEXTUAL}: the store's own word for "this source is about the subject but
     * does not bear on the claim". Writing support there would be exactly the defect this
     * wave is fixing, in the other direction.</p>
     */
    private static EvidenceRelation relationOf(CognitiveTaskReport report) {
        return switch (report.relation()) {
            case SUPPORTS -> EvidenceRelation.SUPPORTS;
            case CONTRADICTS -> EvidenceRelation.CONTRADICTS;
            case INDEPENDENT -> EvidenceRelation.CONTEXTUAL;
        };
    }

    /**
     * How the claim entered the store: the responder, the capability and the lineage the
     * peer declared.
     *
     * <p>The lineage lives here because this is the field that says where a claim came
     * from, and a claim that came back from the peer must say so — the reader has to be
     * able to tell a returning proposition from an independently observed one.</p>
     */
    private static String notesOf(CognitiveTaskReport report) {
        StringBuilder notes = new StringBuilder();
        notes.append("reported by ").append(report.responderId())
                .append(" through ").append(report.capabilityId());
        if (report.capabilityVersion() != null && !report.capabilityVersion().isBlank()) {
            notes.append(" version ").append(report.capabilityVersion());
        }
        if (report.derivedFrom() != null && !report.derivedFrom().isEmpty()) {
            notes.append("; derived from ").append(String.join(", ", report.derivedFrom()));
        }
        if (report.limitations() != null && !report.limitations().isEmpty()) {
            notes.append("; the peer says it does not cover ")
                    .append(String.join(", ", report.limitations()));
        }
        return notes.toString();
    }

    /**
     * True when the claim this request id would write is already in the store.
     *
     * <p>This is the durable idempotency of BRIDGE-8: the store itself remembers that the
     * effect happened.</p>
     */
    private boolean alreadyApplied(String claimId) {
        if (!workbench.hasWorkspace(workspaceId)) {
            return false;
        }
        KnowledgeStore store = workbench.storeForValidation();
        return store.claims(workspaceId).stream().anyMatch(claim -> claim.id().equals(claimId));
    }

    private static String propositionOf(Observation observation) {
        Object text = observation.payload().get("text");
        if (text != null) {
            return String.valueOf(text);
        }
        return observation.id();
    }

    private static String scopeOf(Provenance provenance) {
        if (provenance == null) {
            return null;
        }
        return provenance.origin();
    }

    private static String locatorOf(BridgeEnvelope envelope, Provenance provenance) {
        String origin = provenance == null || provenance.origin() == null
                ? envelope.senderId() : provenance.origin();
        return "the bridge report of " + envelope.senderId() + " at " + envelope.sentAt()
                + " from " + origin;
    }

    private static String conceptIdFor(BridgeEnvelope envelope) {
        return "cpt-bridge-" + shortDigest(envelope.senderId());
    }

    /**
     * The identity of the claim a request id produces.
     *
     * <p>Derived from the request id, so that the same request always asks for the same
     * claim and a repeated delivery is recognisable after a restart.</p>
     */
    static String claimIdFor(String requestId) {
        return BridgeService.BRIDGED_CLAIM_PREFIX + shortDigest(requestId);
    }

    /**
     * The identity of the source a request id produces.
     */
    static String sourceIdFor(String requestId) {
        return BridgeService.BRIDGED_SOURCE_PREFIX + shortDigest(requestId);
    }

    /**
     * The identity of the evidence link a request id produces.
     */
    static String evidenceIdFor(String requestId) {
        return "evd-bridge-" + shortDigest(requestId);
    }

    private static String digestOf(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the platform", e);
        }
    }

    /**
     * A short, stable digest of a string.
     *
     * <p>The length is the number of hex characters of the SHA-256 digest that are kept. It
     * is a named constant because it is a property of the identity scheme, not a tunable.</p>
     */
    private static final int SHORT_DIGEST_LENGTH = 16;

    private static String shortDigest(String value) {
        return digestOf(value).substring(0, SHORT_DIGEST_LENGTH);
    }
}
