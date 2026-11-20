package io.matrix.knowledge.epistemic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.matrix.testsupport.RepositoryPaths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * HARD REVIEW — P1, the leakage guard's exemption.
 *
 * <p>The guard exempts the instrument classes from the SET-REFERENCE check only. This probe
 * runs the guard's own method against a TEMPORARY COPY of the tree (the repository root is
 * pointed at the copy for the duration of the call), first with the copy clean and then
 * with a question of the frozen set planted inside an EXEMPT instrument file. The guard
 * must pass on the clean copy and fail on the planted one.</p>
 *
 * <p><b>Two properties this probe must have, or it proves nothing.</b></p>
 *
 * <ol>
 *   <li><b>Portability.</b> The repository root comes from
 *       {@link io.matrix.testsupport.RepositoryPaths#root()} — the property the build sets,
 *       or the marker file walked up to — never from one developer's home directory. The
 *       earlier version named {@code $HOME/Projects/agi/...}: it passed on that machine and
 *       failed on every clean runner, and a test that cannot find the file it verifies
 *       proves nothing.</li>
 *   <li><b>An assertion, not a print.</b> The verdict of the guard is ASSERTED: the clean
 *       copy must pass and the planted copy must fail, with the complaint naming the
 *       planted file. The earlier version only printed whether the guard had failed, so
 *       the probe stayed green even if the guard had stopped catching the plant — a
 *       false-success test, which is worse than no test at all.</li>
 * </ol>
 *
 * <p>The probe never writes into the repository: the plant lives in the temporary copy,
 * and the digest of the ORIGINAL frozen set is taken before and after, so a probe that
 * contaminated the held-out artefact would be caught by the probe itself.</p>
 */
class HardReviewLeakagePlantTest {

    /** The directory of the engineering documents; the frozen set is inside it. */
    private static final String ENGINEERING = "docs-v2/engineering";

    /** The instrument file the exemption covers: it names the set and reads it at run time. */
    private static final String INSTRUMENT =
            "matrix-core/src/test/java/io/matrix/knowledge/concept/definitions/HandLabelledSpans.java";

    @TempDir
    Path directory;

    /**
     * The frozen set of the COPY, found by its FORM at run time: the document whose
     * cases carry a class and a premise. The file carries no name of the artefact.
     */
    private static Path findTheFrozenSet(Path copy) throws Exception {
        try (var documents = Files.list(copy.resolve(ENGINEERING))) {
            for (Path document : documents.toList()) {
                try {
                    JsonNode parsed = new ObjectMapper().readTree(Files.readAllBytes(document));
                    // the frozen set of the guard: its cases carry a class and a premise
                    if (parsed.path("cases").isArray() && parsed.path("cases").size() > 0
                            && parsed.path("cases").get(0).has("class")
                            && parsed.path("cases").get(0).has("premise")) {
                        return document;
                    }
                } catch (Exception notTheSet) {
                    // not the frozen set
                }
            }
        }
        return null;
    }

    /** The question of the first case of the copied frozen set, read at run time. */
    private static String questionOfTheFirstCase(Path copy) throws Exception {
        Path artefact = findTheFrozenSet(copy);
        if (artefact == null) {
            throw new IllegalStateException("the copied engineering documents hold no frozen set");
        }
        JsonNode dataset = new ObjectMapper().readTree(Files.readAllBytes(artefact));
        for (JsonNode entry : dataset.path("cases")) {
            String question = entry.path("question").asText();
            if (!question.isBlank()) {
                return question;
            }
        }
        return "";
    }

    /** The SHA-256 of a file, as hex. */
    private static String digest(Path file) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }

    @Test
    @DisplayName("the exemption covers the NAME check only: a planted question still fails the guard")
    void theExemptionCannotHideContent() throws Exception {
        // ── the ORIGINAL repository: the root is portable ───────────────────────
        Path repository = RepositoryPaths.root();

        // ── the COPY: the exact fixture scope the guard reads — the engineering
        //    documents, and nothing else of the repository.
        Path copy = directory.resolve("copy");
        Path instrument = copy.resolve(INSTRUMENT);
        Files.createDirectories(instrument.getParent());
        // the whole directory is copied, so this file never NAMES the artefact
        Path engineering = copy.resolve(ENGINEERING);
        Files.createDirectories(engineering);
        Path source = repository.resolve(ENGINEERING);
        assertThat(source).as("the engineering documents %s are missing", source).isDirectory();
        try (var documents = Files.list(source)) {
            for (Path document : documents.toList()) {
                if (Files.isRegularFile(document)) {
                    Files.copy(document, engineering.resolve(document.getFileName().toString()),
                            StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        // the copy must carry the frozen set, and it must be the same bytes as the original.
        // The ORIGINAL is addressed BY THE NAME OF THE COPY, not by a name written down
        // here: this file must never name the artefact, or the leakage guard would rightly
        // report this probe as a leak.
        Path copiedSet = findTheFrozenSet(copy);
        assertThat(copiedSet).as("the copy carries no frozen set").isNotNull();
        Path originalSet = source.resolve(copiedSet.getFileName());
        assertThat(originalSet).as("the original frozen set %s is missing", originalSet)
                .isRegularFile();
        String originalDigest = digest(originalSet);
        assertThat(digest(copiedSet)).as("the copy of the frozen set differs from the original")
                .isEqualTo(originalDigest);
        // and the copy must NOT carry the production tree: only the planted instrument
        assertThat(Files.exists(copy.resolve("matrix-core/src/main/java")))
                .as("the copy carries the production tree; the fixture scope is too wide")
                .isFalse();

        Files.writeString(instrument, "package io.matrix.knowledge.concept.definitions;\n"
                + "final class HandLabelledSpans { }\n");

        String property = RepositoryPaths.ROOT_PROPERTY;
        String before = System.getProperty(property);
        try {
            System.setProperty(property, copy.toString());
            System.out.println("  the repository root the guard will use: "
                    + io.matrix.testsupport.RepositoryPaths.root());

            // ── the control: the copy is clean, so the guard PASSES ──────────────
            assertThatCode(() -> new EpistemicBaselineTest().theSetIsNotLeakedIntoTheDevelopmentCode())
                    .as("the leakage guard on the clean copy must pass")
                    .doesNotThrowAnyException();
            System.out.println("### the leakage guard on the clean copy: it passed");

            // ── the plant: a question of the set inside an exempt instrument file ──
            String question = questionOfTheFirstCase(copy);
            if (question.isBlank()) {
                throw new IllegalStateException("the copied artefact holds no question");
            }
            Files.writeString(instrument, "package io.matrix.knowledge.concept.definitions;\n"
                    + "final class HandLabelledSpans {\n"
                    + "    // PLANTED BY THE HARD REVIEW\n"
                    + "    private static final String PLANTED = \"" + question.replace("\"", "\\\"")
                    + "\";\n}\n");
            System.out.println("  the planted question: [" + question + "]");
            System.out.println("  the planted file: " + Files.exists(instrument));
            System.out.println("  the file carries it: "
                    + Files.readString(instrument).contains(question));

            // ── the verdict: the guard MUST fail on the planted copy ─────────────
            // The failure is caught here so the probe can ASSERT it. A version that
            // only printed the outcome stayed green when the guard stopped working.
            String caught = "";
            try {
                new EpistemicBaselineTest().theSetIsNotLeakedIntoTheDevelopmentCode();
            } catch (AssertionError failure) {
                caught = String.valueOf(failure.getMessage());
            }
            System.out.println("### the leakage guard on the planted copy:");
            System.out.println("  the guard failed: " + !caught.isBlank());
            System.out.println("  it says: " + caught.replaceAll("\\s+", " ").trim());

            assertThat(caught)
                    .as("the leakage guard did NOT catch the planted question: the exemption "
                            + "covers more than the name check, or the guard is not working")
                    .isNotBlank();
            assertThat(caught)
                    .as("the guard's complaint must name the planted file, so the reader can "
                            + "see WHAT was caught")
                    .contains(INSTRUMENT);
        } finally {
            if (before == null) {
                System.clearProperty(property);
            } else {
                System.setProperty(property, before);
            }
        }

        // ── the original frozen set must be untouched by the probe ───────────────
        assertThat(digest(originalSet))
                .as("the probe modified the original held-out set: the plant must live in the copy")
                .isEqualTo(originalDigest);
    }
}
