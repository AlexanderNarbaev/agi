package io.matrix.api;

import io.matrix.security.NetworkExposure;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

/**
 * The authorization policy of the knowledge workbench (RECON-W43.0).
 *
 * <p><b>What the W42 audit found.</b> A search of every {@code src/main}
 * tree for {@code @RolesAllowed}, {@code @PermitAll}, {@code @Secured},
 * {@code @Authenticated} and {@code SecurityIdentity} returned nothing. The
 * workbench's fifty-two routes were open, including every route that writes
 * a claim, a goal, a workspace or a learning state.
 *
 * <p><b>The policy this filter implements is deny-by-default for
 * mutations.</b>
 *
 * <ul>
 *   <li>Read-only requests (GET, HEAD, OPTIONS) are allowed. The mission's
 *       §10.1 requires the owner to <em>browse</em> the knowledge, and a
 *       local-only process is reachable only from the local host.</li>
 *   <li>State-changing requests (POST, PUT, PATCH, DELETE) are allowed in
 *       local-only mode, because the process has not left the loopback
 *       interface and the local user owns the store.</li>
 *   <li>State-changing requests in network-exposed mode are REFUSED unless
 *       they carry the configured credential. There is no exception, no
 *       header that waives it, and no route that opts out: the mode is a
 *       property of the process, and in that mode a write without the
 *       credential is a write by an unauthenticated caller.</li>
 * </ul>
 *
 * <p><b>Why a filter and not a per-resource annotation.</b> An annotation
 * that someone forgets to put on a new route fails OPEN — the route is
 * simply unprotected, and nothing says so. A filter that denies by default
 * fails CLOSED: a new route is protected until someone deliberately opens
 * it. The mission's §14 forbids "a superficial security filter that protects
 * one route while leaving alternate raw-server routes open", which is a
 * statement about the RAW listeners rather than about this filter; those are
 * bound through {@link NetworkExposure#bindAddress()} instead, so that a
 * listener which forgets to bind correctly never becomes reachable in the
 * first place.
 *
 * <p><b>What the filter does not claim.</b> It is not an identity provider
 * and it does not model roles. It has one credential and one rule, and both
 * are visible in this file.
 */
@Provider
@Priority(Priorities.AUTHORIZATION)
public class AuthorizationFilter implements ContainerRequestFilter {

    /** The request property that carries the authenticated principal. */
    public static final String PRINCIPAL_PROPERTY = "matrix.principal";

    /** The request property that carries the tenant identity. */
    public static final String TENANT_PROPERTY = "matrix.tenant";

    /** The principal of a local-only request. */
    public static final String LOCAL_PRINCIPAL = "local";

    /** The error a refused request carries. */
    public static final String REFUSAL = "This process is exposed to the network and this request "
            + "carries no valid credential. Set " + NetworkExposure.MODE_PROPERTY + "=LOCAL_ONLY for "
            + "local use, or present the configured credential.";

    @Override
    public void filter(ContainerRequestContext request) {
        NetworkExposure exposure = NetworkExposure.current();
        String authorization = request.getHeaderString("Authorization");
        boolean authorized = exposure.isAuthorized(authorization);

        request.setProperty(PRINCIPAL_PROPERTY, authorized ? principal(authorization) : null);
        request.setProperty(TENANT_PROPERTY, exposure.tenantIdentity());

        if (authorized || !changesState(request.getMethod())) {
            return;
        }
        request.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                .type(jakarta.ws.rs.core.MediaType.APPLICATION_JSON)
                .entity("{\"error\":\"unauthorized\",\"detail\":\"" + REFUSAL + "\"}")
                .build());
    }

    /**
     * Whether a method changes state.
     *
     * @param method the HTTP method, or null
     * @return whether the method may write
     */
    static boolean changesState(String method) {
        if (method == null) {
            return false;
        }
        return switch (method.toUpperCase(java.util.Locale.ROOT)) {
            case "POST", "PUT", "PATCH", "DELETE" -> true;
            default -> false;
        };
    }

    /** The principal a request is made under. */
    private String principal(String authorization) {
        if (!NetworkExposure.current().isNetworkExposed()) {
            return LOCAL_PRINCIPAL;
        }
        String token = NetworkExposure.bearerToken(authorization);
        return token == null ? null : "bearer";
    }
}
