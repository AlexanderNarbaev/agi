package io.matrix.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TenantContextTest {

    @Test
    void systemTenantHasFixedValues() {
        var t = TenantContext.system();
        assertEquals("system", t.tenantId());
        assertEquals("system", t.instanceId());
        assertEquals("System", t.displayName());
    }

    @Test
    void systemTenantsAreEqual() {
        var a = TenantContext.system();
        var b = TenantContext.system();
        assertEquals(a, b);
        assertEquals(a.tenantId(), b.tenantId());
    }

    @Test
    void createGeneratesUniqueIds() {
        var a = TenantContext.create("Tenant-A");
        var b = TenantContext.create("Tenant-A");
        assertEquals("Tenant-A", a.displayName());
        assertEquals("Tenant-A", b.displayName());
        assertNotEquals(a.tenantId(), b.tenantId());
        assertNotNull(a.instanceId());
        assertNotNull(b.instanceId());
    }

    @Test
    void recordComponents() {
        var t = new TenantContext("id-1", "inst-1", "Display");
        assertEquals("id-1", t.tenantId());
        assertEquals("inst-1", t.instanceId());
        assertEquals("Display", t.displayName());
    }

    @Test
    void equalRecordsByValue() {
        var a = new TenantContext("t", "i", "d");
        var b = new TenantContext("t", "i", "d");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void notEqualDifferentIds() {
        var a = new TenantContext("t1", "i", "d");
        var b = new TenantContext("t2", "i", "d");
        assertNotEquals(a, b);
    }

    @Test
    void toStringContainsTenantId() {
        var t = TenantContext.system();
        assertNotNull(t.toString());
        assertTrue(t.toString().contains("system"));
    }
}