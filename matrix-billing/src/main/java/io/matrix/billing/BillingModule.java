package io.matrix.billing;

/**
 * WAVE T-01 placeholder — Wave T-07 will implement Stripe + credit ledger.
 *
 * <p>Final design (T-07):</p>
 * <ul>
 *   <li>{@code SubscriptionService}: Stripe-backed plan management.</li>
 *   <li>{@code CreditLedger}: Internal token-based API quota tracking.</li>
 *   <li>{@code LicenseValidator}: Cryptographic license key validation (Ed25519).</li>
 * </ul>
 *
 * <p><b>CONSTITUTION compliance:</b> No inference, no LLM. Pure billing/business logic.</p>
 */
public final class BillingModule {

    /** Module version. */
    public static final String VERSION = "0.1.0-T01";

    /** Currency unit for API credits (display). */
    public static final String CREDIT_UNIT = "MCR"; // MATRIX Credit

    /** Stripe publishable key prefix check (do not store secret in code). */
    public static final String STRIPE_KEY_PREFIX = "pk_live_";

    private BillingModule() {
        // static facade only
    }

    public static String status() {
        return "matrix-billing:" + VERSION + ":credit=" + CREDIT_UNIT;
    }
}
