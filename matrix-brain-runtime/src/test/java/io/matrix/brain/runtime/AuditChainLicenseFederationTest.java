package io.matrix.brain.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MIND-W7 — Audit chain, license tiers, federation registry tests.
 */
class AuditChainLicenseFederationTest {

    // ---------- AuditChain ----------

    @Test
    void audit_chain_genesis_and_append() {
        AuditChain chain = new AuditChain();
        assertThat(chain.size()).isZero();
        AuditChain.Entry e1 = chain.append("ANALYZE", "input=hi");
        assertThat(e1.index()).isEqualTo(1L);
        assertThat(e1.prevHash()).isEqualTo("GENESIS");
        assertThat(e1.hash()).isNotBlank();
        AuditChain.Entry e2 = chain.append("TEACH", "fact=paris");
        assertThat(e2.prevHash()).isEqualTo(e1.hash());
        assertThat(chain.size()).isEqualTo(2);
    }

    @Test
    void audit_chain_verify_succeeds_for_clean_chain() {
        AuditChain chain = new AuditChain();
        for (int i = 0; i < 10; i++) chain.append("ACT-" + i, "details");
        assertThat(chain.verify()).isTrue();
    }

    @Test
    void audit_chain_verify_fails_after_tamper() {
        AuditChain chain = new AuditChain();
        for (int i = 0; i < 5; i++) chain.append("ACT", "details");
        assertThat(chain.verify()).isTrue();
        chain.tamper(3L, "TAMPERED");
        assertThat(chain.verify()).isFalse();
    }

    @Test
    void audit_chain_get_returns_null_for_out_of_range() {
        AuditChain chain = new AuditChain();
        chain.append("ACT", "x");
        assertThat(chain.get(0L)).isNull();
        assertThat(chain.get(2L)).isNull();
        assertThat(chain.get(1L)).isNotNull();
    }

    // ---------- LicenseManager ----------

    @Test
    void license_free_can_analyze_but_not_distill() {
        assertThat(LicenseManager.check(
            LicenseManager.Tier.FREE, LicenseManager.Feature.ANALYZE).allowed()).isTrue();
        assertThat(LicenseManager.check(
            LicenseManager.Tier.FREE, LicenseManager.Feature.DISTILL).allowed()).isFalse();
    }

    @Test
    void license_pro_can_learn_but_not_distill() {
        assertThat(LicenseManager.check(
            LicenseManager.Tier.PRO, LicenseManager.Feature.LEARN).allowed()).isTrue();
        assertThat(LicenseManager.check(
            LicenseManager.Tier.PRO, LicenseManager.Feature.DISTILL).allowed()).isFalse();
    }

    @Test
    void license_enterprise_can_distill_and_federate() {
        assertThat(LicenseManager.check(
            LicenseManager.Tier.ENTERPRISE, LicenseManager.Feature.DISTILL).allowed()).isTrue();
        assertThat(LicenseManager.check(
            LicenseManager.Tier.ENTERPRISE, LicenseManager.Feature.FEDERATE).allowed()).isTrue();
    }

    @Test
    void license_denial_includes_reason() {
        LicenseManager.Decision d = LicenseManager.check(
            LicenseManager.Tier.FREE, LicenseManager.Feature.FEDERATE);
        assertThat(d.allowed()).isFalse();
        assertThat(d.reason()).contains("FEDERATE");
    }

    // ---------- FederationRegistry ----------

    @Test
    void federation_register_and_list(@TempDir Path tmp) {
        Path p = tmp.resolve("peers.csv");
        FederationRegistry reg = new FederationRegistry(p);
        reg.register("nodeA", "Matrix A", "http://a:8080");
        reg.register("nodeB", "Matrix B", "http://b:8080");
        assertThat(reg.size()).isEqualTo(2);
        assertThat(reg.list()).hasSize(2);
    }

    @Test
    void federation_heartbeat_updates_timestamp(@TempDir Path tmp) throws Exception {
        Path p = tmp.resolve("peers.csv");
        FederationRegistry reg = new FederationRegistry(p);
        reg.register("nodeA", "Matrix A", "http://a:8080");
        long firstSeen = reg.get("nodeA").lastSeenMillis();
        Thread.sleep(20);
        reg.heartbeat("nodeA");
        long secondSeen = reg.get("nodeA").lastSeenMillis();
        assertThat(secondSeen).isGreaterThan(firstSeen);
    }

    @Test
    void federation_persists_across_reopen(@TempDir Path tmp) {
        Path p = tmp.resolve("peers.csv");
        FederationRegistry r1 = new FederationRegistry(p);
        r1.register("nodeA", "Matrix A", "http://a:8080");
        r1.register("nodeB", "Matrix B", "http://b:8080");
        FederationRegistry r2 = new FederationRegistry(p);
        assertThat(r2.size()).isEqualTo(2);
        assertThat(r2.get("nodeA").endpoint()).isEqualTo("http://a:8080");
        assertThat(r2.get("nodeB").name()).isEqualTo("Matrix B");
    }

    @Test
    void federation_register_is_idempotent(@TempDir Path tmp) {
        Path p = tmp.resolve("peers.csv");
        FederationRegistry reg = new FederationRegistry(p);
        reg.register("nodeA", "Matrix A", "http://a:8080");
        reg.register("nodeA", "Matrix A renamed", "http://a:9090");
        assertThat(reg.size()).isEqualTo(1);
        assertThat(reg.get("nodeA").endpoint()).isEqualTo("http://a:9090");
    }

    @Test
    void federation_get_unknown_returns_null(@TempDir Path tmp) {
        Path p = tmp.resolve("peers.csv");
        FederationRegistry reg = new FederationRegistry(p);
        assertThat(reg.get("nonexistent")).isNull();
    }

    @Test
    void federation_list_ordered_most_recent_first(@TempDir Path tmp) throws Exception {
        Path p = tmp.resolve("peers.csv");
        FederationRegistry reg = new FederationRegistry(p);
        reg.register("A", "A", "http://a");
        Thread.sleep(20);
        reg.register("B", "B", "http://b");
        Thread.sleep(20);
        reg.register("C", "C", "http://c");
        var list = reg.list();
        assertThat(list.get(0).id()).isEqualTo("C");
        assertThat(list.get(2).id()).isEqualTo("A");
    }
}
