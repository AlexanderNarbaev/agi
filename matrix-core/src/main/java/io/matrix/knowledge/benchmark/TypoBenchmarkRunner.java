package io.matrix.knowledge.benchmark;

import io.matrix.knowledge.AbstractionLevel;
import io.matrix.knowledge.Answer;
import io.matrix.knowledge.KnowledgeWorkbench;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs the synthetic typo benchmark over a workspace (RECON-W42.7).
 *
 * <p>The mission's §14 names the row: "≥ 95 % intent preservation on a
 * synthetic typo benchmark". This is the runner that measures it.
 *
 * <p><b>What the run does.</b> For every concept it asks the INTENDED
 * question once, and then each of the misspelled questions. The intended
 * answer is the reference; a case preserves the intent when the answer to
 * the misspelled question covers the same key terms.
 *
 * <p><b>What the run does not do.</b> It does not reward a lucky
 * intersection: the key terms are the concept's own name first, and the
 * half of them that the answer covers is the threshold — a wrong answer
 * that happens to share a word with the reference does not pass.
 */
public final class TypoBenchmarkRunner {

    private TypoBenchmarkRunner() {
    }

    /**
     * Runs the benchmark.
     *
     * @param workbench the workspace
     * @param workspaceId the workspace
     * @param benchmark the benchmark
     * @return the result
     */
    public static TypoBenchmarkGenerator.Result run(KnowledgeWorkbench workbench, String workspaceId,
                                                    TypoBenchmarkGenerator.Benchmark benchmark) {
        Map<String, String> intended = new LinkedHashMap<>();
        Map<String, String> actual = new LinkedHashMap<>();

        // The intended question of a concept is asked once, and the
        // misspelled ones follow it. The order matters: the dialogue
        // context keeps the recent turns, and a question that names its
        // subject needs no context to be read.
        String lastConcept = null;
        String lastReference = null;
        for (TypoBenchmarkGenerator.Case item : benchmark.casesList()) {
            if (!item.concept().equals(lastConcept)) {
                Answer reference = workbench.ask(workspaceId, item.intendedQuestion(),
                        AbstractionLevel.INTRODUCTORY);
                lastReference = reference.statement() == null ? "" : reference.statement();
                lastConcept = item.concept();
            }
            // The LABEL goes with every case of the concept, not only the
            // first one. The first version put it only under the first
            // case's id, and the other two cases of every concept had no
            // reference at all - so they counted as failures while the
            // system answered them correctly.
            intended.put(item.id(), lastReference);
            Answer answer = workbench.ask(workspaceId, item.question(),
                    AbstractionLevel.INTRODUCTORY);
            actual.put(item.id(), answer.statement() == null ? "" : answer.statement());
        }
        return TypoBenchmarkGenerator.measure(benchmark, intended, actual);
    }

    /**
     * The benchmark over a workspace's concepts, generated on the spot.
     *
     * @param workbench the workspace
     * @param workspaceId the workspace
     * @return the benchmark
     */
    public static TypoBenchmarkGenerator.Benchmark benchmark(KnowledgeWorkbench workbench,
                                                             String workspaceId) {
        return TypoBenchmarkGenerator.generate(workbench.workspace(workspaceId));
    }

    /**
     * The result as a map, for the HTTP layer.
     *
     * @param result the result
     * @param benchmark the benchmark
     * @return the map
     */
    public static Map<String, Object> asMap(TypoBenchmarkGenerator.Result result,
                                            TypoBenchmarkGenerator.Benchmark benchmark) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("theDatasetId", benchmark.id());
        out.put("theDatasetVersion", benchmark.version());
        out.put("theSeed", benchmark.seed());
        out.put("theCases", result.cases());
        out.put("thePreserved", result.preserved());
        out.put("theIntentPreservation", result.share());
        out.put("theFailures", result.failures());
        out.put("theNote", "The cases are synthetic: the misspellings are generated from the "
                + "workspace's own concept names by a fixed seed, so the dataset is reproducible "
                + "and its version is the seed. A case preserves the intent when the answer to "
                + "the misspelled question covers the same key terms as the answer to the "
                + "question that was meant.");
        return out;
    }
}
