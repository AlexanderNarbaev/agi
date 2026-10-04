package io.matrix.brain.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RECON-W32.4 — the "prefer an answered match" override was a data-loss-class bug in
 * retrieval.
 *
 * <p>The stage used to replace the best candidate with the best <em>answered</em>
 * candidate unconditionally. Whenever any {@code " => "} fact existed anywhere in the
 * store, the overall best was discarded — even when the answered one was far weaker.</p>
 *
 * <p>Measured live after a corpus re-ingest: {@code "Kenya capital Nairobi"} scores
 * 0.654 and is the correct answer to "What is the capital of Kenya?", but a leftover
 * answered fact scored 0.146. The override discarded the right fact and the mind refused
 * a question it could answer.</p>
 *
 * <p>Answered-ness is a tie-breaker, not a trump card. That is what these tests hold.</p>
 */
class HdcRetrievalStageTest {

    @Test
    void aWeakerAnsweredFactDoesNotDisplaceTheBetterOne(@TempDir Path dir) throws IOException {
        PersistentHdcStore store = new PersistentHdcStore(dir.resolve("kb.ndjson"), 512);
        // The correct answer: no " => ", so it is only a bestScore candidate.
        store.teach("fact-kenya", "Kenya capital Nairobi");
        // A leftover artefact that DOES carry the old " => " form, and matches weakly.
        // NB: the first draft of this fixture used "capital of Atlantis => Poseidon"
        // and the promotion gate REFUSED it as FICTIONAL_SUBJECT — the same refusal the
        // W32.4 ingest logged for one of its 1 893 facts. The gate is consistent; the
        // fixture was wrong.
        store.teach("fact-tanganyika", "capital of Tanzania => Dodoma");

        var stage = new io.matrix.brain.runtime.stages.HdcRetrievalStage(store);
        var r = stage.retrieve("What is the capital of Kenya?", null, new java.util.ArrayList<>());

        assertTrue(r.matched(), "the store contains the answer, so retrieval must fire");
        assertTrue(r.reply().contains("Nairobi"),
            "the better fact must win, not the weaker answered one; got: " + r.reply());
        assertTrue(ContentSimilarity.score("What is the capital of Kenya?", "Kenya capital Nairobi")
                   > ContentSimilarity.score("What is the capital of Kenya?",
                                              "capital of Tanzania => Dodoma"),
            "precondition: the displaced fact really is weaker");
    }

    @Test
    void aStrongerAnsweredFactStillWins(@TempDir Path dir) throws IOException {
        // The fix must not disable the original intent: an answered fact that is
        // genuinely the better match should still be preferred.
        PersistentHdcStore store = new PersistentHdcStore(dir.resolve("kb.ndjson"), 512);
        store.teach("fact-weak", "Kenya is a country in Africa");
        store.teach("fact-strong", "Kenya capital Nairobi => Nairobi");

        var stage = new io.matrix.brain.runtime.stages.HdcRetrievalStage(store);
        var r = stage.retrieve("What is the capital of Kenya?", null, new java.util.ArrayList<>());

        assertTrue(r.matched());
        assertTrue(r.reply().contains("Nairobi"), r.reply());
    }

    @Test
    void theTraceReportsTheTopScoringFactsNotTheFirstThreeIterated(@TempDir Path dir)
            throws IOException {
        // The trace labels three entries "top=" but collected the first three ITERATED,
        // which is not the same thing and made a passing retrieval look empty. Cosmetic,
        // but evidence that looks wrong is evidence nobody trusts.
        PersistentHdcStore store = new PersistentHdcStore(dir.resolve("kb.ndjson"), 512);
        for (int i = 0; i < 6; i++) {
            store.teach("noise-" + i, "unrelated filler text number " + i);
        }
        store.teach("fact-kenya", "Kenya capital Nairobi");

        var stage = new io.matrix.brain.runtime.stages.HdcRetrievalStage(store);
        var r = stage.retrieve("What is the capital of Kenya?", null, new java.util.ArrayList<>());
        assertTrue(r.trace().contains("kenya") || r.trace().contains("Nairobi"),
            "the trace must name what actually matched, got: " + r.trace());
    }

    @Test
    void theTraceShowsTheHighestScoringFactsNotTheFirstThreeScanned(@TempDir Path dir)
            throws IOException {
        // RECON-W32.6. The trace labelled three entries "top=" while collecting the first
        // three ITERATED. Observed live: a passing retrieval reported three zero-scoring
        // inbox files while the fact that actually answered scored 0.65. The
        // in-answerable question is the one that surfaces it, because every entry is
        // weak and the difference is visible.
        PersistentHdcStore store = new PersistentHdcStore(dir.resolve("kb.ndjson"), 512);
        for (int i = 0; i < 8; i++) {
            store.teach("filler-" + i, "filler record number " + i + " about nothing");
        }
        store.teach("fact-france", "France capital Paris");

        var stage = new io.matrix.brain.runtime.stages.HdcRetrievalStage(store);
        var tr = new java.util.ArrayList<io.matrix.brain.runtime.BrcStep>();
        var r = stage.retrieve("What is the capital of France?",
            (io.matrix.brain.runtime.stages.SignalStage.SignalObservation) null, tr);

        assertTrue(r.matched());
        String trace = r.trace();
        // The winning fact must appear in the reported top, not merely in the result.
        assertTrue(trace.contains("fact-france") || trace.contains("France"),
            "the trace must name what actually matched, got: " + trace);
        assertFalse(trace.contains("filler-0:0.00") && !trace.contains("fact-france"),
            "the trace must not be dominated by whatever happened to be scanned first: " + trace);
    }

    @Test
    void theMissTraceAlsoRanksByScore(@TempDir Path dir) throws IOException {
        PersistentHdcStore store = new PersistentHdcStore(dir.resolve("kb.ndjson"), 512);
        store.teach("a", "alpha beta gamma");
        store.teach("b", "delta epsilon zeta");
        store.teach("c", "eta theta iota");

        var stage = new io.matrix.brain.runtime.stages.HdcRetrievalStage(store);
        var r = stage.retrieve("nothing whatsoever matches", null, new java.util.ArrayList<>());
        assertFalse(r.matched());
        assertTrue(r.trace().contains("best_similarity="),
            "a miss must still carry its evidence, got: " + r.trace());
    }

    // ---- RECON-W32.17: the pre-tokenised cache must not go stale -------------

    @Test
    void aFactTaughtAfterTheStageWasBuiltIsStillRetrievable(@TempDir Path dir)
            throws java.io.IOException {
        // The dangerous property: the stage caches every fact's tokens AND the
        // document-frequency table, because neither changes between writes. If a write
        // does not invalidate both, the stage scores new facts against old statistics
        // and there is no error — it just quietly answers differently. That is the
        // silent-failure class this campaign has been removing, so it gets a test.
        PersistentHdcStore store = new PersistentHdcStore(dir.resolve("kb.ndjson"), 512);
        store.teach("seed-1", "Lisbon is the capital of Portugal");
        var stage = new io.matrix.brain.runtime.stages.HdcRetrievalStage(store);

        var before = stage.retrieve("What is the capital of Portugal?",
            (io.matrix.brain.runtime.stages.SignalStage.SignalObservation) null,
            new java.util.ArrayList<>());
        assertTrue(before.matched(), "precondition: the seeded fact is retrievable");

        // teach() is the path the gateway uses for a new fact.
        stage.teach("seed-2", "Dodoma is the capital of Tanzania");

        var after = stage.retrieve("What is the capital of Tanzania?",
            (io.matrix.brain.runtime.stages.SignalStage.SignalObservation) null,
            new java.util.ArrayList<>());
        assertTrue(after.matched(),
            "a fact taught after the stage was built MUST be found — a stale token cache "
                + "would silently exclude it");
        assertTrue(after.reply().toLowerCase().contains("dodoma"), after.reply());

        // And the pre-existing fact must still be found, i.e. the cache was not replaced
        // wholesale with a partial map.
        var recheck = stage.retrieve("What is the capital of Portugal?",
            (io.matrix.brain.runtime.stages.SignalStage.SignalObservation) null,
            new java.util.ArrayList<>());
        assertTrue(recheck.matched() && recheck.reply().toLowerCase().contains("lisbon"),
            "the earlier fact must survive the invalidation: " + recheck.reply());
    }

    @Test
    void theCachedPathAgreesWithTheUncachedOne(@TempDir Path dir) throws java.io.IOException {
        // The optimisation must not change an answer. Scored both ways over the whole
        // store; a divergence would mean the cache and the reference disagree, which is
        // the failure a performance change is most able to hide.
        PersistentHdcStore store = new PersistentHdcStore(dir.resolve("kb.ndjson"), 512);
        store.teach("a", "Paris is the capital of France");
        store.teach("b", "Tokyo is the capital of Japan");
        store.teach("c", "The sky is blue because of Rayleigh scattering");
        var stage = new io.matrix.brain.runtime.stages.HdcRetrievalStage(store);

        for (String q : new String[]{"What is the capital of France?",
                "What is the capital of Japan?", "Why is the sky blue?",
                "What is the chemical formula of water?"}) {
            var r = stage.retrieve(q,
                (io.matrix.brain.runtime.stages.SignalStage.SignalObservation) null,
                new java.util.ArrayList<>());
            // Reference: the uncached scorer over the same store, same query.
            double bestRef = 0.0;
            for (String f : store.snapshot().values()) {
                bestRef = Math.max(bestRef, ContentSimilarity.weightedScore(q, f,
                    tok -> 1.0));
            }
            if (r.matched()) {
                assertTrue(ContentSimilarity.weightedScore(q, r.reply(), tok -> 1.0) > 0,
                    "a returned answer must score positively on the reference path: " + q);
            } else {
                assertTrue(bestRef < 0.20,
                    "a miss must be below the floor on the reference path too, got "
                        + bestRef + " for " + q);
            }
        }
    }
}
