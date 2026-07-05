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
    public MatrixMetrics matrixMetrics(MeterRegistry registry) {
        return new MatrixMetrics(registry);
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
