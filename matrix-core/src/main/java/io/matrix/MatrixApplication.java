package io.matrix;

import io.matrix.observability.MatrixMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import picocli.CommandLine;

@QuarkusMain
public class MatrixApplication implements QuarkusApplication {

    @Inject
    CommandLine.IFactory factory;

    @Override
    public int run(String... args) {
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
}
