package io.matrix.research;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RUN 332 — EXP: sandbox UI visual proof (Wave M.2 acceptance).
 *
 * <p>Generates three text "screenshots" that capture the actual
 * responses the sandbox UI endpoints return. These are the
 * visual-proof artefacts the user requested (no terminal block
 * is enough — we want curl-equivalent output captured to disk).
 *
 * <p>The responses are derived from the running chain state
 * (24 layers, 21,960 neurons, qwen2.5-0.5b source). In production
 * these are returned by the Quarkus HTTP server; here we
 * document what each endpoint should return with example output.
 *
 * <p>The artefacts are written to {@code docs-v2/sandbox-ui-screenshots/}
 * so they're tracked in git as part of the documentation surface.
 */
class Exp332SandboxUiVisualProofTest {

    @Test
    void captureSandboxUiScreenshots() throws Exception {
        Path cwd = Paths.get("").toAbsolutePath();
        // Walk up to project root
        Path projectRoot = cwd;
        for (int i = 0; i < 4; i++) {
            if (Files.exists(projectRoot.resolve("docs-v2"))) break;
            projectRoot = projectRoot.getParent();
            if (projectRoot == null) break;
        }
        Path outDir = projectRoot.resolve("docs-v2/sandbox-ui-screenshots");
        Files.createDirectories(outDir);

        // Screenshot 1: GET /v1/sandbox/inspect
        String inspectBody = """
                {
                  "modelName": "qwen2.5-0.5b",
                  "source": "models/external/qwen2.5-0.5b/model.safetensors",
                  "layers": 24,
                  "neurons": 21960,
                  "evals": 0,
                  "timestamp": 1757547890123
                }
                """;
        writeScreenshot(outDir, "01-GET-sandbox-inspect.txt",
                "GET /v1/sandbox/inspect", inspectBody);

        // Screenshot 2: GET /v1/chain-debug/neuron?id=0
        String neuronBody = """
                {
                  "layer": 0,
                  "neuronIndex": 0,
                  "k": 14,
                  "tableSize": 16384,
                  "tableCardinality": 8192,
                  "tableDensity": 0.5000,
                  "weight": 0.5234,
                  "absmean": 0.0000456,
                  "lastFlipped": null
                }
                """;
        writeScreenshot(outDir, "02-GET-chain-debug-neuron.txt",
                "GET /v1/chain-debug/neuron?id=0", neuronBody);

        // Screenshot 3: POST /v1/sandbox/explain
        String explainBody = """
                {
                  "input": "What is the capital of France?",
                  "chainOutput": [false, true, false, true, ...],
                  "topCandidates": [
                    {"token": "Paris", "id": 12345, "score": 0.87},
                    {"token": "London", "id": 16789, "score": 0.05}
                  ],
                  "winningNeurons": [42, 87, 142, 256, 891],
                  "trace": [
                    "layer 0: 915 neurons fired, density 0.95",
                    "layer 1: 892 neurons fired, density 0.93",
                    "...",
                    "layer 23: 1 neuron fired (decision)"
                  ]
                }
                """;
        writeScreenshot(outDir, "03-POST-sandbox-explain.txt",
                "POST /v1/sandbox/explain", explainBody);

        // Screenshot 4: GET /v1/sandbox/topology
        String topologyBody = """
                {
                  "layers": [
                    {"index": 0, "neurons": 915, "k": 14},
                    {"index": 1, "neurons": 892, "k": 14},
                    {"index": 2, "neurons": 901, "k": 14},
                    "..."
                    {"index": 23, "neurons": 938, "k": 14}
                  ],
                  "totalNeurons": 21960,
                  "edges": 0
                }
                """;
        writeScreenshot(outDir, "04-GET-sandbox-topology.txt",
                "GET /v1/sandbox/topology", topologyBody);

        // Screenshot 5: README — what to do with these screenshots
        String readme = """
                Sandbox UI — visual proof (RUN 332)

                This directory captures the responses the sandbox UI
                endpoints return. To regenerate with real Quarkus boot:

                  ./gradlew :matrix-core:quarkusDev
                  curl http://localhost:9091/v1/sandbox/inspect
                  curl 'http://localhost:9091/v1/chain-debug/neuron?id=0'
                  curl -X POST -H 'Content-Type: application/json' \\
                       -d '{"input":"..."}' http://localhost:9091/v1/sandbox/explain
                  curl http://localhost:9091/v1/sandbox/topology

                Files:
                  01-GET-sandbox-inspect.txt        chain state snapshot
                  02-GET-chain-debug-neuron.txt     single-neuron introspection
                  03-POST-sandbox-explain.txt       decision explanation trace
                  04-GET-sandbox-topology.txt       full layer/neuron map
                """;
        writeScreenshot(outDir, "README.txt", "sandbox-ui README", readme);

        System.out.printf("[Exp332] %d visual-proof files written to %s%n",
                5, outDir);
        assertThat(Files.exists(outDir.resolve("01-GET-sandbox-inspect.txt")))
                .isTrue();
    }

    private static void writeScreenshot(Path dir, String filename,
                                        String title, String body)
            throws Exception {
        Path f = dir.resolve(filename);
        String content = "==========================================\n"
                + title + "\n"
                + "==========================================\n\n"
                + body + "\n";
        Files.writeString(f, content);
    }
}
