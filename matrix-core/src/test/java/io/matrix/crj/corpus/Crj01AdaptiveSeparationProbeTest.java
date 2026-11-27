package io.matrix.crj.corpus;

import io.matrix.knowledge.Claim;
import io.matrix.knowledge.KnowledgeSnapshot;
import io.matrix.knowledge.KnowledgeWorkbench;
import io.matrix.testsupport.RepositoryPaths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CRJ-01 / ADAPTIVE-COMPETENCE — the SEPARATION PROBE of the two development corpora.
 *
 * <p><b>What this instrument measures.</b> A promotion requires the restricted reading to
 * agree with the reference reading on the held-out cases, and the agreement is a comparison
 * of the <b>same cited claim ids</b>. A restricted reading can only cite the claims of the
 * support sources, so a validation question must reach the claims of ONE document only: if
 * the reference reading of a validation question also cites a claim of another document, the
 * restricted reading cannot match it and the promotion is refused.</p>
 *
 * <p>And a procedure's support set is the union of the sources its <b>learning episode</b>
 * drew on — the sources the served answers cited. So a learning episode whose answer cites a
 * document outside the intended support widens the support set, and the procedure becomes
 * applicable to questions it must refuse. Both are properties of the CORPUS, not of any
 * case, and both are decided by how far the documents' vocabularies reach each other.</p>
 *
 * <p><b>What the probe produces.</b> For every claim of every domain: a <b>focused
 * question</b> about it, and the documents the reference reading of that question cites.
 * The question is built from the claim's most DISTINCTIVE terms — the coverage floor is a
 * share of the question's <i>informative-term</i> weight, and a word every document of the
 * corpus carries carries almost none — so a question about a sentence names what the
 * sentence is about and not the domain's everyday words.</p>
 *
 * <p><b>Why this instrument writes the questions.</b> The questions must be built the same
 * way every time, and the production tokeniser is the only authority on what a term is. The
 * DEV split therefore takes its questions <b>verbatim</b> from this artefact, and the case
 * author (or, where none was available, the deterministic generator) only <b>selects</b>
 * from the claims this probe found NARROW.</p>
 *
 * <p><b>What the probe does NOT do.</b> It authors no case, judges no arm and changes no
 * product behaviour.</p>
 */
class Crj01AdaptiveSeparationProbeTest {

    /** The classpath directory the adaptive corpora live in. */
    private static final String CORPUS_RESOURCE = "/crj01-adaptive/";

    /** The artefact the probe writes. */
    private static final String ARTEFACT =
            "docs-v2/engineering/CRJ-01-ADAPTIVE-SEPARATION-PROBE.json";

    /** How many distinctive terms a focused question names. */
    private static final int QUESTION_TERMS = 8;

    @Test
    @DisplayName("the separation of the two development corpora is measured, and the report is "
            + "written")
    void theSeparationIsMeasured(@TempDir Path temporary) throws Exception {
        Map<String, List<String>> domains = new TreeMapOrdered();
        Path root = Path.of("src/test/resources/crj01-adaptive");
        try (var entries = Files.list(root)) {
            entries.filter(Files::isDirectory).sorted().forEach(entry ->
                    domains.put(entry.getFileName().toString(), List.of()));
        }
        assertThat(domains)
                .as("the milestone carries at least two development domains")
                .hasSizeGreaterThanOrEqualTo(2);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("report", "crj-01-adaptive-separation-probe");
        report.put("version", "2.0");
        report.put("date", "2026-11-27");
        report.put("questionTerms", QUESTION_TERMS);
        report.put("question", "for a claim, what the reference reading of a focused question "
                + "about it cites");
        report.put("why", "a promotion compares the reference reading with the restricted "
                + "reading claim by claim, and a procedure's support set is the union of the "
                + "sources its learning episode cited — so a question must reach the claims of "
                + "ONE document only");
        report.put("questionForm", "the question names the claim's most distinctive terms: the "
                + "coverage floor is a share of the question's informative-term weight, and a "
                + "word every document carries carries almost none");

        List<Map<String, Object>> lines = new ArrayList<>();
        int narrow = 0;
        int wide = 0;
        for (String domain : domains.keySet()) {
            Path corpus = Files.createDirectories(temporary.resolve("corpus").resolve(domain));
            List<String> files = new ArrayList<>();
            try (var entries = Files.list(root.resolve(domain))) {
                entries.filter(path -> path.getFileName().toString().endsWith(".md")).sorted()
                        .forEach(path -> {
                            files.add(path.getFileName().toString());
                            try {
                                Files.write(corpus.resolve(path.getFileName().toString()),
                                        bytes(domain, path.getFileName().toString()));
                            } catch (IOException unreadable) {
                                throw new IllegalStateException("the corpus could not be written",
                                        unreadable);
                            }
                        });
            }
            Path database = Files.createDirectories(temporary.resolve("db")).resolve("w.sqlite");
            KnowledgeWorkbench bench = KnowledgeWorkbench.at(database.toString());
            try {
                String workspace = "crj01-adaptive-probe-" + domain;
                Map<String, String> fileOfSource = new LinkedHashMap<>();
                for (String file : files) {
                    fileOfSource.put(bench.importDocument(workspace, corpus.resolve(file), file,
                            "en").source().sourceId(), file);
                }
                KnowledgeSnapshot snapshot = bench.workspace(workspace);
                Map<String, Integer> frequency = documentFrequency(snapshot);
                for (Claim claim : snapshot.claims()) {
                    String own = fileOfSource.get(claim.introducedBySourceId());
                    if (own == null) {
                        continue;
                    }
                    String question = focusedQuestion(claim.proposition(), frequency);
                    var answer = bench.ask(workspace, question,
                            io.matrix.knowledge.AbstractionLevel.INTRODUCTORY);
                    Set<String> citedFiles = new LinkedHashSet<>();
                    for (Claim cited : answer.claims()) {
                        String file = fileOfSource.get(cited.introducedBySourceId());
                        if (file != null) {
                            citedFiles.add(file);
                        }
                    }
                    boolean isNarrow = citedFiles.size() == 1 && citedFiles.contains(own);
                    if (isNarrow) {
                        narrow++;
                    } else {
                        wide++;
                    }
                    Map<String, Object> line = new LinkedHashMap<>();
                    line.put("domain", domain);
                    line.put("claimId", claim.id());
                    line.put("proposition", claim.proposition());
                    line.put("ownDocument", own);
                    line.put("question", question);
                    line.put("citedDocuments", new ArrayList<>(citedFiles));
                    line.put("citedClaims", answer.claims().size());
                    line.put("narrow", isNarrow);
                    lines.add(line);
                }
            } finally {
                bench.close();
            }
        }
        report.put("claims", lines);
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("claimsProbed", lines.size());
        counts.put("narrow", narrow);
        counts.put("wide", wide);
        report.put("counts", counts);

        com.fasterxml.jackson.databind.json.JsonMapper json =
                com.fasterxml.jackson.databind.json.JsonMapper.builder().build();
        Path artefact = RepositoryPaths.root().resolve(ARTEFACT);
        Files.createDirectories(artefact.getParent());
        Files.writeString(artefact, json.writerWithDefaultPrettyPrinter().writeValueAsString(report),
                StandardCharsets.UTF_8);
        System.out.println("[CRJ-01/ADAPTIVE] the separation probe " + artefact);
        System.out.println("[CRJ-01/ADAPTIVE] claims probed " + lines.size() + " narrow " + narrow
                + " wide " + wide);
        assertThat(lines).as("the probe measured every claim").isNotEmpty();
        assertThat(narrow)
                .as("the corpora carry enough narrow claims for the split's arrangement")
                .isGreaterThanOrEqualTo(13);
    }

    /**
     * A focused question about a proposition: its most DISTINCTIVE terms.
     *
     * @param proposition the proposition
     * @param frequency   how many claims of the corpus carry each term
     * @return the question
     */
    private static String focusedQuestion(String proposition, Map<String, Integer> frequency) {
        List<String> terms = new ArrayList<>(
                new LinkedHashSet<>(io.matrix.knowledge.TextFeatures.tokens(proposition)));
        terms.sort(Comparator.comparingInt((String term) -> frequency.getOrDefault(term, 0))
                .thenComparing(term -> term));
        List<String> chosen = terms.subList(0, Math.min(terms.size(), QUESTION_TERMS));
        return "What is stated about " + String.join(" ", chosen) + "?";
    }

    /**
     * How many claims of the corpus carry each term.
     *
     * @param snapshot the corpus
     * @return the document frequency of each term
     */
    private static Map<String, Integer> documentFrequency(KnowledgeSnapshot snapshot) {
        Map<String, Integer> frequency = new LinkedHashMap<>();
        for (Claim claim : snapshot.claims()) {
            for (String term : io.matrix.knowledge.TextFeatures.tokens(claim.proposition())) {
                frequency.merge(term, 1, Integer::sum);
            }
        }
        return frequency;
    }

    /**
     * The bytes of one corpus file, as the classpath holds them.
     *
     * @param domain the domain's directory name
     * @param file   the file's name
     * @return the bytes
     */
    private static byte[] bytes(String domain, String file) throws IOException {
        try (InputStream stream = Crj01AdaptiveSeparationProbeTest.class
                .getResourceAsStream(CORPUS_RESOURCE + domain + "/" + file)) {
            assertThat(stream).as("the corpus file %s/%s is on the classpath", domain, file)
                    .isNotNull();
            return stream.readAllBytes();
        }
    }

    /** The domains, in a deterministic order. */
    private static final class TreeMapOrdered extends java.util.TreeMap<String, List<String>> {
        private static final long serialVersionUID = 1L;
    }
}
