package io.matrix.api.security;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RbacCheckerTest {

    @Test
    void testAdminHasFullAccess() {
        RbacChecker.Principal admin = new RbacChecker.Principal("u1", RbacChecker.Role.ADMIN);
        assertDoesNotThrow(() -> RbacChecker.require(admin, RbacChecker.Role.VIEWER));
        assertDoesNotThrow(() -> RbacChecker.require(admin, RbacChecker.Role.DEVELOPER));
        assertDoesNotThrow(() -> RbacChecker.require(admin, RbacChecker.Role.ADMIN));
    }

    @Test
    void testDeveloperCannotAdminister() {
        RbacChecker.Principal dev = new RbacChecker.Principal("u2", RbacChecker.Role.DEVELOPER);
        assertDoesNotThrow(() -> RbacChecker.require(dev, RbacChecker.Role.DEVELOPER));
        assertDoesNotThrow(() -> RbacChecker.require(dev, RbacChecker.Role.VIEWER));
        assertThrows(SecurityException.class,
            () -> RbacChecker.require(dev, RbacChecker.Role.ADMIN));
    }

    @Test
    void testViewerIsReadOnly() {
        RbacChecker.Principal viewer = new RbacChecker.Principal("u3", RbacChecker.Role.VIEWER);
        assertDoesNotThrow(() -> RbacChecker.require(viewer, RbacChecker.Role.VIEWER));
        assertThrows(SecurityException.class,
            () -> RbacChecker.require(viewer, RbacChecker.Role.DEVELOPER));
    }

    @Test
    void testAnonymousAlwaysForbidden() {
        assertThrows(SecurityException.class,
            () -> RbacChecker.require(null, RbacChecker.Role.VIEWER));
    }

    @Test
    void testRoleHierarchyLevels() {
        assertTrue(RbacChecker.Role.ADMIN.atLeast(RbacChecker.Role.ADMIN));
        assertTrue(RbacChecker.Role.ADMIN.atLeast(RbacChecker.Role.DEVELOPER));
        assertTrue(RbacChecker.Role.DEVELOPER.atLeast(RbacChecker.Role.VIEWER));
        assertFalse(RbacChecker.Role.VIEWER.atLeast(RbacChecker.Role.DEVELOPER));
    }
}
