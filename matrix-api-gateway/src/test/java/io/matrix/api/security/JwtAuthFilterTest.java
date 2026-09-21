package io.matrix.api.security;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JwtAuthFilterTest {

    private final JwtAuthFilter filter = new JwtAuthFilter();

    private String validToken() {
        long futureExp = (System.currentTimeMillis() / 1000L) + 3600;
        return "Bearer user-1|alice@example.com|" + futureExp + "|PRO|DEVELOPER";
    }

    @Test
    void testValidToken() {
        JwtAuthFilter.Claims c = filter.validate(validToken());
        assertEquals("user-1", c.sub());
        assertEquals("alice@example.com", c.email());
        assertEquals(JwtAuthFilter.Plan.PRO, c.plan());
        assertEquals(RbacChecker.Role.DEVELOPER, c.role());
    }

    @Test
    void testMissingBearerPrefix() {
        assertThrows(SecurityException.class,
            () -> filter.validate("user-1|alice|" + System.currentTimeMillis()/1000L + "|PRO|DEVELOPER"));
    }

    @Test
    void testEmptyToken() {
        assertThrows(SecurityException.class, () -> filter.validate("Bearer "));
    }

    @Test
    void testExpiredToken() {
        long pastExp = (System.currentTimeMillis() / 1000L) - 60;
        String expired = "Bearer user|alice|" + pastExp + "|PRO|DEVELOPER";
        assertThrows(SecurityException.class, () -> filter.validate(expired));
    }

    @Test
    void testMalformedToken() {
        assertThrows(SecurityException.class, () -> filter.validate("Bearer only|2parts"));
    }

    @Test
    void testUnknownPlan() {
        long futureExp = (System.currentTimeMillis() / 1000L) + 3600;
        assertThrows(SecurityException.class,
            () -> filter.validate("Bearer u|e|" + futureExp + "|GOLD|DEVELOPER"));
    }

    @Test
    void testUnknownRole() {
        long futureExp = (System.currentTimeMillis() / 1000L) + 3600;
        assertThrows(SecurityException.class,
            () -> filter.validate("Bearer u|e|" + futureExp + "|PRO|GOD"));
    }
}
