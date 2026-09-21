package io.matrix.billing;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * WAVE T-07 — Customer record.
 *
 * A Customer is the entity that subscribes to MATRIX. Has 1:1 with
 * a Stripe customer (T-07.5) and a credit ledger (1:1).
 */
public record Customer(
    @JsonProperty("customer_id") String customerId,
    @JsonProperty("email") String email,
    @JsonProperty("plan") Plan plan,
    @JsonProperty("created_at") Instant createdAt,
    @JsonProperty("stripe_customer_id") String stripeCustomerId,
    @JsonProperty("license_type") LicenseType licenseType,
    @JsonProperty("active") boolean active
) {
    public Customer {
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public static Customer create(String email, Plan plan) {
        return new Customer(
            "cus_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16),
            email,
            plan,
            Instant.now(),
            null,  // populated when Stripe sync happens
            plan == Plan.FREE ? LicenseType.COMMUNITY : LicenseType.COMMERCIAL,
            true
        );
    }
}
