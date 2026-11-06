package io.matrix.knowledge.acquisition;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * The corpus manifest: the record of what each file in the corpus is.
 *
 * <p>The mission's §7 asks the import to preserve the structure, the
 * locations, the warnings, the hashes, the deduplication, the licensing and
 * the traceability from a claim back to a span of a source. The hash and
 * the licensing are the two that the store itself does not carry — the store
 * has the content hash of a source, but it has no field for the licence of
 * the text, and the schema is the owner's to change. So the licence lives
 * HERE, in a file beside the corpus, and the import verifies it before it
 * reads a byte of the text.
 *
 * <p><b>What the verification does.</b>
 *
 * <ul>
 *   <li>a listed file whose bytes differ from the manifest is REPORTED, with
 *       both hashes, and it is not imported as if it were the file the
 *       manifest describes;</li>
 *   <li>a file in the corpus directory that the manifest does not list has
 *       NO recorded licence, and that is reported as well — an unlicenced
 *       file in a corpus is a finding, not a detail;</li>
 *   <li>a listed file that is not on disk is reported as missing.</li>
 * </ul>
 *
 * <p><b>Why the hash matters.</b> A corpus that is edited without the
 * manifest being edited is a corpus whose provenance has been broken. The
 * hash is the check, and it is cheap.
 */
public final class CorpusManifest {

    /** The manifest's name inside the corpus directory. */
    public static final String MANIFEST_NAME = "corpus-manifest.json";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** One file of the corpus. */
    public record Entry(String file, DocumentFormat format, String language, String sha256,
                        String licence, String attribution, String purpose) {
    }

    /** What the verification found about one file. */
    public record EntryReport(String file, boolean listed, boolean present, boolean hashMatches,
                              String expectedSha256, String actualSha256, String licence,
                              String format, String language, List<String> warnings) {

        public EntryReport {
            warnings = List.copyOf(warnings == null ? List.of() : warnings);
        }
    }

    /** The verification of the whole corpus. */
    public record Report(List<EntryReport> entries, List<String> warnings) {

        public Report {
            entries = List.copyOf(entries == null ? List.of() : entries);
            warnings = List.copyOf(warnings == null ? List.of() : warnings);
        }

        /** The number of files the manifest lists. */
        public int listed() {
            return (int) entries.stream().filter(EntryReport::listed).count();
        }

        /** The number of files whose bytes match the manifest. */
        public int hashMatches() {
            return (int) entries.stream().filter(EntryReport::hashMatches).count();
        }

        /** The number of files in the corpus directory the manifest does not list. */
        public int unlisted() {
            return (int) entries.stream().filter(e -> !e.listed()).count();
        }

        /** The number of files with no recorded licence. */
        public int unlicensed() {
            return (int) entries.stream()
                    .filter(e -> e.licence() == null || e.licence().isBlank()).count();
        }

        /** Whether every listed file is present and matches its hash. */
        public boolean verified() {
            return listed() == hashMatches() && unlicensed() == 0 && warnings.isEmpty();
        }
    }

    private CorpusManifest() {
    }

    /**
     * Verifies the corpus in a directory against the manifest beside it.
     *
     * @param corpusDirectory the corpus
     * @return what the verification found
     * @throws IOException when the manifest cannot be read
     */
    public static Report verify(Path corpusDirectory) throws IOException {
        return verify(corpusDirectory, corpusDirectory.resolve(MANIFEST_NAME));
    }

    /**
     * Verifies the corpus in a directory against a manifest.
     *
     * @param corpusDirectory the corpus
     * @param manifestPath the manifest
     * @return what the verification found
     * @throws IOException when the manifest cannot be read
     */
    public static Report verify(Path corpusDirectory, Path manifestPath) throws IOException {
        List<String> warnings = new ArrayList<>();
        List<Entry> listed = new ArrayList<>();
        if (Files.exists(manifestPath)) {
            JsonNode root = MAPPER.readTree(Files.readString(manifestPath, StandardCharsets.UTF_8));
            for (JsonNode node : root.path("entries")) {
                listed.add(new Entry(
                        node.path("file").asText(),
                        format(node.path("format").asText()),
                        node.path("language").asText(null),
                        node.path("sha256").asText(null),
                        node.path("licence").asText(null),
                        node.path("attribution").asText(null),
                        node.path("purpose").asText(null)));
            }
        } else {
            warnings.add("the manifest is missing: " + manifestPath.getFileName()
                    + "; no file in this corpus has a recorded licence");
        }

        List<EntryReport> reports = new ArrayList<>();
        for (Entry entry : listed) {
            Path file = corpusDirectory.resolve(entry.file());
            List<String> entryWarnings = new ArrayList<>();
            boolean present = Files.exists(file);
            String actual = present ? sha256(file) : null;
            boolean matches = present && entry.sha256() != null && entry.sha256().equals(actual);
            if (!present) {
                entryWarnings.add("the manifest lists this file, and it is not on disk");
            } else if (!matches) {
                entryWarnings.add("the bytes of this file differ from the manifest: expected "
                        + entry.sha256() + ", found " + actual);
            }
            if (entry.licence() == null || entry.licence().isBlank()) {
                entryWarnings.add("no licence is recorded for this file");
            }
            reports.add(new EntryReport(entry.file(), true, present, matches, entry.sha256(), actual,
                    entry.licence(), entry.format() == null ? null : entry.format().name(),
                    entry.language(), entryWarnings));
        }

        try (Stream<Path> files = Files.list(corpusDirectory)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                String name = file.getFileName().toString();
                if (MANIFEST_NAME.equals(name)) {
                    continue;
                }
                boolean known = listed.stream().anyMatch(entry -> entry.file().equals(name));
                if (!known) {
                    String actual = sha256(file);
                    reports.add(new EntryReport(name, false, true, false, null, actual, null, null,
                            null, List.of("this file is in the corpus and the manifest does not "
                                    + "list it, so it has no recorded licence")));
                    warnings.add("the corpus holds a file the manifest does not list: " + name);
                }
            }
        }

        for (EntryReport report : reports) {
            warnings.addAll(report.warnings());
        }
        return new Report(reports, warnings);
    }

    /** The format of an entry, or null when the manifest names one this pipeline does not read. */
    private static DocumentFormat format(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        for (DocumentFormat format : DocumentFormat.values()) {
            if (format.name().equalsIgnoreCase(name)) {
                return format;
            }
        }
        return null;
    }

    /**
     * The SHA-256 of a file, in lower-case hex.
     *
     * @param file the file
     * @return the hash
     * @throws IOException when the file cannot be read
     */
    public static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(Files.readAllBytes(file));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required of every JVM", impossible);
        }
    }

    /** The files of a corpus, in a stable order. */
    public static List<Path> files(Path corpusDirectory) throws IOException {
        try (Stream<Path> files = Files.list(corpusDirectory)) {
            return files.filter(Files::isRegularFile)
                    .filter(file -> !MANIFEST_NAME.equals(file.getFileName().toString()))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }
    }
}
