package io.matrix.billing;

import java.util.Objects;

/**
 * WAVE T-07 — License Validator.
 *
 * Validates license keys for compliance with the MATRIX terms.
 *
 * <p><b>Validation steps:</b></p>
 * <ol>
 *   <li>Decode the encoded payload</li>
 *   <li>Verify signature (T-07.5: real Ed25519; T-07: SHA-256 placeholder)</li>
 *   <li>Check expiration</li>
 *   <li>Verify license_type permits the requested operation</li>
 * </ol>
 */
public final class LicenseValidator {

    private final String signingSecret;

    public LicenseValidator(String signingSecret) {
        this.signingSecret = Objects.requireNonNull(signingSecret, "signingSecret");
    }

    /** Validate a license key for any operation. */
    public ValidationResult validate(String encoded) {
        LicenseKey key;
        try {
            key = LicenseKey.decode(encoded);
        } catch (Exception e) {
            return ValidationResult.invalid("Malformed license key: " + e.getMessage());
        }

        if (!key.isValid()) {
            return ValidationResult.invalid("License expired at " + key.expiresAt());
        }

        String payload = key.licenseType().name() + "|" + key.customerId() + "|" +
            key.issuedAt().toEpochMilli() + "|" + key.expiresAt().toEpochMilli() +
            "|" + key.licenseId();
        String expectedSignature = LicenseKey.computeSignature(payload + ":" + signingSecret);

        if (!expectedSignature.equals(key.signature())) {
            return ValidationResult.invalid(
                "Signature mismatch: expected=" + expectedSignature.substring(0, 12) +
                " actual=" + key.signature().substring(0, 12));
        }

        return ValidationResult.valid(key);
    }

    /** Check if a license permits commercial use. */
    public boolean permitsCommercial(LicenseKey key) {
        return key.licenseType() != LicenseType.COMMUNITY;
    }

    public record ValidationResult(boolean valid, String reason, LicenseKey license) {
        public static ValidationResult valid(LicenseKey key) {
            return new ValidationResult(true, null, key);
        }
        public static ValidationResult invalid(String reason) {
            return new ValidationResult(false, reason, null);
        }
    }
}
