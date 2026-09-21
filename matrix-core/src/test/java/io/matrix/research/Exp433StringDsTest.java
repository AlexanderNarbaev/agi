package io.matrix.research;

import io.matrix.neuron.SuffixArray;
import io.matrix.neuron.Trie;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RUN 433 — Coverage for SuffixArray + Trie.
 */
class Exp433StringDsTest {

    @Test
    void suffixArraySortsAllSuffixesLexicographically() {
        String s = "banana";
        int[] sa = SuffixArray.build(s);
        // Expected suffixes sorted: "a", "ana", "anana", "banana", "na", "nana"
        // Their start positions: 5, 3, 1, 0, 4, 2
        assertThat(sa).hasSize(6);
        // Verify suffix[sa[0]] < suffix[sa[1]] < ... lex order
        for (int i = 0; i < sa.length - 1; i++) {
            String s1 = s.substring(sa[i]);
            String s2 = s.substring(sa[i + 1]);
            assertThat(s1.compareTo(s2)).isLessThanOrEqualTo(0);
        }
    }

    @Test
    void suffixArrayEmptyInputReturnsEmpty() {
        assertThat(SuffixArray.build("")).isEmpty();
    }

    @Test
    void suffixArraySingleChar() {
        int[] sa = SuffixArray.build("a");
        assertThat(sa).hasSize(1);
    }

    @Test
    void suffixArrayLongerText() {
        String s = "mississippi";
        int[] sa = SuffixArray.build(s);
        // Verify the array is fully sorted by suffix
        for (int i = 0; i < sa.length - 1; i++) {
            String s1 = s.substring(sa[i]);
            String s2 = s.substring(sa[i + 1]);
            assertThat(s1.compareTo(s2)).isLessThanOrEqualTo(0);
        }
    }

    @Test
    void trieContainsInsertsReturnsTrue() {
        Trie trie = new Trie();
        trie.insert("hello");
        trie.insert("help");
        trie.insert("helmet");
        assertThat(trie.contains("hello")).isTrue();
        assertThat(trie.contains("help")).isTrue();
        assertThat(trie.contains("helmet")).isTrue();
        assertThat(trie.contains("helm")).isFalse();  // partial is not full word
        assertThat(trie.contains("hellos")).isFalse();  // not inserted
    }

    @Test
    void trieStartsWithReturnsTrueForPrefix() {
        Trie trie = new Trie();
        trie.insert("hello");
        trie.insert("help");
        assertThat(trie.startsWith("hel")).isTrue();
        assertThat(trie.startsWith("hell")).isTrue();
        assertThat(trie.startsWith("hello")).isTrue();
        assertThat(trie.startsWith("xyz")).isFalse();
    }

    @Test
    void trieLongestCommonPrefixFindsPrefixSharedByAll() {
        Trie trie = new Trie();
        trie.insert("flower");
        trie.insert("flow");
        trie.insert("flight");
        assertThat(trie.longestCommonPrefix()).isEqualTo("fl");
    }

    @Test
    void trieLongestCommonPrefixOnEmptyBranching() {
        Trie trie = new Trie();
        trie.insert("a");
        trie.insert("b");
        // Root has 2 children → no common prefix
        assertThat(trie.longestCommonPrefix()).isEqualTo("");
    }

    @Test
    void trieSizeCountsNodes() {
        Trie trie = new Trie();
        trie.insert("hello");
        trie.insert("help");
        assertThat(trie.size()).isGreaterThan(6);
    }
}
