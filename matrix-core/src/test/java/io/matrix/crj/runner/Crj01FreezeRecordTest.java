package io.matrix.crj.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.matrix.testsupport.RepositoryPaths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CRJ-01 — the FREEZE RECORD's own test: the record must be re-derivable, and its roles
 * binding.
 *
 * <p><b>Why this test exists.</b> The protocol's §5.6 binds the order: "the reviewer
 * publishes the freeze record and the manifest hash <b>before</b> the first arm run". A
 * freeze record is therefore a thing that has to be <b>checkable</b> by a reader who was
 * not there — otherwise it is a claim, not a record. This test proves three things:</p>
 *
 * <ol>
 *   <li>the record carries <b>every field</b> §5.5 names, and no unnamed extra;</li>
 *   <li>the digests are <b>re-derivable</b>: the record's dataset digest equals the digest
 *       of the compact JSON of the cases array plus one line feed, computed independently
 *       here, and the record's generator digest equals the digest of the source file's
 *       bytes;</li>
 *   <li>the <b>roles are binding</b>: a record without them, or with one person in both
 *       roles, is <b>refused</b> — a freeze record that does not say who froze what is not a
 *       freeze record.</li>
 * </ol>
 */
class Crj01FreezeRecordTest {

    /** The split artefact the record is taken over. */
    private static final String SPLIT = "docs-v2/engineering/CRJ-01-DEV-SPLIT.json";

    /** The repository's root. */
    private static final Path ROOT = RepositoryPaths.root();

    @Test
    @DisplayName("the freeze record carries every field of §5.5, and the digests are re-derivable")
    void theFreezeRecordIsCompleteAndReDerivable() throws Exception {
        Crj01FreezeRecord.Record record = Crj01FreezeRecord.compute(ROOT, SPLIT, 20261121L,
                "the case author", "the freezing reviewer");
        Map<String, Object> fields = record.fields();

        // ── every field §5.5 names, and nothing unnamed ──────────────────────────
        for (String name : Crj01FreezeRecord.fieldNames()) {
            assertThat(fields).as("the record's field " + name).containsKey(name);
        }
        assertThat(fields.keySet())
                .as("the record's fields are exactly the named ones")
                .containsExactlyInAnyOrderElementsOf(List.of(
                        "dataset", "dataset.sha256", "dataset.digestScope",
                        "dataset.fullFileSha256", "generator.sourceSha256", "generator.source",
                        "generator.seed", "oracle.sourceSha256", "seeds",
                        "corpusManifestSha256", "branchSha", "baselineSha", "branchShaMeaning",
                        "instrumentCrosscheck", "roles"));

        // ── the dataset's digest is RE-DERIVABLE ─────────────────────────────────
        ObjectMapper json = JsonMapper.builder().build();
        byte[] splitBytes = Files.readAllBytes(ROOT.resolve(SPLIT));
        Map<?, ?> parsed = json.readValue(splitBytes, Map.class);
        byte[] expected = (json.writeValueAsString(parsed.get("cases")) + "\n")
                .getBytes(StandardCharsets.UTF_8);
        String expectedDigest = hex(MessageDigest.getInstance("SHA-256").digest(expected));
        assertThat(fields.get("dataset.sha256"))
                .as("the dataset's digest, re-derived independently")
                .isEqualTo(expectedDigest);
        // and the file's own digest is recorded SEPARATELY, so a re-serialised file and
        // the file on disk are both traceable
        assertThat(fields.get("dataset.fullFileSha256"))
                .isEqualTo(hex(MessageDigest.getInstance("SHA-256").digest(splitBytes)))
                .isNotEqualTo(fields.get("dataset.sha256"));

        // ── the branch SHA is the freeze's OWN commit, not the protocol's baseline ─
        assertThat(fields.get("branchSha"))
                .as("the freeze records its own commit")
                .isNotEqualTo(Crj01FreezeRecord.PROTOCOL_BASELINE_SHA);
        assertThat(fields.get("baselineSha"))
                .as("and the protocol's baseline is recorded beside it")
                .isEqualTo(Crj01FreezeRecord.PROTOCOL_BASELINE_SHA);
        assertThat(fields.get("branchSha")).isEqualTo(crj01FreezeRecordHeadSha());

        // ── the generator's digest is the digest of the source file's bytes ───────
        byte[] generator = Files.readAllBytes(
                ROOT.resolve(Crj01FreezeRecord.GENERATOR_SOURCE));
        assertThat(fields.get("generator.sourceSha256"))
                .isEqualTo(hex(MessageDigest.getInstance("SHA-256").digest(generator)));

        // ── the oracles' digests are SEPARATE, one per oracle ─────────────────────
        @SuppressWarnings("unchecked")
        Map<String, String> oracles = (Map<String, String>) fields.get("oracle.sourceSha256");
        assertThat(oracles).as("the two oracles are digested separately")
                .containsOnlyKeys("domainA", "domainB");
        assertThat(oracles.get("domainA")).isNotEqualTo(oracles.get("domainB"));

        // ── the seeds: the generator's and the domain-B episode list ──────────────
        @SuppressWarnings("unchecked")
        Map<String, Object> seeds = (Map<String, Object>) fields.get("seeds");
        assertThat(seeds).containsKey("generatorSeed");
        assertThat(seeds.get("generatorSeed")).isEqualTo(20261121L);
        @SuppressWarnings("unchecked")
        List<Object> episodes = (List<Object>) seeds.get("domainBEpisodeSeeds");
        assertThat(episodes).as("the domain-B episode seeds are recorded")
                .hasSize(Crj01DomainBRunner.PRECOMMITTED_SEEDS.size());

        // ── the instrument's cross-check: the truth table against the world ──────
        @SuppressWarnings("unchecked")
        Map<String, Object> instrument = (Map<String, Object>) fields.get("instrumentCrosscheck");
        assertThat(instrument.get("assignments")).isEqualTo(4);
        assertThat(instrument.get("agreements")).isEqualTo(4);
        assertThat(instrument.get("agrees")).isEqualTo(Boolean.TRUE);

        // ── the roles ────────────────────────────────────────────────────────────
        @SuppressWarnings("unchecked")
        Map<String, Object> roles = (Map<String, Object>) fields.get("roles");
        assertThat(roles.get("caseAuthor")).isEqualTo("the case author");
        assertThat(roles.get("freezingReviewer")).isEqualTo("the freezing reviewer");
        assertThat(String.valueOf(roles.get("statement")))
                .contains("different people").contains("neither wrote a solver");
    }

    @Test
    @DisplayName("the record REFUSES to be written without its roles, or with one person in both")
    void theRecordRefusesWithoutItsRoles() {
        assertThatThrownBy(() -> Crj01FreezeRecord.compute(ROOT, SPLIT, 1L, "", "the reviewer"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("names its roles");
        assertThatThrownBy(() -> Crj01FreezeRecord.compute(ROOT, SPLIT, 1L, "the author", ""))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> Crj01FreezeRecord.compute(ROOT, SPLIT, 1L, "one person", "one person"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("different people");
    }

    /**
     * The repository's HEAD, resolved from the shell — an INDEPENDENT derivation of what
     * the record computed, so the two can be compared.
     *
     * @return the SHA
     * @throws Exception when the command fails
     */
    private static String crj01FreezeRecordHeadSha() throws Exception {
        Process process = new ProcessBuilder("git", "rev-parse", "HEAD")
                .directory(ROOT.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8).trim();
        assertThat(process.waitFor()).isZero();
        return output;
    }

    /**
     * The lower-case hex of bytes.
     *
     * @param bytes the bytes
     * @return the hex
     */
    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            out.append(Character.forDigit((b >> 4) & 0xF, 16));
            out.append(Character.forDigit(b & 0xF, 16));
        }
        return out.toString();
    }
}
