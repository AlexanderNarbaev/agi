package io.matrix.brain.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RECON-W31.2 — SIM-1 stopword-aware similarity, EPI-2 measured confidence.
 *
 * <p>W31.2 measured the production retrieval function before changing it. Over 28
 * paraphrases of known facts and 20 questions the clean store cannot answer,
 * {@link PersistentHdcStore#cosine} scored <b>ROC-AUC 0.502</b> — chance — and served
 * <b>12 of 20</b> unknowable questions as confident answers. These tests encode that
 * finding and the replacement, so a regression to the old behaviour fails here rather
 * than on a live question the operator has to notice.</p>
 */
class ContentSimilarityTest {

    // ---- SIM-1: the cause was the interrogative scaffolding ---------------

    @Test
    void stopwordsAreNotContent() {
        assertTrue(ContentSimilarity.contentTokens("what is the capital of france")
            .contains("france"));
        assertTrue(ContentSimilarity.contentTokens("what is the capital of france")
            .contains("capital"));
        for (String sw : List.of("what", "is", "the", "of")) {
            assertFalse(ContentSimilarity.contentTokens("what is the capital of france")
                .contains(sw), "stopword must be dropped: " + sw);
        }
    }

    @Test
    void russianStopwordsAreDroppedToo() {
        // The RU probes are in the live transcript ("Столица Франции?"), so a
        // stopword list that is English-only leaves the RU path uncorrected.
        List<String> toks = ContentSimilarity.contentTokens("столица Франции");
        assertFalse(toks.contains("что"), toks.toString());
        assertTrue(toks.stream().anyMatch(t -> t.startsWith("столиц")), toks.toString());
        assertTrue(toks.stream().anyMatch(t -> t.startsWith("франц")), toks.toString());
    }

    @Test
    void russianInflectionsReduceToACommonStem() {
        // W31.4 measured the RU path at 0/8 on held-out probes while English scored
        // 15/15. The cause was morphology, not data: a question about "Кении"
        // (genitive) could not match a fact about "Кения" (nominative), so the two
        // shared no tokens at all and the score was exactly 0.
        // The two phrases share two stems, not one: the name ("кени") and the relation
        // ("столиц"). Asserting on the first token would compare two different words.
        List<String> question = ContentSimilarity.contentTokens("Столица Кении?");
        List<String> fact = ContentSimilarity.contentTokens("Кения имеет столицу Найроби");
        assertEquals(ContentSimilarity.stem("Кении"), ContentSimilarity.stem("Кения"),
            "genitive and nominative of the same name must reduce to one stem");
        assertTrue(question.containsAll(List.of("столиц", "кени")), question.toString());
        assertTrue(fact.containsAll(List.of("столиц", "кени")), fact.toString());
    }

    @Test
    void aRussianCapitalQuestionNowMatchesItsFact() {
        // The end-to-end property the stemming exists for.
        double s = ContentSimilarity.score(
            "Столица Кении?", "Кения имеет столицу Найроби");
        assertTrue(s >= ContentSimilarity.RETRIEVAL_FLOOR,
            "the RU capital question must reach its fact, scored " + s);
    }

    @Test
    void theLiveFabricationPairNowScoresNearZero() {
        // The exact failure: "What is the chemical formula of water?" was answered
        // "9.8 m/s^2" because the old function scored 0.300 on shared scaffolding.
        double s = ContentSimilarity.score(
            "What is the chemical formula of water?", "gravity => 9.8 m/s^2");
        assertTrue(s < ContentSimilarity.RETRIEVAL_FLOOR,
            "must fall below the floor, was " + s);
    }

    @Test
    void oldFunctionWasAtChanceOnThisPairTheNewOneIsNot() {
        // Documented comparison, asserted so the improvement cannot silently revert.
        //
        // The fact is the one the live system actually served, canonical-db74ff0707ca554f
        // = "What is gravity? => 9.8 m/s^2". Under the old function that pair scored
        // EXACTLY 0.200 against a floor of `bestScore < 0.20`, so a boundary-coincident
        // match was served as a confident answer to a chemistry question. The shorter
        // form "gravity => 9.8 m/s^2" scores 0.000 under the old function, which is
        // why the specific stored form matters and the test names it.
        String fact = "What is gravity? => 9.8 m/s^2";
        double old = PersistentHdcStore.cosine(
            PersistentHdcStore.hashToVector("What is the chemical formula of water?", 512),
            PersistentHdcStore.hashToVector(fact, 512));
        double now = ContentSimilarity.score(
            "What is the chemical formula of water?", fact);
        assertTrue(old >= 0.20,
            "precondition: the old function really did serve this, scored " + old);
        assertTrue(now < old, "new score " + now + " must beat old " + old);
        assertTrue(now < ContentSimilarity.RETRIEVAL_FLOOR,
            "and it must now fall under the floor, scored " + now);
    }

    @Test
    void contentlessInputIsNeverConfident() {
        // A question that is pure scaffolding has no evidence to offer, and must not be
        // answered from it.
        assertTrue(ContentSimilarity.isContentless("what is it"));
        assertEquals(0.0, ContentSimilarity.score("what is it", "Paris is the capital of France"));
        assertFalse(ContentSimilarity.isConfident("what is it", "anything at all"));
    }

    // ---- SIM-1: and it must still retrieve what IS known ------------------

    @Test
    void retrievesAKnownFactFromAParaphrase() {
        double s = ContentSimilarity.score(
            "Which city is France's capital?", "Paris is the capital of France");
        assertTrue(s >= ContentSimilarity.RETRIEVAL_FLOOR,
            "a known fact must remain retrievable, scored " + s);
    }

    @Test
    void nearMissEntitiesAreRefused() {
        // "capital of Australia" scored 0.500 under the old function because "capital
        // is the" scaffolding matched the France fact. Australia is NOT in the store.
        assertFalse(ContentSimilarity.isConfident("What is the capital of Australia?",
            "Paris is the capital of France"));
        assertFalse(ContentSimilarity.isConfident("capital of Peru?",
            "Paris is the capital of France"));
    }

    // ---- EPI-2: confidence must be measured, and must vary ----------------

    @Test
    void confidenceVariesWithEvidenceRatherThanRepeatingOneConstant() {
        // The operator saw 0.75 on everything, including fabrications. Two real hits of
        // different strength must not report the same number.
        var weak = ContentSimilarity.confidenceFor(0.30);
        var strong = ContentSimilarity.confidenceFor(0.90);
        assertNotEquals(weak.value(), strong.value(),
            "confidence must track evidence, not repeat a constant");
        assertTrue(strong.value() > weak.value(), "more evidence must mean more confidence");
        assertTrue(weak.isMeasured() && strong.isMeasured());
    }

    @Test
    void aRefusalReportsZeroNotTheDefaultConstant() {
        var below = ContentSimilarity.confidenceFor(0.05);
        assertEquals(0.0, below.value(), "below the floor means no confidence, not 0.75");
        assertTrue(below.isMeasured(), "zero from a measured score is still measured");
    }

    @Test
    void defaultedConfidenceIsLabelledAsDefaulted() {
        // EPI-2: a constant must be distinguishable from a measurement at the type
        // level, so a consumer cannot report it as if it were earned.
        var d = ContentSimilarity.ConfidenceEvidence.defaulted("stage had no score");
        assertFalse(d.isMeasured(), "a default must never claim to be measured");
        assertEquals(ContentSimilarity.Source.DEFAULTED, d.source());
        assertEquals(ContentSimilarity.DEFAULT_CONFIDENCE, d.value());
    }

    @Test
    void measuredConfidenceStaysInRange() {
        for (double s : List.of(-1.0, 0.0, 0.2, 0.5, 1.0, 2.0, Double.NaN)) {
            double v = ContentSimilarity.confidenceFor(s).value();
            assertTrue(v >= 0.0 && v <= 1.0, "confidence out of range for score " + s);
        }
    }

    // ---- EPI-3: structured ignorance --------------------------------------

    @ParameterizedTest(name = "unknowable question is refused: {0}")
    @ValueSource(strings = {
        "How many legs does a spider have?",
        "What is the chemical formula of water?",
        "Who was the first person on the moon?",
        "What is the capital of Australia?",
        "Which ocean is the largest?",
        "What is the atomic number of carbon?",
        "Who discovered penicillin?",
    })
    void unknowableQuestionsAreRefusedRatherThanFabricated(String q) {
        // These are the questions the live mind answered confidently before W31.2.
        assertFalse(ContentSimilarity.isConfident(q, "Paris is the capital of France"),
            "must not answer from an unrelated fact: " + q);
    }

    // ---- The published numbers must stay true -----------------------------

    @Test
    void theFloorMatchesThePublishedRocNotAGuess() {
        // TUNING-PARAMETERS.md quotes: under the content-aware function the 20
        // unknowable questions topped out at 0.167, and the floor sits at 0.20 — above
        // that maximum, so none is served. If the function changes, this fails and the
        // document has to be re-derived rather than left to rot.
        double worstNegative = 0.0;
        for (String q : List.of(
                "How many legs does a spider have?",
                "What is the chemical formula of water?",
                "Who was the first person on the moon?",
                "What is the capital of Australia?",
                "What is the boiling point of mercury?",
                "What is the largest planet?",
                "How do I bake bread?",
                "Who wrote the novel Dune?",
                "What is the capital of Brazil?",
                "How many continents are there?",
                "Who painted the Mona Lisa?",
                "What is the atomic number of carbon?",
                "Which ocean is the largest?",
                "What is the speed of sound in air?",
                "Who discovered penicillin?",
                "What is the tallest mountain on Earth?",
                "How many days are in a year?",
                "What language is spoken in Brazil?",
                "What is the capital of Germany?",
                "What is gravity?")) {
            worstNegative = Math.max(worstNegative,
                ContentSimilarity.score(q, "Paris is the capital of France"));
        }
        assertTrue(worstNegative < ContentSimilarity.RETRIEVAL_FLOOR,
            "floor must sit above the worst unknowable-question score, which was "
                + worstNegative);
    }

    @Test
    void scoringIsDeterministic() {
        // Article III: same input, same score, every time.
        String q = "What is the capital of France?";
        String f = "Paris is the capital of France";
        double first = ContentSimilarity.score(q, f);
        for (int i = 0; i < 50; i++) {
            assertEquals(first, ContentSimilarity.score(q, f), "scoring must be pure");
        }
    }

    // ---- W31.4: discriminative weighting ---------------------------------

    /**
     * Corpus where "boiling" and "point" appear in many facts, "mercury" in one.
     * Models a real knowledge base about chemistry plus a general-topic section.
     */
    private static java.util.function.ToDoubleFunction<String> chemistryCorpusIdf(int corpusSize) {
        java.util.Map<String, Integer> df = new java.util.HashMap<>();
        for (int i = 0; i < 200; i++) {          // generic topic facts
            df.merge("boiling", corpusSize, Integer::sum);
            df.merge("point", corpusSize, Integer::sum);
            df.merge("water", 200, Integer::sum);
        }
        df.merge("mercury", 1, Integer::sum);     // the discriminating token
        return tok -> ContentSimilarity.idf(df.getOrDefault(tok, 0), corpusSize);
    }

    @Test
    void weightingRefusesTheNearMissThatUnweightedScoreServed() {
        // The measured false positive: "boiling point of mercury" matched a water fact
        // at 0.267 and was served, because "boiling" and "point" were weighted the same
        // as "mercury".
        String q = "What is the boiling point of mercury?";
        String wrongFact = "The boiling point of water is 100 degrees";
        double unweighted = ContentSimilarity.score(q, wrongFact);
        assertTrue(unweighted >= ContentSimilarity.RETRIEVAL_FLOOR,
            "precondition: the unweighted score really did clear the floor, was " + unweighted);

        double weighted = ContentSimilarity.weightedScore(q, wrongFact, chemistryCorpusIdf(400));
        assertTrue(weighted < ContentSimilarity.RETRIEVAL_FLOOR,
            "weighting must push the near-miss below the floor, was " + weighted);
    }

    @Test
    void weightingStillServesTheCorrectFact() {
        // The other half: a fix that only ever refuses is a wall, not a fix.
        String q = "What is the boiling point of mercury?";
        String rightFact = "The boiling point of mercury is 357 degrees Celsius";
        double weighted = ContentSimilarity.weightedScore(q, rightFact, chemistryCorpusIdf(400));
        assertTrue(weighted >= ContentSimilarity.RETRIEVAL_FLOOR,
            "the correct fact must still be retrievable, scored " + weighted);
    }

    @Test
    void weightingPreservesExactMatchesInALargeCorpus() {
        // Regression guard for the opposite failure: if every weight collapsed to zero
        // in a large corpus, a large store would refuse correct answers and recall
        // would die silently. This is the reason MIN_IDF_WEIGHT exists.
        java.util.function.ToDoubleFunction<String> alwaysCommon =
            tok -> ContentSimilarity.idf(400, 400);   // every token in every fact
        double s = ContentSimilarity.weightedScore(
            "What is the capital of France?", "Paris is the capital of France", alwaysCommon);
        assertTrue(s >= ContentSimilarity.RETRIEVAL_FLOOR,
            "an exact match must survive a corpus where its tokens are common, scored " + s);
    }

    @Test
    void weightingFallsBackToUnweightedWhenNoStatisticsAreSupplied() {
        double a = ContentSimilarity.weightedScore("What is the capital of France?",
            "Paris is the capital of France", null);
        assertEquals(ContentSimilarity.score("What is the capital of France?",
            "Paris is the capital of France"), a, 0.0);
    }

    @Test
    void idfIsMonotonicInRarityAndBounded() {
        assertTrue(ContentSimilarity.idf(0, 100) > ContentSimilarity.idf(1, 100),
            "a rarer token must weigh more");
        assertEquals(0.0, ContentSimilarity.idf(100, 100), 1e-12, "everywhere = 0");
        assertEquals(1.0, ContentSimilarity.idf(0, 100), 1e-12, "nowhere = 1");
        assertEquals(1.0, ContentSimilarity.idf(5, 0), 1e-12, "unknown corpus = max rarity");
        for (int df : new int[]{-5, 0, 50, 100, 500}) {
            double w = ContentSimilarity.idf(df, 100);
            assertTrue(w >= 0.0 && w <= 1.0, "out of range for df=" + df);
        }
    }
}
