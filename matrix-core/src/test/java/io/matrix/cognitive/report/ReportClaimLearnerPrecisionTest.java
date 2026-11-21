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
 * CRJ-01 — the LEARNER's PRECISION: how many claims does one journey learn, and are they
 * distinct?
 *
 * <p><b>The defect under measurement.</b> The learner's candidate set is the
 * <b>intersection</b> of the claims its observations cited, and <b>every</b> candidate that
 * passes the gates is promoted. Over a real corpus that intersection is large — the earlier
 * rounds recorded <b>23</b> promotions from one journey — and every one of those promotions
 * carries the <b>same support</b> (the union of the observations' sources) and the same
 * validation. So at serving time they all become applicable <b>together</b>: an answer's
 * evidence grows by N claims that were learned from the same reading of the same
 * documents.</p>
 *
 * <p><b>What is measured.</b> Over the research pool, with the observations constructed the
 * way the journey constructs them — several episodes that all cited the claims they had in
 * common — the measurement reports: the size of the candidate set, the number promoted, the
 * number of <b>distinct propositions</b> among the promotions, and the number of distinct
 * concepts. The last two are what say whether the promotions are <b>redundant</b>.</p>
 *
 * <p><b>Why redundancy is a defect and not a matter of taste.</b> A learned claim joins an
 * answer's evidence. If N learned claims carry the <b>same proposition</b> and the
 * <b>same support</b>, they are the <b>same fact</b> learned N times — and the answer's
 * evidence cites it N times, which inflates the evidence count without adding a single
 * fact. The learner is a <b>durable</b> writer, so what it writes is what the workspace
 * carries forever.</p>
 */
class ReportClaimLearnerPrecisionTest {

    /** The workspace the pool is imported into. */
    private static final String WORKSPACE = "research-pool";

    @Test
    @DisplayName("the learner promotes every candidate, and the promotions are measured for redundancy")
    void theLearnersPromotionsAreMeasured(@TempDir Path directory) throws Exception {
        Path database = Files.createDirectories(directory.resolve("db")).resolve("w.sqlite");
        KnowledgeWorkbench bench = KnowledgeWorkbench.at(database.toString());
        try {
            ClasspathCorpus pool = ClasspathCorpus.extract(AcquisitionCycle.RESEARCH_POOL);
            for (ClasspathCorpus.Entry entry : pool.entries()) {
                bench.importDocument(WORKSPACE, entry.path(), entry.file(), entry.language());
            }
            var snapshot = bench.workspace(WORKSPACE);

            CognitiveService cognitive = new CognitiveService();
            cognitive.workbench(bench);
            CognitiveStore store = cognitive.store();

            // ── the questions the journey asks: the pool's own subjects ───────────
            List<String> questions = List.of(
                    "What is plate tectonics?",
                    "What drives the movement of tectonic plates at subduction zones?",
                    "What evidence shows that the seafloor spreads?",
                    "How are the Earth's magnetic reversals recorded in rocks?",
                    "What happens where two tectonic plates move apart?",
                    "How do geologists determine the age of rocks from their magnetism?",
                    "What did the early continental drift theory propose?",
                    "How is new oceanic crust produced at mid-ocean ridges?",
                    "What is the role of slab pull in the movement of plates?",
                    "How does magnetostratigraphy date a rock?",
                    "What did Morley, Vine and Matthews show about marine magnetic anomalies?",
                    "What happens to the oceanic plate at a trench?",
                    "How do upwelling mantle plumes produce rifting?",
                    "What are the three types of plate boundary?",
                    "How does the Wilson cycle describe an ocean's opening and closing?",
                    "What evidence shows that the continents once joined?",
                    "How fast do plates move, and how is that measured?",
                    "What causes a mountain range to rise?",
                    "How does a subduction zone produce earthquakes?",
                    "What is the difference between continental and oceanic lithosphere?",
                    "How do scientists know the seafloor is younger at its ridge?",
                    "What is the geodynamo?",
                    "How does the Earth's core generate its magnetic field?",
                    "How do reversing magnetic stripes record the Earth's history?",
                    "What is a transform fault?",
                    "What happens to a plate that collides with another?");

            // ── the reference's own answers, read once ──────────────────────────
            List<Answer> answers = new ArrayList<>();
            for (String question : questions) {
                answers.add(bench.ask(WORKSPACE, question, AbstractionLevel.INTRODUCTORY));
            }

            // THE OBSERVATION SET IS CHOSEN, NOT ASSUMED.
            //
            // The learner's candidate set is the INTERSECTION of the observations' claims,
            // and its AGREEMENT gate then demands that the reference carry every candidate
            // on every held-out question. So the observation set has to be one whose
            // intersection is carried by at least five of the pool's questions — otherwise
            // the promotion is not possible at all, whatever the mechanism. The search
            // below finds the observation triple that makes it possible and reports how
            // many triples do.
            int workable = 0;
            List<Integer> best = List.of();
            Set<String> bestCommon = Set.of();
            List<Set<String>> claimSets = new ArrayList<>();
            for (Answer answer : answers) {
                Set<String> set = new LinkedHashSet<>();
                answer.claims().forEach(claim -> set.add(claim.id()));
                claimSets.add(set);
            }
            for (int a = 0; a < questions.size(); a++) {
                for (int b = a + 1; b < questions.size(); b++) {
                    for (int c = b + 1; c < questions.size(); c++) {
                        Set<String> intersection = new LinkedHashSet<>(claimSets.get(a));
                        intersection.retainAll(claimSets.get(b));
                        intersection.retainAll(claimSets.get(c));
                        if (intersection.isEmpty()) {
                            continue;
                        }
                        int carriers = 0;
                        for (Set<String> set : claimSets) {
                            if (set.containsAll(intersection)) {
                                carriers++;
                            }
                        }
                        if (carriers >= 5) {
                            workable++;
                            if (bestCommon.size() < intersection.size()) {
                                bestCommon = intersection;
                                best = List.of(a, b, c);
                            }
                        }
                    }
                }
            }
            System.out.println("[CRJ-01]   the observation triples whose intersection is "
                    + "carried by five or more questions: " + workable);
            assertThat(workable).as("the agreement gate is satisfiable over this corpus")
                    .isPositive();

            Set<String> common = bestCommon;
            List<String> candidateIds = new ArrayList<>(common);
            List<String> candidateSources = new ArrayList<>();
            for (Claim claim : snapshot.claims()) {
                if (common.contains(claim.id()) && claim.introducedBySourceId() != null
                        && !candidateSources.contains(claim.introducedBySourceId())) {
                    candidateSources.add(claim.introducedBySourceId());
                }
            }

            List<ReportClaimLearner.Observation> observations = new ArrayList<>();
            for (int index = 0; index < best.size(); index++) {
                int question = best.get(index);
                observations.add(new ReportClaimLearner.Observation("episode-" + index,
                        questions.get(question) + ", episode " + index,
                        AbstractionLevel.INTRODUCTORY,
                        new ArrayList<>(claimSets.get(question)), candidateSources));
            }
            assertThat(common).as("the episodes have claims in common").isNotEmpty();

            // ── the HELD-OUT cases: questions whose answers CARRY every candidate ──
            // The learner's AGREEMENT gate requires the reference to carry the candidate
            // on EVERY held-out case, so the held-out questions are the ones whose answers
            // do — and the count of those is what makes the measurement possible at all.
            List<String> carrying = new ArrayList<>();
            for (int index = 0; index < questions.size(); index++) {
                Set<String> cited = new LinkedHashSet<>();
                answers.get(index).claims().forEach(claim -> cited.add(claim.id()));
                if (cited.containsAll(common)) {
                    carrying.add(questions.get(index));
                }
            }
            System.out.println("[CRJ-01]   the questions whose answers carry EVERY candidate: "
                    + carrying.size() + " of " + questions.size());
            assertThat(carrying.size())
                    .as("the agreement gate needs at least five such questions")
                    .isGreaterThanOrEqualTo(5);

            List<ReportClaimLearner.Case> heldOut = new ArrayList<>();
            for (int index = 0; index < 5; index++) {
                heldOut.add(new ReportClaimLearner.Case("case-" + index,
                        carrying.get(index), AbstractionLevel.INTRODUCTORY));
            }

            ReportClaimLearner.Outcome outcome = new ReportClaimLearner(bench, store)
                    .learn(WORKSPACE, observations, heldOut, Set.of(), "2026-11-21T00:00:00Z");

            // ── the MEASUREMENT ──────────────────────────────────────────────────
            List<LearnedClaim> promoted = outcome.claims();
            Set<String> propositions = new LinkedHashSet<>();
            Set<String> concepts = new LinkedHashSet<>();
            Set<String> supports = new LinkedHashSet<>();
            Map<String, Integer> perProposition = new LinkedHashMap<>();
            for (LearnedClaim claim : promoted) {
                propositions.add(claim.proposition());
                concepts.add(claim.conceptId());
                supports.add(String.join(",", new java.util.TreeSet<>(claim.support())));
                perProposition.merge(claim.proposition(), 1, Integer::sum);
            }
            int redundant = perProposition.values().stream().filter(count -> count > 1)
                    .mapToInt(count -> count - 1).sum();

            System.out.println("[CRJ-01] the learner's precision over the research pool:");
            System.out.println("[CRJ-01]   the outcome: promoted=" + outcome.promoted()
                    + " reason=" + outcome.reason());;
            System.out.println("[CRJ-01]   the candidate set (the claims the episodes had in "
                    + "common): " + common.size());
            System.out.println("[CRJ-01]   promoted: " + promoted.size()
                    + "; distinct PROPOSITIONS: " + propositions.size()
                    + "; distinct CONCEPTS: " + concepts.size()
                    + "; distinct SUPPORTS: " + supports.size());
            System.out.println("[CRJ-01]   the REDUNDANT promotions (the same proposition "
                    + "promoted more than once): " + redundant);
            System.out.println("[CRJ-01]   the store's learned claims: "
                    + store.learnedClaims(WORKSPACE).size());

            assertThat(promoted).as("the learner promoted claims").isNotEmpty();
            assertThat(promoted.size())
                    .as("and it promoted the candidates that PASSED the gates - not every "
                            + "candidate, and not more than passed")
                    .isLessThanOrEqualTo(common.size());
            assertThat(supports).as("and every promotion carries the SAME support - the union "
                    + "of the observations' sources - so they all become applicable together")
                    .hasSize(1);
            assertThat(concepts)
                    .as("and the promotions are DISTINCT facts, not copies of one fact")
                    .hasSameSizeAs(propositions);
            assertThat(redundant)
                    .as("so the promotions are NOT redundant over this corpus: no proposition "
                            + "was promoted twice, and the '23 promotions from one journey' "
                            + "figure is NOT reproduced here")
                    .isZero();
        } finally {
            bench.close();
        }
    }
}
