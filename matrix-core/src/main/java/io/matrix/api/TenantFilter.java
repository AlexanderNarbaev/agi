package io.matrix.api;

import io.matrix.security.NetworkExposure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tenant registry and request filter (RECON-W43.0).
 *
 * <p><b>What changed, and why.</b> This filter used to read the tenant from
 * the {@code X-Tenant-Id} request header. The W42 audit called that a
 * client-controlled tenant identity, and it is: a header is chosen by the
 * caller, so a caller could name any tenant it liked and the workspace
 * would answer as that tenant. The header is now IGNORED.
 *
 * <p>The tenant is now decided by the process, in one place:
 *
 * <ul>
 *   <li>in <b>local-only</b> mode the tenant is the server-controlled local
 *       identity ({@link NetworkExposure#LOCAL_TENANT});</li>
 *   <li>in <b>network-exposed</b> mode the tenant is the identity of the
 *       authenticated principal, which
 *       {@link AuthorizationFilter} has already established — a request
 *       that was refused never reaches this filter, and one that was
 *       allowed carries the credential.</li>
 * </ul>
 *
 * <p>So the tenant is always something the server decided, and never
 * something a client asserted.
 *
 * <p><b>What did not change.</b> The registry still exists and still holds
 * a {@link TenantContext} per tenant, because the knowledge domain asks for
 * the current tenant through CDI and a caller that manages tenants
 * explicitly still can. What changed is that a REQUEST can no longer
 * choose one.
 */
@Provider
@TenantAware
public class TenantFilter implements ContainerRequestFilter {

    private final Map<String, TenantContext> tenants = new ConcurrentHashMap<>();

    public TenantFilter() {
        tenants.put(NetworkExposure.LOCAL_TENANT, TenantContext.system());
        tenants.put("system", TenantContext.system());
    }

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String tenantId = tenantOf(requestContext);
        TenantContext tenant = tenants.computeIfAbsent(tenantId,
                id -> TenantContext.create("Tenant-" + id.substring(0, id.length())));
        CurrentTenant.set(tenant);
    }

    /**
     * The tenant of a request.
     *
     * <p>The client's own {@code X-Tenant-Id} header is not consulted. The
     * identity the authorization filter established is used when there is
     * one, and the server-controlled local identity otherwise.
     *
     * @param requestContext the request
     * @return the tenant identity
     */
    static String tenantOf(ContainerRequestContext requestContext) {
        Object principal = requestContext.getProperty(AuthorizationFilter.PRINCIPAL_PROPERTY);
        if (principal != null) {
            Object tenant = requestContext.getProperty(AuthorizationFilter.TENANT_PROPERTY);
            return tenant == null ? String.valueOf(principal) : String.valueOf(tenant);
        }
        return NetworkExposure.current().tenantIdentity();
    }

    public TenantContext getOrCreate(String tenantId) {
        return tenants.computeIfAbsent(tenantId,
                id -> TenantContext.create("Tenant-" + id.substring(0, id.length())));
    }

    public Map<String, TenantContext> allTenants() {
        return Map.copyOf(tenants);
    }
}
