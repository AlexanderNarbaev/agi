package io.matrix.knowledge.concept.research;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.matrix.knowledge.AbstractionLevel;
import io.matrix.knowledge.Answer;
import io.matrix.knowledge.Claim;
import io.matrix.knowledge.Concept;
import io.matrix.knowledge.KnowledgeWorkbench;
import io.matrix.knowledge.acquisition.AcquisitionCycle;
import io.matrix.knowledge.acquisition.ClasspathCorpus;
import io.matrix.knowledge.concept.RetrievalExperiment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EPISTEMIC-INTEGRITY P1 — the referent key: the retrieval effect.
 *
 * <p><b>The question.</b> The research was explicit that <i>"no retrieval effect has
 * been measured for any alternative"</i> and that the owner's promotion rule is
 * <i>"A1 and A2 improve AND A4 does not regress AND A5 does not regress"</i>. The
 * referent key is the identity candidate, and an alias layer is exactly the kind of
 * thing that widens a retriever's searchable text — which is how the definition half
 * cost the refusal correctness (18/29 → 17/29, the abstention 6/6 → 5/6). So the
 * retrieval effect of the key has to be MEASURED, not assumed, and a null result has
 * to be published as a null result.</p>
 *
 * <p><b>How the effect is simulated WITHOUT migrating anything.</b> The retriever
 * composes the searchable text of a claim from the proposition, the concept's name and
 * the concept's definition column. The alias layer's whole effect on the retrieval is
 * the <b>alias surface forms</b> entering that text. So the arm writes the alias
 * surface forms into the <code>concept.definition</code> column of a <b>temporary</b>
 * store — the same technique the definition half's own measurement used — and the
 * concept ids, the claims and the rows do not move. Nothing is renamed, no id changes,
 * and the owner's store is never touched.</p>
 *
 * <p><b>The arms.</b>
 * <ul>
 *   <li>{@link Arm#CURRENT} — the production state: no alias in the searchable text;</li>
 *   <li>{@link Arm#KEY} — the alias surface forms of the referent enter it;</li>
 *   <li>{@link Arm#KEY_WITH_DEFINITIONS} — the alias surface forms AND the recorded
 *       definition enter it (the best case for the key).</li>
 * </ul>
 *
 * <p><b>The calibration.</b> The CURRENT arm must reproduce the published official
 * failures — the eight ask-case failures of the eleven named ones — or the instrument
 * is not measuring the official thing.</p>
 */
@DisplayName("P1 — the referent key: the retrieval effect")
class ReferentKeyRetrievalTest {

    /** The official evaluation's dataset: the 29 cases and the six scopes. */
    private static final String OFFICIAL_DATASET =
            "matrix-core/src/main/resources/epistemic-eval/cases.json";

    /** The frozen held-out set (P0.3). It is read and never written. */
    private static final String HELD_OUT_SET = "docs-v2/engineering/EPISTEMIC-HELDOUT-SET.json";

    /** The seed corpus: the two licensed documents of the measured baseline. */
    private static final String SEED_CORPUS = "corpus/plate-tectonics";

    /** The workspace the held-out set is measured in. */
    private static final String HELD_OUT_WORKSPACE = "heldout-referent-key";

    /**
     * The failures of the published official evaluation that are ASK CASES.
     *
     * <p>Nine of the official 29 cases are not asked through the workbench: the five
     * false-premise cases and four of the revision cases carry a premise or a revision
     * instruction instead of a question.</p>
     */
    static final List<String> OFFICIAL_ASK_FAILURES = List.of("A-02", "A-04", "A-05", "A-06",
            "M-01", "P-01", "P-02", "M-03");

    /** The arms of the measurement. */
    enum Arm {
        /** The production state: no alias in the searchable text. */
        CURRENT,
        /** The alias surface forms of the referent enter the searchable text. */
        KEY,
        /** The alias surface forms and the recorded definition enter it. */
        KEY_WITH_DEFINITIONS
    }

    /** One measured case. */
    private record Outcome(String caseId, boolean answered, boolean passed, long millis,
                           List<String> citedPropositions, boolean unanswerable,
                           int goldCited, int goldTotal, double recallAt1, double recallAt5,
                           double recallAt10, double recallAt20, double mrr) {
    }

    /** The metrics of one arm over one case set. */
    private record ArmReport(String set, Arm arm, int cases, int passed, int answered,
                              int abstentions, int unanswerable, int unanswerableAbstained,
                              int answeredWithoutTheGold, double recallAt1, double recallAt5,
                              double recallAt10, double recallAt20, double mrr,
                              long totalMillis) {
    }

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    @DisplayName("the official ask cases: the three arms")
    void theOfficialCasesInTheThreeArms() throws Exception {
        Path work = Files.createTempDirectory("p1-referent-official-");
        Path database = work.resolve("knowledge.sqlite");
        try (RetrievalExperiment experiment = new RetrievalExperiment(work.resolve("harness.sqlite"));
                KnowledgeWorkbench workbench = KnowledgeWorkbench.at(database.toString())) {
            RetrievalExperiment.Dataset dataset = experiment.dataset();
            for (RetrievalExperiment.ScopeSpec scope : dataset.scopes().values()) {
                prepare(workbench, scope);
            }
            Map<String, Map<Arm, Outcome>> byCase = new LinkedHashMap<>();
            int notAsked = 0;
            for (RetrievalExperiment.CaseSpec entry : dataset.cases()) {
                if (entry.question() == null || entry.question().isBlank()) {
                    notAsked++;
                    continue;
                }
                String workspace = dataset.scopes().get(entry.scope()).workspace();
                Map<Arm, Outcome> outcomes = new LinkedHashMap<>();
                // The arms are interleaved per case: a machine that gets busier cannot
                // be mistaken for an arm.
                for (Arm arm : Arm.values()) {
                    applyArm(workbench, workspace, arm);
                    outcomes.put(arm, measure(workbench, workspace, entry));
                }
                byCase.put(entry.id(), outcomes);
            }
            List<ArmReport> reports = new ArrayList<>();
            for (Arm arm : Arm.values()) {
                reports.add(summarise("official-ask-20", arm, byCase));
            }
            report(reports, byCase);
            assertThat(byCase).as("every ask case must be measured in every arm").hasSize(20);
            assertThat(failures(byCase, Arm.CURRENT))
                    .as("the arm without the aliases must reproduce the official failures")
                    .containsExactlyInAnyOrderElementsOf(OFFICIAL_ASK_FAILURES);
        }
    }

    @Test
    @DisplayName("the frozen held-out set: the three arms")
    void theHeldOutSetInTheThreeArms() throws Exception {
        Path root = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath();
        for (Path directory = root; directory != null; directory = directory.getParent()) {
            if (Files.isRegularFile(directory.resolve(HELD_OUT_SET))) {
                root = directory;
                break;
            }
        }
        JsonNode set = JSON.readTree(Files.readAllBytes(root.resolve(HELD_OUT_SET)));
        Path work = Files.createTempDirectory("p1-referent-heldout-");
        Path database = work.resolve("knowledge.sqlite");
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(database.toString())) {
            ClasspathCorpus corpus = ClasspathCorpus.extract(SEED_CORPUS);
            for (ClasspathCorpus.Entry entry : corpus.entries()) {
                workbench.importDocument(HELD_OUT_WORKSPACE, entry.path(), entry.file(),
                        entry.language());
            }
            Map<String, Map<Arm, Outcome>> byCase = new LinkedHashMap<>();
            for (JsonNode entry : set.path("cases")) {
                Map<Arm, Outcome> outcomes = new LinkedHashMap<>();
                for (Arm arm : Arm.values()) {
                    applyArm(workbench, HELD_OUT_WORKSPACE, arm);
                    outcomes.put(arm, measureHeldOut(workbench, HELD_OUT_WORKSPACE, entry));
                }
                byCase.put(entry.path("id").asText(), outcomes);
            }
            List<ArmReport> reports = new ArrayList<>();
            for (Arm arm : Arm.values()) {
                reports.add(summarise("held-out-16", arm, byCase));
            }
            report(reports, byCase);
            assertThat(byCase).hasSize(set.path("cases").size());
        }
    }

    // ── the preparation ───────────────────────────────────────────────────────

    /** Prepares a scope with the production methods of the dataset. */
    private static void prepare(KnowledgeWorkbench workbench, RetrievalExperiment.ScopeSpec scope)
            throws Exception {
        for (RetrievalExperiment.PrepareStep step : scope.prepare()) {
            if (step.path() == null) {
                continue;
            }
            if (step.path().endsWith("/seed")) {
                AcquisitionCycle.loadSeed(workbench, scope.workspace());
            } else if (step.path().endsWith("/learn")) {
                AcquisitionCycle.run(workbench, scope.workspace(), step.question(),
                        step.documents() == null ? 3 : step.documents());
            } else if (step.path().endsWith("/revise")) {
                AcquisitionCycle.acquireAndRevise(workbench, scope.workspace(), step.file());
            } else {
                throw new IllegalStateException("the dataset names a preparation this instrument "
                        + "does not know: " + step.path());
            }
        }
    }

    /**
     * Puts the arm's alias surface forms into the concepts' searchable text.
     *
     * <p>The arm is a property of the STORE, so that the production path — the
     * workbench, the gates, the composer — is measured exactly as it is served. The
     * column the retriever reads is the {@code concept.definition} column, and the
     * alias surface forms are the whole effect of an alias layer on the searchable
     * text.</p>
     */
    private static void applyArm(KnowledgeWorkbench workbench, String workspace, Arm arm) {
        Map<String, String> aliasByConceptId = aliasByConceptId(workbench, workspace);
        Map<String, String> definitionsByConceptId = new LinkedHashMap<>();
        for (io.matrix.knowledge.ConceptDefinition definition
                : workbench.storeForValidation().conceptDefinitions(workspace)) {
            definitionsByConceptId.put(definition.conceptId(), definition.definition());
        }
        for (Concept concept : workbench.storeForValidation().concepts(workspace)) {
            String overlay = null;
            if (arm != Arm.CURRENT) {
                String alias = aliasByConceptId.get(concept.id());
                if (alias != null && !alias.isBlank()) {
                    overlay = alias;
                }
                if (arm == Arm.KEY_WITH_DEFINITIONS) {
                    String definition = definitionsByConceptId.get(concept.id());
                    if (definition != null && !definition.isBlank()) {
                        overlay = overlay == null ? definition : overlay + " " + definition;
                    }
                }
            }
            workbench.storeForValidation().updateConceptDefinition(workspace, concept.id(), overlay);
        }
    }

    /**
     * The alias surface forms of the referent each concept belongs to.
     *
     * <p>A concept of the current splitter may hold a claim whose subject is an alias
     * of the referent; the overlay is every surface form of that referent, minus the
     * concept's own name.</p>
     */
    private static Map<String, String> aliasByConceptId(KnowledgeWorkbench workbench,
                                                         String workspace) {
        io.matrix.knowledge.KnowledgeSnapshot snapshot = workbench.workspace(workspace);
        ReferentResolver.Result resolved = ReferentResolver.resolve(snapshot.claims(),
                snapshot.concepts());
        Map<String, String> conceptIdByClaimId = new LinkedHashMap<>();
        for (Claim claim : snapshot.claims()) {
            conceptIdByClaimId.put(claim.id(), claim.conceptId());
        }
        Map<String, Set<String>> aliasesByConceptId = new LinkedHashMap<>();
        for (ReferentResolver.Cluster cluster : resolved.clustersDetail()) {
            Set<String> surfaceForms = new LinkedHashSet<>(cluster.aliases());
            surfaceForms.add(cluster.displayName());
            for (String claimId : cluster.claimIds()) {
                String conceptId = conceptIdByClaimId.get(claimId);
                if (conceptId != null) {
                    aliasesByConceptId.computeIfAbsent(conceptId, key -> new LinkedHashSet<>())
                            .addAll(surfaceForms);
                }
            }
        }
        Map<String, String> overlays = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : aliasesByConceptId.entrySet()) {
            Set<String> surfaceForms = entry.getValue();
            StringBuilder builder = new StringBuilder();
            for (String surface : surfaceForms) {
                if (builder.length() > 0) {
                    builder.append(" ");
                }
                builder.append(surface);
            }
            overlays.put(entry.getKey(), builder.toString());
        }
        return overlays;
    }

    // ── the measurement ───────────────────────────────────────────────────────

    /**
     * Measures one official case with the OFFICIAL runner's rules.
     *
     * @param workbench the workbench
     * @param workspace the workspace
     * @param entry the case
     * @return the outcome
     */
    private static Outcome measure(KnowledgeWorkbench workbench, String workspace,
                                   RetrievalExperiment.CaseSpec entry) {
        long started = System.nanoTime();
        Answer answer = workbench.ask(workspace, entry.question(),
                AbstractionLevel.valueOf((entry.level() == null ? "INTRODUCTORY" : entry.level())
                        .toUpperCase(Locale.ROOT)));
        long millis = (System.nanoTime() - started) / 1_000_000L;
        List<String> problems = RetrievalExperiment.judgeAnswer(entry.expect(), answer);
        List<String> cited = new ArrayList<>();
        for (Claim claim : answer.claims()) {
            cited.add(claim.proposition());
        }
        // The ranked candidate list: the production retriever at floor 0.0.
        io.matrix.knowledge.KnowledgeSnapshot snapshot = workbench.workspace(workspace);
        List<String> ranked = new ArrayList<>();
        for (Claim claim : new io.matrix.knowledge.KnowledgeRetriever(snapshot,
                RetrievalExperiment.RANKING_FLOOR).answer(entry.question(),
                AbstractionLevel.valueOf((entry.level() == null ? "INTRODUCTORY" : entry.level())
                        .toUpperCase(Locale.ROOT))).claims()) {
            ranked.add(claim.proposition());
        }
        List<String> gold = RetrievalExperiment.goldOf(entry);
        int goldCited = 0;
        List<Integer> goldRanks = new ArrayList<>();
        for (String proposition : gold) {
            boolean citedNow = cited.stream().anyMatch(text -> RetrievalExperiment.normalise(text)
                    .equals(RetrievalExperiment.normalise(proposition)));
            if (citedNow) {
                goldCited++;
            }
            int rank = 0;
            for (int index = 0; index < ranked.size(); index++) {
                if (RetrievalExperiment.normalise(ranked.get(index))
                        .equals(RetrievalExperiment.normalise(proposition))) {
                    rank = index + 1;
                    break;
                }
            }
            goldRanks.add(rank);
        }
        return new Outcome(entry.id(), answer.answered(), problems.isEmpty(), millis, cited,
                "unanswerable".equals(entry.category()), goldCited, gold.size(),
                recallAt(gold, goldRanks, 1), recallAt(gold, goldRanks, 5),
                recallAt(gold, goldRanks, 10), recallAt(gold, goldRanks, 20),
                mrr(goldRanks));
    }

    /** Measures one held-out case: the answered share and the gold citations. */
    private static Outcome measureHeldOut(KnowledgeWorkbench workbench, String workspace,
                                           JsonNode entry) {
        String question = entry.path("question").asText();
        long started = System.nanoTime();
        Answer answer = workbench.ask(workspace, question, AbstractionLevel.INTRODUCTORY);
        long millis = (System.nanoTime() - started) / 1_000_000L;
        Set<String> cited = new LinkedHashSet<>();
        for (Claim claim : answer.claims()) {
            cited.add(claim.proposition());
        }
        boolean expectedAnswered = !"UNANSWERED".equals(entry.path("expectedDisposition").asText())
                && !"REFUSED".equals(entry.path("expectedDisposition").asText());
        boolean goldCited = true;
        int goldTotal = 0;
        for (JsonNode gold : entry.path("goldClaims")) {
            goldTotal++;
            if (!cited.contains(gold.path("proposition").asText())) {
                goldCited = false;
            }
        }
        boolean passed = answer.answered() == expectedAnswered && goldCited;
        return new Outcome(entry.path("id").asText(), answer.answered(), passed, millis,
                new ArrayList<>(cited), false, goldCited ? goldTotal : 0, goldTotal,
                0, 0, 0, 0, 0);
    }

    /** The share of the golds found within the top K. */
    private static double recallAt(List<String> gold, List<Integer> ranks, int k) {
        if (gold.isEmpty()) {
            return 0;
        }
        int found = 0;
        for (int rank : ranks) {
            if (rank > 0 && rank <= k) {
                found++;
            }
        }
        return (double) found / gold.size();
    }

    /** The reciprocal rank of the topmost gold. */
    private static double mrr(List<Integer> ranks) {
        int best = Integer.MAX_VALUE;
        for (int rank : ranks) {
            if (rank > 0 && rank < best) {
                best = rank;
            }
        }
        return best == Integer.MAX_VALUE ? 0 : 1.0 / best;
    }

    /** The metrics of one arm. */
    private static ArmReport summarise(String set, Arm arm, Map<String, Map<Arm, Outcome>> byCase) {
        int passed = 0;
        int answered = 0;
        int abstentions = 0;
        int unanswerable = 0;
        int unanswerableAbstained = 0;
        int answeredWithoutTheGold = 0;
        long millis = 0;
        double recall1 = 0;
        double recall5 = 0;
        double recall10 = 0;
        double recall20 = 0;
        double mrr = 0;
        int measured = 0;
        for (Map<Arm, Outcome> outcomes : byCase.values()) {
            Outcome outcome = outcomes.get(arm);
            if (outcome.passed()) {
                passed++;
            }
            if (outcome.answered()) {
                answered++;
                if (!outcome.passed()) {
                    answeredWithoutTheGold++;
                }
            } else {
                abstentions++;
            }
            if (outcome.unanswerable()) {
                unanswerable++;
                if (!outcome.answered()) {
                    unanswerableAbstained++;
                }
            }
            millis += outcome.millis();
            if (outcome.goldTotal() > 0) {
                recall1 += outcome.recallAt1();
                recall5 += outcome.recallAt5();
                recall10 += outcome.recallAt10();
                recall20 += outcome.recallAt20();
                mrr += outcome.mrr();
                measured++;
            }
        }
        double divisor = Math.max(1, measured);
        return new ArmReport(set, arm, byCase.size(), passed, answered, abstentions, unanswerable,
                unanswerableAbstained, answeredWithoutTheGold, recall1 / divisor, recall5 / divisor,
                recall10 / divisor, recall20 / divisor, mrr / divisor, millis);
    }

    /** The cases an arm fails. */
    private static List<String> failures(Map<String, Map<Arm, Outcome>> byCase, Arm arm) {
        List<String> failures = new ArrayList<>();
        for (Map.Entry<String, Map<Arm, Outcome>> entry : byCase.entrySet()) {
            if (!entry.getValue().get(arm).passed()) {
                failures.add(entry.getKey());
            }
        }
        return failures;
    }

    /** Prints the arms' metrics and the cases that move. */
    private static void report(List<ArmReport> reports,
                               Map<String, Map<Arm, Outcome>> byCase) {
        for (ArmReport report : reports) {
            System.out.printf("[referent-key] %s %s: cases=%d passed=%d answered=%d "
                            + "abstentions=%d theUnanswerableAbstained=%d/%d "
                            + "answeredWithoutTheGold=%d recall@1=%.4f recall@5=%.4f "
                            + "recall@10=%.4f recall@20=%.4f mrr=%.4f meanMillis=%d%n",
                    report.set(), report.arm(), report.cases(), report.passed(),
                    report.answered(), report.abstentions(), report.unanswerableAbstained(),
                    report.unanswerable(), report.answeredWithoutTheGold(), report.recallAt1(),
                    report.recallAt5(), report.recallAt10(), report.recallAt20(), report.mrr(),
                    report.totalMillis() / Math.max(1, report.cases()));
        }
        for (Map.Entry<String, Map<Arm, Outcome>> entry : byCase.entrySet()) {
            Map<Arm, Outcome> outcomes = entry.getValue();
            Outcome current = outcomes.get(Arm.CURRENT);
            for (Arm arm : new Arm[] {Arm.KEY, Arm.KEY_WITH_DEFINITIONS}) {
                Outcome other = outcomes.get(arm);
                if (other.passed() != current.passed() || other.answered() != current.answered()) {
                    System.out.println("[referent-key] the case " + entry.getKey() + " moves in "
                            + arm + ": passed " + current.passed() + " -> " + other.passed()
                            + ", answered " + current.answered() + " -> " + other.answered());
                }
            }
        }
    }
}
