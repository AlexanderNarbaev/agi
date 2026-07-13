package io.matrix.api;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for SecurityHeadersFilter.
 *
 * Verifies the filter class is properly configured and all required
 * security header constants are defined.
 */
class SecurityHeadersFilterTest {

    @Test
    void shouldImplementContainerResponseFilter() {
        assertThat(jakarta.ws.rs.container.ContainerResponseFilter.class
                .isAssignableFrom(SecurityHeadersFilter.class)).isTrue();
    }

    @Test
    void shouldBeAnnotatedAsProvider() {
        assertThat(SecurityHeadersFilter.class.getAnnotation(jakarta.ws.rs.ext.Provider.class))
                .isNotNull();
    }

    @Test
    void shouldHaveFilterMethod() throws NoSuchMethodException {
        Method filterMethod = SecurityHeadersFilter.class.getMethod(
                "filter",
                jakarta.ws.rs.container.ContainerRequestContext.class,
                jakarta.ws.rs.container.ContainerResponseContext.class);
        assertThat(filterMethod).isNotNull();
    }

    @Test
    void shouldDefineXContentTypeOptionsHeader() {
        assertThat(SecurityHeadersFilter.HEADER_X_CONTENT_TYPE_OPTIONS)
                .isEqualTo("X-Content-Type-Options");
    }

    @Test
    void shouldDefineXFrameOptionsHeader() {
        assertThat(SecurityHeadersFilter.HEADER_X_FRAME_OPTIONS)
                .isEqualTo("X-Frame-Options");
    }

    @Test
    void shouldDefineXXssProtectionHeader() {
        assertThat(SecurityHeadersFilter.HEADER_X_XSS_PROTECTION)
                .isEqualTo("X-XSS-Protection");
    }

    @Test
    void shouldDefineStrictTransportSecurityHeader() {
        assertThat(SecurityHeadersFilter.HEADER_STRICT_TRANSPORT_SECURITY)
                .isEqualTo("Strict-Transport-Security");
    }

    @Test
    void shouldDefineContentSecurityPolicyHeader() {
        assertThat(SecurityHeadersFilter.HEADER_CONTENT_SECURITY_POLICY)
                .isEqualTo("Content-Security-Policy");
    }

    @Test
    void shouldDefineReferrerPolicyHeader() {
        assertThat(SecurityHeadersFilter.HEADER_REFERRER_POLICY)
                .isEqualTo("Referrer-Policy");
    }

    @Test
    void shouldDefinePermissionsPolicyHeader() {
        assertThat(SecurityHeadersFilter.HEADER_PERMISSIONS_POLICY)
                .isEqualTo("Permissions-Policy");
    }

    @Test
    void shouldDefineAllSevenSecurityHeaders() {
        Set<String> headerFields = Arrays.stream(SecurityHeadersFilter.class.getDeclaredFields())
                .filter(f -> java.lang.reflect.Modifier.isStatic(f.getModifiers()))
                .filter(f -> f.getName().startsWith("HEADER_"))
                .map(f -> f.getName())
                .collect(Collectors.toSet());

        assertThat(headerFields).containsExactlyInAnyOrder(
                "HEADER_X_CONTENT_TYPE_OPTIONS",
                "HEADER_X_FRAME_OPTIONS",
                "HEADER_X_XSS_PROTECTION",
                "HEADER_STRICT_TRANSPORT_SECURITY",
                "HEADER_CONTENT_SECURITY_POLICY",
                "HEADER_REFERRER_POLICY",
                "HEADER_PERMISSIONS_POLICY"
        );
    }

    @Test
    void shouldBeInstantiable() {
        SecurityHeadersFilter instance = new SecurityHeadersFilter();
        assertThat(instance).isNotNull();
    }
}
