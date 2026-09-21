package io.matrix.mediator.hierarchy;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MediatorMessageTest {

    @Test
    void shouldCreateCommand() {
        UUID corrId = UUID.randomUUID();
        var cmd = new MediatorMessage.Command(
                corrId,
                MediatorLevel.LOBE, "lobe-1",
                MediatorLevel.CLUSTER, "cluster-1",
                "REQUEST_RESOURCES", "details");

        assertThat(cmd.correlationId()).isEqualTo(corrId);
        assertThat(cmd.sourceLevel()).isEqualTo(MediatorLevel.LOBE);
        assertThat(cmd.targetLevel()).isEqualTo(MediatorLevel.CLUSTER);
        assertThat(cmd.action()).isEqualTo("REQUEST_RESOURCES");
    }

    @Test
    void shouldCreateOkResponse() {
        UUID corrId = UUID.randomUUID();
        var resp = MediatorMessage.Response.ok(corrId, "done");

        assertThat(resp.success()).isTrue();
        assertThat(resp.result()).isEqualTo("done");
        assertThat(resp.errorMessage()).isNull();
    }

    @Test
    void shouldCreateErrorResponse() {
        UUID corrId = UUID.randomUUID();
        var resp = MediatorMessage.Response.error(corrId, "timeout");

        assertThat(resp.success()).isFalse();
        assertThat(resp.result()).isNull();
        assertThat(resp.errorMessage()).isEqualTo("timeout");
    }
}
