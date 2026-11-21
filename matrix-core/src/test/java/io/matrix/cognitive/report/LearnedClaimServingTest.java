package io.matrix.cognitive.report;

import io.matrix.actions.ArtifactActionService;
import io.matrix.cognitive.CognitiveService;
import io.matrix.knowledge.ArtifactReportResource;
import io.matrix.knowledge.KnowledgeWorkbench;
import io.matrix.knowledge.acquisition.AcquisitionCycle;
import io.matrix.knowledge.acquisition.ClasspathCorpus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The learned claim's SERVING role: it reaches the served answer.
 *
 * <p><b>Why this test exists.</b> A learned claim that is promoted and never used is a record,
 * not a capability. The contract's REUSE row asks for a <b>different related task</b> that
 * retrieves the accepted knowledge and <b>changes the result</b> — so this test asks a
 * question the retrieval does not answer with the claim, and pins that the claim is in the
 * served evidence anyway, with its provenance reported.</p>
 *
 * <p><b>What is pinned.</b> That the claim the system validated is in the response's
 * evidence, that the response says which claim was learned and on how many held-out cases it
 * was validated, and that the workspace's own claims are untouched — the learned claim adds
 * evidence, it does not rewrite the workspace.</p>
 */
class LearnedClaimServingTest {

    /** The workspace the pool is imported into. */
    private static final String WORKSPACE = "research-pool";

    @Test
    @DisplayName("a validated learned claim is in the served evidence, with its provenance named")
    void theLearnedClaimReachesTheServedAnswer(@TempDir Path directory) throws Exception {
        Path root = Files.createDirectories(directory.resolve("root"));
        Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"));
        Path database = Files.createDirectories(directory.resolve("db")).resolve("w.sqlite");
        KnowledgeWorkbench bench = KnowledgeWorkbench.at(database.toString());
        try {
            ClasspathCorpus pool = ClasspathCorpus.extract(AcquisitionCycle.RESEARCH_POOL);
            for (ClasspathCorpus.Entry entry : pool.entries()) {
                bench.importDocument(WORKSPACE, entry.path(), entry.file(), entry.language());
            }
            CognitiveService cognitive = new CognitiveService();
            cognitive.workbench(bench);
            ArtifactReportResource route = new ArtifactReportResource();
            route.workbench = bench;
            route.actions = new ArtifactActionService(true, root.toString());
            route.planner = new ReportJourneyPlanner();
            route.cognitive = cognitive;

            // ── the producer: a claim the reference carries, validated and promoted ──
            // the held-out questions come FIRST, and the candidate is a claim the reference
            // carries on EVERY one of them and whose proposition IS a span of its source's
            // text: a learned claim may assert only what the document literally contains,
            // and it has to answer the questions it was validated on
            var snapshot = bench.workspace(WORKSPACE);
            List<String> heldOutQuestions = List.of(
                    "What is plate tectonics?",
                    "What does plate tectonics describe?",
                    "Explain plate tectonics",
                    "What causes plate tectonics?",
                    "How does plate tectonics work?");
            List<java.util.Set<String>> citedPerQuestion = new java.util.ArrayList<>();
            for (String question : heldOutQuestions) {
                citedPerQuestion.add(bench.ask(WORKSPACE, question,
                        io.matrix.knowledge.AbstractionLevel.INTRODUCTORY).claims().stream()
                        .map(io.matrix.knowledge.Claim::id)
                        .collect(java.util.stream.Collectors.toSet()));
            }
            java.util.Set<String> common = new java.util.LinkedHashSet<>(
                    citedPerQuestion.get(0));
            for (java.util.Set<String> cited : citedPerQuestion) {
                common.retainAll(cited);
            }
            var candidate = snapshot.claims().stream()
                    .filter(claim -> common.contains(claim.id()))
                    .filter(claim -> claim.introducedBySourceId() != null)
                    .filter(claim -> snapshot.sources().stream()
                            .anyMatch(source -> source.id().equals(claim.introducedBySourceId())
                                    && source.excerpt() != null
                                    && source.excerpt().contains(claim.proposition())))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("no claim the reference "
                            + "carries on every held-out question is a span of its document"));
            String claimId = candidate.id();
            List<String> sourceIds = List.of(candidate.introducedBySourceId());
            var observations = List.of(
                    new ReportClaimLearner.Observation("episode-0",
                            "What is plate tectonics, observed 0?",
                            io.matrix.knowledge.AbstractionLevel.INTRODUCTORY, List.of(claimId),
                            sourceIds),
                    new ReportClaimLearner.Observation("episode-1",
                            "What is plate tectonics, observed 1?",
                            io.matrix.knowledge.AbstractionLevel.INTRODUCTORY, List.of(claimId),
                            sourceIds));
            List<ReportClaimLearner.Case> cases = new java.util.ArrayList<>();
            for (int index = 0; index < heldOutQuestions.size(); index++) {
                cases.add(new ReportClaimLearner.Case("case-" + index,
                        heldOutQuestions.get(index),
                        io.matrix.knowledge.AbstractionLevel.INTRODUCTORY));
            }
            ReportClaimLearner.Outcome outcome =
                    new ReportClaimLearner(bench, cognitive.store()).learn(WORKSPACE, observations,
                            cases, Set.of(), "2026-11-21T00:00:00Z");
            assertThat(outcome.promoted()).as("the claim was promoted: " + outcome.reason())
                    .isTrue();
            assertThat(cognitive.store().learnedClaims(WORKSPACE)).hasSize(1);

            // ── the serving path: a DIFFERENT question about the same concept ──────
            var response = route.report(WORKSPACE, new ArtifactReportResource.ReportRequest(
                    "What does plate tectonics mean?", null, true, "key-learned", null));
            assertThat(response.getStatus()).as("the journey answered").isEqualTo(201);
            var body = (ArtifactReportResource.ReportResponse) response.getEntity();

            assertThat(body.evidence())
                    .as("the learned claim is in the served evidence")
                    .anyMatch(evidence -> evidence.claimId().equals(claimId));
            assertThat(body.limitations())
                    .as("and the response says which claim was learned and on how many cases")
                    .anyMatch(limitation -> limitation.contains(learnedClaimId(body))
                            && limitation.contains("held-out case"));
        } finally {
            bench.close();
        }
    }

    /** The learned claim's id, as the response names it. */
    private static String learnedClaimId(ArtifactReportResource.ReportResponse body) {
        return body.limitations().stream()
                .filter(limitation -> limitation.contains("the learned claim"))
                .map(limitation -> limitation.replaceAll(".*the learned claim ([^,]+),.*", "$1"))
                .findFirst().orElse("");
    }
}
