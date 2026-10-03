package io.matrix.brain.runtime.stages;

import io.matrix.brain.runtime.BrcStep;
import io.matrix.brain.runtime.ContentSimilarity;
import io.matrix.brain.runtime.PersistentHdcStore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.BitSet;
import java.util.List;
import java.util.Map;

/**
 * MIND-W2 — Stage 7: HDC vector retrieval (persistent).
 *
 * <p>Hyperdimensional memory backed by {@link PersistentHdcStore} so taught
 * facts survive JVM restarts. The store is loaded eagerly at construction
 * and re-saved atomically on every teach. Cosine similarity is computed
 * bit-by-bit (Hamming/Jaccard) over 256-bit hypervectors.</p>
 *
 * <p>CONSTITUTION Article III — deterministic BitSet hash; identical input
 * yields identical vector; identical store content yields identical NDJSON
 * bytes (sorted indices).</p>
 */
public final class HdcRetrievalStage {

    public static final int DIM = 256;

    /**
     * A running top-N of scored candidates, for the trace.
     *
     * <p>RECON-W32.6. The trace used to label three entries "top=" while collecting the
     * first three ITERATED, which is not the same thing. Observed live: a passing
     * retrieval reported {@code top=[t100.wav:0.00, tone440b.wav:0.00, blue32.png:0.00]}
     * while the fact that actually answered scored 0.65. Evidence that looks wrong is
     * evidence nobody trusts, and a trace that always shows zeros is a trace nobody reads.
     *
     * @param capacity entries to keep, smallest count wins ties
     */
    private static final class TopScored {
        private final int capacity;
        private final java.util.List<String> entries = new java.util.ArrayList<>();

        TopScored(int capacity) { this.capacity = capacity; }

        void offer(String id, double score) {
            String line = id + ":" + String.format(java.util.Locale.ROOT, "%.2f", score);
            entries.add(line);
            entries.sort(Comparator.comparingDouble(l -> -Double.parseDouble(
                l.substring(l.lastIndexOf(':') + 1))));
            while (entries.size() > capacity) entries.remove(entries.size() - 1);
        }

        java.util.List<String> asList() { return java.util.List.copyOf(entries); }
        int size() { return entries.size(); }
    }

    private final PersistentHdcStore store;

    /**
     * Corpus document-frequency statistics, for discriminative scoring.
     *
     * <p>W31.4 measured the real false-positive mode: unweighted scoring treats "boiling"
     * and "point" as equal evidence to "mercury", so "What is the boiling point of
     * mercury?" matched a water fact at 0.267 and was served. Weighting shared tokens by
     * inverse document frequency pushes that below the floor, because the tokens that
     * actually discriminate are exactly the ones a water fact lacks.</p>
     *
     * <p>Recomputed on construction and after a bulk teach, never on a cache timer:
     * stale statistics would silently change every score.</p>
     */
    private java.util.Map<String, Integer> documentFrequency = java.util.Map.of();

    /** Rebuild DF statistics from the store contents. Unit: token -> fact count. */
    private void refreshDocumentFrequency() {
        java.util.Map<String, Integer> df = new java.util.HashMap<>();
        if (store != null) {
            for (String content : store.snapshot().values()) {
                for (String tok : ContentSimilarity.contentTokens(content)) {
                    df.merge(tok, 1, Integer::sum);
                }
            }
        }
        documentFrequency = df;
    }

    /** IDF over the current corpus; a token absent from the map is maximally rare. */
    private java.util.function.ToDoubleFunction<String> corpusIdf() {
        final int corpus = store == null ? 0 : store.size();
        return tok -> ContentSimilarity.idf(documentFrequency.getOrDefault(tok, 0), corpus);
    }

    /** In-memory mode (no persistence) — used by CI mode and tests. */
    public HdcRetrievalStage() {
        this.store = null;
        seedInMemory();
    }

    /** Persistent mode — wired by MindCycle when a storage path is provided. */
    public HdcRetrievalStage(PersistentHdcStore store) {
        this.store = store;
        if (store != null && store.size() == 0) seedPersistent();
        refreshDocumentFrequency();
    }

    /** Teach a fact to the persistent store. Returns the contradiction report. */
    public PersistentHdcStore.ContradictionReport teach(String id, String content) {
        if (store == null) throw new IllegalStateException("teach requires persistent store");
        PersistentHdcStore.ContradictionReport r = store.checkContradiction(id, content);
        store.teach(id, content);
        return r;
    }

    /** Number of stored facts (persistent or in-memory). */
    public int size() {
        return store != null ? store.size() : inMemoryVectors.size();
    }

    /**
     * Retrieval outcome.
     *
     * <p>RECON-W31.2 / EPI-2 + EPI-3. {@code confidenceSource} distinguishes a measured
     * score from a defaulted constant, so a consumer reporting this value to an
     * operator can tell earned confidence from a placeholder. {@code trace} carries the
     * structured-ignorance evidence required by EPI-3: on a miss the caller can state
     * the best similarity seen, the floor, and the nearest fact, instead of a bare
     * "no match".</p>
     */
    public record HdcResult(boolean matched, String reply, double confidence,
                            ContentSimilarity.Source confidenceSource, String trace) {
        public static HdcResult miss(String trace) {
            return new HdcResult(false, "", 0.0,
                ContentSimilarity.Source.MEASURED, trace);
        }
        public static HdcResult hit(String reply, ContentSimilarity.ConfidenceEvidence ev,
                                    String trace) {
            return new HdcResult(true, reply, ev.value(), ev.source(), trace);
        }
        /** True when the confidence was derived from a score, not asserted. */
        public boolean confidenceIsMeasured() {
            return confidenceSource == ContentSimilarity.Source.MEASURED;
        }
    }

    public HdcResult retrieve(String input, SignalStage.SignalObservation obs, List<BrcStep> trace) {
        if (store != null) return retrievePersistent(input, obs, trace);
        return retrieveInMemory(input, obs, trace);
    }

    /**
     * RECON-W31.4 — retrieve using BOTH the transliterated and the original form.
     *
     * <p><b>Why two forms are now mandatory rather than optional.</b> Since W2 the
     * gateway transliterates Cyrillic to Latin before the mind sees the input, so
     * "Столица Кении?" arrives here as "Stolitsa Kenii?". That was correct while the
     * knowledge store was Latin-only. W31.4 ingested 952 genuinely Cyrillic facts, and
     * a transliterated query shares no tokens with them — so the entire Russian half of
     * the corpus became unreachable, and the live gateway answered 1 of 3 Russian
     * probes while an in-process harness measuring the same store scored 7 of 8.</p>
     *
     * <p>W22 already solved this for language-aware reasoning by carrying both strings
     * through the cycle; this stage was simply not given the second one. The fix is to
     * score both forms and keep the better match, which costs one extra pass and makes
     * neither script lose.</p>
     *
     * @param input          the retrieval form (possibly transliterated)
     * @param originalInput  the untouched text; equal to {@code input} when identical
     * @param obs            signal observation for the trace
     * @param trace          receives the scoring step
     * @return the better of the two retrievals
     */
    public HdcResult retrieve(String input, String originalInput,
                              SignalStage.SignalObservation obs, List<BrcStep> trace) {
        HdcResult primary = retrieve(input, obs, trace);
        if (originalInput == null || originalInput.equals(input)) return primary;
        HdcResult secondary = retrieve(originalInput, obs, trace);
        // Keep the stronger match. Ties go to the retrieval form, so a Latin question
        // behaves exactly as it did before this change.
        return (secondary.matched() && !primary.matched())
            || (secondary.matched() && secondary.confidence() > primary.confidence())
            ? secondary : primary;
    }

    private HdcResult retrievePersistent(String input, SignalStage.SignalObservation obs, List<BrcStep> trace) {
        if (store.size() == 0) {
            trace.add(BrcStep.of("HDC_MEMORY", false, 0.50,
                List.of("reason=empty-store", "mode=persistent")));
            return HdcResult.miss("mode=persistent reason=empty-store");
        }
        // RECON-W31.2 SIM-1: score on CONTENT tokens, not raw token overlap. The old
        // BitSet Jaccard was measured at ROC-AUC 0.502 (chance) and served 12 of 20
        // unknowable questions; see ContentSimilarity for the full finding.
        Map<String, String> contents = store.snapshot();
        double bestScore = 0.0;
        double bestAnsweredScore = 0.0;
        String bestId = null;
        String bestContent = null;
        String bestAnsweredId = null;
        String bestAnsweredContent = null;
        TopScored top = new TopScored(3);
        java.util.function.ToDoubleFunction<String> idf = corpusIdf();
        for (Map.Entry<String, String> e : contents.entrySet()) {
            double sim = ContentSimilarity.weightedScore(input, e.getValue(), idf);
            if (sim > bestScore) {
                bestScore = sim;
                bestId = e.getKey();
                bestContent = e.getValue();
            }
            // Prefer entries that actually have an answer (contain " => ").
            String c = e.getValue();
            if (c != null && c.contains(" => ") && sim > bestAnsweredScore) {
                bestAnsweredScore = sim;
                bestAnsweredId = e.getKey();
                bestAnsweredContent = c;
            }
            top.offer(e.getKey(), sim);
        }
        java.util.List<String> topIds = top.asList();
        // Prefer an answered match ONLY when it is actually the better match.
        //
        // This used to be unconditional, and it silently discarded the best candidate
        // whenever any " => " fact existed anywhere in the store: the overall best was
        // overwritten by the best ANSWERED one, even when that was far weaker. Measured
        // live after a corpus re-ingest: "Kenya capital Nairobi" scores 0.654 and is the
        // right answer, but a leftover "capital of Atlantis => Poseidon" scored 0.146,
        // so the override discarded the correct fact and the mind refused a question it
        // could answer.
        //
        // Answered-ness is a tie-breaker, not a trump card.
        if (bestAnsweredId != null && bestAnsweredScore > bestScore) {
            bestId = bestAnsweredId;
            bestContent = bestAnsweredContent;
            bestScore = bestAnsweredScore;
        }
        if (bestScore < ContentSimilarity.RETRIEVAL_FLOOR || bestId == null) {
            // EPI-3 structured ignorance: report WHAT was seen and what was required,
            // so "I don't know" carries an evidence trail instead of being a bare miss.
            String ignoranceTrace = "mode=persistent best_similarity=" + bestScore
                + " floor=" + ContentSimilarity.RETRIEVAL_FLOOR
                + " nearest_fact_id=" + (bestId == null ? "none" : bestId)
                + " top=" + topIds;
            trace.add(BrcStep.of("HDC_MEMORY", false, bestScore,
                List.of("mode=persistent", "best=" + bestScore, "top=" + topIds)));
            return HdcResult.miss(ignoranceTrace);
        }
        // Split content on " => " separator; if absent treat full content as answer.
        String hReply;
        if (bestContent != null) {
            int sep = bestContent.indexOf(" => ");
            hReply = (sep > 0) ? bestContent.substring(sep + 4) : bestContent;
        } else {
            hReply = "";
        }
        trace.add(BrcStep.of("HDC_MEMORY", true, bestScore,
            List.of("mode=persistent", "best=" + bestId, "top=" + topIds,
                "confidence_source=measured", "score=" + bestScore)));
        return HdcResult.hit(hReply,
            ContentSimilarity.confidenceFor(bestScore),
            "mode=persistent best=" + bestId + " score=" + bestScore
                + " floor=" + ContentSimilarity.RETRIEVAL_FLOOR);
    }

    // -----------------------------------------------------------------
    // In-memory mode (CI / tests)
    // -----------------------------------------------------------------

    private final java.util.Map<String, BitSet> inMemoryVectors = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<String, String> inMemoryContents = new java.util.concurrent.ConcurrentHashMap<>();

    private void seedInMemory() {
        seed("seed-capital-france", "Paris is the capital of France");
        seed("seed-capital-japan", "Tokyo is the capital of Japan");
        seed("seed-capital-russia", "Moscow is the capital of Russia");
        seed("seed-color-sky", "The sky appears blue during daytime due to Rayleigh scattering");
        seed("seed-planet-count", "There are eight planets in the solar system");
        seed("seed-ai-definition", "AI is the field of building systems that perform tasks requiring intelligence when done by humans");
    }

    private void seedPersistent() {
        // Seed a minimal corpus so the persistent mode also has something to retrieve
        // on first boot. Subsequent teach() calls persist atomically.
        java.util.Map<String, String> seed = new java.util.LinkedHashMap<>();
        seed.put("seed-capital-france", "Paris is the capital of France");
        seed.put("seed-capital-japan", "Tokyo is the capital of Japan");
        seed.put("seed-capital-russia", "Moscow is the capital of Russia");
        seed.put("seed-color-sky", "The sky appears blue during daytime due to Rayleigh scattering");
        seed.put("seed-planet-count", "There are eight planets in the solar system");
        seed.put("seed-ai-definition", "AI is the field of building systems that perform tasks requiring intelligence when done by humans");
        store.teachAll(seed);
    }

    private void seed(String id, String content) {
        inMemoryVectors.put(id, PersistentHdcStore.hashToVector(content, DIM));
        inMemoryContents.put(id, content);
    }

    private HdcResult retrieveInMemory(String input, SignalStage.SignalObservation obs, List<BrcStep> trace) {
        if (inMemoryVectors.isEmpty()) {
            trace.add(BrcStep.of("HDC_MEMORY", false, 0.50,
                List.of("reason=empty-store", "mode=in-memory")));
            return HdcResult.miss("mode=in-memory reason=empty-store");
        }
        // Same content-aware scoring as the persistent path. W31.1 learned the hard way
        // that fixing one of two code paths leaves the other quietly wrong, and the
        // in-memory path is what CI mode and several tests actually exercise — a fix
        // applied only to the persistent path would have passed those and shipped a
        // chance-level scorer into production.
        double bestScore = 0.0;
        String bestId = null;
        String bestContent = null;
        TopScored top = new TopScored(3);
        for (Map.Entry<String, BitSet> e : inMemoryVectors.entrySet()) {
            double sim = ContentSimilarity.weightedScore(input, inMemoryContents.get(e.getKey()),
                corpusIdf());
            if (sim > bestScore) {
                bestScore = sim;
                bestId = e.getKey();
                bestContent = inMemoryContents.get(e.getKey());
            }
            top.offer(e.getKey(), sim);
        }
        java.util.List<String> topIds = top.asList();
        if (bestScore < ContentSimilarity.RETRIEVAL_FLOOR || bestId == null) {
            String ignoranceTrace = "mode=in-memory best_similarity=" + bestScore
                + " floor=" + ContentSimilarity.RETRIEVAL_FLOOR
                + " nearest_fact_id=" + (bestId == null ? "none" : bestId)
                + " top=" + topIds;
            trace.add(BrcStep.of("HDC_MEMORY", false, bestScore,
                List.of("mode=in-memory", "best=" + bestScore, "top=" + topIds)));
            return HdcResult.miss(ignoranceTrace);
        }
        String hReply;
        if (bestContent != null) {
            int sep = bestContent.indexOf(" => ");
            hReply = (sep > 0) ? bestContent.substring(sep + 4) : bestContent;
        } else {
            hReply = "";
        }
        trace.add(BrcStep.of("HDC_MEMORY", true, bestScore,
            List.of("mode=in-memory", "best=" + bestId, "top=" + topIds,
                "confidence_source=measured", "score=" + bestScore)));
        return HdcResult.hit(hReply,
            ContentSimilarity.confidenceFor(bestScore),
            "mode=in-memory best=" + bestId + " score=" + bestScore
                + " floor=" + ContentSimilarity.RETRIEVAL_FLOOR);
    }
}
