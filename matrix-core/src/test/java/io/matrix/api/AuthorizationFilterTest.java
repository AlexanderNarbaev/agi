package io.matrix.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.matrix.security.NetworkExposure;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * RECON-W43.0 — the authorization policy of the workbench.
 *
 * <p>These tests exercise the FILTER, not a constant: they hand it a
 * request and observe whether the request was allowed through. The
 * repository already contains a test for the response-header filter that
 * only asserts that constants exist, and the W43 mission's §14 forbids
 * exactly that shape for a behaviour that matters.
 *
 * <p>The request context is a dynamic proxy rather than a hand-written
 * double, so that the test states only the four things the filter actually
 * uses and does not have to be updated when the interface grows.
 */
class AuthorizationFilterTest {

    @AfterEach
    void restoreTheDefault() {
        NetworkExposure.reset();
    }

    /** The outcome of one request through the filter. */
    private record Outcome(boolean aborted, Response refusal, Map<String, Object> properties) {
    }

    /**
     * Runs one request through the filter.
     *
     * @param method the HTTP method
     * @param headers the request headers
     */
    private Outcome run(String method, Map<String, String> headers) {
        Map<String, Object> properties = new HashMap<>();
        boolean[] aborted = {false};
        Response[] refusal = {null};

        ContainerRequestContext context = (ContainerRequestContext) Proxy.newProxyInstance(
                ContainerRequestContext.class.getClassLoader(),
                new Class<?>[]{ContainerRequestContext.class},
                (proxy, invoked, args) -> switch (invoked.getName()) {
                    case "getMethod" -> method;
                    case "getHeaderString" -> headers.get(String.valueOf(args[0]));
                    case "setProperty" -> {
                        properties.put(String.valueOf(args[0]), args[1]);
                        yield null;
                    }
                    case "getProperty" -> properties.get(String.valueOf(args[0]));
                    case "abortWith" -> {
                        aborted[0] = true;
                        refusal[0] = (Response) args[0];
                        yield null;
                    }
                    case "toString" -> "request";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(invoked.getName());
                });

        new AuthorizationFilter().filter(context);
        return new Outcome(aborted[0], refusal[0], properties);
    }

    @Test
    @DisplayName("in local-only mode an anonymous write is allowed")
    void inLocalOnlyModeAnAnonymousWriteIsAllowed() {
        NetworkExposure.configure(NetworkExposure.Mode.LOCAL_ONLY, null);

        Outcome outcome = run("POST", Map.of());

        assertThat(outcome.aborted()).isFalse();
    }

    @Test
    @DisplayName("in network-exposed mode an anonymous write is refused")
    void inNetworkExposedModeAnAnonymousWriteIsRefused() {
        NetworkExposure.configure(NetworkExposure.Mode.NETWORK_EXPOSED, "secret");

        Outcome outcome = run("POST", Map.of());

        assertThat(outcome.aborted()).isTrue();
        assertThat(outcome.refusal()).isNotNull();
        assertThat(outcome.refusal().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("in network-exposed mode a write with the credential is allowed")
    void inNetworkExposedModeAWriteWithTheCredentialIsAllowed() {
        NetworkExposure.configure(NetworkExposure.Mode.NETWORK_EXPOSED, "secret");

        Outcome outcome = run("POST", Map.of("Authorization", "Bearer secret"));

        assertThat(outcome.aborted()).isFalse();
    }

    @Test
    @DisplayName("in network-exposed mode a write with a wrong credential is refused")
    void inNetworkExposedModeAWriteWithAWrongCredentialIsRefused() {
        NetworkExposure.configure(NetworkExposure.Mode.NETWORK_EXPOSED, "secret");

        Outcome outcome = run("POST", Map.of("Authorization", "Bearer nope"));

        assertThat(outcome.aborted()).isTrue();
    }

    @Test
    @DisplayName("in network-exposed mode an anonymous read is allowed")
    void inNetworkExposedModeAnAnonymousReadIsAllowed() {
        NetworkExposure.configure(NetworkExposure.Mode.NETWORK_EXPOSED, "secret");

        Outcome outcome = run("GET", Map.of());

        assertThat(outcome.aborted()).isFalse();
    }

    @Test
    @DisplayName("every state-changing method is covered, and no read method is")
    void everyStateChangingMethodIsCoveredAndNoReadMethodIs() {
        assertThat(AuthorizationFilter.changesState("POST")).isTrue();
        assertThat(AuthorizationFilter.changesState("PUT")).isTrue();
        assertThat(AuthorizationFilter.changesState("PATCH")).isTrue();
        assertThat(AuthorizationFilter.changesState("DELETE")).isTrue();
        assertThat(AuthorizationFilter.changesState("post")).isTrue();
        assertThat(AuthorizationFilter.changesState("GET")).isFalse();
        assertThat(AuthorizationFilter.changesState("HEAD")).isFalse();
        assertThat(AuthorizationFilter.changesState("OPTIONS")).isFalse();
        assertThat(AuthorizationFilter.changesState(null)).isFalse();
    }

    @Test
    @DisplayName("the tenant of a request is the server's, never the caller's header")
    void theTenantOfARequestIsTheServersNeverTheCallersHeader() {
        NetworkExposure.configure(NetworkExposure.Mode.LOCAL_ONLY, null);

        Outcome outcome = run("GET", Map.of("X-Tenant-Id", "someone-else"));

        assertThat(outcome.properties())
                .containsEntry(AuthorizationFilter.TENANT_PROPERTY, NetworkExposure.LOCAL_TENANT);
        assertThat(outcome.properties().get(AuthorizationFilter.TENANT_PROPERTY))
                .isNotEqualTo("someone-else");
    }

    @Test
    @DisplayName("the tenant of an authenticated request comes from the authenticated identity")
    void theTenantOfAnAuthenticatedRequestComesFromTheAuthenticatedIdentity() {
        NetworkExposure.configure(NetworkExposure.Mode.NETWORK_EXPOSED, "secret");

        Outcome outcome = run("GET",
                Map.of("Authorization", "Bearer secret", "X-Tenant-Id", "someone-else"));

        assertThat(outcome.properties().get(AuthorizationFilter.TENANT_PROPERTY))
                .isNotEqualTo("someone-else");
    }
}
