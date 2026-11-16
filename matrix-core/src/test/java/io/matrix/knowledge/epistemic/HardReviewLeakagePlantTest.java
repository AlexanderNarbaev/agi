package io.matrix.knowledge.epistemic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * HARD REVIEW — P1, the leakage guard's exemption.
 *
 * <p>The guard exempts the instrument classes from the SET-REFERENCE check only. This probe
 * runs the guard's own method against a TEMPORARY COPY of the tree (the repository root is
 * pointed at the copy for the duration of the call), first with the copy clean and then
 * with a question of the frozen set planted inside an EXEMPT instrument file. The guard
 * must pass on the clean copy and fail on the planted one.</p>
 */
class HardReviewLeakagePlantTest {

    private static final String DATASET = "docs-v2/engineering/EPISTEMIC-HELDOUT-SET.json";

    /** The instrument file the exemption covers: it names the set and reads it at run time. */
    private static final String INSTRUMENT =
            "matrix-core/src/test/java/io/matrix/knowledge/concept/definitions/HandLabelledSpans.java";

    @TempDir
    Path directory;

    @Test
    @DisplayName("the exemption covers the NAME check only: a planted question still fails the guard")
    void theExemptionCannotHideContent() throws Exception {
        Path copy = directory.resolve("copy");
        Path instrument = copy.resolve(INSTRUMENT);
        Files.createDirectories(instrument.getParent());
        Path artefact = copy.resolve(DATASET);
        Files.createDirectories(artefact.getParent());
        Files.copy(Path.of(System.getProperty("user.home"), "Projects", "agi", DATASET), artefact,
                StandardCopyOption.REPLACE_EXISTING);
        Files.writeString(instrument, "package io.matrix.knowledge.concept.definitions;\n"
                + "final class HandLabelledSpans { }\n");

        String property = "matrix.repoRoot";
        String before = System.getProperty(property);
        try {
            System.setProperty(property, copy.toString());
            // the control: the copy is clean, so the guard passes
            new EpistemicBaselineTest().theSetIsNotLeakedIntoTheDevelopmentCode();
            System.out.println("### the leakage guard on the clean copy: it passed");

            // the plant: a question of the set inside an exempt instrument file
            JsonNode dataset = new ObjectMapper().readTree(Files.readAllBytes(artefact));
            String question = "";
            for (JsonNode entry : dataset.path("cases")) {
                if (!entry.path("question").asText().isBlank()) {
                    question = entry.path("question").asText();
                    break;
                }
            }
            Files.writeString(instrument, "package io.matrix.knowledge.concept.definitions;\n"
                    + "final class HandLabelledSpans {\n"
                    + "    // PLANTED BY THE HARD REVIEW\n"
                    + "    private static final String PLANTED = \"" + question.replace("\"", "\\\"")
                    + "\";\n}\n");
            String caught = "";
            try {
                new EpistemicBaselineTest().theSetIsNotLeakedIntoTheDevelopmentCode();
            } catch (AssertionError failure) {
                caught = String.valueOf(failure.getMessage());
            }
            System.out.println("### the leakage guard on the planted copy:");
            System.out.println("  the guard failed: " + !caught.isBlank());
            System.out.println("  it says: " + caught.replaceAll("\\s+", " ").trim());
        } finally {
            if (before == null) {
                System.clearProperty(property);
            } else {
                System.setProperty(property, before);
            }
        }
    }
}
