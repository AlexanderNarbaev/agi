package io.matrix.crj.corpus;

import io.matrix.knowledge.Claim;
import io.matrix.knowledge.KnowledgeSnapshot;
import io.matrix.knowledge.KnowledgeWorkbench;
import io.matrix.knowledge.Source;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CRJ-01 / ADAPTIVE-COMPETENCE — the LEARNING-EPISODE PROBE.
 *
 * <p><b>What it measures.</b> A procedure's support set is the union of the sources its
 * learning episode drew on — the sources the served answers CITED. So the support set is as
 * wide as the reference reading's evidence, and a learning episode whose answer cites a
 * document outside the intended support widens the support set and makes the procedure
 * applicable to questions it must refuse.</p>
 *
 * <p>This probe answers, for every question of the split, <b>which documents the reference
 * reading cites</b> — and therefore how wide the support set a learning episode over those
 * questions would be.</p>
 */
class Crj01AdaptiveLearningProbeTest {

    /** The classpath directory the adaptive corpora live in. */
    private static final String CORPUS_RESOURCE = "/crj01-adaptive/";

    @Test
    @DisplayName("the learning episode's width is measured: which documents the reference "
            + "reading cites for each question")
    void theLearningEpisodesWidthIsMeasured(@TempDir Path temporary) throws Exception {
        String splitPath = "docs-v2/engineering/CRJ-01-ADAPTIVE-DEV-SPLIT.json";
        com.fasterxml.jackson.databind.json.JsonMapper json =
                com.fasterxml.jackson.databind.json.JsonMapper.builder().build();
        @SuppressWarnings("unchecked")
        Map<String, Object> split = json.readValue(
                Files.readString(io.matrix.testsupport.RepositoryPaths.root().resolve(splitPath)),
                Map.class);

        for (Object domainEntry : (List<?>) split.get("domains")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> domain = (Map<String, Object>) domainEntry;
            String name = String.valueOf(domain.get("id"));
            Path corpus = Files.createDirectories(temporary.resolve("corpus").resolve(name));
            List<String> files = (List<String>) domain.get("files");
            for (String file : files) {
                Files.write(corpus.resolve(file), bytes(name, file));
            }
            Path database = Files.createDirectories(temporary.resolve("db")).resolve("w.sqlite");
            KnowledgeWorkbench bench = KnowledgeWorkbench.at(database.toString());
            try {
                String workspace = "crj01-adaptive-probe-" + name;
                Map<String, String> fileOfSource = new LinkedHashMap<>();
                for (String file : files) {
                    String sourceId = bench.importDocument(workspace, corpus.resolve(file), file,
                            "en").source().sourceId();
                    fileOfSource.put(sourceId, file);
                }
                for (Object caseEntry : (List<?>) split.get("cases")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> testCase = (Map<String, Object>) caseEntry;
                    if (!name.equals(String.valueOf(testCase.get("domain")))) {
                        continue;
                    }
                    var answer = bench.ask(workspace, String.valueOf(testCase.get("question")),
                            io.matrix.knowledge.AbstractionLevel.INTRODUCTORY);
                    Set<String> citedFiles = new LinkedHashSet<>();
                    for (Claim cited : answer.claims()) {
                        String file = fileOfSource.get(cited.introducedBySourceId());
                        if (file != null) {
                            citedFiles.add(file);
                        }
                    }
                    String goldFile = String.valueOf(testCase.get("corpusFile"));
                    System.out.println("[CRJ-01/ADAPTIVE/LEARN] " + testCase.get("id") + " "
                            + testCase.get("subset") + " gold=" + goldFile
                            + " cited=" + citedFiles + " claims=" + answer.claims().size()
                            + (citedFiles.size() == 1 && citedFiles.contains(goldFile)
                                    ? " OK" : " WIDE"));
                }
            } finally {
                bench.close();
            }
        }
    }

    /**
     * The bytes of one corpus file, as the classpath holds them.
     *
     * @param domain the domain's directory name
     * @param file   the file's name
     * @return the bytes
     */
    private static byte[] bytes(String domain, String file) throws IOException {
        try (InputStream stream = Crj01AdaptiveLearningProbeTest.class
                .getResourceAsStream(CORPUS_RESOURCE + domain + "/" + file)) {
            assertThat(stream).as("the corpus file %s/%s is on the classpath", domain, file)
                    .isNotNull();
            return stream.readAllBytes();
        }
    }

    /**
     * A file's presence on the classpath.
     *
     * @param stream the stream
     * @return the assertion
     */
    private static org.assertj.core.api.AbstractObjectAssert<?, ?> assertThat(Object stream) {
        return org.assertj.core.api.Assertions.assertThat(stream);
    }
}
