package io.matrix.knowledge.epistemic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.matrix.knowledge.AbstractionLevel;
import io.matrix.knowledge.Answer;
import io.matrix.knowledge.KnowledgeWorkbench;
import io.matrix.knowledge.acquisition.AcquisitionCycle;
import io.matrix.knowledge.acquisition.ClasspathCorpus;
import io.matrix.testsupport.RepositoryPaths;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The latency of the production asking path, measured the way the independent reviewer
 * measured it: in-process, two repetitions, the same questions per corpus.
 *
 * <p><b>What is measured.</b> The whole {@code ask} call — the retrieval, the premise's
 * assessment and the refutation scan — because that is what the caller waits for. The mean
 * of each repetition is printed, and the two repetitions are printed separately so that the
 * reader can see the spread.</p>
 *
 * <p><b>Which questions.</b> The 36 questions the W42 benchmark v3 holds that are not
 * answerable: they all assert a premise the corpus does not settle, and they are the
 * questions that reach the refutation scan — the instrument the third review round measured
 * as the hot spot. The same 36 questions are asked against both corpora, so the two numbers
 * differ only in the corpus they are asked of.</p>
 */
class PremiseLatencyMeasurementTest {

    /** The benchmark the questions are read from. */
    private static final String BENCHMARK =
            "matrix-core/src/main/resources/benchmark/w42-benchmark-v3.json";

    private static KnowledgeWorkbench workbench;
    private static List<String> questions;
    private static List<String> premiseQuestions;
    private static final String SEED = "latency-seed";
    private static final String POOL = "latency-pool";

    @TempDir
    Path directory;

    @BeforeAll
    static void prepare() throws Exception {
        Path database = Files.createTempDirectory("latency-").resolve("knowledge.sqlite");
        workbench = KnowledgeWorkbench.at(database.toString());
        ClasspathCorpus seed = ClasspathCorpus.extract(AcquisitionCycle.SEED_CORPUS);
        for (ClasspathCorpus.Entry entry : seed.entries()) {
            workbench.importDocument(SEED, entry.path(), entry.file(), entry.language());
        }
        ClasspathCorpus pool = ClasspathCorpus.extract(AcquisitionCycle.RESEARCH_POOL);
        for (ClasspathCorpus.Entry entry : pool.entries()) {
            workbench.importDocument(POOL, entry.path(), entry.file(), entry.language());
        }
        questions = readQuestions(false);
        premiseQuestions = readQuestions(true);
    }

    /** The 36 questions of the benchmark that assert a premise the corpus does not settle. */
    private static List<String> readQuestions(boolean withPremise) throws Exception {
        JsonNode root = new ObjectMapper().readTree(
                Files.readString(RepositoryPaths.root().resolve(BENCHMARK)));
        List<String> asked = new ArrayList<>();
        for (JsonNode item : root.get("items")) {
            String question = item.path("question").asText();
            if (withPremise) {
                if (carriesPremise(question)) {
                    asked.add(question);
                }
            } else if (!item.path("answerable").asBoolean()) {
                asked.add(question);
            }
        }
        return asked;
    }

    /** Whether the question opens with a premise marker or is a polar question. */
    private static boolean carriesPremise(String question) {
        String lower = question.toLowerCase(java.util.Locale.ROOT);
        for (String marker : List.of("since ", "because ", "assuming ", "given that ")) {
            if (lower.startsWith(marker)) {
                return true;
            }
        }
        for (String auxiliary : List.of("is ", "are ", "was ", "were ", "do ", "does ", "did ",
                "can ", "could ", "has ", "have ", "had ", "will ", "would ", "should ", "must ")) {
            if (lower.startsWith(auxiliary)) {
                return true;
            }
        }
        return false;
    }

    @Test
    @DisplayName("where the time of one question goes")
    void whereTheTimeGoes() {
        for (String[] corpus : new String[][] {{SEED, "seed"}, {POOL, "pool"}}) {
            workbench.workspace(corpus[0]);
            long read = 0L;
            long index = 0L;
            for (int i = 0; i < 5; i++) {
                long started = System.nanoTime();
                io.matrix.knowledge.KnowledgeSnapshot snapshot = workbench.workspace(corpus[0]);
                read += (System.nanoTime() - started) / 1_000_000L;
                started = System.nanoTime();
                new io.matrix.knowledge.KnowledgeRetriever(snapshot, 0.10);
                index += (System.nanoTime() - started) / 1_000_000L;
            }
            System.out.println("### " + corpus[1] + ": the workspace read "
                    + ((double) read / 5) + " ms, the retrieval's index "
                    + ((double) index / 5) + " ms (mean of 5)");
        }
    }

    @Test
    @DisplayName("the latency of the asking path: in-process, two repetitions, 36 questions a corpus")
    void theLatencyOfTheAskingPath() {
        System.out.println("### the questions measured: " + questions.size()
                + " (and " + premiseQuestions.size() + " that carry a premise)");
        org.assertj.core.api.Assertions.assertThat(questions)
                .as("the instrument measures 36 questions").hasSize(36);
        measure(questions, "the 36 unanswered questions");
        measure(premiseQuestions, "the questions that carry a premise");
    }

    private void measure(List<String> asked, String what) {
        System.out.println("### " + what);
        for (String[] corpus : new String[][] {{SEED, "seed"}, {POOL, "pool"}}) {
            // The warm-up ask: the first call reads the workspace and builds the index, and
            // the measurement is of the path, not of the first read.
            workbench.ask(corpus[0], asked.get(0), AbstractionLevel.INTRODUCTORY);
            for (int repetition = 0; repetition < 2; repetition++) {
                long total = 0L;
                for (String question : asked) {
                    long started = System.nanoTime();
                    Answer answer = workbench.ask(corpus[0], question,
                            AbstractionLevel.INTRODUCTORY);
                    total += (System.nanoTime() - started) / 1_000_000L;
                    if (answer == null) {
                        throw new IllegalStateException("the asking path returned nothing");
                    }
                }
                System.out.println("### " + corpus[1] + " repetition " + (repetition + 1)
                        + ": the mean " + ((double) total / asked.size()) + " ms over "
                        + asked.size() + " questions");
            }
        }
    }
}
