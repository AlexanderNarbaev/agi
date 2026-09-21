package io.matrix.verification;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;

@Liveness
@ApplicationScoped
public class VerificationHealthCheck implements HealthCheck {

    @Inject
    RuntimeVerifier verifier;

    @Inject
    VerificationReport report;

    @Override
    public HealthCheckResponse call() {
        long critical = report.getCriticalViolations();
        long total = report.getTotalViolations();

        if (critical > 0) {
            return HealthCheckResponse.builder()
                    .name("verification")
                    .down()
                    .withData("criticalViolations", Long.toString(critical))
                    .withData("totalViolations", Long.toString(total))
                    .build();
        }

        return HealthCheckResponse.builder()
                .name("verification")
                .up()
                .withData("totalViolations", Long.toString(total))
                .withData("properties", Integer.toString(verifier.getAvailableProperties().size()))
                .build();
    }
}
