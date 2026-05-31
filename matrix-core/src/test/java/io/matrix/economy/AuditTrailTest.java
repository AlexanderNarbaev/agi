package io.matrix.economy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuditTrailTest {

    @Test
    void shouldRecordTransaction() {
        AuditTrail trail = new AuditTrail();

        trail.record(AuditTrail.TransactionType.PUBLICATION_REWARD,
                "i1", 10.0, 100.0, "test");

        assertThat(trail.size()).isEqualTo(1);
    }

    @Test
    void shouldFilterByInstance() {
        AuditTrail trail = new AuditTrail();
        trail.record(AuditTrail.TransactionType.PUBLICATION_REWARD,
                "i1", 10, 100, "p1");
        trail.record(AuditTrail.TransactionType.DOWNLOAD_CHARGE,
                "i2", -1, 50, "d1");

        assertThat(trail.forInstance("i1")).hasSize(1);
        assertThat(trail.forInstance("i2")).hasSize(1);
    }

    @Test
    void shouldFilterByType() {
        AuditTrail trail = new AuditTrail();
        trail.record(AuditTrail.TransactionType.CERTIFICATION_BONUS,
                "i1", 5, 105, "cert");
        trail.record(AuditTrail.TransactionType.CERTIFICATION_BONUS,
                "i2", 5, 55, "cert2");

        assertThat(trail.byType(AuditTrail.TransactionType.CERTIFICATION_BONUS)).hasSize(2);
    }

    @Test
    void shouldCalculateTotalRewards() {
        AuditTrail trail = new AuditTrail();
        trail.record(AuditTrail.TransactionType.PUBLICATION_REWARD,
                "i1", 10, 100, "p1");
        trail.record(AuditTrail.TransactionType.CERTIFICATION_BONUS,
                "i1", 5, 105, "cert");
        trail.record(AuditTrail.TransactionType.DOWNLOAD_CHARGE,
                "i1", -1, 104, "d1");

        assertThat(trail.totalRewards("i1")).isEqualTo(15.0);
    }

    @Test
    void shouldBeImmutable() {
        AuditTrail trail = new AuditTrail();
        trail.record(AuditTrail.TransactionType.PUBLICATION_REWARD,
                "i1", 10, 100, "p");

        var all = trail.all();
        assertThat(all).hasSize(1);
    }
}
