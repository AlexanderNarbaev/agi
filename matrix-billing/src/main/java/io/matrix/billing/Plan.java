package io.matrix.billing;

/**
 * WAVE T-07 — Subscription Plans.
 *
 * The three public-facing pricing tiers. Each plan maps to a credit
 * allowance and rate-limit (the latter is enforced by matrix-api-gateway).
 */
public enum Plan {
    /** Community tier: 100 credits/hour, basic features. */
    FREE("FREE", "Community", 100, 0.0),

    /** Professional tier: 1,000 credits/hour, full features. */
    PRO("PRO", "Professional", 1_000, 49.0),

    /** Enterprise tier: unlimited credits, SLA, on-prem option. */
    ENTERPRISE("ENTERPRISE", "Enterprise", Integer.MAX_VALUE, 0.0);

    public final String code;
    public final String displayName;
    public final int creditsPerHour;
    public final double pricePerMonth;

    Plan(String code, String displayName, int creditsPerHour, double pricePerMonth) {
        this.code = code;
        this.displayName = displayName;
        this.creditsPerHour = creditsPerHour;
        this.pricePerMonth = pricePerMonth;
    }

    public static Plan fromCode(String code) {
        for (Plan p : values()) {
            if (p.code.equalsIgnoreCase(code)) return p;
        }
        throw new IllegalArgumentException("Unknown plan code: " + code);
    }
}
