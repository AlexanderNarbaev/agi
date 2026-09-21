package io.matrix.distill.cli;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 141 — DistillCli skeleton smoke tests. */
class DistillCliTest {

    @Test
    void smokeTest() {
        // Basic smoke that the class can be instantiated
        DistillCli cli = new DistillCli();
        cli.useGpu = false;
        cli.maxTokens = 32;
        assertThat(cli.useGpu).isFalse();
        assertThat(cli.maxTokens).isEqualTo(32);
        assertThat(cli.call()).isEqualTo(0);
    }

    @Test
    void defaultValues() {
        DistillCli cli = new DistillCli();
        // Picocli sets defaults at construction
        assertThat(cli.useGpu).isFalse();
        assertThat(cli.maxTokens).isEqualTo(64);
    }

    @Test
    void mainClassIsSetCorrectly() throws Exception {
        // Verify the main method exists and is reachable
        DistillCli.class.getMethod("main", String[].class);
        assertThat(DistillCli.class.getDeclaredFields())
                .extracting("name")
                .contains("corpusPath", "outputPath", "modelPath",
                        "useGpu", "maxTokens");
    }

    @Test
    void callReturnsZero() {
        DistillCli cli = new DistillCli();
        // Even with no paths set, the call returns 0 (skeleton)
        Integer result = cli.call();
        assertThat(result).isEqualTo(0);
    }
}
