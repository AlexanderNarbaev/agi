package io.matrix.economy;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SpiralCertificationTest {

    @Test
    void shouldCertifyRegenerativeFnl() {
        SpiralCertification cert = new SpiralCertification();

        var report = cert.evaluate("fnl-1", "author-1",
                List.of("public_dataset", "consented_data"),
                0.2, true, true);

        assertThat(report.level())
                .isEqualTo(SpiralCertification.CertificationLevel.REGENERATIVE);
        assertThat(report.isCertified()).isTrue();
        assertThat(report.passed()).hasSize(5);
    }

    @Test
    void shouldCertifySpiralCompatible() {
        SpiralCertification cert = new SpiralCertification();

        var report = cert.evaluate("fnl-2", "author-2",
                List.of("public_dataset"),
                0.3, true, false);

        assertThat(report.level())
                .isEqualTo(SpiralCertification.CertificationLevel.SPIRAL_COMPATIBLE);
        assertThat(report.isCertified()).isTrue();
    }

    @Test
    void shouldRateBasic() {
        SpiralCertification cert = new SpiralCertification();

        var report = cert.evaluate("fnl-3", "author-3",
                List.of("unknown"),
                0.4, false, false);

        assertThat(report.level())
                .isEqualTo(SpiralCertification.CertificationLevel.BASIC);
    }

    @Test
    void shouldRateUncertified() {
        SpiralCertification cert = new SpiralCertification();

        var report = cert.evaluate("fnl-4", "author-4",
                List.of(),
                0.9, false, false);

        assertThat(report.level())
                .isEqualTo(SpiralCertification.CertificationLevel.UNCERTIFIED);
    }

    @Test
    void shouldTrackCertifications() {
        SpiralCertification cert = new SpiralCertification();
        cert.evaluate("f1", "a1", List.of("data"), 0.2, true, true);
        cert.evaluate("f2", "a2", List.of(), 0.9, false, false);

        assertThat(cert.certifications()).hasSize(2);
        assertThat(cert.certifiedFnls()).hasSize(1);
    }
}
