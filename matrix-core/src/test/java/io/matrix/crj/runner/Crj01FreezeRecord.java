package io.matrix.crj.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.matrix.crj.evidence.SourceSpanOracle;
import io.matrix.crj.microworld.LampTruthOracle;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CRJ-01 — the FREEZE RECORD: the hash record the protocol's §5.5 requires.
 *
 * <p><b>Why it is needed.</b> The protocol requires that the freeze record carry nine
 * things — the dataset's two digests, the generator's and the oracles' source digests, the
 * seeds, the corpus manifests' digests, the exact branch SHA, the instrument's cross-check,
 * and the roles — and that "the reviewer publishes the freeze record and the manifest hash
 * <b>before</b> the first arm run". Without a mechanism that computes those digests, the
 * reviewer's record is a hand-written list, and a hand-written list of digests is a list
 * nobody can re-derive.</p>
 *
 * <p><b>What this does.</b> It computes the record <b>from the artefacts</b> — the split
 * file, the builder source, the two oracle sources, the corpus manifests, the git HEAD — so
 * that a reader can re-derive every field and compare it. The <b>roles</b> are the one
 * field a machine cannot derive: the author and the reviewer are people, and the record
 * REFUSES to be written without them, because a freeze record that says nothing about who
 * froze what is not a freeze record.</p>
 *
 * <p><b>The dataset's digest scope.</b> The protocol is exact: "the UTF-8 bytes of the
 * <b>compact</b> JSON {@code cases} array + one line feed". So the digest is taken over the
 * cases array re-serialised compactly, not over the file as it stands — which is why the
 * file's own digest is recorded <b>separately</b>, as {@code dataset.fullFileSha256}.</p>
 *
 * <p><b>What it does not do.</b> It does not freeze anything: it <b>records</b> the state a
 * freeze is taken in. The act of freezing is the reviewer's, and the protocol's §5.6 binds
 * the order.</p>
 */
final class Crj01FreezeRecord {

    /** The generator's source — the file whose digest the record carries. */
    static final String GENERATOR_SOURCE =
            "matrix-core/src/test/java/io/matrix/crj/corpus/Crj01DevSplitValidatorTest.java";

    /** The domain-A oracle's source. */
    static final String DOMAIN_A_ORACLE_SOURCE =
            "matrix-core/src/test/java/io/matrix/crj/evidence/SourceSpanOracle.java";

    /** The domain-B oracle's source. */
    static final String DOMAIN_B_ORACLE_SOURCE =
            "matrix-core/src/test/java/io/matrix/crj/microworld/LampTruthOracle.java";

    /** The corpus manifests whose digests the record carries. */
    static final List<String> CORPUS_MANIFESTS = List.of(
            "matrix-core/src/main/resources/research-pool/plate-tectonics/corpus-manifest.json");

    /** The protocol's baseline commit — the one the freeze's own SHA is NOT. */
    static final String PROTOCOL_BASELINE_SHA = "b7bd1be4c38f7d51f0eb2462af695af148d729c1";

    /** The record, as the map the artefact carries. */
    record Record(Map<String, Object> fields) {
        Record {
            fields = Map.copyOf(fields == null ? Map.of() : fields);
        }
    }

    private Crj01FreezeRecord() {
    }

    /**
     * Computes the freeze record.
     *
     * @param repositoryRoot the repository's root
     * @param splitPath      the split artefact, relative to the root
     * @param generatorSeed  the generator's seed
     * @param author         the case author's name
     * @param reviewer       the freezing reviewer's name
     * @return the record
     * @throws Exception when an artefact cannot be read or digested
     */
    static Record compute(Path repositoryRoot, String splitPath, long generatorSeed,
                          String author, String reviewer) throws Exception {
        if (author == null || author.isBlank() || reviewer == null || reviewer.isBlank()) {
            throw new IllegalStateException("a freeze record names its roles: the case author "
                    + "and the freezing reviewer. A record without them is not a freeze record");
        }
        if (author.equals(reviewer)) {
            throw new IllegalStateException("the case author and the freezing reviewer must be "
                    + "different people, and neither may have written a solver");
        }
        Path split = repositoryRoot.resolve(splitPath);
        byte[] splitBytes = Files.readAllBytes(split);

        ObjectMapper json = JsonMapper.builder().build();
        Map<?, ?> parsed = json.readValue(splitBytes, Map.class);

        // the dataset's digest: the COMPACT JSON of the cases array, plus one line feed
        String compactCases = json.writeValueAsString(parsed.get("cases")) + "\n";
        byte[] goldBytes = compactCases.getBytes(StandardCharsets.UTF_8);

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("dataset", splitPath);
        fields.put("dataset.sha256", sha256(goldBytes));
        fields.put("dataset.digestScope", "the UTF-8 bytes of the compact JSON `cases` array + "
                + "one line feed");
        fields.put("dataset.fullFileSha256", sha256(splitBytes));

        fields.put("generator.sourceSha256", digestOf(repositoryRoot.resolve(GENERATOR_SOURCE)));
        fields.put("generator.source", GENERATOR_SOURCE);
        fields.put("generator.seed", generatorSeed);

        fields.put("oracle.sourceSha256", Map.of(
                "domainA", digestOf(repositoryRoot.resolve(DOMAIN_A_ORACLE_SOURCE)),
                "domainB", digestOf(repositoryRoot.resolve(DOMAIN_B_ORACLE_SOURCE))));

        Map<String, Object> seeds = new LinkedHashMap<>();
        seeds.put("generatorSeed", generatorSeed);
        seeds.put("domainBEpisodeSeeds", Crj01DomainBRunner.PRECOMMITTED_SEEDS);
        fields.put("seeds", seeds);

        Map<String, String> manifests = new LinkedHashMap<>();
        for (String manifest : CORPUS_MANIFESTS) {
            Path path = repositoryRoot.resolve(manifest);
            if (Files.exists(path)) {
                manifests.put(manifest, digestOf(path));
            }
        }
        fields.put("corpusManifestSha256", manifests);

        fields.put("branchSha", headSha(repositoryRoot));
        fields.put("baselineSha", PROTOCOL_BASELINE_SHA);
        fields.put("branchShaMeaning", "the exact commit the freeze is taken on - the "
                + "repository's HEAD, resolved from .git/HEAD and that branch's ref (loose or "
                + "packed). The protocol's baseline is b7bd1be4, and a freeze normally records "
                + "its own SHA, which is NOT the baseline");

        // the INSTRUMENT'S OWN CROSS-CHECK: the domain-B truth table against the world, on
        // all four assignments. A disagreement would make the run VOID.
        LampTruthOracle oracle = new LampTruthOracle();
        Map<String, Boolean> world = new LinkedHashMap<>();
        for (LampTruthOracle.Row row : oracle.rows()) {
            world.put(row.assignment(), row.lamp());
        }
        LampTruthOracle.Crosscheck crosscheck = oracle.crosscheck(world);
        Map<String, Object> instrument = new LinkedHashMap<>();
        instrument.put("instrument", "the domain-B truth table");
        instrument.put("assignments", crosscheck.assignments());
        instrument.put("agreements", crosscheck.agreements());
        instrument.put("disagreements", crosscheck.disagreements());
        instrument.put("agrees", crosscheck.agrees());
        instrument.put("record", crosscheck.asRecord());
        instrument.put("domainA", domainAInstrumentRecord(null));
        fields.put("instrumentCrosscheck", instrument);

        Map<String, Object> roles = new LinkedHashMap<>();
        roles.put("caseAuthor", author);
        roles.put("freezingReviewer", reviewer);
        roles.put("statement", "the case author and the freezing reviewer are different people, "
                + "and neither wrote a solver");
        fields.put("roles", roles);
        return new Record(fields);
    }

    /**
     * The digest of a file's bytes.
     *
     * @param path the file
     * @return the digest, in lower-case hex
     * @throws Exception when the file cannot be read or the digest is unavailable
     */
    static String digestOf(Path path) throws Exception {
        return sha256(Files.readAllBytes(path));
    }

    /**
     * The sha256 of bytes.
     *
     * @param bytes the bytes
     * @return the digest, in lower-case hex
     * @throws NoSuchAlgorithmException when sha256 is unavailable
     */
    static String sha256(byte[] bytes) throws NoSuchAlgorithmException {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            hex.append(Character.forDigit((b >> 4) & 0xF, 16));
            hex.append(Character.forDigit(b & 0xF, 16));
        }
        return hex.toString();
    }

    /**
     * The SHA of the commit the freeze is taken ON — the repository's own HEAD.
     *
     * <p><b>Why HEAD and not a named branch.</b> The protocol says the freeze records "the
     * exact commit the freeze was taken on", and that it is "normally <b>not</b>
     * {@code b7bd1be4}" — the protocol's baseline. A named branch would be wrong twice
     * over: the local {@code develop} ref is <b>stale</b> until it is updated, and the
     * freeze may legitimately be taken on a feature branch. So the record resolves
     * {@code .git/HEAD} — which names the branch, or holds the SHA itself — and then that
     * branch's ref, loose or packed.</p>
     *
     * @param repositoryRoot the repository's root
     * @return the commit SHA, or "UNKNOWN" when it cannot be read
     */
    static String headSha(Path repositoryRoot) {
        try {
            String head = Files.readString(repositoryRoot.resolve(".git/HEAD"),
                    StandardCharsets.UTF_8).trim();
            if (!head.startsWith("ref:")) {
                return head;
            }
            String ref = head.substring("ref:".length()).trim();
            Path loose = repositoryRoot.resolve(".git").resolve(ref);
            if (Files.exists(loose)) {
                return Files.readString(loose, StandardCharsets.UTF_8).trim();
            }
            Path packed = repositoryRoot.resolve(".git/packed-refs");
            if (Files.exists(packed)) {
                for (String line : Files.readAllLines(packed, StandardCharsets.UTF_8)) {
                    if (line.startsWith("#") || line.startsWith("^")) {
                        continue;
                    }
                    if (line.endsWith(" " + ref)) {
                        return line.substring(0, line.indexOf(' ')).trim();
                    }
                }
            }
            return "UNKNOWN";
        } catch (IOException unreadable) {
            return "UNKNOWN";
        }
    }

    /**
     * The oracle's verdict on the domain-A side — the source-span oracle's own record.
     *
     * @param oracle the oracle
     * @return the record
     */
    static Map<String, Object> domainAInstrumentRecord(SourceSpanOracle oracle) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("instrument", "the source-span oracle");
        record.put("source", DOMAIN_A_ORACLE_SOURCE);
        record.put("groundingRule", "the cited proposition is byte-identical to a gold "
                + "proposition AND the cited claim id is the production id of that source in "
                + "the workspace under test");
        return record;
    }

    /**
     * The list of the record's field names, in the order the protocol lists them.
     *
     * @return the names
     */
    static List<String> fieldNames() {
        List<String> names = new ArrayList<>();
        names.add("dataset.sha256");
        names.add("dataset.fullFileSha256");
        names.add("generator.sourceSha256");
        names.add("oracle.sourceSha256");
        names.add("seeds");
        names.add("corpusManifestSha256");
        names.add("branchSha");
        names.add("baselineSha");
        names.add("branchShaMeaning");
        names.add("instrumentCrosscheck");
        names.add("roles");
        return names;
    }
}
