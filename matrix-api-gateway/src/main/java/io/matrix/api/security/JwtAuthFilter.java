package io.matrix.api.security;

import java.util.Objects;

/**
 * WAVE T-02 — JWT validation stub.
 *
 * <p>Full signature verification requires a JWKS endpoint. T-02 ships with
 * a structural-only validator (decodes payload, checks expiry) suitable for
 * local development and tests. Production T-02.5 wires up RS256/EdDSA
 * signature verification against the configured issuer.</p>
 *
 * <p><b>CONSTITUTION:</b> No LLM is invoked anywhere in token parsing.</p>
 */
public final class JwtAuthFilter {

    /** Plan tiers used for rate limit assignment. */
    public enum Plan {
        FREE(100),           // 100 req/hr
        PRO(1_000),          // 1k req/hr
        ENTERPRISE(Integer.MAX_VALUE);  // unlimited

        public final int requestsPerHour;

        Plan(int requestsPerHour) {
            this.requestsPerHour = requestsPerHour;
        }
    }

    /** Decoded token claims (subset). */
    public record Claims(String sub, String email, long exp, Plan plan, RbacChecker.Role role) {}

    /**
     * Validate a Bearer token and extract claims. T-02 stub: structural check only.
     *
     * @throws SecurityException if token is malformed or expired
     */
    public Claims validate(String bearerToken) {
        if (bearerToken == null) {
            throw new SecurityException("Authorization header missing");
        }
        if (!bearerToken.startsWith("Bearer ")) {
            throw new SecurityException("Authorization header missing Bearer prefix");
        }
        String token = bearerToken.substring("Bearer ".length()).trim();
        if (token.isEmpty()) {
            throw new SecurityException("Empty bearer token");
        }

        // T-02 stub: tokens have the form
        //   sub|email|expEpochSeconds|plan|role
        // Production T-02.5: replace with RS256/EdDSA verification via SmallRye JWT.
        String[] parts = token.split("\\|", -1);
        if (parts.length != 5) {
            throw new SecurityException("Malformed token (expected 5 segments)");
        }

        String sub = parts[0];
        String email = parts[1];
        long exp;
        try {
            exp = Long.parseLong(parts[2]);
        } catch (NumberFormatException nfe) {
            throw new SecurityException("Invalid token exp claim");
        }
        long nowSeconds = System.currentTimeMillis() / 1000L;
        if (exp < nowSeconds) {
            throw new SecurityException("Token expired");
        }

        Plan plan;
        try {
            plan = Plan.valueOf(parts[3]);
        } catch (IllegalArgumentException iae) {
            throw new SecurityException("Unknown plan: " + parts[3]);
        }

        RbacChecker.Role role;
        try {
            role = RbacChecker.Role.valueOf(parts[4]);
        } catch (IllegalArgumentException iae) {
            throw new SecurityException("Unknown role: " + parts[4]);
        }

        return new Claims(sub, email, exp, plan, role);
    }
}
