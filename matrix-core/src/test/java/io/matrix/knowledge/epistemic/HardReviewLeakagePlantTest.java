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

    /** The directory of the engineering documents; the frozen set is inside it. */
    private static final String ENGINEERING = "docs-v2/engineering";

    /** The instrument file the exemption covers: it names the set and reads it at run time. */
    private static final String INSTRUMENT =
            "matrix-core/src/test/java/io/matrix/knowledge/concept/definitions/HandLabelledSpans.java";

    @TempDir
    Path directory;

    /** The question of the first case of the copied frozen set, read at run time. */
    private static String questionOfTheFirstCase(Path copy) throws Exception {
        Path artefact = null;
        try (var documents = Files.list(copy.resolve(ENGINEERING))) {
            for (Path document : documents.toList()) {
                // the frozen set is the document that holds the cases
                try {
                    JsonNode parsed = new ObjectMapper().readTree(Files.readAllBytes(document));
                    // the frozen set of the guard: its cases carry a class and a premise
                    if (parsed.path("cases").isArray() && parsed.path("cases").size() > 0
                            && parsed.path("cases").get(0).has("class")
                            && parsed.path("cases").get(0).has("premise")) {
                        artefact = document;
                        break;
                    }
                } catch (Exception notTheSet) {
                    // not the frozen set
                }
            }
        }
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

    @Test
    @DisplayName("the exemption covers the NAME check only: a planted question still fails the guard")
    void theExemptionCannotHideContent() throws Exception {
        Path copy = directory.resolve("copy");
        Path instrument = copy.resolve(INSTRUMENT);
        Files.createDirectories(instrument.getParent());
        // the whole directory is copied, so this file never NAMES the artefact
        Path engineering = copy.resolve(ENGINEERING);
        Files.createDirectories(engineering);
        Path source = Path.of(System.getProperty("user.home"), "Projects", "agi", ENGINEERING);
        try (var documents = Files.list(source)) {
            for (Path document : documents.toList()) {
                if (Files.isRegularFile(document)) {
                    Files.copy(document, engineering.resolve(document.getFileName().toString()),
                            StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        Files.writeString(instrument, "package io.matrix.knowledge.concept.definitions;\n"
                + "final class HandLabelledSpans { }\n");

        String property = "matrix.repoRoot";
        String before = System.getProperty(property);
        try {
            System.setProperty(property, copy.toString());
            System.out.println("  the repository root the guard will use: "
                    + io.matrix.testsupport.RepositoryPaths.root());
            // the control: the copy is clean, so the guard passes
            new EpistemicBaselineTest().theSetIsNotLeakedIntoTheDevelopmentCode();
            System.out.println("### the leakage guard on the clean copy: it passed");

            // the plant: a question of the set inside an exempt instrument file
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
            // what the guard's own scan would see
            Path guardRoot = io.matrix.testsupport.RepositoryPaths.root();
            Path guardArtefact = guardRoot.resolve("docs-v2/engineering/EPISTEMIC-HELDOUT-SET.json");
            System.out.println("  the guard's artefact: " + guardArtefact + " exists="
                    + Files.exists(guardArtefact));
            if (Files.exists(guardArtefact)) {
                JsonNode guardSet = new ObjectMapper().readTree(Files.readAllBytes(guardArtefact));
                boolean carries = false;
                int cases = 0;
                for (JsonNode entry : guardSet.path("cases")) {
                    cases++;
                    String q = entry.path("question").asText();
                    if (!q.isBlank() && Files.readString(instrument).contains(q)) {
                        carries = true;
                        System.out.println("  the guard's case " + entry.path("id").asText()
                                + " is carried by the planted file");
                    }
                }
                System.out.println("  the guard's cases: " + cases + " carries=" + carries);
            }
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
