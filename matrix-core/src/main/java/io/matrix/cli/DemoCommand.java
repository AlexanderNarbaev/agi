package io.matrix.cli;

import io.matrix.SystemDemo;
import io.matrix.observability.MatrixMetrics;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import picocli.CommandLine.Command;

@Command(name = "demo", description = "Run full system demo — all phases smoke test")
public class DemoCommand implements Runnable {

    @Inject
    Logger log;

    @Inject
    MatrixMetrics metrics;

    @Override
    public void run() {
        log.info("MATRIX System Demo starting...");
        metrics.actorMessage();
        try {
            SystemDemo.main(new String[0]);
            log.info("MATRIX System Demo complete — ALL SYSTEMS NOMINAL");
        } catch (Exception e) {
            log.error("System demo failed", e);
            metrics.actorError();
        }
    }
}
