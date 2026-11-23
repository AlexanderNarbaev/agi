package io.matrix.cognitive.report;

import io.matrix.actions.ArtifactWriter;
import io.matrix.cognitive.algorithms.AlgorithmCapability;
import io.matrix.cognitive.algorithms.AlgorithmFamilies;
import io.matrix.cognitive.algorithms.AlgorithmMethod;
import io.matrix.cognitive.algorithms.AlgorithmRefusalException;
import io.matrix.cognitive.algorithms.ComputeBudget;
import io.matrix.cognitive.algorithms.DeclaredCost;
import io.matrix.knowledge.Answer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Set;

/**
 * P2 — the two methods the report journey offers to the selector.
 *
 * <p><b>The reference method</b> is the journey the product already has: ask the workspace,
 * assemble the report from what it said, hand it to the bounded writer. It is always
 * admissible, and it is the fallback the other method names.</p>
 *
 * <p><b>The replay method</b> is the cheap path, and it is the one that makes the loop a
 * loop. It is admissible only inside a domain the trace establishes: the identity was
 * already committed, the workspace has not changed since, and the requested file name is
 * the one that was committed. Inside that domain it serves the retained answer and the
 * retained artifact instead of asking the workspace again — and the artifact it serves is
 * read from the disk and checked against the digest that was recorded, so a replay is
 * verified rather than remembered.</p>
 *
 * <p><b>The applicability domain is the precondition.</b> The domain is expressed as the
 * facts the request carries, and the selector enforces it: a request that does not
 * establish the domain cannot select the replay, and the reference method takes over.
 * That is the mechanism the portfolio already has for a cached procedure, reused here
 * because the property is the same one — a cheap path is reusable only inside a domain
 * somebody verified.</p>
 *
 * <p><b>What the replay does not do.</b> It does not re-implement the workspace's answer,
 * and it does not trust the trace: the artifact is read from the file system and measured
 * again. A trace that says the artifact is there and an artifact that is not there are two
 * different facts, and the method believes the second one.</p>
 */
public final class ReportJourneyMethod
        implements AlgorithmMethod<ReportJourney.Problem, ReportJourney.Result>,
        ReportJourneyPlanner.JourneyMethod {

    /** The capability id of the reference method. */
    public static final String REFERENCE_ID = "report.journey.reference.v1";

    /** The capability id of the replay method. */
    public static final String REPLAY_ID = "report.journey.replay.v1";

    /** The version of both methods. */
    public static final String VERSION = "1.0.0";

    /**
     * The fact: the action's identity was already committed to the output root.
     *
     * <p>It is established by reading the writer's append-only index, which is where the
     * commit lives — not by the trace, and not by the action's own report.</p>
     */
    public static final String FACT_IDENTITY_COMMITTED = "report.identity-committed";

    /**
     * The fact: the workspace has not changed since the retained journey.
     *
     * <p>It is established by comparing the workspace's fingerprint with the one the
     * retained journey recorded. A workspace that changed may answer differently, and an
     * answer from the older workspace would be an answer to a question that was not
     * asked.</p>
     */
    public static final String FACT_WORKSPACE_UNCHANGED = "report.workspace-unchanged";

    /**
     * The fact: the requested report name is the one that was committed.
     *
     * <p>Otherwise the request asks for a different artifact under the same identity, and
     * the writer's rule — one identity, one artifact — turns it into a refusal. The
     * replay must not turn that refusal into a silent answer.</p>
     */
    public static final String FACT_NAME_UNCHANGED = "report.name-unchanged";

    /**
     * The fact: the question is the one the retained journey answered.
     *
     * <p>The identity names the ACTION, and the action's content is the answer to a
     * specific question. A reused idempotency key with a different question is a different
     * request under the same identity: the retained answer does not answer it, and
     * serving it anyway would report a success for a question nobody asked. The fact is
     * established by comparing the request's question with the one the retained journey
     * recorded — a retained journey that names no question establishes it for nobody.</p>
     */
    public static final String FACT_QUESTION_UNCHANGED = "report.question-unchanged";

    /**
     * The fact: the answer was retrieved at the level the request asks for.
     *
     * <p>The level is part of the request, and the workspace answers a question at a
     * level: an answer retrieved at one level is not the answer another level would
     * have produced. The fact is established by comparing the request's level with the one
     * the retained journey recorded.</p>
     */
    public static final String FACT_LEVEL_UNCHANGED = "report.level-unchanged";

    /** The declared cost of the reference method, in milliseconds. */
    public static final long REFERENCE_DECLARED_COST_MILLIS = 25L;

    /** The declared cost of the replay method, in milliseconds. */
    public static final long REPLAY_DECLARED_COST_MILLIS = 1L;

    /**
     * The number of cases the replay's agreement with the reference was measured over.
     *
     * <p>MEASURED: the replay validation corpus of the closed-loop test — the same request
     * run twice, and the two answers compared.</p>
     */
    public static final int REPLAY_VALIDATION_CASES = 3;

    /** When the replay's agreement was measured. */
    public static final String REPLAY_MEASURED_AT = "2026-11-16";

    private final String id;
    private final boolean replay;
    private final AlgorithmCapability capability;

    private ReportJourneyMethod(String id, boolean replay, Set<String> preconditions,
            long declaredCostMillis, AlgorithmCapability.AccuracyProfile accuracy) {
        this.id = id;
        this.replay = replay;
        this.capability = new AlgorithmCapability(
                id,
                VERSION,
                Set.of(AlgorithmFamilies.KNOWLEDGE_REPORT),
                preconditions,
                Set.of(ReportJourney.Problem.class.getName()),
                Set.of(ReportJourney.Result.class.getName()),
                new AlgorithmCapability.ComplexityProfile(
                        DeclaredCost.declare("O(claims) over the workspace's snapshot", declaredCostMillis,
                                0),
                        "O(claims) for the fingerprint"),
                new AlgorithmCapability.ResourceProfile(
                        "one workspace snapshot; the replay holds no snapshot of its own", true, false,
                        "the workspace cannot be read: the journey is refused rather than "
                                + "answered from a guess"),
                accuracy,
                new AlgorithmCapability.DeterminismProfile(true,
                        "none: the journey has no randomness and reads the workspace as it is"),
                new AlgorithmCapability.ReliabilityProfile(
                        "the artifact is missing or its digest disagrees: the effect is a "
                                + "failed effect and is recorded as such", false,
                        "the replay falls back to asking the workspace"),
                new AlgorithmCapability.VerificationStrategy(
                        "the artifact's size and SHA-256, measured on its bytes by a reader that "
                                + "did not write it",
                        "io.matrix.cognitive.report.ArtifactObserver.observe"),
                replay ? REFERENCE_ID : null);
    }

    /**
     * The reference method: ask the workspace, assemble the report, write it.
     *
     * @return the method
     */
    public static ReportJourneyMethod reference() {
        return new ReportJourneyMethod(REFERENCE_ID, false, Set.of(),
                REFERENCE_DECLARED_COST_MILLIS,
                new AlgorithmCapability.AccuracyProfile(
                        "the journey the product has always taken", 1.0, 1,
                        "the product's own report route", REPLAY_MEASURED_AT));
    }

    /**
     * The replay method: serve the retained journey, inside the domain the trace proves.
     *
     * @return the method
     */
    public static ReportJourneyMethod replay() {
        return new ReportJourneyMethod(REPLAY_ID, true,
                Set.of(FACT_IDENTITY_COMMITTED, FACT_WORKSPACE_UNCHANGED, FACT_NAME_UNCHANGED,
                        FACT_QUESTION_UNCHANGED, FACT_LEVEL_UNCHANGED),
                REPLAY_DECLARED_COST_MILLIS,
                new AlgorithmCapability.AccuracyProfile(
                        "the agreement with the reference journey's answer", 1.0,
                        REPLAY_VALIDATION_CASES,
                        "the closed-loop replay validation corpus", REPLAY_MEASURED_AT));
    }

    /** The capability the selector reads. */
    public AlgorithmCapability capability() {
        return capability;
    }

    /** Whether this is the replay method. */
    public boolean isReplay() {
        return replay;
    }

    @Override
    public boolean preconditionsHold(ReportJourney.Problem input) {
        if (input == null) {
            return false;
        }
        if (!replay) {
            return true;
        }
        ReportJourney.Retained retained = input.retained();
        if (retained == null) {
            return false;
        }
        if (!retained.fileName().equals(input.reportName())) {
            return false;
        }
        if (!retained.workspaceFingerprint().equals(input.workspaceFingerprint())) {
            return false;
        }
        // the retained answer answers ONE question at ONE level. A record that names
        // neither cannot state which question it answers, so it is not replayable: the
        // method refuses it instead of serving an answer of unknown provenance.
        if (retained.question() == null || !retained.question().equals(input.question())) {
            return false;
        }
        if (retained.level() == null
                || !retained.level().equals(input.level().name())) {
            return false;
        }
        return committed(input.writer(), input.identity());
    }

    @Override
    public ReportJourney.Result execute(ReportJourney.Problem input, ComputeBudget budget) {
        if (!preconditionsHold(input)) {
            // a method that cannot honour its declared preconditions REFUSES; it does not
            // run anyway and it does not fall back to another method behind the caller's
            // back. Forcing a method does not change that.
            throw new AlgorithmRefusalException("the journey's preconditions do not hold for "
                    + "this request: the method cannot run inside its applicability domain");
        }
        return replay ? replay(input) : reference(input);
    }

    private ReportJourney.Result reference(ReportJourney.Problem input) {
        var snapshot = input.selectedEvidence();
        Answer answer = input.workbench().askInView(snapshot, input.question(), input.level(), Set.of(), input.historical());
        // the learned claims of the workspace join the evidence: a claim the system has
        // validated belongs in the evidence of a question about its concept, even when the
        // question's words did not point at it
        answer = answer.withAdditionalClaims(LearnedClaims.additions(
                snapshot, answer, input.learnedClaims()));
        byte[] content = ReportBuilder.markdown(input.workspaceId(), input.question(), answer)
                .getBytes(StandardCharsets.UTF_8);
        ArtifactWriter.Result write = input.writer().write(input.identity(), input.reportName(),
                content);
        return new ReportJourney.Result(answer, content, write, input.workspaceFingerprint(),
                false);
    }

    private ReportJourney.Result replay(ReportJourney.Problem input) {
        ReportJourney.Retained retained = input.retained();
        Path artifact = input.writer().root().resolve(retained.fileName());
        byte[] content;
        try {
            content = Files.readAllBytes(artifact);
        } catch (IOException e) {
            throw new IllegalStateException("the retained artifact could not be read", e);
        }
        if (!retained.sha256().equals(ArtifactObserver.sha256(content))) {
            throw new IllegalStateException("the retained artifact does not carry the digest the "
                    + "trace recorded");
        }
        ArtifactWriter.Result write = input.writer().write(input.identity(), retained.fileName(),
                content);
        return new ReportJourney.Result(null, content, write, input.workspaceFingerprint(), true);
    }

    /**
     * Whether the writer's index holds a committed record for the identity.
     *
     * @param writer   the bounded writer
     * @param identity the action identity
     * @return true when the artifact was committed
     */
    public static boolean committed(ArtifactWriter writer, String identity) {
        try {
            for (ArtifactWriter.Record record : writer.readIndex()) {
                if (record.actionId().equals(identity) && "COMMITTED".equals(record.status())) {
                    return true;
                }
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }

    /**
     * Whether the artifact the writer committed is still there.
     *
     * @param writer   the bounded writer
     * @param fileName the artifact's plain file name
     * @return true when the file is there
     */
    public static boolean present(ArtifactWriter writer, String fileName) {
        return Files.isRegularFile(writer.root().resolve(fileName), LinkOption.NOFOLLOW_LINKS);
    }
}
