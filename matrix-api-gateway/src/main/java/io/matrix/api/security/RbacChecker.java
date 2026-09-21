package io.matrix.api.security;

import java.util.Objects;

/**
 * WAVE T-02 — RBAC role checker.
 *
 * <p>Three roles, ordered by privilege:</p>
 * <ul>
 *   <li>{@link Role#ADMIN} — full access, can manage keys, view audit</li>
 *   <li>{@link Role#DEVELOPER} — can call analyze/explain/federate</li>
 *   <li>{@link Role#VIEWER} — read-only access to explain</li>
 * </ul>
 *
 * <p><b>Note:</b> Real JWT signature verification happens in
 * {@code JwtAuthFilter}. This class only enforces role logic on the
 * authenticated principal.</p>
 */
public final class RbacChecker {

    public enum Role {
        ADMIN(3),
        DEVELOPER(2),
        VIEWER(1);

        public final int level;

        Role(int level) {
            this.level = level;
        }

        public boolean atLeast(Role required) {
            return this.level >= required.level;
        }
    }

    /** Authenticated principal — for T-02 just an opaque user ID + role. */
    public record Principal(String userId, Role role) {
        public Principal {
            Objects.requireNonNull(userId, "userId");
            Objects.requireNonNull(role, "role");
        }
    }

    private RbacChecker() {}

    /**
     * Throws {@link SecurityException} if {@code actual} does not meet
     * {@code required}.
     */
    public static void require(Principal actual, Role required) {
        if (actual == null || !actual.role().atLeast(required)) {
            throw new SecurityException(
                "Forbidden: role " + (actual == null ? "ANONYMOUS" : actual.role())
                + " insufficient for " + required);
        }
    }
}
