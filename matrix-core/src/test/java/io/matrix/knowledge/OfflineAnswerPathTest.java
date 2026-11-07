package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * W50 — the proof that the answering path needs no model.
 *
 * <p>Two independent facts are asserted here, because a claim without an
 * artefact is not evidence:
 *
 * <ol>
 *   <li>the knowledge package references no network client and no model
 *       runtime at all — the compiled classes are scanned for the constant
 *       pool entries that would name one;</li>
 *   <li>an answer is nevertheless produced, from the persisted store, with
 *       its claims and their sources attached.</li>
 * </ol>
 *
 * <p>The runtime half of the demonstration — the same four turns with the
 * network pointed at a closed proxy — is {@code scripts/offline-answer-proof.sh}.
 */
class OfflineAnswerPathTest {

    /** Constant-pool entries that would mean the class can reach out. */
    private static final List<String> FORBIDDEN = List.of(
            "java/net/HttpURLConnection",
            "java/net/Socket",
            "java/net/http",
            "java/net/URLConnection",
            "okhttp3",
            "retrofit2",
            "ai/onnxruntime",
            "org/apache/http",
            "org/llama",
            "org/springframework/web/client");

    @Test
    void theEpistemicCoreReferencesNoNetworkClientAndNoModelRuntime() throws Exception {
        Path packageRoot = Paths.get(KnowledgeWorkbench.class.getResource("KnowledgeWorkbench.class").toURI())
                .getParent().getParent();
        // The epistemic core is the top-level package plus the acquisition
        // path. The `dialogue` and `benchmark` subpackages are adapters and
        // harnesses around it: a Telegram transport may of course open a
        // socket, and it is not on the answering path.
        List<Path> classes = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(packageRoot, 2)) {
            walk.filter(path -> path.toString().endsWith(".class")).forEach(classes::add);
        }
        classes.removeIf(path -> {
            Path relative = packageRoot.relativize(path);
            return relative.getNameCount() > 1
                    && !relative.getName(0).toString().equals("acquisition");
        });
        assertThat(classes).as("the compiled epistemic classes must be found").isNotEmpty();

        List<String> offenders = new ArrayList<>();
        for (Path file : classes) {
            String constantPool = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
            for (String marker : FORBIDDEN) {
                if (constantPool.contains(marker)) {
                    offenders.add(packageRoot.relativize(file) + " references " + marker);
                }
            }
        }
        assertThat(offenders)
                .as("the epistemic core must not depend on a network client or a model runtime")
                .isEmpty();
    }

    @Test
    void theAnswerIsBuiltFromThePersistedStoreWithoutAModel(@TempDir Path directory) throws IOException {
        KnowledgeWorkbench workbench = KnowledgeWorkbench.at(directory.resolve("knowledge.sqlite").toString());
        Path document = directory.resolve("usgs-this-dynamic-earth.md");
        try (InputStream source = getClass().getResourceAsStream(
                "/corpus/plate-tectonics/usgs-this-dynamic-earth.md")) {
            assertThat(source).as("the licensed corpus must ship with the application").isNotNull();
            Files.copy(source, document);
        }
        workbench.importDocument("offline", document, "en");

        Answer answer = workbench.ask("offline", "What is plate tectonics?", AbstractionLevel.INTRODUCTORY);

        assertThat(answer.answered()).as("the seeded corpus answers the question").isTrue();
        assertThat(answer.statement()).isNotBlank();
        assertThat(answer.claims()).as("the answer names the claims it rests on").isNotEmpty();
        assertThat(answer.sources()).as("the answer names the sources of those claims").isNotEmpty();
        assertThat(answer.claims())
                .allSatisfy(claim -> assertThat(claim.introducedBySourceId())
                        .as("every cited claim keeps its source identity").isNotBlank());
    }
}
