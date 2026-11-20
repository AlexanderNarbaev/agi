package io.matrix.knowledge.concept.definitions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.matrix.knowledge.AbstractionLevel;
import io.matrix.knowledge.Answer;
import io.matrix.knowledge.Claim;
import io.matrix.knowledge.Concept;
import io.matrix.knowledge.KnowledgeWorkbench;
import io.matrix.knowledge.Source;
import io.matrix.knowledge.acquisition.AcquisitionCycle;
import io.matrix.knowledge.acquisition.ClasspathCorpus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EPISTEMIC-INTEGRITY P1 — WHERE THE DEFINITIONS ACTUALLY HELP (the owner's option C:
 * "A now + a separate measurement task for the benefit of the definitions").
 *
 * <p><b>THE QUESTION.</b> The definitions are the corpus's own sentences about a concept
 * (120 of the seed corpus's 326 concepts; 24 of a 25-sample are about their concept).
 * Putting them into the retriever's searchable text <b>uniformly</b> degrades the refusal
 * correctness: the official evaluation moves from 18/29 to 17/29, the unanswerable
 * abstention from 6/6 to 5/6, and the unsupported-answer rate from 0.3000 to 0.4500. Is
 * there a SUBSET of the definitions — or of the queries — where a definition helps
 * <b>without</b> that degradation?</p>
 *
 * <p><b>THE ARMS.</b> Three arms over the same corpus, the same questions and the same
 * method, interleaved case by case so that a machine that gets busier cannot be mistaken
 * for an arm:</p>
 * <ul>
 *   <li>{@link Arm#WITHOUT} — the production state: no concept carries a definition in
 *       its searchable text;</li>
 *   <li>{@link Arm#WITH_ALL} — every recorded definition enters the searchable text
 *       (the uniform H1 variant, and the known degradation);</li>
 *   <li>{@link Arm#WITH_RULE} — only the definitions of the concepts that satisfy
 *       {@link #ruleHolds(String)} enter it.</li>
 * </ul>
 *
 * <p><b>THE RULE is stated over the concept's NAME, never over a case, a question or a
 * corpus phrase.</b> It says: a concept's definition enters the searchable text only when
 * the concept's name is a <b>referent</b> rather than a fragment — not a single function
 * word, not a generic class ("these", "many", "such"), and not a name the extractor cut
 * inside a clause (its last word is one the subject extractor stops at).</p>
 *
 * <p><b>THE VERDICT IS MEASURED, NOT ASSUMED.</b> The rule is a candidate, fitted to the
 * shape of the names and NOT to the answers: the test asserts the rule's structure, and
 * the per-case comparison is printed for the reviewer to judge. The rule is
 * <b>[speculative]</b> until it is measured on a set it was not fitted to.</p>
 */
class DefinitionBenefitMeasurementTest {

    /** The official evaluation's dataset: the 29 cases and the six scopes. */
    private static final String OFFICIAL_DATASET =
            "matrix-core/src/main/resources/epistemic-eval/cases.json";

    /**
     * The failures of the published official evaluation that are ASK CASES.
     *
     * <p>Nine of the official 29 cases are not asked through the workbench: the five
     * false-premise cases and four of the revision cases carry a premise or a revision
     * instruction instead of a question, and the official runner judges them on their own
     * paths. This instrument measures the twenty ASK cases, so its calibration target is
     * the eight ask-case failures of the eleven named ones — and the other three
     * (F-02, R-04, R-05) are outside what an ask can measure.</p>
     */
    static final List<String> OFFICIAL_ASK_FAILURES = List.of("A-02", "A-04", "A-05", "A-06",
            "M-01", "P-01", "P-02", "M-03");

    /** The eleven failures of the published official evaluation, for the record. */
    static final List<String> OFFICIAL_FAILURES = List.of("A-02", "A-04", "A-05", "A-06", "F-02",
            "M-01", "P-01", "P-02", "M-03", "R-04", "R-05");

    /** The frozen held-out set (P0.3). Its digest is checked by its own builder. */
    private static final String HELD_OUT_SET = "docs-v2/engineering/EPISTEMIC-HELDOUT-SET.json";

    /** The seed corpus: the two licensed documents of the measured baseline. */
    private static final String SEED_CORPUS = "corpus/plate-tectonics";

    /**
     * The words that cannot be a concept's referent on their own: a pronoun, an
     * article, a quantifier, a conjunction or a preposition.
     *
     * <p>The list is closed and it is a RULE about words, not a lookup of the corpus:
     * a name that is one of these is a reference or a connective, never a thing.</p>
     */
    static final Set<String> FUNCTION_WORDS = Set.of(
            "it", "its", "they", "them", "their", "these", "those", "this", "that", "he",
            "she", "his", "her", "we", "us", "our", "you", "your", "there", "here",
            "the", "a", "an", "one", "ones", "other", "others", "another", "such",
            "many", "most", "some", "several", "various", "different", "both", "all",
            "each", "every", "few", "more", "less", "much", "no", "none",
            "and", "or", "but", "if", "because", "since", "while", "when", "where",
            "which", "who", "what", "how", "why", "not", "as", "by", "for", "from",
            "with", "without", "to", "of", "in", "on", "at", "into", "onto", "over",
            "under", "above", "below", "after", "before", "during", "between");

    /**
     * The words that open a name which denotes a CLASS of things rather than a thing.
     *
     * <p>"Many were published by European scientists" and "Such volcanoes are typically
     * strung out in chains" name classes, and a definition attached to a class states
     * nothing about any member of it.</p>
     */
    static final Set<String> CLASS_OPENERS = Set.of(
            "many", "most", "some", "several", "various", "different", "other", "others",
            "such", "these", "those", "both", "all", "each", "every", "few", "more",
            "less", "numerous", "certain", "example", "examples");

    /**
     * The words the subject extractor stops at, so a name that ENDS with one of them
     * was cut inside a clause.
     *
     * <p>This is a subset of the production subject extractor's verb list, written out
     * here because that list is private to the extractor and this test must not change
     * production code to read it. The names it catches are the ones the research
     * measured: "This division should not", "Convergence can", "Plate tectonics came to".</p>
     */
    static final Set<String> CLAUSE_CUT_WORDS = Set.of(
            "is", "are", "was", "were", "be", "been", "being", "has", "have", "had",
            "means", "refer", "consist", "include", "require", "need", "produce",
            "generate", "release", "absorb", "convert", "transform", "use", "build",
            "happen", "occur", "form", "create", "make", "contain", "provide", "depend",
            "result", "lead", "cause", "enable", "allow", "determine", "control",
            "represent", "describe", "define", "equal", "become", "remain", "stay",
            "work", "function", "act", "react", "respond", "grow", "move", "split",
            "come", "go", "get", "give", "take", "show", "seem", "appear");

    /**
     * The words that cannot OPEN a concept's name.
     *
     * <p>A preposition, a conjunction, a subordinating adverb or a demonstrative opens a
     * phrase, not a referent: "Because it", "while Earth", "In this understanding, plate
     * motion", "At zones of continent-to-continent rifting", "This force". These names are
     * the fragments the extractor cut inside a sentence, and the v1 rule (which only
     * rejected a name ENDING with a verb) admits them.</p>
     */
    static final Set<String> CLAUSE_OPENERS = Set.of(
            "in", "on", "at", "by", "for", "from", "with", "without", "to", "of", "as",
            "because", "since", "while", "although", "though", "despite", "unless", "if",
            "when", "where", "after", "before", "during", "through", "under", "over",
            "across", "along", "around", "beyond", "within", "upon", "into", "onto",
            "this", "that", "these", "those", "it", "they", "he", "she", "we", "i");

    /**
     * The words that cannot END a concept's name.
     *
     * <p>A relative pronoun means the name was cut inside a clause: "slabs that",
     * "the zone which".</p>
     */
    static final Set<String> RELATIVE_ENDINGS = Set.of("that", "which", "who", "whose", "whom");

    /** The arms of the measurement. */
    enum Arm {
        /** The production state: no definition is in the searchable text. */
        WITHOUT,
        /** Every recorded definition is in the searchable text (the uniform H1). */
        WITH_ALL,
        /** Only the definitions of the concepts the rule accepts are. */
        WITH_RULE,
        /** Only the definitions of the concepts the STRICT rule accepts are. */
        WITH_STRICT_RULE
    }

    /**
     * The subset rule under test.
     *
     * @param name the concept's name
     * @return whether the concept's definition may enter the searchable text
     */
    static boolean ruleHolds(String name) {
        return ruleHolds(name, false);
    }

    /**
     * The strict variant of the rule.
     *
     * @param name the concept's name
     * @return whether the concept's definition may enter the searchable text
     */
    static boolean strictRuleHolds(String name) {
        return ruleHolds(name, true);
    }

    private static boolean ruleHolds(String name, boolean strict) {
        if (strict) {
            if (name == null || name.isBlank()) {
                return false;
            }
            List<String> words = words(name);
            if (words.isEmpty()) {
                return false;
            }
            if (CLAUSE_OPENERS.contains(words.get(0))) {
                return false;
            }
            if (RELATIVE_ENDINGS.contains(words.get(words.size() - 1))) {
                return false;
            }
        }
        if (name == null || name.isBlank()) {
            return false;
        }
        List<String> words = words(name);
        if (words.isEmpty()) {
            return false;
        }
        // (a) a name of one function word is a reference, not a referent.
        if (words.size() == 1 && FUNCTION_WORDS.contains(words.get(0))) {
            return false;
        }
        // (b) a name that opens with a class word denotes a class.
        if (CLASS_OPENERS.contains(words.get(0))) {
            return false;
        }
        // (c) a name the extractor cut inside a clause is a fragment.
        if (CLAUSE_CUT_WORDS.contains(words.get(words.size() - 1))) {
            return false;
        }
        return true;
    }

    /** The words of a text: the runs of letters, lower-cased. */
    private static List<String> words(String text) {
        List<String> words = new ArrayList<>();
        int index = 0;
        while (index < text.length()) {
            if (Character.isLetter(text.charAt(index))) {
                int end = index;
                while (end < text.length() && Character.isLetter(text.charAt(end))) {
                    end++;
                }
                words.add(text.substring(index, end).toLowerCase(Locale.ROOT));
                index = end;
            } else {
                index++;
            }
        }
        return words;
    }

    /** One measured case. */
    private record Outcome(String caseId, boolean answered, boolean passed, long millis,
                           List<String> citedPropositions, boolean unanswerable,
                           List<String> citedConceptNames) {
    }

    /** The metrics of one arm over one case set. */
    private record ArmReport(String arm, String set, int cases, int passed, int answered,
                             int abstentions, int unanswerable, int unanswerableAbstained,
                             int answeredWithoutTheGold, long totalMillis) {
    }

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    @DisplayName("the official 29 cases — the three arms, and the cases that move")
    void theOfficialCasesInTheThreeArms() throws Exception {
        Path work = Files.createTempDirectory("p1-benefit-");
        Path database = work.resolve("knowledge.sqlite");
        Path harnessDatabase = work.resolve("harness.sqlite");
        try (io.matrix.knowledge.concept.RetrievalExperiment experiment =
                     new io.matrix.knowledge.concept.RetrievalExperiment(harnessDatabase);
                KnowledgeWorkbench workbench = KnowledgeWorkbench.at(database.toString())) {
            io.matrix.knowledge.concept.RetrievalExperiment.Dataset dataset = experiment.dataset();
            // The scopes are prepared with the production methods, once.
            for (io.matrix.knowledge.concept.RetrievalExperiment.ScopeSpec scope
                    : dataset.scopes().values()) {
                prepare(workbench, scope);
            }
            Map<String, Map<Arm, Outcome>> byCase = new LinkedHashMap<>();
            Map<String, List<ConceptDefinitionRow>> admittedByWorkspace = new LinkedHashMap<>();
            int notAsked = 0;
            for (io.matrix.knowledge.concept.RetrievalExperiment.CaseSpec entry : dataset.cases()) {
                if (entry.question() == null || entry.question().isBlank()) {
                    // A false-premise or a revision case: the official runner judges it
                    // on its own path, and an ask cannot measure it.
                    notAsked++;
                    continue;
                }
                String workspace = dataset.scopes().get(entry.scope()).workspace();
                admittedByWorkspace.computeIfAbsent(workspace, key ->
                        recordedDefinitions(workbench, key));
                Map<Arm, Outcome> outcomes = new LinkedHashMap<>();
                // The arms are interleaved per case: a machine that gets busier
                // cannot be mistaken for an arm.
                for (Arm arm : new Arm[] {Arm.WITHOUT, Arm.WITH_ALL, Arm.WITH_RULE,
                        Arm.WITH_STRICT_RULE}) {
                    applyArm(workbench, workspace, arm);
                    outcomes.put(arm, measure(workbench, workspace, entry));
                }
                byCase.put(entry.id(), outcomes);
            }
            List<ArmReport> reports = new ArrayList<>();
            for (Arm arm : Arm.values()) {
                reports.add(summarise("official-29", arm, byCase));
            }
            Map<String, String> admitted = new LinkedHashMap<>();
            for (Map.Entry<String, List<ConceptDefinitionRow>> scope : admittedByWorkspace
                    .entrySet()) {
                for (ConceptDefinitionRow row : scope.getValue()) {
                    if (ruleHolds(row.name())) {
                        admitted.put(row.conceptId(), row.name());
                    }
                }
            }
            Map<String, String> strictAdmitted = new LinkedHashMap<>();
            for (Map.Entry<String, List<ConceptDefinitionRow>> scope : admittedByWorkspace
                    .entrySet()) {
                for (ConceptDefinitionRow row : scope.getValue()) {
                    if (strictRuleHolds(row.name())) {
                        strictAdmitted.put(row.conceptId(), row.name());
                    }
                }
            }
            System.out.println("[BENEFIT] the rule admits " + admitted.size()
                    + " and the strict rule " + strictAdmitted.size()
                    + " of the recorded definitions");
            report(reports, byCase, admitted);
            System.out.println("[BENEFIT] the official 29 cases: " + byCase.size()
                    + " are ask cases and " + notAsked + " are judged on their own paths");
            assertThat(byCase).as("every ask case must be measured in every arm").hasSize(20);
            // THE CALIBRATION: the arm without the definitions must reproduce the
            // published ask-case failures, or the instrument is not measuring the
            // official thing.
            assertThat(failures(byCase, Arm.WITHOUT))
                    .as("the arm without the definitions must reproduce the official failures")
                    .containsExactlyInAnyOrderElementsOf(OFFICIAL_ASK_FAILURES);
        }
    }

    @Test
    @DisplayName("the frozen held-out set — the three arms")
    void theHeldOutSetInTheThreeArms() throws Exception {
        Path root = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath();
        for (Path directory = root; directory != null; directory = directory.getParent()) {
            if (Files.isRegularFile(directory.resolve(HELD_OUT_SET))) {
                root = directory;
                break;
            }
        }
        JsonNode set = JSON.readTree(Files.readAllBytes(root.resolve(HELD_OUT_SET)));
        Path work = Files.createTempDirectory("p1-benefit-heldout-");
        Path database = work.resolve("knowledge.sqlite");
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(database.toString())) {
            ClasspathCorpus corpus = ClasspathCorpus.extract(SEED_CORPUS);
            for (ClasspathCorpus.Entry entry : corpus.entries()) {
                workbench.importDocument("heldout-benefit", entry.path(), entry.file(),
                        entry.language());
            }
            Map<String, Map<Arm, Outcome>> byCase = new LinkedHashMap<>();
            for (JsonNode entry : set.path("cases")) {
                Map<Arm, Outcome> outcomes = new LinkedHashMap<>();
                for (Arm arm : new Arm[] {Arm.WITHOUT, Arm.WITH_ALL, Arm.WITH_RULE,
                        Arm.WITH_STRICT_RULE}) {
                    applyArm(workbench, "heldout-benefit", arm);
                    outcomes.put(arm, measureHeldOut(workbench, "heldout-benefit", entry));
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
    private static void prepare(KnowledgeWorkbench workbench,
                                io.matrix.knowledge.concept.RetrievalExperiment.ScopeSpec scope)
            throws Exception {
        for (io.matrix.knowledge.concept.RetrievalExperiment.PrepareStep step : scope.prepare()) {
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
     * Puts the arm's definitions into the concepts' searchable text.
     *
     * <p>The arm is a property of the STORE, so that the production path — the workbench,
     * the gates, the composer — is measured exactly as it is served. The definitions
     * themselves are the recorded ones (the {@code concept_definition} table), and the
     * column the retriever reads is the {@code concept.definition} column.</p>
     */
    private static void applyArm(KnowledgeWorkbench workbench, String workspace, Arm arm) {
        List<ConceptDefinitionRow> definitions = recordedDefinitions(workbench, workspace);
        for (ConceptDefinitionRow row : definitions) {
            String text = null;
            if (arm == Arm.WITH_ALL) {
                text = row.definition();
            } else if (arm == Arm.WITH_RULE && ruleHolds(row.name())) {
                text = row.definition();
            } else if (arm == Arm.WITH_STRICT_RULE && strictRuleHolds(row.name())) {
                text = row.definition();
            }
            workbench.storeForValidation().updateConceptDefinition(workspace, row.conceptId(), text);
        }
    }

    /** One recorded definition, with the name of its concept. */
    private record ConceptDefinitionRow(String conceptId, String name, String definition) {
    }

    /** The recorded definitions of a workspace, with their concepts' names. */
    private static List<ConceptDefinitionRow> recordedDefinitions(KnowledgeWorkbench workbench,
                                                                  String workspace) {
        Map<String, String> names = new LinkedHashMap<>();
        for (Concept concept : workbench.storeForValidation().concepts(workspace)) {
            names.put(concept.id(), concept.name());
        }
        List<ConceptDefinitionRow> rows = new ArrayList<>();
        for (io.matrix.knowledge.ConceptDefinition definition
                : workbench.storeForValidation().conceptDefinitions(workspace)) {
            rows.add(new ConceptDefinitionRow(definition.conceptId(),
                    names.getOrDefault(definition.conceptId(), ""), definition.definition()));
        }
        return rows;
    }

    // ── the measurement ───────────────────────────────────────────────────────

    /**
     * Measures one official case with the OFFICIAL runner's rules.
     *
     * <p>The rules are the harness's own {@code judgeAnswer} — the comparison the official
     * runner makes, whitespace collapsed and lower-cased — so the arm without the
     * definitions reproduces the published eighteen of twenty-nine and the eleven named
     * failures, and the arms are comparable with the published row.</p>
     */
    private static Outcome measure(KnowledgeWorkbench workbench, String workspace,
                                   io.matrix.knowledge.concept.RetrievalExperiment.CaseSpec entry) {
        long started = System.nanoTime();
        Answer answer = workbench.ask(workspace, entry.question(),
                AbstractionLevel.valueOf((entry.level() == null ? "INTRODUCTORY" : entry.level())
                        .toUpperCase(Locale.ROOT)));
        long millis = (System.nanoTime() - started) / 1_000_000L;
        List<String> problems = io.matrix.knowledge.concept.RetrievalExperiment
                .judgeAnswer(entry.expect(), answer);
        List<String> cited = new ArrayList<>();
        for (Claim claim : answer.claims()) {
            cited.add(claim.proposition());
        }
        List<String> conceptNames = new ArrayList<>();
        for (Concept concept : answer.concepts()) {
            conceptNames.add(concept.name());
        }
        return new Outcome(entry.id(), answer.answered(), problems.isEmpty(), millis, cited,
                "unanswerable".equals(entry.category()), conceptNames);
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
        for (JsonNode gold : entry.path("goldClaims")) {
            if (!cited.contains(gold.path("proposition").asText())) {
                goldCited = false;
            }
        }
        boolean passed = answer.answered() == expectedAnswered && goldCited;
        return new Outcome(entry.path("id").asText(), answer.answered(), passed, millis,
                new ArrayList<>(cited), false, List.of());
    }

    /** How many distinct values a list holds. */
    private static int distinct(List<String> values) {
        return new LinkedHashSet<>(values).size();
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
        }
        return new ArmReport(set, arm.name(), byCase.size(), passed, answered, abstentions,
                unanswerable, unanswerableAbstained, answeredWithoutTheGold, millis);
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
        report(reports, byCase, Map.of());
    }

    /** Prints the arms' metrics and the cases that move. */
    private static void report(List<ArmReport> reports, Map<String, Map<Arm, Outcome>> byCase,
                               Map<String, String> admittedByConceptId) {
        for (ArmReport report : reports) {
            System.out.println("[BENEFIT] " + report.set() + " " + report.arm()
                    + ": cases=" + report.cases() + " passed=" + report.passed()
                    + " answered=" + report.answered() + " abstentions=" + report.abstentions()
                    + " theUnanswerableAbstained=" + report.unanswerableAbstained() + "/"
                    + report.unanswerable()
                    + " answeredWithoutTheGold=" + report.answeredWithoutTheGold()
                    + " meanMillis=" + (report.totalMillis() / Math.max(1, report.cases())));
        }
        for (Map.Entry<String, Map<Arm, Outcome>> entry : byCase.entrySet()) {
            Map<Arm, Outcome> outcomes = entry.getValue();
            Outcome without = outcomes.get(Arm.WITHOUT);
            boolean moved = outcomes.get(Arm.WITH_ALL).passed() != without.passed()
                    || outcomes.get(Arm.WITH_RULE).passed() != without.passed()
                    || outcomes.get(Arm.WITH_STRICT_RULE).passed() != without.passed();
            if (moved) {
                System.out.println("[BENEFIT] the case " + entry.getKey() + " moves: without="
                        + without.passed() + " withAll=" + outcomes.get(Arm.WITH_ALL).passed()
                        + " withRule=" + outcomes.get(Arm.WITH_RULE).passed()
                        + " withStrictRule=" + outcomes.get(Arm.WITH_STRICT_RULE).passed()
                        + " (answered: without=" + without.answered()
                        + " withAll=" + outcomes.get(Arm.WITH_ALL).answered()
                        + " withRule=" + outcomes.get(Arm.WITH_RULE).answered() + ")");
                int named = 0;
                for (String name : outcomes.get(Arm.WITH_RULE).citedConceptNames()) {
                    if (admittedByConceptId.containsValue(name) && named < 3) {
                        System.out.println("[BENEFIT]   the case " + entry.getKey()
                                + " cites the concept '" + name + "', whose definition the rule "
                                + "admits");
                        named++;
                    }
                }
            }
        }
    }
}
