package io.matrix.billing;

/**
 * WAVE T-07 — Billing Module facade.
 *
 * Singleton access point for matrix-api-gateway integration.
 * Replaces T-01 placeholder.
 *
 * <p>Modules exposed:</p>
 * <ul>
 *   <li>{@link CreditLedger} — internal currency accounting</li>
 *   <li>{@link SubscriptionService} — customer lifecycle</li>
 *   <li>{@link LicenseValidator} — cryptographic license checks</li>
 *   <li>{@link StripeWebhookHandler} — Stripe event processing</li>
 * </ul>
 *
 * <p><b>CONSTITUTION compliance:</b> All billing ops are transactional.
 * No LLM.</p>
 */
public final class BillingModule {

    /** Module version. */
    public static final String VERSION = "0.1.0-T07";

    /** Currency unit for API credits (display). */
    public static final String CREDIT_UNIT = "MCR";

    /** Stripe webhook secret prefix check (do not store real secret in code). */
    public static final String STRIPE_KEY_PREFIX = "whsec_";

    private static final CreditLedger LEDGER = new CreditLedger();
    private static final LicenseValidator VALIDATOR = new LicenseValidator("t07-dev-signing-secret");
    private static final SubscriptionService SUBSCRIPTIONS = new SubscriptionService(
        LEDGER, VALIDATOR, "t07-dev-signing-secret");
    private static final StripeWebhookHandler WEBHOOK = new StripeWebhookHandler(
        "t07-dev-webhook-secret", SUBSCRIPTIONS, LEDGER);

    private BillingModule() {}

    public static String status() {
        return "matrix-billing:" + VERSION
            + ":credit=" + CREDIT_UNIT
            + ":customers=" + SUBSCRIPTIONS.customerCount();
    }

    public static CreditLedger ledger() { return LEDGER; }
    public static SubscriptionService subscriptions() { return SUBSCRIPTIONS; }
    public static LicenseValidator validator() { return VALIDATOR; }
    public static StripeWebhookHandler webhook() { return WEBHOOK; }
}
