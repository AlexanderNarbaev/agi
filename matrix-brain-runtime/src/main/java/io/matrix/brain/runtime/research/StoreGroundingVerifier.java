package io.matrix.brain.runtime.research;

import io.matrix.brain.runtime.PromotionGate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * EPISTEMIC-INTEGRITY P3 — the INDEPENDENT checker of an interaction's outcome.
 *
 * <p><b>What it checks.</b> The reply that the mind produced, against the machine's OWN
 * knowledge store — the store the mind retrieved from, read as TEXT, not as the vector
 * the pipeline scored. The check answers one question: <em>is this answer backed by
 * something the machine actually knows?</em></p>
 *
 * <p><b>Why the store and not a gold answer.</b> The evaluation's gold answers are the
 * evaluation's; using them would launder the evaluation into the label. The store is the
 * machine's own knowledge, it is independent of the harness, and it is what the
 * retrieval actually consulted — so a reply that is faithful to it was grounded in
 * something real, and one that is not was not.</p>
 *
 * <p><b>What it does NOT claim.</b> {@code GROUNDED} is not "correct". It is "faithful to
 * the machine's knowledge". Whether the machine's knowledge is true is a further question
 * this corpus cannot answer, and the experiment says so rather than quietly widening the
 * word.</p>
 *
 * <p><b>The rule (mine — {@code [speculative]}).</b> A reply is GROUNDED when some store
 * fact covers at least {@link #GROUNDING_COVERAGE} of the reply's content terms; it is
 * NOT_GROUNDED when the store holds facts but none covers that share; and UNKNOWN when
 * the store holds no fact at all, or the reply carries no content term to check. A
 * refusal is reported as REFUSED before any of that, because a refusal is not an answer
 * to be grounded.</p>
 */
public final class StoreGroundingVerifier {

    /**
     * The share of a reply's content terms a store fact must cover.
     *
     * <p>The number is mine ({@code [speculative]}): it is the point where a fact about
     * the answer is clearly the source of the answer, rather than a fact that happens to
     * share a word with it. The experiment publishes the sensitivity of its result to
     * this threshold, so a reader can see whether the conclusion depends on it.</p>
     */
    public static final double GROUNDING_COVERAGE = 0.6;

    /** The store's facts: id to text. */
    private final Map<String, String> facts;

    /** The facts as content-term sets, computed once. */
    private final List<List<String>> factTerms;

    /** The fact ids, parallel to {@link #factTerms}. */
    private final List<String> factIds;

    public StoreGroundingVerifier(Map<String, String> facts) {
        this.facts = Map.copyOf(facts);
        this.factIds = new ArrayList<>(this.facts.keySet());
        this.factTerms = new ArrayList<>();
        for (String id : this.factIds) {
            this.factTerms.add(OutcomeTerms.contentTerms(this.facts.get(id)));
        }
    }

    /** The number of facts the checker can verify against. */
    public int factCount() {
        return factIds.size();
    }

    /**
     * Verifies one reply.
     *
     * @param input  the question that was asked
     * @param reply  the answer the mind produced
     * @param refused whether the production path already refused the interaction
     * @return the verdict, with its evidence and the cost of obtaining it
     */
    public Verdict verify(String input, String reply, boolean refused) {
        long started = System.nanoTime();
        if (refused || reply == null || reply.isBlank()) {
            return new Verdict(OutcomeSignal.Outcome.REFUSED,
                    "the production path refused the interaction",
                    System.nanoTime() - started);
        }
        List<String> terms = OutcomeTerms.contentTerms(reply);
        if (terms.isEmpty()) {
            return new Verdict(OutcomeSignal.Outcome.UNKNOWN,
                    "the reply carries no content term to verify",
                    System.nanoTime() - started);
        }
        if (factIds.isEmpty()) {
            return new Verdict(OutcomeSignal.Outcome.UNKNOWN,
                    "the knowledge store holds no fact to verify against",
                    System.nanoTime() - started);
        }
        double best = 0.0;
        String bestId = null;
        int bestCovered = 0;
        for (int i = 0; i < factIds.size(); i++) {
            List<String> candidate = factTerms.get(i);
            if (candidate.isEmpty()) continue;
            int covered = 0;
            for (String term : terms) {
                if (contains(candidate, term)) covered++;
            }
            double share = (double) covered / terms.size();
            if (share > best) {
                best = share;
                bestId = factIds.get(i);
                bestCovered = covered;
            }
        }
        String evidence = bestId + " covers " + bestCovered + "/" + terms.size()
                + " of the reply's content terms (" + String.format(java.util.Locale.ROOT,
                "%.2f", best) + ")";
        OutcomeSignal.Outcome outcome = best >= GROUNDING_COVERAGE
                ? OutcomeSignal.Outcome.GROUNDED : OutcomeSignal.Outcome.NOT_GROUNDED;
        return new Verdict(outcome, evidence, System.nanoTime() - started);
    }

    /** Whether a term is covered by a fact's terms, allowing a shared prefix. */
    private static boolean contains(List<String> factTerms, String term) {
        for (String candidate : factTerms) {
            if (candidate.equals(term)) return true;
            if (candidate.length() >= 4 && term.length() >= 4
                    && (candidate.startsWith(term) || term.startsWith(candidate))) {
                return true;
            }
        }
        return false;
    }

    /** One verification: the verdict, the evidence, and what it cost. */
    public record Verdict(OutcomeSignal.Outcome outcome, String evidence, long nanos) {
    }
}
