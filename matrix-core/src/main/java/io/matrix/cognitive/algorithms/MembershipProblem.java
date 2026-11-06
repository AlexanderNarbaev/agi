package io.matrix.cognitive.algorithms;

import java.nio.charset.StandardCharsets;
import java.util.List;

import io.matrix.neuron.XxHash;

/**
 * W44.2 — the membership family's shared input and output.
 *
 * <p>This family exists to make a trade-off visible in the selector rather than in a comment:
 * scanning a corpus costs time per query and nothing up front; an index costs time up front and
 * then answers in constant time. Which one is cheaper depends on how many queries follow, and
 * that is the caller's knowledge, not the method's.</p>
 *
 * <p>The input therefore carries THREE things: the corpus, the queries, and an OPTIONAL prebuilt
 * index. The methods that need an index declare {@link Preconditions#INDEX_PREBUILT} and refuse
 * to run without one; the scanning method needs nothing. The cost of BUILDING the index is paid
 * by the caller, once, and it is a real cost that the benchmark measures rather than hides.</p>
 *
 * <p>The keys are strings and the Bloom filter takes longs, so the family hashes them with
 * {@link XxHash#xxh3(byte[])} — an existing, stable, seedless hash already in the repository.
 * The hash is stable across runs and machines, which a {@code String.hashCode()} would also be,
 * but XxHash spreads 64 bits instead of 32 and so collides far less often on a corpus of
 * arbitrary strings.</p>
 */
public record MembershipProblem(List<String> corpus, List<String> queries, Object prebuiltIndex) {

    public MembershipProblem {
        corpus = corpus == null ? List.of() : List.copyOf(corpus);
        queries = queries == null ? List.of() : List.copyOf(queries);
    }

    /** A problem with no index built. */
    public MembershipProblem(List<String> corpus, List<String> queries) {
        this(corpus, queries, null);
    }

    /**
     * The stable 64-bit key of a corpus entry.
     *
     * @param value the string
     * @return its key
     */
    public static long keyOf(String value) {
        return XxHash.xxh3(value.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Whether each query is in the corpus.
     *
     * @param present one flag per query, in query order
     * @param corpusSize how many entries the corpus holds
     * @param queryCount how many queries were asked
     * @param note what the result is and is not
     */
    public record MembershipOutcome(
            List<Boolean> present, int corpusSize, int queryCount, String note) {

        public MembershipOutcome {
            present = present == null ? List.of() : List.copyOf(present);
            note = note == null ? "" : note;
        }

        /** How many queries were answered present. */
        public long hits() {
            return present.stream().filter(Boolean::booleanValue).count();
        }
    }
}
