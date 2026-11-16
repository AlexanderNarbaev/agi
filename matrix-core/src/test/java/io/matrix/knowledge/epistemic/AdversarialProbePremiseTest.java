package io.matrix.knowledge.epistemic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.matrix.knowledge.AbstractionLevel;
import io.matrix.knowledge.Answer;
import io.matrix.knowledge.Claim;
import io.matrix.knowledge.ClaimConflictDetector;
import io.matrix.knowledge.KnowledgeSnapshot;
import io.matrix.knowledge.KnowledgeWorkbench;
import io.matrix.knowledge.Source;
import io.matrix.knowledge.acquisition.AcquisitionCycle;
import io.matrix.knowledge.acquisition.ClasspathCorpus;
import io.matrix.testsupport.RepositoryPaths;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The adversarial probe of the production asking path's premise semantics.
 *
 * <p><b>This file is a REVIEW instrument, not a specification.</b> It does not decide what the
 * right answer is; it tries to break the behaviour that is on the tree and records what it sees.
 * Every assertion here is one the owner's directive states as a requirement:
 *
 * <ul>
 *   <li>&#167;3 P0.1 - "a supported premise may proceed; a refuted premise is explicitly rejected
 *       with source/claim IDs; an unsupported premise is not labeled false";</li>
 *   <li>&#167;3 P0.1 - "Do not invent a new universal semantic parser or hardcode test strings";</li>
 *   <li>&#167;3 P0.2 - "Preserve both sides and their provenance";</li>
 *   <li>&#167;9 - "input assertions never self-confirm"; "source links/spans/digests retained";</li>
 *   <li>&#167;9 - "Epistemic status must separate supported / refuted / unsupported / unresolved /
 *       contradictory / ambiguous".</li>
 * </ul>
 *
 * <p>The cases come from the FROZEN held-out set
 * ({@code docs-v2/engineering/C2A-HELDOUT-SET.json}), read at run time - the probe does not
 * restate the questions, so it cannot drift from the dataset and it cannot smuggle a question's
 * wording into the implementation's favour. The NOVEL-SHAPE cases are written here, by hand, with
 * content the corpus has never seen: they are the control that tells a RULE from a LOOKUP.
 */
class AdversarialProbePremiseTest {

    /** The frozen held-out set of the previous wave. */
    private static final String DATASET = "docs-v2/engineering/C2A-HELDOUT-SET.json";

    /** The research pool's workspace - the corpus the held-out set was written against. */
    private static final String POOL_WORKSPACE = "adversarial-probe-pool";

    private static KnowledgeWorkbench workbench;
    private static String databasePath;
    private static List<JsonNode> heldOutCases;
    private static KnowledgeSnapshot poolSnapshot;

    @TempDir
    Path directory;

    @BeforeAll
    static void prepare() throws Exception {
        Path database = Files.createTempDirectory("adversarial-probe-").resolve("knowledge.sqlite");
        databasePath = database.toString();
        workbench = KnowledgeWorkbench.at(databasePath);

        ClasspathCorpus pool = ClasspathCorpus.extract(AcquisitionCycle.RESEARCH_POOL);
        for (ClasspathCorpus.Entry entry : pool.entries()) {
            workbench.importDocument(POOL_WORKSPACE, entry.path(), entry.file(), entry.language());
        }
        poolSnapshot = workbench.workspace(POOL_WORKSPACE);

        workbench.loadBundledPackage("photosynthesis");
        workbench.loadBundledPackage("logic");

        JsonNode root = new ObjectMapper().readTree(
                Files.readString(RepositoryPaths.root().resolve(DATASET)));
        heldOutCases = new ArrayList<>();
        for (JsonNode node : root.get("cases")) {
            heldOutCases.add(node);
        }
    }

    // -- the probes ------------------------------------------------------------

    @Test
    @DisplayName("probe 1: the frozen held-out set through the production asking path")
    void theHeldOutSetGoesThroughTheProductionPath() {
        assertNotNull(poolSnapshot);
        System.out.println("### PROBE 1 - the production path over the frozen held-out set");
        System.out.println("workspace=" + POOL_WORKSPACE + " claims=" + poolSnapshot.claims().size()
                + " sources=" + poolSnapshot.sources().size());
        for (JsonNode heldOut : heldOutCases) {
            String id = heldOut.get("id").asText();
            String kind = heldOut.get("kind").asText();
            String question = heldOut.get("question").asText();
            AbstractionLevel level = levelOf(heldOut.get("level").asText());

            Answer answer = workbench.ask(POOL_WORKSPACE, question, level);
            String cited = citedPropositions(answer);
            List<String> gold = goldPropositions(heldOut);
            String visible = cited + " " + answer.limitations() + " "
                    + String.valueOf(answer.abstentionReason());
            int goldVisible = 0;
            for (String proposition : gold) {
                if (visible.contains(proposition)) {
                    goldVisible++;
                }
            }
            System.out.println("  " + id + " " + kind
                    + " | answered=" + answer.answered()
                    + " | disposition=" + dispositionOf(answer)
                    + " | claims=" + answer.claims().size()
                    + " | goldVisible=" + goldVisible + "/" + gold.size());
            if (!answer.answered()) {
                System.out.println("      refusal: " + oneLine(answer.abstentionReason()));
            }
            for (String limitation : answer.limitations()) {
                System.out.println("      limitation: " + oneLine(limitation));
            }
            assertNoAbsolutePath(answer, id);
        }
    }

    @Test
    @DisplayName("probe 2: a premise the corpus does not hold is not called false")
    void anUnsupportedPremiseIsNotCalledFalse() {
        System.out.println("### PROBE 2 - premises the corpus knows NOTHING about");
        String[][] probes = {
                {"photosynthesis",
                        "Since tardigrades can survive in a vacuum indefinitely, what does a plant release during the day?"},
                {"photosynthesis",
                        "Since quantum tunnelling makes photosynthesis unnecessary, what is the role of RuBisCO?"},
                {"logic",
                        "Since every syllogism is a valid argument, what are the premises of modus ponens?"},
                {POOL_WORKSPACE,
                        "Did the Sun orbit the Earth, as Ptolemy maintained?"},
                {POOL_WORKSPACE,
                        "Since the Pacific Ocean is shrinking every year, what drives the motion of the tectonic plates?"},
        };
        for (String[] pair : probes) {
            Answer answer = workbench.ask(pair[0], pair[1], AbstractionLevel.INTRODUCTORY);
            String body = body(answer);
            System.out.println("  Q: " + pair[1]);
            System.out.println("     answered=" + answer.answered()
                    + " disposition=" + dispositionOf(answer)
                    + " claims=" + answer.claims().size());
            System.out.println("     reason=" + oneLine(answer.abstentionReason()));
            for (String limitation : answer.limitations()) {
                System.out.println("     limitation: " + oneLine(limitation));
            }
            assertFalse(answer.abstentionReason() != null
                            && answer.abstentionReason().toLowerCase().contains("contradicted"),
                    "an unsupported premise was reported as contradicted: "
                            + answer.abstentionReason());
            assertFalse(body.toLowerCase().contains("the premise of the question is contradicted"),
                    "an unsupported premise was called false: " + body);
            assertNoAbsolutePath(answer, pair[1]);
        }
    }

    @Test
    @DisplayName("probe 3: a supported premise still gets its question answered")
    void aSupportedPremiseStillGetsItsQuestionAnswered() {
        System.out.println("### PROBE 3 - a SUPPORTED premise must still be answered");
        String[][] probes = {
                {"photosynthesis",
                        "Since photosynthesis converts light energy into chemical energy, what is the role of RuBisCO?"},
                {"photosynthesis", "What is photosynthesis?"},
                {"logic", "What is propositional logic?"},
                {POOL_WORKSPACE, "What drives the motion of the tectonic plates?"},
        };
        for (String[] pair : probes) {
            Answer answer = workbench.ask(pair[0], pair[1], AbstractionLevel.INTRODUCTORY);
            System.out.println("  Q: " + pair[1]);
            System.out.println("     answered=" + answer.answered()
                    + " disposition=" + dispositionOf(answer)
                    + " claims=" + answer.claims().size());
            System.out.println("     statement=" + oneLine(answer.statement()));
            assertTrue(answer.answered(),
                    "a question whose premise is supported was not answered: "
                            + oneLine(answer.abstentionReason()));
            assertNoAbsolutePath(answer, pair[1]);
        }
    }

    @Test
    @DisplayName("probe 4: a refuted premise is rejected and names the claim and the source")
    void aRefutedPremiseIsRejectedAndNamesTheClaimAndTheSource() {
        System.out.println("### PROBE 4 - a premise the workspace REFUTES");
        String[][] probes = {
                {"photosynthesis",
                        "Since photosynthesis produces oxygen from carbon dioxide, what does a plant release during the day?"},
                {"logic",
                        "Since every valid argument has true premises, what are the premises of modus ponens?"},
        };
        for (String[] pair : probes) {
            Answer answer = workbench.ask(pair[0], pair[1], AbstractionLevel.INTRODUCTORY);
            String body = body(answer);
            System.out.println("  Q: " + pair[1]);
            System.out.println("     answered=" + answer.answered()
                    + " disposition=" + dispositionOf(answer)
                    + " claims=" + answer.claims().size()
                    + " sources=" + answer.sources().size());
            System.out.println("     refusal: " + oneLine(answer.abstentionReason()));
            for (String limitation : answer.limitations()) {
                System.out.println("     limitation: " + oneLine(limitation));
            }
            assertFalse(answer.answered(),
                    "a refuted premise was answered as if it held: " + body);
            assertNoAbsolutePath(answer, pair[1]);
            String ids = body.replaceAll("[^A-Za-z0-9:_-]", " ");
            boolean namesAClaim = ids.contains("clm-") || ids.contains("cl-");
            boolean namesASource = body.contains("src-")
                    || answer.sources().stream().map(Source::title).anyMatch(t -> body.contains(t))
                    || answer.sources().stream().map(Source::id).anyMatch(i -> body.contains(i));
            System.out.println("     namesAClaimId=" + namesAClaim + " namesASource=" + namesASource);
            assertTrue(namesAClaim, "the rejection names no claim id: " + body);
            assertTrue(namesASource, "the rejection names no source: " + body);
        }
    }

    @Test
    @DisplayName("probe 5: a conflict reaches the caller with both sides and their provenance")
    void aConflictSurfacesBothSidesWithTheirProvenance() {
        System.out.println("### PROBE 5 - the contradictory cases of the frozen set");
        int conflictsSeen = 0;
        int bothSides = 0;
        for (JsonNode heldOut : heldOutCases) {
            if (!"CONTRADICTORY".equals(heldOut.get("kind").asText())) {
                continue;
            }
            conflictsSeen++;
            String question = heldOut.get("question").asText();
            Answer answer = workbench.ask(POOL_WORKSPACE, question, levelOf(heldOut.get("level").asText()));
            List<String> gold = goldPropositions(heldOut);
            String cited = citedPropositions(answer);
            String body = body(answer);
            boolean first = visible(cited, body, gold.get(0));
            boolean second = visible(cited, body, gold.get(1));
            if (first && second) {
                bothSides++;
            }
            System.out.println("  " + heldOut.get("id").asText()
                    + " | answered=" + answer.answered()
                    + " | disposition=" + dispositionOf(answer)
                    + " | claims=" + answer.claims().size()
                    + " | side1=" + first + " side2=" + second);
            for (String limitation : answer.limitations()) {
                System.out.println("      limitation: " + oneLine(limitation));
            }
            assertNoAbsolutePath(answer, heldOut.get("id").asText());
        }
        System.out.println("  conflicts=" + conflictsSeen + " bothSidesVisible=" + bothSides);
        assertTrue(bothSides == conflictsSeen,
                "a conflict did not reach the caller with both sides: " + bothSides
                        + " of " + conflictsSeen);
    }

    @Test
    @DisplayName("probe 6: the same premise in different words is treated the same")
    void aParaphraseIsTreatedLikeTheOriginal() {
        System.out.println("### PROBE 6 - a paraphrase against its original");
        // The originals are READ from the frozen regression set at run time; a probe may
        // name the artefact, but it may not carry the set's content. The paraphrases are
        // the reviewer's own negative controls, written by hand.
        String[][] pairs = {
                {questionOf("H-U06"),
                        "Do the reversals of the geomagnetic field happen at regular intervals through the history of the Earth?"},
                {questionOf("H-U04"),
                        "Are the lavas of intraplate volcanoes chemically the same as mid-ocean ridge basalts?"},
        };
        for (String[] pair : pairs) {
            Answer original = workbench.ask(POOL_WORKSPACE, pair[0], AbstractionLevel.INTRODUCTORY);
            Answer paraphrase = workbench.ask(POOL_WORKSPACE, pair[1], AbstractionLevel.INTRODUCTORY);
            List<String> originalGold = goldPropositions(goldOf(pair[0]));
            List<String> paraphraseGold = goldPropositions(goldOf(pair[1]));
            boolean originalVisible = visible(citedPropositions(original), body(original), originalGold);
            boolean paraphraseVisible = visible(citedPropositions(paraphrase), body(paraphrase), paraphraseGold);
            String originalStatus = statusOf(original);
            String paraphraseStatus = statusOf(paraphrase);
            System.out.println("  original:    answered=" + original.answered()
                    + " status=" + originalStatus
                    + " goldVisible=" + originalVisible);
            System.out.println("  paraphrase:  answered=" + paraphrase.answered()
                    + " status=" + paraphraseStatus
                    + " goldVisible=" + paraphraseVisible);
            assertTrue(originalVisible,
                    "the original question did not surface the corpus's denial");
            assertTrue(originalStatus.equals(paraphraseStatus),
                    "the paraphrase got a different premise disposition ("
                            + originalStatus + " against " + paraphraseStatus + ")");
        }
    }

    @Test
    @DisplayName("probe 7: a novel question of the same shape behaves like the frozen one")
    void aNovelQuestionOfTheSameShapeBehavesLikeTheFrozenOne() {
        System.out.println("### PROBE 7 - novel content, the same grammatical shape");
        String[][] probes = {
                {POOL_WORKSPACE, "In the research pool, which plate does the word mantle name?"},
                {POOL_WORKSPACE, "In the research pool, what does the term basalt denote?"},
                {POOL_WORKSPACE, "In the research pool, which kinds of boundary are distinguished, and what tells them apart?"},
        };
        for (String[] pair : probes) {
            Answer answer = workbench.ask(pair[0], pair[1], AbstractionLevel.INTRODUCTORY);
            System.out.println("  Q: " + pair[1]);
            System.out.println("     answered=" + answer.answered()
                    + " disposition=" + dispositionOf(answer)
                    + " claims=" + answer.claims().size());
            for (String limitation : answer.limitations()) {
                System.out.println("     limitation: " + oneLine(limitation));
            }
            assertNoAbsolutePath(answer, pair[1]);
        }
    }

    @Test
    @DisplayName("probe 8: concurrent asks complete")
    void concurrentAsksComplete() throws Exception {
        System.out.println("### PROBE 8 - concurrent asks on one workbench");
        int threads = 4;
        int perThread = 3;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger failures = new AtomicInteger();
        AtomicReference<String> firstFailure = new AtomicReference<>();
        List<Thread> workers = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            final int index = i;
            Thread worker = new Thread(() -> {
                try {
                    start.await();
                    for (int n = 0; n < perThread; n++) {
                        workbench.ask(POOL_WORKSPACE,
                                "What drives the motion of the tectonic plates?", AbstractionLevel.INTRODUCTORY);
                        workbench.ask(POOL_WORKSPACE,
                                "Since the plates move apart at mid-ocean ridges, how fast do they spread?",
                                AbstractionLevel.INTRODUCTORY);
                    }
                } catch (Throwable e) {
                    failures.incrementAndGet();
                    firstFailure.compareAndSet(null, e.toString());
                } finally {
                    done.countDown();
                }
            }, "adversarial-probe-ask-" + index);
            workers.add(worker);
            worker.start();
        }
        start.countDown();
        boolean finished = done.await(180, TimeUnit.SECONDS);
        for (Thread worker : workers) {
            worker.join(5_000);
        }
        System.out.println("  finished=" + finished + " failures=" + failures.get()
                + " first=" + firstFailure.get());
        assertTrue(finished, "the concurrent asks did not finish in 180 seconds - a lock was taken twice");
        assertTrue(failures.get() == 0, "a concurrent ask failed: " + firstFailure.get());
    }

    // -- the helpers ------------------------------------------------------------

    /** The disposition the answer carries, read reflectively so the probe survives a change of shape. */
    private static String dispositionOf(Answer answer) {
        try {
            Object disposition = Answer.class.getMethod("premise").invoke(answer);
            if (disposition == null) {
                return "NONE";
            }
            Object status = disposition.getClass().getMethod("status").invoke(disposition);
            Object reason = disposition.getClass().getMethod("reason").invoke(disposition);
            Object conflicts = disposition.getClass().getMethod("conflicts").invoke(disposition);
            Object readings = disposition.getClass().getMethod("readings").invoke(disposition);
            return status + " | conflicts=" + ((List<?>) conflicts).size()
                    + " readings=" + ((List<?>) readings).size()
                    + " | " + oneLine(String.valueOf(reason));
        } catch (ReflectiveOperationException e) {
            return "NO-DISPOSITION-FIELD (" + e.getClass().getSimpleName() + ")";
        }
    }

    /** The premise status of an answer, read reflectively. */
    private static String statusOf(Answer answer) {
        try {
            Object disposition = Answer.class.getMethod("premise").invoke(answer);
            if (disposition == null) {
                return "NONE";
            }
            return String.valueOf(disposition.getClass().getMethod("status").invoke(disposition));
        } catch (ReflectiveOperationException e) {
            return "NO-DISPOSITION-FIELD";
        }
    }

    /** The body of an answer - everything the caller sees. */
    private static String body(Answer answer) {
        return answer.statement() + " " + answer.abstentionReason() + " "
                + answer.limitations() + " " + answer.gaps() + " " + answer.unmatchedTerms();
    }

    private static String citedPropositions(Answer answer) {
        List<String> propositions = new ArrayList<>();
        for (Claim claim : answer.claims()) {
            propositions.add(claim.proposition());
        }
        return String.join(" || ", propositions);
    }

    private static boolean visible(String cited, String body, String proposition) {
        return proposition != null && !proposition.isBlank()
                && (cited.contains(proposition) || body.contains(proposition));
    }

    private static boolean visible(String cited, String body, List<String> propositions) {
        for (String proposition : propositions) {
            if (!visible(cited, body, proposition)) {
                return false;
            }
        }
        return !propositions.isEmpty();
    }

    /** The question of a case of the frozen regression set, read at run time. */
    private static String questionOf(String caseId) {
        for (JsonNode heldOut : heldOutCases) {
            if (caseId.equals(heldOut.get("id").asText())) {
                return heldOut.get("question").asText();
            }
        }
        throw new IllegalStateException("the frozen set holds no case " + caseId);
    }

    /** The gold claims of the case that asks this question. */
    private static JsonNode goldOf(String question) {
        for (JsonNode heldOut : heldOutCases) {
            if (question.equals(heldOut.get("question").asText())) {
                return heldOut;
            }
        }
        return null;
    }

    private static List<String> goldPropositions(JsonNode heldOut) {
        List<String> propositions = new ArrayList<>();
        if (heldOut == null || heldOut.get("goldClaims") == null) {
            return propositions;
        }
        for (JsonNode gold : heldOut.get("goldClaims")) {
            propositions.add(gold.get("proposition").asText());
        }
        return propositions;
    }

    /** The path confinement: no absolute path in any body, answer or refusal. */
    private static void assertNoAbsolutePath(Answer answer, String where) {
        String body = body(answer);
        for (String marker : List.of(databasePath, "/home/", "/root/", "/tmp/", "file:/", "\\\\")) {
            if (body.contains(marker)) {
                fail("the response body of " + where + " carries the absolute path marker "
                        + marker + ": " + body);
            }
        }
        for (Source source : answer.sources()) {
            String locator = source.locator();
            if (locator == null) {
                continue;
            }
            // A URL is not a server path. A FILESYSTEM path is.
            if (locator.startsWith("/") || locator.startsWith("\\\\")
                    || locator.matches("^[A-Za-z]:\\\\.*")) {
                fail("the response body of " + where + " carries a filesystem path in a source "
                        + "locator: " + locator);
            }
        }
    }

    private static AbstractionLevel levelOf(String level) {
        return switch (level == null ? "" : level.toUpperCase()) {
            case "INTRODUCTORY", "INTRO" -> AbstractionLevel.INTRODUCTORY;
            case "ADVANCED", "ADV" -> AbstractionLevel.ADVANCED;
            default -> AbstractionLevel.INTRODUCTORY;
        };
    }

    private static String oneLine(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("\\s+", " ").trim();
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// ROUND 2 — the implementer's claims, checked independently.
//
// 9  the conflict detector on the logic package: the count, and each pair
// 10 a genuine contradiction of the SAME assertion is still caught
// 11 the frozen conflict case reaches the caller with both sides
// 12 the frozen ambiguity cases surface the readings they NAME (a match,
//    not a count — the harness counts, it does not match)
// 13 the two open defects
// ═══════════════════════════════════════════════════════════════════════════
class AdversarialProbeRound2Test {

    /** The frozen epistemic held-out set of the P0.3 wave. */
    private static final String EPISTEMIC_DATASET =
            "docs-v2/engineering/EPISTEMIC-HELDOUT-SET.json";

    /** The research pool's workspace. */
    private static final String POOL_WORKSPACE = "adversarial-probe-pool";

    private static KnowledgeWorkbench workbench;
    private static List<JsonNode> cases;
    private static KnowledgeSnapshot pool;

    @TempDir
    Path directory;

    @BeforeAll
    static void prepare() throws Exception {
        Path database = Files.createTempDirectory("adversarial-probe-r2-").resolve("knowledge.sqlite");
        workbench = KnowledgeWorkbench.at(database.toString());
        ClasspathCorpus corpus = ClasspathCorpus.extract(AcquisitionCycle.SEED_CORPUS);
        for (ClasspathCorpus.Entry entry : corpus.entries()) {
            workbench.importDocument(POOL_WORKSPACE, entry.path(), entry.file(), entry.language());
        }
        pool = workbench.workspace(POOL_WORKSPACE);
        workbench.loadBundledPackage("photosynthesis");
        workbench.loadBundledPackage("logic");
        JsonNode root = new ObjectMapper().readTree(
                Files.readString(RepositoryPaths.root().resolve(EPISTEMIC_DATASET)));
        cases = new ArrayList<>();
        for (JsonNode node : root.get("cases")) {
            cases.add(node);
        }
    }

    @Test
    @DisplayName("probe 9: the conflict detector on the logic package")
    void theConflictDetectorOnTheLogicPackage() {
        KnowledgeSnapshot logic = workbench.workspace("logic");
        Map<String, Source> sources = new HashMap<>();
        for (Source source : logic.sources()) {
            sources.put(source.id(), source);
        }
        List<ClaimConflictDetector.Conflict> found =
                ClaimConflictDetector.detect(logic.claims(), sources);
        System.out.println("### PROBE 9 - the logic package: claims=" + logic.claims().size()
                + " conflicts=" + found.size());
        for (ClaimConflictDetector.Conflict conflict : found) {
            System.out.println("  reason: " + conflict.reason());
            for (ClaimConflictDetector.Side side : conflict.sides()) {
                System.out.println("     side " + side.claimId() + ": " + side.proposition());
            }
        }
        // the pairs the reviewer named as FALSE in round 1 must not be reported
        for (ClaimConflictDetector.Conflict conflict : found) {
            String text = conflict.sides().toString();
            assertFalse(text.contains("implies the propositional variable q"),
                    "a conflict was reported between UNRELATED propositions: " + conflict.reason());
            assertFalse(text.contains("false in the assignment under consideration.")
                            && text.contains("not true in the other assignment"),
                    "a conflict was reported between accounts of DIFFERENT assignments: "
                            + conflict.reason());
            assertFalse(text.contains("false in the assignment under consideration.")
                            && text.contains("not true in the assignment"),
                    "a conflict was reported between two accounts that SAY THE SAME THING: "
                            + conflict.reason());
        }
    }

    @Test
    @DisplayName("probe 10: a genuine contradiction of the same assertion is still caught")
    void aGenuineContradictionIsStillCaught() {
        KnowledgeSnapshot logic = workbench.workspace("logic");
        Map<String, Source> sources = new HashMap<>();
        for (Source source : logic.sources()) {
            sources.put(source.id(), source);
        }
        Claim asserts = null;
        Claim denies = null;
        for (Claim claim : logic.claims()) {
            if (claim.proposition().startsWith("The propositional variable p is true in the assignment")) {
                asserts = claim;
            }
            if (claim.proposition().startsWith("The propositional variable p is false in the assignment")) {
                denies = claim;
            }
        }
        System.out.println("### PROBE 10 - the same assertion, the opposite polarity");
        System.out.println("  asserts: " + (asserts == null ? "none" : asserts.id()));
        System.out.println("  denies:  " + (denies == null ? "none" : denies.id()));
        assertNotNull(asserts, "the logic package holds no 'p is true in the assignment' claim");
        assertNotNull(denies, "the logic package holds no 'p is false in the assignment' claim");
        List<ClaimConflictDetector.Conflict> found =
                ClaimConflictDetector.detect(List.of(asserts, denies), sources);
        System.out.println("  conflicts=" + found.size());
        for (ClaimConflictDetector.Conflict conflict : found) {
            System.out.println("  reason: " + conflict.reason());
            for (ClaimConflictDetector.Side side : conflict.sides()) {
                System.out.println("     side " + side.claimId() + ": " + side.proposition());
            }
        }
        assertTrue(!found.isEmpty(),
                "the same assertion with the opposite polarity was NOT reported as a conflict");
    }

    @Test
    @DisplayName("probe 11: the frozen conflict case reaches the caller with both sides")
    void theFrozenConflictCaseReachesTheCaller() {
        JsonNode heldOut = caseOf("E-C01");
        Answer answer = workbench.ask(POOL_WORKSPACE, heldOut.get("question").asText(),
                AbstractionLevel.INTRODUCTORY);
        String body = body(answer);
        System.out.println("### PROBE 11 - E-C01 (CONFLICTING_EVIDENCE)");
        System.out.println("  answered=" + answer.answered()
                + " disposition=" + dispositionOf(answer));
        int visible = 0;
        for (JsonNode gold : heldOut.get("goldClaims")) {
            String proposition = gold.get("proposition").asText();
            boolean seen = body.contains(proposition);
            if (seen) {
                visible++;
            }
            System.out.println("  gold " + gold.get("claimId").asText() + " visible=" + seen
                    + " | " + proposition.substring(0, Math.min(90, proposition.length())));
        }
        System.out.println("  sides surfaced=" + visible + "/" + heldOut.get("goldClaims").size());
        for (String limitation : answer.limitations()) {
            System.out.println("  limitation: " + oneLine(limitation));
        }
    }

    @Test
    @DisplayName("probe 12: the frozen ambiguity cases surface the readings they name")
    void theFrozenAmbiguityCasesSurfaceTheirReadings() {
        for (String id : List.of("E-A01", "E-A02")) {
            JsonNode heldOut = caseOf(id);
            Answer answer = workbench.ask(POOL_WORKSPACE, heldOut.get("question").asText(),
                    AbstractionLevel.INTRODUCTORY);
            String body = body(answer);
            int named = 0;
            int matched = 0;
            System.out.println("### PROBE 12 - " + id + " (AMBIGUOUS_READINGS)");
            System.out.println("  disposition=" + dispositionOf(answer));
            for (JsonNode reading : heldOut.get("readings")) {
                String label = reading.get("reading").asText();
                for (JsonNode span : reading.get("spans")) {
                    named++;
                    String proposition = span.get("proposition").asText();
                    boolean seen = body.contains(proposition);
                    if (seen) {
                        matched++;
                    }
                    System.out.println("  reading \"" + label + "\" -> span visible=" + seen
                            + " | " + proposition.substring(0, Math.min(80, proposition.length())));
                }
            }
            System.out.println("  readings NAMED=" + named + " MATCHED=" + matched
                    + " reported=" + (answer.premise() == null ? 0 : answer.premise().readings().size()));
        }
    }

    @Test
    @DisplayName("probe 13: the two open defects")
    void theTwoOpenDefects() {
        System.out.println("### PROBE 13 - the two open defects");
        System.out.println("  (a) the photosynthesis premise the corpus states");
        Answer a = workbench.ask("photosynthesis",
                "Since plants release oxygen during the day, what does a plant release during the day?",
                AbstractionLevel.INTRODUCTORY);
        System.out.println("     answered=" + a.answered() + " disposition=" + dispositionOf(a));
        System.out.println("     refusal=" + oneLine(a.abstentionReason()));
        for (String limitation : a.limitations()) {
            System.out.println("     limitation: " + oneLine(limitation));
        }
        System.out.println("  (b) the paraphrase pairs of the frozen set");
        for (String id : List.of("E-S01", "E-S02", "E-P01", "E-P02")) {
            JsonNode heldOut = caseOf(id);
            Answer answer = workbench.ask(POOL_WORKSPACE, heldOut.get("question").asText(),
                    AbstractionLevel.INTRODUCTORY);
            System.out.println("     " + id + " answered=" + answer.answered()
                    + " status=" + statusOf(answer)
                    + " | " + heldOut.get("question").asText());
        }
    }

    // ── the helpers ──────────────────────────────────────────────────────────

    private static JsonNode caseOf(String id) {
        for (JsonNode heldOut : cases) {
            if (id.equals(heldOut.get("id").asText())) {
                return heldOut;
            }
        }
        throw new IllegalStateException("the frozen set holds no case " + id);
    }

    private static String body(Answer answer) {
        StringBuilder text = new StringBuilder();
        text.append(answer.statement()).append(' ').append(answer.abstentionReason()).append(' ');
        text.append(answer.limitations()).append(' ');
        if (answer.premise() != null) {
            text.append(answer.premise().readings()).append(' ');
            text.append(answer.premise().conflicts()).append(' ');
        }
        return text.toString();
    }

    private static String dispositionOf(Answer answer) {
        if (answer.premise() == null) {
            return "NONE";
        }
        return answer.premise().status() + " | conflicts="
                + answer.premise().conflicts().size()
                + " readings=" + answer.premise().readings().size()
                + " | " + oneLine(answer.premise().reason());
    }

    private static String statusOf(Answer answer) {
        return answer.premise() == null ? "NONE" : String.valueOf(answer.premise().status());
    }

    private static String oneLine(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").trim();
    }
}
