package io.matrix.api;

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
 * Tenant registry and request filter for multi-tenant isolation.
 *
 * <p>Extracts tenant ID from {@code X-Tenant-Id} header and
 * makes it available via CDI.
 */
@Provider
@TenantAware
public class TenantFilter implements ContainerRequestFilter {

    private final Map<String, TenantContext> tenants = new ConcurrentHashMap<>();

    public TenantFilter() {
        tenants.put("system", TenantContext.system());
    }

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String tenantId = requestContext.getHeaderString("X-Tenant-Id");

        if (tenantId == null || tenantId.isBlank()) {
            tenantId = "system";
        }

        TenantContext tenant = tenants.computeIfAbsent(tenantId,
                id -> TenantContext.create("Tenant-" + id.substring(0, 8)));

        CurrentTenant.set(tenant);
    }

    public TenantContext getOrCreate(String tenantId) {
        return tenants.computeIfAbsent(tenantId,
                id -> TenantContext.create("Tenant-" + id.substring(0, 8)));
    }

    public Map<String, TenantContext> allTenants() {
        return Map.copyOf(tenants);
    }
}
