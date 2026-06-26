package io.matrix.api;

/**
 * Thread-local holder for the current tenant context.
 */
public final class CurrentTenant {

    private static final ThreadLocal<TenantContext> current = new ThreadLocal<>();

    private CurrentTenant() {}

    public static void set(TenantContext tenant) {
        current.set(tenant);
    }

    public static TenantContext get() {
        return current.get();
    }

    public static String tenantId() {
        TenantContext tc = current.get();
        return tc != null ? tc.tenantId() : "system";
    }

    public static void clear() {
        current.remove();
    }
}
