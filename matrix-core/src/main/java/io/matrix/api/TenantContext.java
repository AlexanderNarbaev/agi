package io.matrix.api;

import java.util.UUID;

/**
 * Tenant context isolated per tenant for multi-tenant deployments.
 *
 * <p>Each tenant has an independent instance of the MATRIX cognitive system.
 * Tenants cannot access each other's data, neurons, or evolutionary state.
 */
public record TenantContext(
        String tenantId,
        String instanceId,
        String displayName
) {
    public static TenantContext create(String displayName) {
        String id = UUID.randomUUID().toString();
        return new TenantContext(id, id, displayName);
    }

    public static TenantContext system() {
        return new TenantContext("system", "system", "System");
    }
}
