package io.matrix.crj.corpus;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.matrix.knowledge.Claim;
import io.matrix.knowledge.EpistemicPolicy;
import io.matrix.knowledge.KnowledgeSnapshot;
import io.matrix.knowledge.KnowledgeWorkbench;
import io.matrix.knowledge.acquisition.SourceRecord;
import io.matrix.testsupport.RepositoryPaths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CRJ-01 / COMPETENCE-REACH — the CORPUS REPORT of the two fresh development domains.
 *
 * <p><b>What this instrument is for.</b> A case of this milestone names the evidence a correct
 * answer must cite, and that evidence is a sentence of the corpus. The production acquisition
 * path is what turns a sentence into a claim, and it is <b>conservative</b>: a sentence it does
 * not recognise is simply not proposed. So a corpus that is not measured through that path is a
 * corpus whose gold may not exist. This instrument measures it and publishes the measurement:
 * per document, the digest of its bytes, the source id the production import gave it, every
 * claim the production path derived from it, and — the reason this instrument exists —
 * <b>every sentence the production path did NOT turn into a claim</b>.</p>
 *
 * <p><b>Why the missing sentences are the point.</b> A corpus that is written to a contract and
 * never measured is a corpus that carries a silent gap, and the gap surfaces later as a gold
 * that cannot be resolved. The missing sentences are named here, verbatim, so that the gap is
 * closed <b>before</b> the cases are written against the corpus.</p>
 *
 * <p><b>What this instrument does not do.</b> It authors no case, judges no arm, and changes no
 * product behaviour. It is the map the case author works from — and it is the implementer's
 * instrument, so the case author is given its <b>output</b> and not the code.</p>
 */
class Crj01ReachCorpusReportBuilder {

    /** The classpath directory the reach corpora live in. */
    private static final String CORPUS_RESOURCE = "/crj01-reach/";

    /** The artefact the measurement is written to. */
    private static final String ARTEFACT =
            "docs-v2/engineering/CRJ-01-REACH-CORPUS-REPORT.json";

    @Test
    @DisplayName("the reach corpora are measured by the production import path, every sentence is "
            + "accounted for, and the report is written")
    void theCorporaAreMeasured(@TempDir Path temporary) throws Exception {
        Map<String, List<String>> domains = discoverDomains();
        assertThat(domains)
                .as("the milestone carries at least two genuinely different development domains")
                .hasSizeGreaterThanOrEqualTo(2);

        List<Map<String, Object>> domainReports = new ArrayList<>();
        int totalClaims = 0;
        int citableClaims = 0;
        int totalSentences = 0;
        int missingSentences = 0;
        for (Map.Entry<String, List<String>> domain : domains.entrySet()) {
            Path corpus = Files.createDirectories(temporary.resolve("corpus").resolve(domain.getKey()));
            for (String file : domain.getValue()) {
                Files.write(corpus.resolve(file), bytes(domain.getKey(), file));
            }

            Path database = Files.createDirectories(temporary.resolve("db")).resolve("w.sqlite");
            KnowledgeWorkbench bench = KnowledgeWorkbench.at(database.toString());
            try {
                String workspace = "crj01-reach-corpus-" + domain.getKey();
                Map<String, SourceRecord> sources = new LinkedHashMap<>();
                for (String file : domain.getValue()) {
                    SourceRecord source = bench.importDocument(workspace, corpus.resolve(file), file,
                            "en").source();
                    sources.put(file, source);
                }
                KnowledgeSnapshot snapshot = bench.workspace(workspace);

                List<Map<String, Object>> documents = new ArrayList<>();
                int domainClaims = 0;
                int domainCitable = 0;
                for (String file : domain.getValue()) {
                    SourceRecord source = sources.get(file);
                    List<Map<String, Object>> claims = new ArrayList<>();
                    for (Claim claim : snapshot.claims()) {
                        if (!source.sourceId().equals(claim.introducedBySourceId())) {
                            continue;
                        }
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("claimId", claim.id());
                        row.put("proposition", claim.proposition());
                        row.put("status", String.valueOf(claim.status()));
                        row.put("conceptId", claim.conceptId());
                        row.put("corpusFile", file);
                        claims.add(row);
                        domainClaims++;
                        if (EpistemicPolicy.isCitable(claim.status())) {
                            domainCitable++;
                        }
                    }
                    claims.sort(Comparator.comparing(row -> String.valueOf(row.get("proposition"))));
                    Map<String, Object> document = new LinkedHashMap<>();
                    document.put("file", file);
                    document.put("sha256", sha256(Files.readAllBytes(corpus.resolve(file))));
                    document.put("sourceId", source.sourceId());
                    document.put("claims", claims);
                    document.put("sentences", sentencesOf(corpus.resolve(file)));
                    document.put("missingSentences", missingOf(corpus.resolve(file), claims));
                    documents.add(document);
                }
                totalClaims += domainClaims;
                citableClaims += domainCitable;
                for (Map<String, Object> document : documents) {
                    totalSentences += ((List<?>) document.get("sentences")).size();
                    missingSentences += ((List<?>) document.get("missingSentences")).size();
                }
                Map<String, Object> counts = new LinkedHashMap<>();
                counts.put("documents", domain.getValue().size());
                counts.put("claims", domainClaims);
                counts.put("citableClaims", domainCitable);
                domainReports.add(domainReport(domain.getKey(), workspace, domain.getValue(),
                        documents, counts));
            } finally {
                bench.close();
            }
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("report", "crj-01-reach-corpus-report");
        report.put("version", "1.0");
        report.put("corpus", "crj01-reach (synthetic, test-owned)");
        report.put("corpusDirectory", "matrix-core/src/test/resources/crj01-reach");
        report.put("acquisitionPath", "the PRODUCTION import path: "
                + "workbench.importDocument(workspace, path, file, language)");
        report.put("purpose", "the verbatim evidence the COMPETENCE-REACH DEV cases are written "
                + "from: the sentences the production acquisition path derived, with the production "
                + "claim ids it gave them");
        report.put("missingSentencesNote", "the sentences the production acquisition path did NOT "
                + "turn into a claim; a case's necessary evidence may only name a sentence that is "
                + "NOT in this list, because a sentence that was not extracted has no claim to "
                + "cite");
        report.put("domainOrderNote", "the domains are reported in the lexicographic order of "
                + "their directory names, and the files of a domain in the lexicographic order of "
                + "their names; the order is stated so the artefact can be re-derived");
        report.put("domains", domainReports);
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("domains", domainReports.size());
        counts.put("claims", totalClaims);
        counts.put("citableClaims", citableClaims);
        counts.put("sentences", totalSentences);
        counts.put("missingSentences", missingSentences);
        report.put("counts", counts);

        ObjectMapper json = JsonMapper.builder().build();
        String text = json.writerWithDefaultPrettyPrinter().writeValueAsString(report);
        Path artefact = RepositoryPaths.root().resolve(ARTEFACT);
        Files.createDirectories(artefact.getParent());
        Files.writeString(artefact, text, StandardCharsets.UTF_8);
        Map<?, ?> reread = JsonMapper.builder().build().readValue(artefact.toFile(), Map.class);
        assertThat(((List<?>) reread.get("domains")).size())
                .as("the artefact carries every domain")
                .isEqualTo(domainReports.size());

        System.out.println("[CRJ-01/REACH] the corpus report " + artefact);
        System.out.println("[CRJ-01/REACH] domains " + domainReports.size()
                + " sentences " + totalSentences + " claims " + totalClaims
                + " citable " + citableClaims + " missing " + missingSentences);

        assertThat(totalSentences)
                .as("the corpora carry the sentences the cases are written from")
                .isGreaterThanOrEqualTo(40);
        assertThat(missingSentences)
                .as("EVERY sentence of the corpora was turned into a claim by the production "
                        + "acquisition path; a sentence that was not extracted would leave a "
                        + "necessary evidence that no answer could ever cite")
                .isZero();
        assertThat(citableClaims)
                .as("the corpora carry claims the served path is allowed to cite")
                .isPositive();
    }

    /**
     * The report of one domain.
     *
     * @param domain    the domain
     * @param workspace the workspace the corpus was imported into
     * @param files     the domain's files
     * @param documents the documents' reports
     * @param counts    the domain's counts
     * @return the report
     */
    private static Map<String, Object> domainReport(String domain, String workspace,
            List<String> files, List<Map<String, Object>> documents, Map<String, Object> counts) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("domain", domain);
        report.put("workspace", workspace);
        report.put("files", files);
        report.put("documents", documents);
        report.put("counts", counts);
        return report;
    }

    /**
     * The sentences of a document, as the production extractor splits them.
     *
     * @param path the document
     * @return the sentences
     */
    private static List<String> sentencesOf(Path path) throws IOException {
        List<String> sentences = new ArrayList<>();
        for (String block : Files.readString(path, StandardCharsets.UTF_8).split("\n\\s*\n")) {
            String trimmed = block.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            sentences.add(trimmed);
        }
        return sentences;
    }

    /**
     * The sentences the production path did not turn into a claim.
     *
     * @param path   the document
     * @param claims the claims the production path derived
     * @return the sentences, verbatim
     */
    private static List<String> missingOf(Path path, List<Map<String, Object>> claims)
            throws IOException {
        List<String> extracted = new ArrayList<>();
        for (Map<String, Object> claim : claims) {
            extracted.add(String.valueOf(claim.get("proposition")));
        }
        List<String> missing = new ArrayList<>();
        for (String sentence : sentencesOf(path)) {
            if (!extracted.contains(sentence)) {
                missing.add(sentence);
            }
        }
        return missing;
    }

    /**
     * The domains on the classpath, and the files of each, in a deterministic order.
     *
     * @return the domains, by directory name, each holding its files by file name
     */
    private static Map<String, List<String>> discoverDomains() throws IOException {
        Map<String, List<String>> domains = new TreeMap<>();
        Path root = Path.of("src/test/resources/crj01-reach");
        if (!Files.isDirectory(root)) {
            return domains;
        }
        try (var entries = Files.list(root)) {
            for (Path entry : entries.filter(Files::isDirectory).sorted().toList()) {
                List<String> files = new ArrayList<>();
                try (var inside = Files.list(entry)) {
                    inside.filter(path -> path.getFileName().toString().endsWith(".md"))
                            .sorted()
                            .forEach(path -> files.add(path.getFileName().toString()));
                }
                if (!files.isEmpty()) {
                    domains.put(entry.getFileName().toString(), files);
                }
            }
        }
        return domains;
    }

    /**
     * The bytes of one corpus file, as the classpath holds them.
     *
     * @param domain the domain's directory name
     * @param file   the file's name
     * @return the bytes
     */
    private static byte[] bytes(String domain, String file) throws IOException {
        try (InputStream stream = Crj01ReachCorpusReportBuilder.class
                .getResourceAsStream(CORPUS_RESOURCE + domain + "/" + file)) {
            assertThat(stream).as("the corpus file %s/%s is on the classpath", domain, file)
                    .isNotNull();
            return stream.readAllBytes();
        }
    }

    /**
     * The digest of some bytes.
     *
     * @param bytes the bytes
     * @return the digest, in lowercase hex
     */
    static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }
}
