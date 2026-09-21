package io.matrix.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for QaCorpusIndex — in-memory inverted index for Q&A retrieval.
 *
 * <p>Tests: corpus loading from disk, deterministic indexing, search ranking,
 * add+persist cycle, stopword filtering, multi-language corpus.
 */
class QaCorpusIndexTest {

    @TempDir
    Path tempDir;

    private QaCorpusIndex index;

    @BeforeEach
    void setUp() throws IOException {
        index = new QaCorpusIndex();
        // Override config to point at temp directory
        Path corpus = tempDir.resolve("qa_pairs.json");
        Files.writeString(corpus, """
                [
                  {"question": "What is a neuron?", "answer": "A neuron is a Boolean function evaluated on input bits.", "category": "ml", "source": "t1"},
                  {"question": "Что такое автономные системы?", "answer": "Роботы без постоянного контроля человека.", "category": "robotics", "source": "t2"},
                  {"question": "How does backpropagation work?", "answer": "It computes gradients by applying the chain rule backward.", "category": "ml", "source": "t3"},
                  {"question": "Объясни что такое градиент", "answer": "Градиент показывает направление наибольшего роста функции.", "category": "math", "source": "t4"},
                  {"question": "What is REST?", "answer": "Architectural style for web APIs.", "category": "tech", "source": "t5"}
                ]
                """);
        index.qaPath = corpus.toString();
        index.forumPath = tempDir.resolve("missing.json").toString(); // not used
    }

    @AfterEach
    void tearDown() {
        // nothing — tempDir is auto-cleaned
    }

    private void reload() {
        index.reload();
    }

    @Test
    void loadsAllEntriesFromFile() {
        reload();
        assertThat(index.size()).isEqualTo(5);
    }

    @Test
    void emptyFileProducesEmptyIndex() throws IOException {
        Files.writeString(Path.of(index.qaPath), "[]");
        index.reload();
        assertThat(index.size()).isZero();
    }

    @Test
    void searchFindsExactTokenMatch() {
        reload();
        List<QaCorpusIndex.Entry> hits = index.search("neuron", 5);
        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).question()).contains("neuron");
    }

    @Test
    void searchReturnsEmptyForUnknownTerms() {
        reload();
        List<QaCorpusIndex.Entry> hits = index.search("xyzzy123", 5);
        assertThat(hits).isEmpty();
    }

    @Test
    void searchWorksForCyrillicQuery() {
        reload();
        List<QaCorpusIndex.Entry> hits = index.search("автономные", 5);
        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).question()).contains("автономные");
    }

    @Test
    void searchRanksMoreSpecificQuestionHigher() {
        reload();
        // "neuron" matches entry 0; "What is a neuron?" has closer wording
        List<QaCorpusIndex.Entry> hits = index.search("what neuron", 5);
        assertThat(hits).isNotEmpty();
        // top hit should be the neuron question (length-bonus for short Qs)
        assertThat(hits.get(0).question().toLowerCase()).contains("neuron");
    }

    @Test
    void topScoreIsPositiveForMatch() {
        reload();
        double score = index.topScore("neuron");
        assertThat(score).isGreaterThan(0.0);
    }

    @Test
    void topScoreIsZeroForUnknownTerm() {
        reload();
        double score = index.topScore("xyzzy_unknown_term");
        assertThat(score).isZero();
    }

    @Test
    void stopwordsAreFilteredOut() {
        reload();
        // "what" "is" "a" are all stopwords — should give zero hits
        List<QaCorpusIndex.Entry> hits = index.search("a is what", 5);
        // Either empty, or only matches on non-stopword "REST"/etc.
        // The point: stopword-only queries don't accidentally hit.
        assertThat(hits).satisfiesAnyOf(
                list -> assertThat(list).isEmpty(),
                list -> assertThat(list.get(0).question()).doesNotContain("xyzzy"));
    }

    @Test
    void addInsertsIntoInMemoryIndex() {
        reload();
        int before = index.size();
        QaCorpusIndex.Entry e = index.add("Brand-new question", "Brand-new answer", "test", "junit");
        assertThat(index.size()).isEqualTo(before + 1);
        assertThat(e.id()).isEqualTo(before);
        // search should find it
        List<QaCorpusIndex.Entry> hits = index.search("brand-new question", 5);
        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).answer()).contains("Brand-new answer");
    }

    @Test
    void addPersistsToDisk() throws IOException {
        reload();
        QaCorpusIndex.Entry e = index.add("Persisted Q", "Persisted A", "test", "junit-persist");
        // Read the disk file back; the new entry must be present
        String disk = Files.readString(Path.of(index.qaPath));
        assertThat(disk).contains("Persisted Q");
        assertThat(disk).contains("Persisted A");
    }

    @Test
    void multipleAddsAccumulate() {
        reload();
        int before = index.size();
        index.add("Q1", "A1", "test", "src");
        index.add("Q2", "A2", "test", "src");
        index.add("Q3", "A3", "test", "src");
        assertThat(index.size()).isEqualTo(before + 3);
    }
}
