package io.matrix.noosphere;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FnlPackageTest {

    @Test
    void shouldBuildWithBuilder() {
        FnlPackage fnl = FnlPackage.builder()
                .name("Edge Detector")
                .type("VISION")
                .version("2.1.0")
                .authorInstanceId("instance-1")
                .accuracy(0.95)
                .generation(42)
                .description("Detects edges in visual input")
                .tags("vision", "edge", "detection")
                .certified(true)
                .snapshotHash("sha256:abc123")
                .build();

        assertThat(fnl.name()).isEqualTo("Edge Detector");
        assertThat(fnl.type()).isEqualTo("VISION");
        assertThat(fnl.accuracy()).isEqualTo(0.95);
        assertThat(fnl.certified()).isTrue();
        assertThat(fnl.tags()).contains("vision", "edge", "detection");
        assertThat(fnl.id()).isNotNull();
    }

    @Test
    void shouldSetDefaults() {
        FnlPackage fnl = FnlPackage.builder()
                .name("Test")
                .authorInstanceId("i1")
                .build();

        assertThat(fnl.version()).isEqualTo("1.0.0");
        assertThat(fnl.type()).isEqualTo("GENERIC");
        assertThat(fnl.tags()).isEmpty();
        assertThat(fnl.certified()).isFalse();
    }
}
