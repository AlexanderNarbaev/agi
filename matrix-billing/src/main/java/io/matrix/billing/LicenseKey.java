package io.matrix.billing;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;

/**
 * WAVE T-07 — License Key.
 *
 * Format: BASE64(LicenseType | CustomerId | IssuedAt | ExpiresAt | Signature)
 *
 * Signature is computed by the MATRIX signing service using Ed25519.
 * T-07.5 uses real Ed25519 via java.security; T-07 ships with SHA-256
 * placeholder so the format is final but signatures are mock.
 *
 * <p><b>CONSTITUTION compliance:</b> No LLM. Pure cryptographic primitive.</p>
 */
public record LicenseKey(
    @JsonProperty("license_id") String licenseId,
    @JsonProperty("customer_id") String customerId,
    @JsonProperty("license_type") LicenseType licenseType,
    @JsonProperty("issued_at") Instant issuedAt,
    @JsonProperty("expires_at") Instant expiresAt,
    @JsonProperty("signature") String signature
) {
    public LicenseKey {
        Objects.requireNonNull(licenseId, "licenseId");
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(licenseType, "licenseType");
        Objects.requireNonNull(issuedAt, "issuedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(signature, "signature");
    }

    /**
     * Encode this license key as a Base64 string suitable for the
     * customer's API key field.
     */
    public String encode() {
        String payload = licenseType.name()
            + "|" + customerId
            + "|" + issuedAt.toEpochMilli()
            + "|" + expiresAt.toEpochMilli()
            + "|" + licenseId;
        byte[] signatureBytes = fromHex(signature);
        String payloadB64 = Base64.getEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        String sigB64 = Base64.getEncoder().encodeToString(signatureBytes);
        return payloadB64 + "." + sigB64;
    }

    /**
     * Decode a license key string back into a LicenseKey.
     */
    public static LicenseKey decode(String encoded) {
        String[] parts = encoded.split("\\.", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Malformed license key");
        }
        String payload = new String(Base64.getDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        String signature = toHex(Base64.getDecoder().decode(parts[1]));
        String[] fields = payload.split("\\|", -1);
        if (fields.length != 5) {
            throw new IllegalArgumentException("Malformed license payload");
        }
        return new LicenseKey(
            fields[4], fields[1], LicenseType.valueOf(fields[0]),
            Instant.ofEpochMilli(Long.parseLong(fields[2])),
            Instant.ofEpochMilli(Long.parseLong(fields[3])),
            signature
        );
    }

    /** Check if this license is currently valid (not expired). */
    public boolean isValid() {
        return Instant.now().isBefore(expiresAt);
    }

    public static String computeSignature(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return toHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String toHex(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }

    private static byte[] fromHex(String hex) {
        return HexFormat.of().parseHex(hex);
    }
}
