package io.matrix.economy;

import io.matrix.noosphere.FnlPackage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RegenerativeEconomicsTest {

    @Test
    void shouldPublishFnlWithAudit() {
        RegenerativeEconomics eco = new RegenerativeEconomics();
        FnlPackage fnl = FnlPackage.builder()
                .name("Test FNL").authorInstanceId("i1").accuracy(0.9).build();

        double reward = eco.publishFnl(fnl);

        assertThat(reward).isGreaterThan(0);
        assertThat(eco.auditTrail().size()).isEqualTo(1);
        assertThat(eco.economyLog()).isNotEmpty();
    }

    @Test
    void shouldCertifyFnl() {
        RegenerativeEconomics eco = new RegenerativeEconomics();

        var report = eco.certify("fnl-1", "author-1",
                List.of("public_data"), 0.3, true, true);

        assertThat(report.isCertified()).isTrue();
        assertThat(report.level())
                .isEqualTo(SpiralCertification.CertificationLevel.REGENERATIVE);
    }

    @Test
    void shouldContributeResourcesWithCredits() {
        RegenerativeEconomics eco = new RegenerativeEconomics();

        double credits = eco.contributeResources("i1", 4.0, 16.0, 100.0);

        assertThat(credits).isGreaterThan(0);
        assertThat(eco.pool().contributorCount()).isEqualTo(1);
    }

    @Test
    void shouldDownloadFnlWithCredits() {
        RegenerativeEconomics eco = new RegenerativeEconomics();
        FnlPackage fnl = FnlPackage.builder()
                .name("F").authorInstanceId("i1").build();
        eco.publishFnl(fnl);

        boolean allowed = eco.downloadFnl("i1", "Test");

        assertThat(allowed).isTrue();
    }

    @Test
    void shouldRejectDownloadWithoutCredits() {
        RegenerativeEconomics eco = new RegenerativeEconomics();

        boolean allowed = eco.downloadFnl("poor-instance", "Test");

        assertThat(allowed).isFalse();
        assertThat(eco.economyLog().stream()
                .anyMatch(l -> l.contains("DENIED"))).isTrue();
    }

    @Test
    void shouldLogAllActivity() {
        RegenerativeEconomics eco = new RegenerativeEconomics();
        FnlPackage fnl = FnlPackage.builder()
                .name("F").authorInstanceId("i1").build();

        eco.publishFnl(fnl);
        eco.contributeResources("i1", 2, 8, 50);
        eco.downloadFnl("i1", "F");

        assertThat(eco.economyLog()).hasSize(3);
    }

    @Test
    void shouldExposeSubComponents() {
        RegenerativeEconomics eco = new RegenerativeEconomics();

        assertThat(eco.creditModel()).isNotNull();
        assertThat(eco.auditTrail()).isNotNull();
        assertThat(eco.certification()).isNotNull();
        assertThat(eco.pool()).isNotNull();
    }
}
