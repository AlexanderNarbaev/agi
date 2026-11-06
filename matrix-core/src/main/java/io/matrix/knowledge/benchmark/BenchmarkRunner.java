package io.matrix.knowledge.benchmark;

import io.matrix.knowledge.Answer;
import io.matrix.knowledge.Claim;
import io.matrix.knowledge.KnowledgeSnapshot;
import io.matrix.knowledge.KnowledgeWorkbench;
import io.matrix.knowledge.TextFeatures;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Runs the W42 benchmark against a workbench and reports what happened.
 *
 * <p>The runner measures four things, and reports them separately because
 * they answer different questions:
 *
 * <ol>
 *   <li><b>Answer accuracy</b> — of the items the workspace CAN answer, how
 *       many produced an answer that mentions the required terms?</li>
 *   <li><b>Abstention quality</b> — of the items the workspace CANNOT
 *       answer, how many were refused? An answer to an unanswerable item is
 *       a failure, and a refusal of an answerable item is also a failure.</li>
 *   <li><b>Retrieval comparison</b> — for each answerable item, would the
 *       BM25 baseline have ranked the same claims first? The measure is the
 *       overlap of the two rankings, so a retrieval change can be
 *       attributed to the change rather than to the dataset.</li>
 *   <li><b>Per-category and per-language breakdown</b> — because an average
 *       over categories hides exactly the failures that matter: a Russian
 *       item and a misspelled item are different capabilities.</li>
 * </ol>
 *
 * <p><b>The sample sizes are small and the report says so.</b> The runner
 * prints the count beside every rate. A rate over six items is a
 * demonstration, not a measurement of a capability.
 */
public final class BenchmarkRunner {

    private final KnowledgeWorkbench workbench;

    public BenchmarkRunner(KnowledgeWorkbench workbench) {
        this.workbench = workbench;
    }

    /** One item's result. */
    public record ItemResult(
            BenchmarkItem item,
            boolean answered,
            boolean expectedToAnswer,
            boolean correct,
            double keyTermCoverage,
            double supportScore,
            double bm25Overlap,
            String note) {
    }

    /** The whole run's result. */
    public record Report(
            List<ItemResult> results,
            int answerableCount,
            int answeredCorrectly,
            int unanswerableCount,
            int refusedCorrectly,
            double meanBm25Overlap,
            Map<String, Group> byCategory,
            Map<String, Group> byLanguage) {

        /** A group of items with the same category or language. */
        public record Group(int items, int answerable, int correct, int refusedCorrectly) {
        }

        /** The share of the answerable items answered correctly. */
        public double answerAccuracy() {
            return answerableCount == 0 ? 0.0 : (double) answeredCorrectly / answerableCount;
        }

        /** The share of the unanswerable items refused. */
        public double abstentionQuality() {
            return unanswerableCount == 0 ? 0.0 : (double) refusedCorrectly / unanswerableCount;
        }
    }

    /**
     * Loads the packaged workspace of an item when the workspace is empty.
     *
     * @param workspaceId the workspace the item belongs to
     * @return whether the workspace was loaded for the measurement
     */
    private boolean ensureWorkspace(String workspaceId) {
        if (workspaceId == null || workspaceId.isBlank()) {
            return false;
        }
        if (!workbench.workspace(workspaceId).concepts().isEmpty()) {
            return false;
        }
        try {
            workbench.loadBundledPackage(workspaceId);
            return true;
        } catch (RuntimeException e) {
            // There is no package under this name. The item is measured
            // against the empty workspace, and the result shows it.
            return false;
        }
    }

    /** Runs every item of a benchmark. */
    public Report run(List<BenchmarkItem> items) {
        List<ItemResult> results = new ArrayList<>();
        Map<String, KnowledgeSnapshot> snapshots = new LinkedHashMap<>();

        for (BenchmarkItem item : items) {
            // THE ITEM'S OWN WORKSPACE.
            //
            // Every item names the workspace it belongs to, and the runner
            // asks that workspace. But a workspace that was never LOADED
            // measures an empty one, and the items of the other workspace
            // were measured against nothing. The probe loaded both packages
            // by hand, so the probe's number was right and the dashboard's
            // was not - the same instrument, two different numbers.
            //
            // The instrument now loads the packaged workspace when the
            // workspace is empty, and says so on the item.
            boolean loaded = ensureWorkspace(item.workspace());
            KnowledgeSnapshot snapshot = snapshots.computeIfAbsent(item.workspace(),
                    workbench::workspace);
            Answer answer = workbench.ask(item.workspace(), item.question(),
                    io.matrix.knowledge.AbstractionLevel.INTRODUCTORY);
            boolean answered = answer.answered();
            boolean expected = item.answerable();

            double coverage = 0.0;
            boolean correct;
            String note = loaded
                    ? "the workspace of this item was empty, and the packaged workspace was "
                            + "loaded for the measurement"
                    : "";
            if (expected) {
                coverage = keyTermCoverage(item, answer);
                boolean mentions = mentionsAll(item.mustMention(), answer.statement());
                boolean avoids = mentionsNone(item.mustNotMention(), answer.statement());
                correct = answered && mentions && avoids;
                if (!answered) {
                    note = "the workspace refused an answerable item";
                } else if (!mentions) {
                    note = "the answer does not mention " + item.mustMention();
                } else if (!avoids) {
                    note = "the answer mentions " + item.mustNotMention();
                }
            } else {
                correct = !answered;
                if (answered) {
                    note = "the workspace answered an item it cannot support";
                }
            }

            double overlap = 0.0;
            if (answered && expected) {
                overlap = bm25Overlap(snapshot, item, answer);
            }

            results.add(new ItemResult(item, answered, expected, correct, coverage,
                    answer.supportScore(), overlap, note));
        }
        return summarise(results);
    }

    /** Groups the results and computes the rates. */
    private Report summarise(List<ItemResult> results) {
        int answerable = 0;
        int answeredCorrectly = 0;
        int unanswerable = 0;
        int refusedCorrectly = 0;
        double overlapSum = 0.0;
        int overlapCount = 0;

        Map<String, int[]> category = new LinkedHashMap<>();
        Map<String, int[]> language = new LinkedHashMap<>();

        for (ItemResult result : results) {
            if (result.expectedToAnswer()) {
                answerable++;
                if (result.correct()) {
                    answeredCorrectly++;
                }
                overlapSum += result.bm25Overlap();
                overlapCount++;
            } else {
                unanswerable++;
                if (result.correct()) {
                    refusedCorrectly++;
                }
            }
            category.computeIfAbsent(result.item().category(), key -> new int[4]);
            int[] bucket = category.get(result.item().category());
            bucket[0]++;
            if (result.expectedToAnswer()) {
                bucket[1]++;
                if (result.correct()) {
                    bucket[2]++;
                }
            } else if (result.correct()) {
                bucket[3]++;
            }

            language.computeIfAbsent(result.item().language(), key -> new int[4]);
            int[] lang = language.get(result.item().language());
            lang[0]++;
            if (result.expectedToAnswer()) {
                lang[1]++;
                if (result.correct()) {
                    lang[2]++;
                }
            } else if (result.correct()) {
                lang[3]++;
            }
        }

        Map<String, Report.Group> categories = new LinkedHashMap<>();
        category.forEach((key, bucket) -> categories.put(key,
                new Report.Group(bucket[0], bucket[1], bucket[2], bucket[3])));
        Map<String, Report.Group> languages = new LinkedHashMap<>();
        language.forEach((key, bucket) -> languages.put(key,
                new Report.Group(bucket[0], bucket[1], bucket[2], bucket[3])));

        return new Report(results, answerable, answeredCorrectly, unanswerable, refusedCorrectly,
                overlapCount == 0 ? 0.0 : overlapSum / overlapCount, categories, languages);
    }

    /** The share of the item's key terms the answer carries. */
    private double keyTermCoverage(BenchmarkItem item, Answer answer) {
        if (item.keyTerms().isEmpty()) {
            return 0.0;
        }
        Set<String> answerTerms = new LinkedHashSet<>(TextFeatures.tokens(answer.statement()));
        int found = 0;
        for (String keyTerm : item.keyTerms()) {
            for (String term : TextFeatures.tokens(keyTerm)) {
                if (answerTerms.contains(term)) {
                    found++;
                    break;
                }
            }
        }
        return (double) found / item.keyTerms().size();
    }

    private boolean mentionsAll(List<String> required, String answer) {
        String lower = answer.toLowerCase(Locale.ROOT);
        for (String phrase : required) {
            if (!lower.contains(phrase.toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        return true;
    }

    private boolean mentionsNone(List<String> forbidden, String answer) {
        String lower = answer.toLowerCase(Locale.ROOT);
        for (String phrase : forbidden) {
            if (lower.contains(phrase.toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        return true;
    }

    /**
     * The overlap between the claims MATRIX used and the claims BM25 ranks
     * first.
     *
     * <p>The comparison is over the top-k claims of each ranking, with
     * k the number MATRIX used. A retrieval that finds the same claims as
     * the standard baseline has an overlap of 1; a retrieval that finds
     * different claims has an overlap near 0.
     */
    private double bm25Overlap(KnowledgeSnapshot snapshot, BenchmarkItem item, Answer answer) {
        Set<String> used = new LinkedHashSet<>();
        for (Claim claim : answer.claims()) {
            used.add(claim.id());
        }
        if (used.isEmpty()) {
            return 0.0;
        }
        Bm25Index index = new Bm25Index(snapshot);
        List<Bm25Index.Scored> ranked = index.rank(item.question());
        Set<String> top = new LinkedHashSet<>();
        for (int i = 0; i < Math.min(used.size(), ranked.size()); i++) {
            top.add(ranked.get(i).claim().id());
        }
        Set<String> shared = new LinkedHashSet<>(used);
        shared.retainAll(top);
        return (double) shared.size() / used.size();
    }
}
