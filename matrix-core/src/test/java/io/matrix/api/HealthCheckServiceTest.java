package io.matrix.api;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 129 — HealthCheckService unit tests. */
class HealthCheckServiceTest {

    @Test
    void initialStatusIsUnknown() {
        QwenOnnxBridge stub = new QwenOnnxBridge(Path.of("/tmp/none"));
        HealthCheckService svc = new HealthCheckService(stub, 100, null);
        assertThat(svc.lastStatus()).isEqualTo(HealthCheckService.HealthStatus.UNKNOWN);
        assertThat(svc.totalChecks()).isZero();
        svc.stop();
    }

    @Test
    void detectsHealthyBridge() throws Exception {
        QwenOnnxBridge stub = new QwenOnnxBridge(Path.of("/tmp/none")) {
            @Override
            public boolean isLoaded() { return true; }
        };
        HealthCheckService svc = new HealthCheckService(stub, 30, null);
        svc.start();
        // Wait for at least one check
        Thread.sleep(100);
        assertThat(svc.totalChecks()).isGreaterThan(0);
        assertThat(svc.lastStatus()).isEqualTo(
                HealthCheckService.HealthStatus.HEALTHY);
        svc.stop();
    }

    @Test
    void detectsDegradedBridge() throws Exception {
        QwenOnnxBridge stub = new QwenOnnxBridge(Path.of("/tmp/none")) {
            @Override
            public boolean isLoaded() { return false; }
        };
        HealthCheckService svc = new HealthCheckService(stub, 30, null);
        svc.start();
        Thread.sleep(100);
        assertThat(svc.lastStatus()).isEqualTo(
                HealthCheckService.HealthStatus.DEGRADED);
        svc.stop();
    }

    @Test
    void callbackInvoked() throws Exception {
        QwenOnnxBridge stub = new QwenOnnxBridge(Path.of("/tmp/none")) {
            @Override
            public boolean isLoaded() { return true; }
        };
        CountDownLatch latch = new CountDownLatch(1);
        HealthCheckService svc = new HealthCheckService(stub, 30,
                status -> latch.countDown());
        svc.start();
        assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue();
        svc.stop();
    }

    @Test
    void failureRateTracking() throws Exception {
        QwenOnnxBridge stub = new QwenOnnxBridge(Path.of("/tmp/none")) {
            @Override
            public boolean isLoaded() { return true; }
        };
        HealthCheckService svc = new HealthCheckService(stub, 30, null);
        // No failures yet
        assertThat(svc.failureRate()).isZero();
        svc.stop();
    }

    @Test
    void lastCheckMsUpdates() throws Exception {
        QwenOnnxBridge stub = new QwenOnnxBridge(Path.of("/tmp/none")) {
            @Override
            public boolean isLoaded() { return true; }
        };
        HealthCheckService svc = new HealthCheckService(stub, 30, null);
        svc.start();
        Thread.sleep(80);
        long ms = svc.lastCheckMs();
        assertThat(ms).isGreaterThanOrEqualTo(0);
        assertThat(ms).isLessThan(1000);  // recent
        svc.stop();
    }
}
