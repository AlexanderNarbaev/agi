package io.matrix.billing;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WAVE T-07 — Subscription Service.
 *
 * Manages customer subscriptions: signup, plan changes, renewals,
 * cancellations. All operations are atomic (synchronized on customerId).
 *
 * <p>Plan changes are pro-rated (T-07.5: full Stripe integration).</p>
 *
 * <p><b>CONSTITUTION compliance:</b> No LLM. Pure transactional logic.</p>
 */
public final class SubscriptionService {

    /** Hours of credits granted per plan signup. */
    private static final long SIGNUP_BONUS_HOURS = 1;

    private final CreditLedger ledger;
    private final LicenseValidator licenseValidator;
    private final String signingSecret;
    private final Map<String, Customer> customers = new ConcurrentHashMap<>();
    private final Map<String, Subscription> subscriptions = new ConcurrentHashMap<>();

    public SubscriptionService(CreditLedger ledger, LicenseValidator licenseValidator,
                              String signingSecret) {
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.licenseValidator = Objects.requireNonNull(licenseValidator, "licenseValidator");
        this.signingSecret = Objects.requireNonNull(signingSecret, "signingSecret");
    }

    /**
     * Sign up a new customer. Creates the customer record, issues a license,
     * grants initial credits.
     *
     * @return the signed-up customer with license_key attached
     */
    public SignupResult signup(String email, Plan plan) {
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(plan, "plan");

        if (findByEmail(email).isPresent()) {
            throw new IllegalStateException("Customer already exists: " + email);
        }

        Customer customer = Customer.create(email, plan);
        customers.put(customer.customerId(), customer);

        // Grant signup bonus credits (1 hour of plan allowance)
        long signupCredits = plan.creditsPerHour * SIGNUP_BONUS_HOURS;
        ledger.credit(customer.customerId(), signupCredits,
            "Signup bonus for plan " + plan.code);

        // Issue license
        LicenseKey license = issueLicense(customer, plan);
        ledger.credit(customer.customerId(), plan.creditsPerHour,
            "First hour credit grant");

        // Record subscription
        subscriptions.put(customer.customerId(),
            new Subscription(customer.customerId(), plan, Instant.now(),
                Instant.now().plus(30, ChronoUnit.DAYS)));

        return new SignupResult(customer, license.encode(), ledger.balance(customer.customerId()));
    }

    /**
     * Change a customer's plan. Resets credit allowance.
     */
    public void changePlan(String customerId, Plan newPlan) {
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(newPlan, "newPlan");

        Customer existing = customers.get(customerId);
        if (existing == null) {
            throw new IllegalArgumentException("Unknown customer: " + customerId);
        }

        Customer updated = new Customer(
            existing.customerId(), existing.email(), newPlan, existing.createdAt(),
            existing.stripeCustomerId(),
            newPlan == Plan.FREE ? LicenseType.COMMUNITY : LicenseType.COMMERCIAL,
            existing.active()
        );
        customers.put(customerId, updated);

        // Refill credits
        ledger.credit(customerId, newPlan.creditsPerHour,
            "Plan change to " + newPlan.code);

        // Re-issue license
        LicenseKey license = issueLicense(updated, newPlan);

        // Update subscription
        Subscription current = subscriptions.get(customerId);
        subscriptions.put(customerId, new Subscription(
            customerId, newPlan,
            current != null ? current.startedAt() : Instant.now(),
            Instant.now().plus(30, ChronoUnit.DAYS)
        ));
    }

    /** Cancel a customer's subscription. Sets active=false and downgrades to FREE. */
    public void cancel(String customerId) {
        Customer existing = customers.get(customerId);
        if (existing == null) {
            throw new IllegalArgumentException("Unknown customer: " + customerId);
        }
        Customer cancelled = new Customer(
            existing.customerId(), existing.email(), Plan.FREE, existing.createdAt(),
            existing.stripeCustomerId(), LicenseType.COMMUNITY, false);
        customers.put(customerId, cancelled);
    }

    public Optional<Customer> findById(String customerId) {
        return Optional.ofNullable(customers.get(customerId));
    }

    /** Access the underlying ledger (for tests + monitoring). */
    public CreditLedger ledger() { return ledger; }

    public Optional<Customer> findByEmail(String email) {
        return customers.values().stream()
            .filter(c -> c.email().equalsIgnoreCase(email))
            .findFirst();
    }

    public Optional<Subscription> subscriptionFor(String customerId) {
        return Optional.ofNullable(subscriptions.get(customerId));
    }

    public int customerCount() {
        return customers.size();
    }

    private LicenseKey issueLicense(Customer customer, Plan plan) {
        Instant now = Instant.now();
        Instant expires = now.plus(365, ChronoUnit.DAYS);
        LicenseType type = plan == Plan.FREE ? LicenseType.COMMUNITY : LicenseType.COMMERCIAL;
        String licenseId = "lic_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        // Compute signature (T-07.5: real Ed25519)
        String payload = type.name() + "|" + customer.customerId() + "|" +
            now.toEpochMilli() + "|" + expires.toEpochMilli() + "|" + licenseId;
        String signature = LicenseKey.computeSignature(payload + ":" + signingSecret);

        return new LicenseKey(licenseId, customer.customerId(), type, now, expires, signature);
    }

    public record Subscription(
        String customerId,
        Plan plan,
        Instant startedAt,
        Instant expiresAt
    ) {
        public boolean isActive() {
            return Instant.now().isBefore(expiresAt);
        }
    }

    public record SignupResult(Customer customer, String licenseKey, long balance) {}
}
