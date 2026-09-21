package io.matrix.distill.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * RUN 141 — Matrix Distill CLI (skeleton).
 *
 * <p>This is the entry point for offline knowledge distillation. It is
 * INTENTIONALLY a separate Gradle subproject so that:
 * <ul>
 *   <li>matrix-core runtime classpath never includes ONNX inference libs
 *       (CONSTITUTION I)</li>
 *   <li>Phase γ distillation pipeline can run as one-shot CLI on a
 *       workstation without spinning up MATRIX runtime</li>
 *   <li>The CLI can be invoked from cron, CI, or shell scripts</li>
 * </ul>
 *
 * <p>Future RUNs in phase γ will populate this with Qwen
 * inference, booleanization, weight-serialization steps.
 */
@Command(
        name = "matrix-distill",
        mixinStandardHelpOptions = true,
        version = "matrix-distill 1.0.0",
        description = "Offline knowledge distillation from LLM to Boolean Chain weights"
)
public class DistillCli implements Callable<Integer> {

    @Parameters(index = "0", description = "Path to input corpus (Q&A pairs JSONL)")
    Path corpusPath;

    @Parameters(index = "1", description = "Path to output weights file (JSON)")
    Path outputPath;

    @Option(names = "--model", description = "Path to model directory (HF or ONNX)")
    Path modelPath;

    @Option(names = "--use-gpu", description = "Enable CUDA execution provider")
    boolean useGpu = false;

    @Option(names = "--max-tokens",
            description = "Maximum tokens per generation (default: ${DEFAULT-VALUE})")
    int maxTokens = 64;

    @Override
    public Integer call() {
        System.out.println("[distill] corpus=" + corpusPath);
        System.out.println("[distill] output=" + outputPath);
        System.out.println("[distill] model=" + modelPath);
        System.out.println("[distill] useGpu=" + useGpu);
        System.out.println("[distill] maxTokens=" + maxTokens);
        System.out.println("[distill] (skeleton — phase γ will populate)");
        return 0;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new DistillCli()).execute(args);
        System.exit(exitCode);
    }
}
