package io.matrix.neuron;

import org.junit.jupiter.api.Test;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HdcBindingTest {

    private static long[] r(int seed) {
        return HdcEncoding.random(new Random(seed));
    }

    @Test
    void bindIsItsOwnInverse() {
        long[] a = r(1);
        long[] b = r(2);
        long[] bound = HdcBinding.bind(a, b);
        long[] recovered = HdcBinding.unbind(bound, b);
        assertThat(HdcEncoding.hamming(recovered, a)).isEqualTo(0);
    }

    @Test
    void bindIsCommutative() {
        long[] a = r(1);
        long[] b = r(2);
        long[] ab = HdcBinding.bind(a, b);
        long[] ba = HdcBinding.bind(b, a);
        assertThat(HdcEncoding.hamming(ab, ba)).isEqualTo(0);
    }

    @Test
    void sequenceShiftsByPosition() {
        long[] v = r(7);
        long[] s = HdcBinding.sequence(v, 5);
        long[] expected = HdcEncoding.permute(v, 5);
        assertThat(HdcEncoding.hamming(s, expected)).isEqualTo(0);
    }

    @Test
    void sequenceAtZeroIsIdentity() {
        long[] v = r(7);
        long[] s = HdcBinding.sequence(v, 0);
        assertThat(HdcEncoding.hamming(s, v)).isEqualTo(0);
    }

    @Test
    void sequenceAtDimIsIdentity() {
        long[] v = r(7);
        long[] s = HdcBinding.sequence(v, HdcEncoding.DIM);
        assertThat(HdcEncoding.hamming(s, v)).isEqualTo(0);
    }

    @Test
    void sequenceAtDifferentPositionsAreApproximatelyOrthogonal() {
        long[] v = r(7);
        long[] s3 = HdcBinding.sequence(v, 3);
        long[] s4 = HdcBinding.sequence(v, 4);
        int d = HdcEncoding.hamming(s3, s4);
        assertThat(d).isBetween(380, 644); // ~DIM/2 ± 3 sigma
    }

    @Test
    void recordCreatesSingleVector() {
        Map<String, long[]> roles = new LinkedHashMap<>();
        roles.put("agent", r(101));
        roles.put("action", r(102));
        Map<String, long[]> fillers = new LinkedHashMap<>();
        fillers.put("agent", r(201));
        fillers.put("action", r(202));
        long[] rec = HdcBinding.record(roles, fillers);
        assertThat(rec).hasSize(HdcEncoding.WORDS);
    }

    @Test
    void recordRejectsMismatchedRoles() {
        Map<String, long[]> roles = new LinkedHashMap<>();
        roles.put("agent", r(101));
        Map<String, long[]> fillers = new LinkedHashMap<>();
        fillers.put("action", r(202)); // mismatch — no "agent"
        assertThatThrownBy(() -> HdcBinding.record(roles, fillers))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recordRejectsNull() {
        assertThatThrownBy(() -> HdcBinding.record(null, new HashMap<>()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HdcBinding.record(new HashMap<>(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void stringToCodeIsDeterministic() {
        long[] a = HdcBinding.stringToCode("agent", new Random(42));
        long[] b = HdcBinding.stringToCode("agent", new Random(42));
        assertThat(HdcEncoding.hamming(a, b)).isEqualTo(0);
    }

    @Test
    void stringToCodeYieldsDifferentCodesForDifferentStrings() {
        long[] a = HdcBinding.stringToCode("agent", new Random(42));
        long[] b = HdcBinding.stringToCode("patient", new Random(42));
        int d = HdcEncoding.hamming(a, b);
        assertThat(d).isBetween(380, 644);
    }

    @Test
    void stringToCodeRejectsNullArgs() {
        assertThatThrownBy(() -> HdcBinding.stringToCode(null, new Random(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HdcBinding.stringToCode("x", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ngramEncodesSequence() {
        List<long[]> seq = Arrays.asList(r(1), r(2), r(3));
        long[] gram = HdcBinding.ngram(seq);
        assertThat(gram).hasSize(HdcEncoding.WORDS);
    }

    @Test
    void ngramDifferentSequencesProduceDifferentVectors() {
        List<long[]> seqA = Arrays.asList(r(1), r(2), r(3));
        List<long[]> seqB = Arrays.asList(r(1), r(2), r(4));
        long[] a = HdcBinding.ngram(seqA);
        long[] b = HdcBinding.ngram(seqB);
        // Different last item, so vectors differ significantly.
        assertThat(HdcEncoding.hamming(a, b)).isBetween(380, 644);
    }

    @Test
    void ngramSameSequencesProduceSameVectors() {
        List<long[]> seq = Arrays.asList(r(1), r(2), r(3));
        long[] a = HdcBinding.ngram(seq);
        long[] b = HdcBinding.ngram(seq);
        assertThat(HdcEncoding.hamming(a, b)).isEqualTo(0);
    }

    @Test
    void ngramRejectsEmptyOrBadInput() {
        assertThatThrownBy(() -> HdcBinding.ngram(new ArrayList<>()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HdcBinding.ngram(null))
                .isInstanceOf(IllegalArgumentException.class);
        List<long[]> bad = new ArrayList<>();
        bad.add(r(1));
        bad.add(new long[2]); // wrong length
        assertThatThrownBy(() -> HdcBinding.ngram(bad))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cleanupFindsExactMatch() {
        Map<String, long[]> codebook = new HashMap<>();
        codebook.put("a", r(1));
        codebook.put("b", r(2));
        codebook.put("c", r(3));
        Map.Entry<String, long[]> hit = HdcBinding.cleanup(r(2), codebook);
        assertThat(hit.getKey()).isEqualTo("b");
        assertThat(HdcEncoding.hamming(hit.getValue(), r(2))).isEqualTo(0);
    }

    @Test
    void cleanupFindsNearestForNoisyQuery() {
        Map<String, long[]> codebook = new HashMap<>();
        long[] a = r(1);
        long[] b = r(2);
        codebook.put("a", a);
        codebook.put("b", b);
        // Build noisy query: small bit-flips from a
        long[] noisy = a.clone();
        for (int i = 0; i < 5; i++) {
            noisy[i >>> 6] ^= (1L << (i & 63));
        }
        Map.Entry<String, long[]> hit = HdcBinding.cleanup(noisy, codebook);
        assertThat(hit.getKey()).isEqualTo("a");
    }

    @Test
    void cleanupOnEmptyCodebookReturnsNull() {
        assertThat(HdcBinding.cleanup(r(1), new HashMap<>())).isNull();
        assertThat(HdcBinding.cleanup(r(1), null)).isNull();
    }

    @Test
    void cleanupRejectsBadQuery() {
        assertThatThrownBy(() -> HdcBinding.cleanup(null, new HashMap<>()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HdcBinding.cleanup(new long[2], new HashMap<>()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cleanupTopKFindsNearestK() {
        Map<String, long[]> codebook = new HashMap<>();
        codebook.put("a", r(1));
        codebook.put("b", r(2));
        codebook.put("c", r(3));
        codebook.put("d", r(4));
        List<Map.Entry<String, Integer>> top2 = HdcBinding.cleanupTopK(r(2), codebook, 2);
        assertThat(top2).hasSize(2);
        assertThat(top2.get(0).getKey()).isEqualTo("b"); // exact match, distance 0
        assertThat(top2.get(0).getValue()).isEqualTo(0);
    }

    @Test
    void cleanupTopKSortsByDistance() {
        Map<String, long[]> codebook = new HashMap<>();
        codebook.put("a", r(1));
        codebook.put("b", r(2));
        codebook.put("c", r(3));
        List<Map.Entry<String, Integer>> top3 = HdcBinding.cleanupTopK(r(2), codebook, 3);
        assertThat(top3).hasSize(3);
        for (int i = 0; i < 2; i++) {
            assertThat(top3.get(i).getValue()).isLessThanOrEqualTo(top3.get(i + 1).getValue());
        }
    }

    @Test
    void cleanupTopKRespectsLimit() {
        Map<String, long[]> codebook = new HashMap<>();
        codebook.put("a", r(1));
        codebook.put("b", r(2));
        List<Map.Entry<String, Integer>> top1 = HdcBinding.cleanupTopK(r(2), codebook, 1);
        assertThat(top1).hasSize(1);
    }

    @Test
    void cleanupTopKRejectsBadQuery() {
        assertThatThrownBy(() -> HdcBinding.cleanupTopK(null, new HashMap<>(), 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HdcBinding.cleanupTopK(new long[2], new HashMap<>(), 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cleanupTopKOnEmptyCodebookReturnsEmpty() {
        List<Map.Entry<String, Integer>> result =
                HdcBinding.cleanupTopK(r(1), new HashMap<>(), 5);
        assertThat(result).isEmpty();
    }

    @Test
    void recordFromStringsWithRandomRngWorks() {
        Map<String, long[]> fillers = new LinkedHashMap<>();
        fillers.put("agent", r(201));
        fillers.put("action", r(202));
        long[] rec = HdcBinding.recordFromStrings(fillers, new Random(99));
        assertThat(rec).hasSize(HdcEncoding.WORDS);
    }

    @Test
    void recordFromStringsIsDeterministic() {
        Map<String, long[]> fillers = new LinkedHashMap<>();
        fillers.put("agent", r(201));
        fillers.put("action", r(202));
        long[] a = HdcBinding.recordFromStrings(fillers, new Random(99));
        long[] b = HdcBinding.recordFromStrings(fillers, new Random(99));
        assertThat(HdcEncoding.hamming(a, b)).isEqualTo(0);
    }

    @Test
    void recordFromStringsRejectsNullArgs() {
        assertThatThrownBy(() -> HdcBinding.recordFromStrings(null, new Random(1)))
                .isInstanceOf(IllegalArgumentException.class);
        Map<String, long[]> fillers = new LinkedHashMap<>();
        assertThatThrownBy(() -> HdcBinding.recordFromStrings(fillers, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void compositionalRecordsShareFillerStructure() {
        // Two records sharing the agent filler should be more similar than
        // two records with no shared fillers (Kanerva compositional generalization).
        Map<String, long[]> roles = new LinkedHashMap<>();
        roles.put("agent", r(101));
        roles.put("action", r(102));

        long[] sharedAgent = r(201);
        long[] actionA = r(301);
        long[] actionB = r(302);

        Map<String, long[]> f1 = new LinkedHashMap<>();
        f1.put("agent", sharedAgent);
        f1.put("action", actionA);

        Map<String, long[]> f2 = new LinkedHashMap<>();
        f2.put("agent", sharedAgent);
        f2.put("action", actionB);

        long[] rec1 = HdcBinding.record(roles, f1);
        long[] rec2 = HdcBinding.record(roles, f2);

        // Similarity should be > 0 (some shared structure) but not 1 (different action)
        double sim = HdcEncoding.similarity(rec1, rec2);
        assertThat(sim).isGreaterThan(0.0);
        assertThat(sim).isLessThan(1.0);
    }
}
