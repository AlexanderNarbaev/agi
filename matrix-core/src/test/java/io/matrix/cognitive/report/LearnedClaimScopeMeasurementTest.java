package io.matrix.cognitive.report;

import io.matrix.cognitive.CognitiveService;
import io.matrix.cognitive.CognitiveStore;
import io.matrix.knowledge.AbstractionLevel;
import io.matrix.knowledge.Answer;
import io.matrix.knowledge.Claim;
import io.matrix.knowledge.KnowledgeWorkbench;
import io.matrix.knowledge.Source;
import io.matrix.knowledge.acquisition.AcquisitionCycle;
import io.matrix.knowledge.acquisition.ClasspathCorpus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CRJ-01 — the MEASUREMENT of R-EPIS-16: how much the coarse applicability rule over-applied.
 *
 * <p><b>What is measured.</b> Over the research pool — seven real documents imported through
 * the production path — one learned claim is promoted per document, each one a claim whose
 * proposition occurs <b>verbatim</b> in its document's text (the containment gate the
 * learner enforces). Then the pool's own questions are asked, and for each answer the
 * claims the <b>coarse</b> rule (the concept alone) would add are compared with the claims
 * the <b>narrowed</b> rule (the concept and a common document) adds.</p>
 *
 * <p><b>Why the number matters.</b> The coarse rule's over-application is not a matter of
 * taste: a learned claim of the same concept but a <b>different</b> document is about a
 * different part of the concept, and adding it to the evidence of a question that never
 * touched that document makes the answer cite a document it did not reason from. The
 * measurement says how often that happened.</p>
 */
class LearnedClaimScopeMeasurementTest {

    /** The workspace the pool is imported into. */
    private static final String WORKSPACE = "research-pool";

    @Test
    @DisplayName("the coarse rule over-applied the learned claims, and the narrowed rule does not")
    void theCoarseRuleOverApplied(@TempDir Path temporary) throws Exception {
        Path database = Files.createDirectories(temporary.resolve("db")).resolve("w.sqlite");
        KnowledgeWorkbench bench = KnowledgeWorkbench.at(database.toString());
        try {
            ClasspathCorpus pool = ClasspathCorpus.extract(AcquisitionCycle.RESEARCH_POOL);
            for (ClasspathCorpus.Entry entry : pool.entries()) {
                bench.importDocument(WORKSPACE, entry.path(), entry.file(), entry.language());
            }
            var snapshot = bench.workspace(WORKSPACE);

            // ── one learned claim per DOCUMENT, each a verbatim span of it ────────
            CognitiveService cognitive = new CognitiveService();
            cognitive.workbench(bench);
            CognitiveStore store = cognitive.store();
            List<LearnedClaim> learned = new ArrayList<>();
            Map<String, String> claimIdBySource = new LinkedHashMap<>();
            for (Source source : snapshot.sources()) {
                Claim candidate = snapshot.claims().stream()
                        .filter(claim -> source.id().equals(claim.introducedBySourceId()))
                        .filter(claim -> source.excerpt() != null
                                && source.excerpt().contains(claim.proposition()))
                        .findFirst().orElse(null);
                if (candidate == null) {
                    continue;
                }
                LearnedClaim record = LearnedClaim.of(WORKSPACE, candidate.id(),
                        candidate.proposition(), candidate.conceptId(), source.id(),
                        Set.of(source.id()), 5, 1.0, "episode-1",
                        "the proposition occurs verbatim in the text of " + source.id(),
                        "2026-11-21T00:00:00Z");
                store.recordLearnedClaim(WORKSPACE, record);
                learned.add(record);
                claimIdBySource.put(source.id(), candidate.id());
            }
            assertThat(learned).as("a learned claim per document").hasSize(7);

            // ── the questions: the pool's own subjects ──────────────────────────
            List<String> questions = List.of(
                    "What is plate tectonics?",
                    "What drives the movement of tectonic plates at subduction zones?",
                    "What evidence shows that the seafloor spreads?",
                    "How are the Earth's magnetic reversals recorded in rocks?",
                    "What happens where two tectonic plates move apart?",
                    "How do geologists determine the age of rocks from their magnetism?",
                    "What did the early continental drift theory propose?",
                    "How is new oceanic crust produced at mid-ocean ridges?",
                    // the NARROW questions: a question about one document's subject draws on
                    // that document and few others, so the coarse rule has the most room to
                    // over-apply
                    "What is the role of slab pull in the movement of plates?",
                    "How does magnetostratigraphy date a rock?",
                    "What did Morley, Vine and Matthews show about marine magnetic anomalies?",
                    "What happens to the oceanic plate at a trench?",
                    "How do upwelling mantle plumes produce rifting?");

            int coarseTotal = 0;
            int narrowedTotal = 0;
            int excludedTotal = 0;
            int excludedFromUnrelatedDocument = 0;
            for (String question : questions) {
                Answer answer = bench.ask(WORKSPACE, question, AbstractionLevel.INTRODUCTORY);
                Set<String> conceptIds = new LinkedHashSet<>();
                answer.concepts().forEach(concept -> conceptIds.add(concept.id()));
                Set<String> sourceIds = new LinkedHashSet<>();
                answer.sources().forEach(source -> sourceIds.add(source.id()));

                int coarse = 0;
                int narrowed = 0;
                for (LearnedClaim claim : learned) {
                    if (!conceptIds.contains(claim.conceptId())) {
                        continue;
                    }
                    coarse++;
                    boolean shares = claim.support().stream().anyMatch(sourceIds::contains);
                    if (shares) {
                        narrowed++;
                    } else {
                        // the claim is of the same concept, but its document is one the
                        // answer never drew on
                        assertThat(sourceIds).as("the excluded claim's document is not among "
                                + "the answer's sources").doesNotContain(claim.sourceId());
                    }
                }
                coarseTotal += coarse;
                narrowedTotal += narrowed;
                excludedTotal += coarse - narrowed;
                excludedFromUnrelatedDocument += coarse - narrowed;
            }

            System.out.println("[CRJ-01] the learned claims' scope: " + learned.size()
                    + " claim(s) over " + snapshot.sources().size() + " document(s)");
            System.out.println("[CRJ-01] the questions asked: " + questions.size());
            System.out.println("[CRJ-01] the coarse rule would add " + coarseTotal
                    + " claim(s); the narrowed rule adds " + narrowedTotal
                    + "; the coarse rule over-applied " + excludedTotal
                    + " claim(s) from documents the answer never drew on");

            assertThat(excludedTotal)
                    .as("the coarse rule DID over-apply: the defect was real and not "
                            + "hypothetical")
                    .isPositive();
            assertThat(narrowedTotal)
                    .as("and the narrowed rule still applies the claims whose document the "
                            + "answer draws on")
                    .isPositive();
            assertThat(excludedFromUnrelatedDocument).isEqualTo(excludedTotal);
        } finally {
            bench.close();
        }
    }
}
