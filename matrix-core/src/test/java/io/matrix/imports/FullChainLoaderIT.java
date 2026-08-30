package io.matrix.imports;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wave I test: load ALL 24 transformer blocks from Qwen2.5-0.5B
 * via {@link FullChainLoader}, run a forward pass, verify the chain.
 *
 * <p>Requires {@code models/external/qwen2.5-0.5b/model.safetensors}
 * — copied there by the session. Skipped if the file is missing.
 */
class FullChainLoaderIT {

    private static final List<Path> CANDIDATES = List.of(
            Path.of("models/external/qwen2.5-0.5b/model.safetensors"),
            Path.of("/tmp/opencode/matrix-import/models--Qwen--Qwen2.5-0.5B/snapshots/060db6499f32faf8b98477b0a26969ef7d8b9987/model.safetensors"));

    private static Path findSafetensors() {
        for (Path p : CANDIDATES) {
            if (Files.isRegularFile(p)) return p;
        }
        return null;
    }

    @Test
    void loadAllLayersFromQwenSafetensors() throws Exception {
        Path safetensors = findSafetensors();
        if (safetensors == null) {
            System.out.println("[Wave I] no safetensors found — skipping");
            return;
        }
        System.out.println("[Wave I] loading ALL layers from " + safetensors);
        long t0 = System.currentTimeMillis();
        BooleanChainRunner runner = FullChainLoader.loadAll(safetensors, 1 << 14, "model");
        long ms = System.currentTimeMillis() - t0;

        System.out.println("[Wave I] =======================================");
        System.out.println("[Wave I] model:           " + runner.modelName());
        System.out.println("[Wave I] layers:          " + runner.layerCount());
        System.out.println("[Wave I] total neurons:   " + runner.totalNeurons());
        System.out.println("[Wave I] load time:       " + ms + " ms");
        System.out.println("[Wave I] =======================================");

        // sanity: full Qwen 0.5B has 24 transformer blocks
        assertThat(runner.layerCount()).isEqualTo(24);
        assertThat(runner.totalNeurons()).isGreaterThan(10_000);

        // forward pass: 896-bit random input (Qwen 0.5B hidden dim)
        boolean[] input = new boolean[896];
        for (int i = 0; i < 896; i++) input[i] = (i * 31 & 1) == 1;
        t0 = System.nanoTime();
        boolean[] out = runner.evaluate(input);
        long forwardMs = (System.nanoTime() - t0) / 1_000_000;

        System.out.println("[Wave I] forward pass:    " + forwardMs + " ms");
        System.out.println("[Wave I] output bits set:  " + countSet(out));
        System.out.println("[Wave I] =======================================");
        assertThat(forwardMs).isLessThan(500L);  // 24-block chain should run in <500 ms
    }

    private static int countSet(boolean[] bits) {
        int c = 0;
        for (boolean b : bits) if (b) c++;
        return c;
    }
}