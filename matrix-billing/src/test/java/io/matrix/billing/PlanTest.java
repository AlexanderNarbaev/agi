package io.matrix.billing;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlanTest {

    @Test
    void testFreePlan() {
        Plan free = Plan.FREE;
        assertEquals("FREE", free.code);
        assertEquals(100, free.creditsPerHour);
        assertEquals(0.0, free.pricePerMonth);
    }

    @Test
    void testProPlan() {
        Plan pro = Plan.PRO;
        assertEquals(1_000, pro.creditsPerHour);
        assertEquals(49.0, pro.pricePerMonth);
    }

    @Test
    void testEnterprisePlan() {
        Plan ent = Plan.ENTERPRISE;
        assertEquals(Integer.MAX_VALUE, ent.creditsPerHour);
    }

    @Test
    void testFromCode() {
        assertEquals(Plan.FREE, Plan.fromCode("free"));
        assertEquals(Plan.PRO, Plan.fromCode("PRO"));
        assertEquals(Plan.ENTERPRISE, Plan.fromCode("enterprise"));
    }

    @Test
    void testFromCodeUnknownThrows() {
        assertThrows(IllegalArgumentException.class, () -> Plan.fromCode("gold"));
    }

    @Test
    void testAllPlans() {
        assertEquals(3, Plan.values().length);
    }
}
