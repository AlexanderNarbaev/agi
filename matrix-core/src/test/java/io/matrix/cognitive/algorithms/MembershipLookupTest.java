package io.matrix.cognitive.algorithms;

import io.matrix.neuron.BloomFilter;
import io.matrix.neuron.Trie;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W44.2 — the membership methods: the exact ones agree, the probabilistic one is measured, and
 * the index is only used when the caller says it was built.
 */
class MembershipLookupTest {

    /** How many entries the corpus holds. */
    private static final int CORPUS_SIZE = 256;

    /** How many queries are asked. Half of them are present and half are not. */
    private static final int QUERY_COUNT = 64;

    /** The bits of the Bloom filter. A power of two, so the index is a mask. */
    private static final int BLOOM_BITS = 4096;

    /** The hash functions of the Bloom filter. */
    private static final int BLOOM_HASHES = 4;

    private final LinearScanMembershipMethod scan = new LinearScanMembershipMethod();
    private final TrieMembershipMethod trie = new TrieMembershipMethod();
    private final BloomFilterMembershipMethod bloom = new BloomFilterMembershipMethod();

    /** The corpus: distinct, deterministic strings. */
    private static List<String> corpus() {
        List<String> corpus = new ArrayList<>(CORPUS_SIZE);
        for (int i = 0; i < CORPUS_SIZE; i++) {
            corpus.add(String.format("entry-%04d", i));
        }
        return corpus;
    }

    /** The queries: half present, half absent. */
    private static List<String> queries() {
        List<String> queries = new ArrayList<>(QUERY_COUNT);
        for (int i = 0; i < QUERY_COUNT / 2; i++) {
            queries.add(String.format("entry-%04d", i * 2));
            queries.add(String.format("absent-%04d", i * 2));
        }
        return queries;
    }

    private static Trie trieOver(List<String> corpus) {
        Trie index = new Trie();
        for (String entry : corpus) {
            index.insert(entry);
        }
        return index;
    }

    private static BloomFilter filterOver(List<String> corpus) {
        BloomFilter filter = new BloomFilter(BLOOM_BITS, BLOOM_HASHES);
        for (String entry : corpus) {
            filter.add(MembershipProblem.keyOf(entry));
        }
        return filter;
    }

    @Test
    void aProblemWithoutAnIndexCanBeBuiltWithoutOne() {
        MembershipProblem problem = new MembershipProblem(List.of("a"), List.of("a"));

        assertThat(problem.prebuiltIndex()).isNull();
        assertThat(problem.corpus()).hasSize(1);
        assertThat(problem.queries()).hasSize(1);
    }

    @Test
    void theCorpusHasExactlyAsManyQueriesAsTheCapabilitiesDeclare() {
        assertThat(queries()).hasSize(QUERY_COUNT);
        assertThat(QUERY_COUNT).isEqualTo(LinearScanMembershipMethod.VALIDATION_CASES);
        assertThat(QUERY_COUNT).isEqualTo(TrieMembershipMethod.VALIDATION_CASES);
        assertThat(QUERY_COUNT).isEqualTo(BloomFilterMembershipMethod.VALIDATION_CASES);
    }

    @Test
    void theScanAndTheTrieAgreeOnEveryQuery() {
        List<String> corpus = corpus();
        List<String> queries = queries();

        MembershipProblem.MembershipOutcome byScan = scan.execute(
                new MembershipProblem(corpus, queries, null), ComputeBudget.unbounded());
        MembershipProblem.MembershipOutcome byTrie = trie.execute(
                new MembershipProblem(corpus, queries, trieOver(corpus)), ComputeBudget.unbounded());

        assertThat(byTrie.present()).isEqualTo(byScan.present());
        assertThat(byScan.hits()).isEqualTo(QUERY_COUNT / 2);
    }

    @Test
    void theBloomFilterNeverSaysAbsentForSomethingThatIsPresent() {
        List<String> corpus = corpus();
        List<String> queries = queries();

        MembershipProblem.MembershipOutcome byScan = scan.execute(
                new MembershipProblem(corpus, queries, null), ComputeBudget.unbounded());
        MembershipProblem.MembershipOutcome byBloom = bloom.execute(
                new MembershipProblem(corpus, queries, filterOver(corpus)), ComputeBudget.unbounded());

        for (int i = 0; i < QUERY_COUNT; i++) {
            if (byScan.present().get(i)) {
                assertThat(byBloom.present().get(i))
                        .as("the filter's answer for the present entry %s", queries.get(i))
                        .isTrue();
            }
        }
    }

    @Test
    void theBloomFilterClearsTheAccuracyFloorItDeclares() {
        List<String> corpus = corpus();
        List<String> queries = queries();

        MembershipProblem.MembershipOutcome byScan = scan.execute(
                new MembershipProblem(corpus, queries, null), ComputeBudget.unbounded());
        MembershipProblem.MembershipOutcome byBloom = bloom.execute(
                new MembershipProblem(corpus, queries, filterOver(corpus)), ComputeBudget.unbounded());

        long agreements = 0;
        for (int i = 0; i < QUERY_COUNT; i++) {
            if (byScan.present().get(i).equals(byBloom.present().get(i))) {
                agreements++;
            }
        }
        double measuredRate = (double) agreements / (double) QUERY_COUNT;

        assertThat(measuredRate)
                .as("the measured agreement of the filter with the exact answer")
                .isGreaterThanOrEqualTo(BloomFilterMembershipMethod.DECLARED_ACCURACY_FLOOR);
        assertThat(bloom.capability().accuracyEvidence().value())
                .isEqualTo(BloomFilterMembershipMethod.DECLARED_ACCURACY_FLOOR);
    }

    @Test
    void theIndexedMethodsRefuseToRunWithoutAnIndex() {
        MembershipProblem withoutAnIndex = new MembershipProblem(corpus(), queries(), null);

        assertThat(trie.preconditionsHold(withoutAnIndex)).isFalse();
        assertThat(bloom.preconditionsHold(withoutAnIndex)).isFalse();
        assertThatThrownBy(() -> trie.execute(withoutAnIndex, ComputeBudget.unbounded()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(Preconditions.INDEX_PREBUILT);
        assertThatThrownBy(() -> bloom.execute(withoutAnIndex, ComputeBudget.unbounded()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(Preconditions.INDEX_PREBUILT);
    }

    @Test
    void theIndexedMethodsRefuseAnIndexOfTheWrongKind() {
        List<String> corpus = corpus();

        assertThat(trie.preconditionsHold(
                new MembershipProblem(corpus, queries(), filterOver(corpus)))).isFalse();
        assertThat(bloom.preconditionsHold(
                new MembershipProblem(corpus, queries(), trieOver(corpus)))).isFalse();
    }

    @Test
    void theSelectorWillNotOfferTheIndexedMethodsWhenTheIndexWasNotBuilt() {
        AlgorithmSelector selector = new AlgorithmSelector(
                new AlgorithmRegistry().register(scan).register(trie).register(bloom), new CostLedger());

        ProblemRequest request = new ProblemRequest(
                AlgorithmFamilies.MEMBERSHIP_LOOKUP,
                new MembershipProblem(corpus(), queries(), null),
                Set.of(), ComputeBudget.unbounded());

        AlgorithmDecision decision = selector.decide(request);

        assertThat(decision.selectedAlgorithmId()).isEqualTo(LinearScanMembershipMethod.ID);
        assertThat(decision.rejected()).hasSize(2);
        assertThat(decision.rejected()).allSatisfy(candidate ->
                assertThat(candidate.verdict()).contains(Preconditions.INDEX_PREBUILT));
    }

    @Test
    void theSelectorOffersTheIndexedMethodsWhenTheIndexWasBuilt() {
        AlgorithmSelector selector = new AlgorithmSelector(
                new AlgorithmRegistry().register(scan).register(trie).register(bloom), new CostLedger());

        ProblemRequest request = new ProblemRequest(
                AlgorithmFamilies.MEMBERSHIP_LOOKUP,
                new MembershipProblem(corpus(), queries(), trieOver(corpus())),
                Set.of(Preconditions.INDEX_PREBUILT), ComputeBudget.unbounded());

        AlgorithmDecision decision = selector.decide(request);

        // All three were considered. The scan needs no index, the trie has one and is admitted,
        // and the filter is blocked because what it was handed is a trie and not a filter.
        assertThat(decision.candidates()).hasSize(3);
        assertThat(decision.candidates()).anySatisfy(candidate -> {
            assertThat(candidate.algorithmId()).isEqualTo(BloomFilterMembershipMethod.ID);
            assertThat(candidate.selected()).isFalse();
            assertThat(candidate.verdict()).contains("own precondition check failed");
        });
        assertThat(decision.candidates()).anySatisfy(candidate -> {
            assertThat(candidate.algorithmId()).isEqualTo(TrieMembershipMethod.ID);
            assertThat(candidate.verdict()).doesNotContain("blocked");
        });
    }

    @Test
    void theIndexMethodsNameTheScanAsTheirFallback() {
        assertThat(trie.capability().fallbackAlgorithmId()).isEqualTo(LinearScanMembershipMethod.ID);
        assertThat(bloom.capability().fallbackAlgorithmId()).isEqualTo(LinearScanMembershipMethod.ID);
    }
}
