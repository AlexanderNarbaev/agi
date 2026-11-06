package io.matrix;

import io.matrix.events.KafkaEventJournal;
import io.matrix.events.KafkaTopics;
import io.matrix.observability.MatrixMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import picocli.CommandLine;

@QuarkusMain
public class MatrixApplication implements QuarkusApplication {

    @Inject
    CommandLine.IFactory factory;

    /**
     * Static main for native-image entry-point. Quarkus 3.38.3 +
     * GraalVM 25.x has trouble auto-detecting the main class when it
     * implements {@link QuarkusApplication}, so we provide an explicit
     * static main that calls {@link Quarkus#run(Class, String...)}.
     */
    public static void main(String... args) {
        // RECON-W43: the ONNX runtime extracts a native library of about
        // 583 MB into a fresh temporary directory for EVERY session load and
        // never removes it. Fifty-two of them filled the per-user quota of
        // the /tmp tmpfs and took the build down with it. The application's
        // scratch directory is its own, and the runs before this one are
        // cleaned up here - BEFORE anything loads a model.
        io.matrix.Scratch.prepare();
        Quarkus.run(MatrixApplication.class, args);
    }

    @Override
    public int run(String... args) {
        if (args.length == 0) {
            System.out.println("MATRIX v2.0.0 — HTTP server mode (port " +
                    System.getProperty("quarkus.http.port", "9091") + ")");
            System.out.println("  REST API: http://localhost:9091/api/v1/");
            System.out.println("  Metrics:  http://localhost:9091/metrics");
            System.out.println("  Health:   http://localhost:9091/q/health");
            Quarkus.waitForExit();
            return 0;
        }
        return new CommandLine(new MatrixTopCommand(), factory).execute(args);
    }

    @Produces
    @Singleton
    public ActorSystem<Void> actorSystem() {
        return ActorSystem.create(Behaviors.empty(), "matrix");
    }

    @Produces
    @Singleton
    public KafkaEventJournal kafkaEventJournal(
            @ConfigProperty(name = "kafka.bootstrap.servers", defaultValue = "localhost:9092")
            String bootstrapServers) {
        KafkaTopics.ensureTopics(bootstrapServers);
        return new KafkaEventJournal("neuron-events", bootstrapServers);
    }
}
