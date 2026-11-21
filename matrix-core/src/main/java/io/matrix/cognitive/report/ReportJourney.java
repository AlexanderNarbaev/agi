package io.matrix.cognitive.report;

import io.matrix.actions.ArtifactWriter;
import io.matrix.knowledge.AbstractionLevel;
import io.matrix.knowledge.Answer;
import io.matrix.knowledge.KnowledgeWorkbench;

import java.util.List;

/**
 * P2 — the closed loop's problem and its result.
 *
 * <p><b>What this is.</b> The product's one real action — the bounded artifact write — had
 * no method of its own: the route asked the workbench and called the writer directly, so
 * nothing in the product chose a method, recorded why it chose it, or consulted what
 * happened last time. A route that always behaves the same is a service, not a cognitive
 * loop. This record is the family payload the portfolio's methods share: the question, the
 * workspace it is asked over, the identity the action is bound to, and the workspace the
 * journey may consult.</p>
 *
 * <p><b>What it is not.</b> The payload carries no authority. It cannot widen the consent,
 * the output-root confinement or the writer's lease — those are the action's own rules and
 * they are checked before the journey starts. A plan can only decide HOW the journey runs,
 * never WHETHER it is allowed to.</p>
 *
 * @see ReportJourneyMethod
 * @see ReportJourneyPlanner
 */
public final class ReportJourney {

    private ReportJourney() {
    }

    /**
     * The problem the family solves: one question over one workspace, into one artifact.
     *
     * @param workspaceId the workspace the question is asked over
     * @param question    the question
     * @param identity    the action's identity — the writer's own derivation of it
     * @param reportName  the plain file name the artifact carries
     * @param level       the abstraction level the workspace answers at
     * @param workbench   the workspace the question is asked of
     * @param writer      the bounded writer, over the server's own output root
     * @param retained    what the last journey for this identity left behind, or null
     * @param workspaceFingerprint the fingerprint of the workspace as it is now
     */
    public record Problem(
            String workspaceId,
            String question,
            String identity,
            String reportName,
            AbstractionLevel level,
            KnowledgeWorkbench workbench,
            ArtifactWriter writer,
            Retained retained,
            String workspaceFingerprint,
            List<LearnedClaim> learnedClaims) {

        public Problem {
            if (workspaceId == null || workspaceId.isBlank()) {
                throw new IllegalArgumentException("the workspace must not be blank");
            }
            if (identity == null || identity.isBlank()) {
                throw new IllegalArgumentException("the action identity must not be blank");
            }
            learnedClaims = List.copyOf(learnedClaims == null ? List.of() : learnedClaims);
        }

        /** A problem without any learned claim — the shape a caller that knows of none uses. */
        public Problem(String workspaceId, String question, String identity, String reportName,
                       AbstractionLevel level, KnowledgeWorkbench workbench, ArtifactWriter writer,
                       Retained retained, String workspaceFingerprint) {
            this(workspaceId, question, identity, reportName, level, workbench, writer, retained,
                    workspaceFingerprint, List.of());
        }
    }

    /**
     * What the last journey for this identity left behind.
     *
     * <p>This is the trace consulted before the method is chosen. It holds the method that
     * ran, the fingerprint of the workspace the answer came from, the digest of the artifact
     * and the response that was served — so that a later analogous task can be answered from
     * the retained evidence instead of from the workspace, and so that the reply can say
     * that it was replayed.</p>
     *
     * <p>The QUESTION and the LEVEL are part of it because the replay's applicability domain
     * is the REQUEST, not the identity. An answer retrieved for one question may only serve
     * that question, and an answer retrieved at one level may only serve that level: the
     * identity names the action, and the action's content is the answer to a specific
     * question. A retained journey that does not name its question — a record written before
     * this rule existed — names no question, and the replay refuses it rather than guessing
     * which question it answers.</p>
     *
     * @param methodId            the method that produced the retained journey
     * @param question            the question that was asked, or null when the record does not name it
     * @param level               the abstraction level the answer was retrieved at, or null when
     *                            the record does not name it
     * @param workspaceFingerprint the fingerprint of the workspace the answer came from
     * @param sha256              the digest of the committed artifact
     * @param fileName            the artifact's plain file name
     * @param responseJson        the response that was served, as JSON
     */
    public record Retained(
            String methodId,
            String question,
            String level,
            String workspaceFingerprint,
            String sha256,
            String fileName,
            String responseJson) {

        public Retained {
            if (methodId == null || methodId.isBlank()) {
                throw new IllegalArgumentException("the retained method must not be blank");
            }
        }
    }

    /**
     * What a method produced.
     *
     * <p>The answer is null on the replay path: the workspace was not asked, because the
     * retained journey already holds what it said. The bytes are the artifact's content
     * either way, and the writer's own result says what happened to the file — written,
     * or replayed.</p>
     *
     * @param answer               the workspace's answer, or null on the replay path
     * @param content              the artifact's bytes
     * @param write                the writer's own result
     * @param workspaceFingerprint the fingerprint of the workspace the answer came from
     * @param replayed             whether the journey replayed a retained one
     */
    public record Result(
            Answer answer,
            byte[] content,
            ArtifactWriter.Result write,
            String workspaceFingerprint,
            boolean replayed) {

        public Result {
            content = content == null ? new byte[0] : content.clone();
        }

        @Override
        public byte[] content() {
            return content.clone();
        }
    }
}
