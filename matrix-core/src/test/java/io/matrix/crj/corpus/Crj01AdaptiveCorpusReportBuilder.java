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
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CRJ-01 / ADAPTIVE-COMPETENCE — the CORPUS REPORT of the two fresh development domains.
 *
 * <p><b>What this instrument is for.</b> The milestone needs corpora the independent case
 * author can draw <b>verbatim</b> gold from, and the gold of a CRJ-01 case is not a sentence a
 * writer chooses: it is a sentence the <b>production acquisition path</b> extracted, with the
 * <b>production claim id</b> that extraction gave it. A case written against a sentence the
 * extractor never proposed is a broken case, and the oracle refuses it — which is the right
 * behaviour, but it is a broken case nonetheless.</p>
 *
 * <p>So the corpora are measured before any case is written, and the measurement is published:
 * {@code docs-v2/engineering/CRJ-01-ADAPTIVE-CORPUS-REPORT.json} names, per document, the digest
 * of its bytes, the source id the production import gave it, and every claim the production path
 * derived from it. The independent case author works from that report — and from nothing else.</p>
 *
 * <p><b>What the corpora are.</b> <b>Two</b> synthetic document sets on <b>two genuinely
 * different</b> technical subjects, written for this milestone and owned by it. They are the
 * protocol's "new, third corpus on a different technical subject", twice over, so that a finding
 * on one subject is not a finding about a subject. They are SYNTHETIC, so the milestone owns its
 * bytes outright and no licensed text is redistributed.</p>
 *
 * <p><b>Why the report is generic.</b> Every directory under {@code crj01-adaptive/} is a
 * domain; the instrument discovers them rather than naming them, so a domain added later is
 * measured by the same instrument and by the same rules. The discovery is by directory name and
 * the file order inside a domain is the file name order — <b>deterministic</b>, and stated here
 * because an evidence artefact whose order depends on a file system listing cannot be
 * re-derived.</p>
 *
 * <p><b>What this instrument does not do.</b> It authors no case, judges no arm and changes no
 * product behaviour. It is the map the case author works from.</p>
 */
class Crj01AdaptiveCorpusReportBuilder {

    /** The classpath directory the adaptive corpora live in. */
    private static final String CORPUS_RESOURCE = "/crj01-adaptive/";

    /** The artefact the measurement is written to. */
    private static final String ARTEFACT =
            "docs-v2/engineering/CRJ-01-ADAPTIVE-CORPUS-REPORT.json";

    @Test
    @DisplayName("the adaptive corpora are measured by the production import path, and the report "
            + "is written")
    void theCorporaAreMeasured(@TempDir Path temporary) throws Exception {
        // ── the corpora, as the bytes on disk ───────────────────────────────────
        Map<String, List<String>> domains = discoverDomains();
        assertThat(domains)
                .as("the milestone carries at least two genuinely different development domains")
                .hasSizeGreaterThanOrEqualTo(2);

        List<Map<String, Object>> domainReports = new ArrayList<>();
        int totalClaims = 0;
        int citableClaims = 0;
        for (Map.Entry<String, List<String>> domain : domains.entrySet()) {
            Path corpus = Files.createDirectories(temporary.resolve("corpus").resolve(domain.getKey()));
            for (String file : domain.getValue()) {
                Files.write(corpus.resolve(file), bytes(domain.getKey(), file));
            }

            // ── the workspace, assembled through the PRODUCTION acquisition path ───
            Path database = Files.createDirectories(temporary.resolve("db")).resolve("w.sqlite");
            KnowledgeWorkbench bench = KnowledgeWorkbench.at(database.toString());
            try {
                String workspace = "crj01-adaptive-corpus-" + domain.getKey();
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
                        if (io.matrix.knowledge.EpistemicPolicy.isCitable(claim.status())) {
                            domainCitable++;
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
                totalClaims += domainClaims;
                citableClaims += domainCitable;

                Map<String, Object> domainReport = new LinkedHashMap<>();
                domainReport.put("domain", domain.getKey());
                domainReport.put("workspace", workspace);
                domainReport.put("files", domain.getValue());
                domainReport.put("documents", documents);
                Map<String, Object> counts = new LinkedHashMap<>();
                counts.put("documents", domain.getValue().size());
                counts.put("claims", domainClaims);
                counts.put("citableClaims", domainCitable);
                domainReport.put("counts", counts);
                domainReports.add(domainReport);
            } finally {
                bench.close();
            }
        }

        // ── the report ───────────────────────────────────────────────────────────
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("report", "crj-01-adaptive-corpus-report");
        report.put("version", "1.0");
        report.put("corpus", "crj01-adaptive (synthetic, test-owned)");
        report.put("corpusDirectory", "matrix-core/src/test/resources/crj01-adaptive");
        report.put("acquisitionPath", "the PRODUCTION import path: "
                + "workbench.importDocument(workspace, path, file, language)");
        report.put("purpose", "the verbatim gold the ADAPTIVE-COMPETENCE DEV cases are written "
                + "from: the sentences the production acquisition path derived, with the production "
                + "claim ids it gave them");
        report.put("claimStatusNote", "the status a claim carries after the production import's "
                + "own validation; a case's gold must name a claim of a CITABLE status, because "
                + "the served path does not cite the others");
        report.put("domainOrderNote", "the domains are reported in the lexicographic order of "
                + "their directory names, and the files of a domain in the lexicographic order of "
                + "their names; the order is stated so the artefact can be re-derived");
        report.put("domains", domainReports);
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("domains", domainReports.size());
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
        assertThat(((List<?>) reread.get("domains")).size())
                .as("the artefact carries every domain")
                .isEqualTo(domainReports.size());
        long recorded = ((List<?>) reread.get("domains")).stream()
                .map(domain -> (List<?>) ((Map<?, ?>) domain).get("documents"))
                .mapToLong(List::size).sum();
        assertThat(recorded)
                .as("the artefact carries every document of every domain")
                .isEqualTo(domains.values().stream().mapToInt(List::size).sum());

        System.out.println("[CRJ-01/ADAPTIVE] the corpus report " + artefact);
        System.out.println("[CRJ-01/ADAPTIVE] domains " + domainReports.size()
                + " claims " + totalClaims + " citable " + citableClaims);

        assertThat(totalClaims)
                .as("every document carries claims the production path derived")
                .isGreaterThanOrEqualTo(40);
        assertThat(citableClaims)
                .as("the corpora carry claims the served path is allowed to cite")
                .isPositive();
    }

    /**
     * The domains on the classpath, and the files of each, in a deterministic order.
     *
     * @return the domains, by directory name, each holding its files by file name
     */
    private static Map<String, List<String>> discoverDomains() throws IOException {
        Map<String, List<String>> domains = new TreeMap<>();
        Path root = Path.of("src/test/resources/crj01-adaptive");
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
        try (InputStream stream = Crj01AdaptiveCorpusReportBuilder.class
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
