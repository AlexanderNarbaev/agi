package io.matrix.evolution;

import io.matrix.ethics.EthicalFilter;
import io.matrix.ethics.StructuralSafetyGuard;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class ProtectedSelfRewriteTest {

    @Test
    void shouldApproveSafeRewrite() {
        var ethics = new EthicalFilter();
        var guard = StructuralSafetyGuard.defaults();
        var psr = new ProtectedSelfRewrite(ethics, guard);
        var req = new ProtectedSelfRewrite.RewriteRequest(
                "rw-1", "io.matrix.util.Cache", "evict",
                "Add LRU-eviction policy to Cache to reduce memory pressure under load.",
                java.util.Map.of("severity", "low"));
        AtomicBoolean applied = new AtomicBoolean();
        var decision = psr.gateAndApply(req, r -> applied.set(true));
        assertThat(decision.approved()).isTrue();
        assertThat(applied).isTrue();
    }

    @Test
    void shouldRejectByEthicsOnHarmfulDescription() {
        var ethics = new EthicalFilter();
        var guard = StructuralSafetyGuard.defaults();
        var psr = new ProtectedSelfRewrite(ethics, guard);
        var req = new ProtectedSelfRewrite.RewriteRequest(
                "rw-2", "io.matrix.foo", "bar",
                "Modify kill switch to bypass safety drivers",
                java.util.Map.of("severity", "low"));
        var decision = psr.gateOnly(req);
        assertThat(decision.blocked()).isTrue();
        assertThat(decision.reason()).contains("EthicalFilter");
    }

    @Test
    void shouldEscalateHighRiskToHumanGate() {
        var ethics = new EthicalFilter();
        var guard = StructuralSafetyGuard.defaults();
        var psr = new ProtectedSelfRewrite(ethics, guard);
        // Use a gated operation directly so the StructuralSafetyGuard sees it
        // in the default gated-operations set ("deploy_production").
        var req = new ProtectedSelfRewrite.RewriteRequest(
                "rw-3", "io.matrix.Deployment", "deploy_production",
                "Optimize deployment pipeline throughput.",
                java.util.Map.of("severity", "high"));
        // The class#method is constructed as "io.matrix.Deployment#deploy_production",
        // so the synthetic operation matches the gated name regardless of prefix.
        var decision = psr.gateOnly(req);
        assertThat(decision.status())
                .isEqualTo(ProtectedSelfRewrite.Decision.Status.ESCALATED);
        assertThat(decision.structuralVerdict().gateId()).isPresent();
    }

    @Test
    void shouldRejectWhenApplierThrows() {
        var ethics = new EthicalFilter();
        var guard = StructuralSafetyGuard.defaults();
        var psr = new ProtectedSelfRewrite(ethics, guard);
        var req = new ProtectedSelfRewrite.RewriteRequest(
                "rw-4", "io.matrix.util.Cache", "prune",
                "Compress cache entries to save disk",
                java.util.Map.of("severity", "low"));
        var decision = psr.gateAndApply(req, r -> { throw new RuntimeException("disk full"); });
        assertThat(decision.status())
                .isEqualTo(ProtectedSelfRewrite.Decision.Status.REJECTED);
        assertThat(decision.reason()).contains("Applier threw");
    }
}
