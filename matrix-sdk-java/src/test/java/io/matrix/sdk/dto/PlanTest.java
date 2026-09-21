package io.matrix.sdk.dto;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlanTest {

    @Test
    void testPlanRates() {
        assertEquals(100, Plan.FREE.maxRequestsPerHour());
        assertEquals(1_000, Plan.PRO.maxRequestsPerHour());
        assertEquals(Integer.MAX_VALUE, Plan.ENTERPRISE.maxRequestsPerHour());
    }

    @Test
    void testPlanCount() {
        assertEquals(3, Plan.values().length);
    }
}
