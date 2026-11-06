package io.matrix.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * RECON-W43.0 — the network boundary of a MATRIX process.
 *
 * <p>These tests are about BEHAVIOUR: where a listener may bind, when the
 * process refuses to start, and when a request is authorized. A test that
 * only asserted that a constant exists would prove nothing about either.
 */
class NetworkExposureTest {

    @AfterEach
    void restoreTheDefault() {
        NetworkExposure.reset();
    }

    @Test
    @DisplayName("a process with no configuration is local-only and binds loopback")
    void aProcessWithNoConfigurationIsLocalOnlyAndBindsLoopback() {
        NetworkExposure exposure = NetworkExposure.of(NetworkExposure.Mode.LOCAL_ONLY, null);

        assertThat(exposure.isNetworkExposed()).isFalse();
        assertThat(exposure.bindAddress()).isEqualTo(NetworkExposure.LOOPBACK);
        assertThat(exposure.hasToken()).isFalse();
    }

    @Test
    @DisplayName("a network-exposed process binds every interface")
    void aNetworkExposedProcessBindsEveryInterface() {
        NetworkExposure exposure =
                NetworkExposure.of(NetworkExposure.Mode.NETWORK_EXPOSED, "secret");

        assertThat(exposure.isNetworkExposed()).isTrue();
        assertThat(exposure.bindAddress()).isEqualTo(NetworkExposure.ALL_INTERFACES);
    }

    @Test
    @DisplayName("a network-exposed process without a credential refuses to start")
    void aNetworkExposedProcessWithoutACredentialRefusesToStart() {
        NetworkExposure exposure = NetworkExposure.of(NetworkExposure.Mode.NETWORK_EXPOSED, null);

        // This is the startup guard. It is the difference between a
        // deployment that is misconfigured and a deployment that serves the
        // network with no way to tell who is asking.
        assertThatThrownBy(() -> exposure.guard("the API gateway"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("the API gateway")
                .hasMessageContaining(NetworkExposure.TOKEN_PROPERTY);
    }

    @Test
    @DisplayName("a local-only process starts without a credential")
    void aLocalOnlyProcessStartsWithoutACredential() {
        NetworkExposure exposure = NetworkExposure.of(NetworkExposure.Mode.LOCAL_ONLY, null);

        exposure.guard("the API gateway");
    }

    @Test
    @DisplayName("a network-exposed process with a credential starts")
    void aNetworkExposedProcessWithACredentialStarts() {
        NetworkExposure exposure =
                NetworkExposure.of(NetworkExposure.Mode.NETWORK_EXPOSED, "secret");

        exposure.guard("the API gateway");
    }

    @Test
    @DisplayName("in network-exposed mode a request without the credential is refused")
    void inNetworkExposedModeARequestWithoutTheCredentialIsRefused() {
        NetworkExposure exposure =
                NetworkExposure.of(NetworkExposure.Mode.NETWORK_EXPOSED, "secret");

        assertThat(exposure.isAuthorized(null)).isFalse();
        assertThat(exposure.isAuthorized("Bearer wrong")).isFalse();
        assertThat(exposure.isAuthorized("Bearer secret")).isTrue();
        assertThat(exposure.isAuthorized("secret")).isFalse();
    }

    @Test
    @DisplayName("in local-only mode a request is allowed without a credential")
    void inLocalOnlyModeARequestIsAllowedWithoutACredential() {
        NetworkExposure exposure = NetworkExposure.of(NetworkExposure.Mode.LOCAL_ONLY, null);

        assertThat(exposure.isAuthorized(null)).isTrue();
    }

    @Test
    @DisplayName("the tenant identity is decided by the server, not by the caller")
    void theTenantIdentityIsDecidedByTheServerNotByTheCaller() {
        NetworkExposure local = NetworkExposure.of(NetworkExposure.Mode.LOCAL_ONLY, null);
        NetworkExposure exposed =
                NetworkExposure.of(NetworkExposure.Mode.NETWORK_EXPOSED, "secret");

        assertThat(local.tenantIdentity()).isEqualTo(NetworkExposure.LOCAL_TENANT);
        assertThat(exposed.tenantIdentity()).isNotEqualTo(NetworkExposure.LOCAL_TENANT);
    }

    @Test
    @DisplayName("the mode is parsed from its name, and an unknown mode is an error")
    void theModeIsParsedFromItsNameAndAnUnknownModeIsAnError() {
        assertThat(NetworkExposure.Mode.parse("LOCAL_ONLY"))
                .isEqualTo(NetworkExposure.Mode.LOCAL_ONLY);
        assertThat(NetworkExposure.Mode.parse("network-exposed"))
                .isEqualTo(NetworkExposure.Mode.NETWORK_EXPOSED);
        assertThat(NetworkExposure.Mode.parse(null)).isEqualTo(NetworkExposure.Mode.LOCAL_ONLY);

        // A typo in a deployment variable must not silently mean "local",
        // and must not silently mean "exposed" either.
        assertThatThrownBy(() -> NetworkExposure.Mode.parse("NETWORK"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("the bearer token is read from the Authorization header")
    void theBearerTokenIsReadFromTheAuthorizationHeader() {
        assertThat(NetworkExposure.bearerToken("Bearer abc")).isEqualTo("abc");
        assertThat(NetworkExposure.bearerToken("bearer abc")).isEqualTo("abc");
        assertThat(NetworkExposure.bearerToken("Basic abc")).isNull();
        assertThat(NetworkExposure.bearerToken("Bearer ")).isNull();
        assertThat(NetworkExposure.bearerToken(null)).isNull();
    }
}
