package io.matrix.consciousness;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * RUN 230 — BrainLoopReportGenerator.
 *
 * <p>Generates a comprehensive report of brain state, including
 * arousal, trace stats, saturation, compliance.
 *
 * <p>Reports saved as text files for human review.
 */
public final class BrainLoopReportGenerator {

    public static String generate(BrainLoopService svc) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== MATRIX Brain Loop Report ===\n");
        sb.append("Arousal: ").append(svc.arousal()).append('\n');
        sb.append("Trace steps: ").append(svc.trace().count()).append('\n');
        if (svc.trace().count() > 0) {
            sb.append("Trace head hash: ").append(svc.trace().steps().get(0).hash).append('\n');
            sb.append("Trace tail hash: ").append(svc.trace().last().hash).append('\n');
        }
        var saturation = BrainLoopSaturation.compute(svc, 1000);
        sb.append("Saturation: cycles=").append(saturation.cycles())
                .append(" capacity=").append(saturation.capacity())
                .append(" ratio=").append(saturation.saturationRatio())
                .append('\n');
        var health = BrainLoopHealth.compute(svc);
        sb.append("Health: score=").append(health.score())
                .append(" status=").append(health.status())
                .append('\n');
        return sb.toString();
    }

    public static void save(BrainLoopService svc, Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, generate(svc));
    }
}
