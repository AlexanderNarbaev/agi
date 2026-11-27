package io.matrix.crj.corpus;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.matrix.knowledge.Claim;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CRJ-01 / H2 — the CORPUS REPORT of the H2 development corpus.
 *
 * <p><b>What this instrument is for.</b> The H2 milestone needs a corpus the case author
 * can draw verbatim gold from, and the gold of a CRJ-01 case is not a sentence a writer
 * chooses: it is a sentence the <b>production acquisition path</b> extracted, with the
 * <b>production claim id</b> that extraction gave it. A case written against a sentence the
 * extractor never proposed is a broken case, and the oracle would refuse it — which is the
 * right behaviour, but it is a broken case nonetheless.</p>
 *
 * <p>So the corpus is measured before any case is written, and the measurement is
 * published: {@code docs-v2/engineering/CRJ-01-H2-CORPUS-REPORT.json} names, per document,
 * the digest of its bytes, the source id the production import gave it, and every claim the
 * production path derived from it.</p>
 *
 * <p><b>What the corpus is.</b> Four synthetic documents on one technical subject (the
 * measurement of seafloor spreading), written for this milestone. They are the protocol's
 * "new, third corpus on a different technical subject" — and they are SYNTHETIC, so the
 * milestone owns its bytes outright and no licensed text is redistributed.</p>
 *
 * <p><b>What this instrument does not do.</b> It authors no case, judges no arm and changes
 * no product behaviour. It is the map the case author works from.</p>
 */
class Crj01H2CorpusReportBuilder {

    /** The classpath directory the H2 corpus lives in. */
    private static final String CORPUS_RESOURCE = "/crj01-h2/";

    /** The workspace the corpus is imported into. */
    private static final String WORKSPACE = "crj01-h2-corpus";

    /** The artefact the measurement is written to. */
    private static final String ARTEFACT = "docs-v2/engineering/CRJ-01-H2-CORPUS-REPORT.json";

    /** The corpus files, in the order the production import path reads them. */
    private static final List<String> FILES = List.of(
            "h2-magnetic-anomalies.md",
            "h2-spreading-rate.md",
            "h2-deep-trenches.md",
            "h2-hydrothermal-vents.md");

    @Test
    @DisplayName("the H2 corpus is measured by the production import path, and the report is written")
    void theCorpusIsMeasured(@TempDir Path temporary) throws Exception {
        // ── the corpus, as the bytes on disk ────────────────────────────────────
        Path corpus = Files.createDirectories(temporary.resolve("corpus"));
        for (String file : FILES) {
            Files.write(corpus.resolve(file), bytes(file));
        }

        // ── the workspace, assembled through the PRODUCTION acquisition path ─────
        Path database = Files.createDirectories(temporary.resolve("db")).resolve("w.sqlite");
        KnowledgeWorkbench bench = KnowledgeWorkbench.at(database.toString());
        try {
            Map<String, SourceRecord> sources = new LinkedHashMap<>();
            for (String file : FILES) {
                SourceRecord source = bench.importDocument(WORKSPACE, corpus.resolve(file), file,
                        "en").source();
                sources.put(file, source);
            }
            KnowledgeSnapshot snapshot = bench.workspace(WORKSPACE);

            // ── the report ───────────────────────────────────────────────────────
            Map<String, Object> report = new LinkedHashMap<>();
            report.put("report", "crj-01-h2-corpus-report");
            report.put("version", "1.0");
            report.put("corpus", "crj01-h2 (synthetic, test-owned)");
            report.put("corpusDirectory", "matrix-core/src/test/resources/crj01-h2");
            report.put("acquisitionPath", "the PRODUCTION import path: "
                    + "workbench.importDocument(workspace, path, file, language)");
            report.put("purpose", "the verbatim gold the H2 DEV cases are written from: the "
                    + "sentences the production acquisition path derived, with the production "
                    + "claim ids it gave them");
            report.put("workspace", WORKSPACE);
            report.put("claimStatusNote", "the status a claim carries after the production "
                    + "import's own validation; a case's gold must name a claim of a "
                    + "CITABLE status, because the served path does not cite the others");

            List<Map<String, Object>> documents = new ArrayList<>();
            int totalClaims = 0;
            int citableClaims = 0;
            for (String file : FILES) {
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
                    claims.add(row);
                    totalClaims++;
                    if (io.matrix.knowledge.EpistemicPolicy.isCitable(claim.status())) {
                        citableClaims++;
                    }
                }
                claims.sort(Comparator.comparing(row -> String.valueOf(row.get("proposition"))));
                Map<String, Object> document = new LinkedHashMap<>();
                document.put("file", file);
                document.put("sha256", sha256(Files.readAllBytes(corpus.resolve(file))));
                document.put("sourceId", source.sourceId());
                document.put("claims", claims);
                documents.add(document);
            }
            report.put("documents", documents);
            Map<String, Object> counts = new LinkedHashMap<>();
            counts.put("documents", FILES.size());
            counts.put("claims", totalClaims);
            counts.put("citableClaims", citableClaims);
            report.put("counts", counts);

            ObjectMapper json = JsonMapper.builder().build();
            String text = json.writerWithDefaultPrettyPrinter().writeValueAsString(report);
            Path artefact = RepositoryPaths.root().resolve(ARTEFACT);
            Files.createDirectories(artefact.getParent());
            Files.writeString(artefact, text, StandardCharsets.UTF_8);
            // the artefact must be READABLE and must carry the claims it claims to — comparing
            // the text with itself would assert nothing
            Map<?, ?> reread = JsonMapper.builder().build().readValue(artefact.toFile(), Map.class);
            assertThat(((List<?>) reread.get("documents")).size())
                    .as("the artefact carries every document")
                    .isEqualTo(FILES.size());
            long recorded = ((List<?>) reread.get("documents")).stream()
                    .map(document -> (List<?>) ((Map<?, ?>) document).get("claims"))
                    .mapToLong(List::size).sum();
            assertThat(recorded)
                    .as("the artefact carries every claim the production path derived")
                    .isEqualTo(totalClaims);

            System.out.println("[CRJ-01/H2] the corpus report " + artefact);
            System.out.println("[CRJ-01/H2] claims " + totalClaims + " citable " + citableClaims);

            assertThat(totalClaims)
                    .as("every document carries claims the production path derived")
                    .isGreaterThanOrEqualTo(20);
            assertThat(citableClaims)
                    .as("the corpus carries claims the served path is allowed to cite")
                    .isPositive();
        } finally {
            bench.close();
        }
    }

    /**
     * The bytes of one corpus file, as the classpath holds them.
     *
     * @param file the file's name
     * @return the bytes
     */
    private static byte[] bytes(String file) throws IOException {
        try (InputStream stream = Crj01H2CorpusReportBuilder.class
                .getResourceAsStream(CORPUS_RESOURCE + file)) {
            assertThat(stream).as("the corpus file %s is on the classpath", file).isNotNull();
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
