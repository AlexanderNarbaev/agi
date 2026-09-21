package io.matrix.noosphere;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NoosphereRegistryTest {

    @Test
    void shouldBeEmptyInitially() {
        NoosphereRegistry reg = new NoosphereRegistry();
        assertThat(reg.size()).isEqualTo(0);
    }

    @Test
    void shouldPublishFnl() {
        NoosphereRegistry reg = new NoosphereRegistry();
        FnlPackage fnl = FnlPackage.builder()
                .name("Test FNL")
                .authorInstanceId("instance-1")
                .type("VISION")
                .accuracy(0.9)
                .build();

        var result = reg.publish(fnl);
        assertThat(result.success()).isTrue();
        assertThat(reg.size()).isEqualTo(1);
    }

    @Test
    void shouldRejectInvalidFnl() {
        NoosphereRegistry reg = new NoosphereRegistry();
        FnlPackage fnl = FnlPackage.builder().build();

        var result = reg.publish(fnl);
        assertThat(result.success()).isFalse();
    }

    @Test
    void shouldRevokeEntry() {
        NoosphereRegistry reg = new NoosphereRegistry();
        var result = reg.publish(FnlPackage.builder()
                .name("Test").authorInstanceId("i1").build());

        boolean revoked = reg.revoke(result.entryId());
        assertThat(revoked).isTrue();

        var entry = reg.get(result.entryId());
        assertThat(entry.status()).isEqualTo(NoosphereRegistry.EntryStatus.REVOKED);
    }

    @Test
    void shouldQueryByAuthor() {
        NoosphereRegistry reg = new NoosphereRegistry();
        reg.publish(FnlPackage.builder().name("F1").authorInstanceId("i1").build());
        reg.publish(FnlPackage.builder().name("F2").authorInstanceId("i1").build());
        reg.publish(FnlPackage.builder().name("F3").authorInstanceId("i2").build());

        assertThat(reg.byAuthor("i1")).hasSize(2);
        assertThat(reg.byAuthor("i2")).hasSize(1);
        assertThat(reg.byAuthor("i3")).isEmpty();
    }

    @Test
    void shouldQueryByType() {
        NoosphereRegistry reg = new NoosphereRegistry();
        reg.publish(FnlPackage.builder().name("V1").authorInstanceId("i1").type("VISION").build());
        reg.publish(FnlPackage.builder().name("T1").authorInstanceId("i1").type("TEXT").build());

        assertThat(reg.byType("VISION")).hasSize(1);
        assertThat(reg.byType("TEXT")).hasSize(1);
    }

    @Test
    void shouldLogEvents() {
        NoosphereRegistry reg = new NoosphereRegistry();
        reg.publish(FnlPackage.builder().name("Test").authorInstanceId("i1").build());

        assertThat(reg.eventLog()).isNotEmpty();
        assertThat(reg.eventLog().get(0)).contains("PUBLISH");
    }
}
