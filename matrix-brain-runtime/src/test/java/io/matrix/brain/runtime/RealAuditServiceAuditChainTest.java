package io.matrix.brain.runtime;

import io.matrix.ethics.EthicalFilter;
import io.matrix.safety.SafetyMonitor;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** Every claim the gateway records is in the tamper-evident chain, and a rewrite breaks it. */
class RealAuditServiceAuditChainTest {

    @Test
    void everyRecordedClaimIsInTheChainAndARewriteBreaksIt() {
        SafetyMonitor safety = new SafetyMonitor(new EthicalFilter());
        RealAuditService service = new RealAuditService(safety);
        service.record("the first claim", "topic-a", true, 0.8);
        service.record("the second claim", "topic-b", false, 0.4);

        AuditChain chain = service.auditChain();
        assertThat(chain.size()).isEqualTo(2);
        assertThat(chain.verify()).isTrue();

        chain.tamper(1, "a rewritten claim");
        assertThat(chain.verify()).isFalse();
    }
}
