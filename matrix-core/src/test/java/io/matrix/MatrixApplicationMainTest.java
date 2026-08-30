package io.matrix;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test for {@link MatrixApplication}: the static main added in
 * the Wave H native-build work. We don't actually launch Quarkus
 * here (that needs the full runtime); we just verify that the
 * method exists and is callable.
 */
class MatrixApplicationMainTest {

    @Test
    void staticMainMethodIsCallable() throws Exception {
        var main = MatrixApplication.class.getMethod("main", String[].class);
        assertThat(main).isNotNull();
        assertThat(java.lang.reflect.Modifier.isStatic(main.getModifiers())).isTrue();
        assertThat(main.getParameterCount()).isEqualTo(1);
        assertThat(main.getParameterTypes()[0]).isEqualTo(String[].class);
    }

    @Test
    void classImplementsQuarkusApplication() {
        assertThat(io.quarkus.runtime.QuarkusApplication.class)
                .as("MatrixApplication must implement QuarkusApplication for Quarkus lifecycle")
                .isAssignableFrom(MatrixApplication.class);
    }
}