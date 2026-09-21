package io.matrix.billing;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class LicenseValidatorTest {

    private final LicenseValidator validator = new LicenseValidator("test-secret");

private LicenseKey makeValidKeyWithSecret(String secret) {
        // Validator computes sig over: type|customer|issued|expires|licenseId:secret
        Instant now = Instant.now();
        long issued = now.toEpochMilli();
        long expires = now.plusSeconds(86400).toEpochMilli();
        LicenseType type = LicenseType.COMMERCIAL; String payload = type.name() + "|user-1|" + issued + "|" + expires + "|lic_test";
        String signature = LicenseKey.computeSignature(payload + ":" + secret);
        return new LicenseKey("lic_test", "user-1", type, now, now.plusSeconds(86400), signature);
    }

    @Test
    void testValidKey() {
        LicenseKey key = makeValidKeyWithSecret("test-secret");
        LicenseValidator.ValidationResult result = validator.validate(key.encode());
        assertTrue(result.valid());
        assertNotNull(result.license());
    }

    @Test
    void testMalformedKey() {
        var result = validator.validate("not.a.valid.key");
        assertFalse(result.valid());
        assertNotNull(result.reason());
    }

    @Test
    void testExpiredKey() {
        Instant now = Instant.now();
        LicenseType type = LicenseType.COMMERCIAL;
        String payload = type.name() + "|user-1|" + now.minusSeconds(86400).toEpochMilli() + "|" +
            now.minusSeconds(60).toEpochMilli() + "|lic_expired";
        String signature = LicenseKey.computeSignature(payload + ":test-secret");
        LicenseKey expired = new LicenseKey("lic_expired", "user-1", type,
            now.minusSeconds(86400), now.minusSeconds(60), signature);
        var result = validator.validate(expired.encode());
        assertFalse(result.valid());
        assertTrue(result.reason().toLowerCase().contains("expired"));
    }

    @Test
    void testWrongSecret() {
        LicenseKey key = makeValidKeyWithSecret("test-secret");
        LicenseValidator otherValidator = new LicenseValidator("wrong-secret");
        var result = otherValidator.validate(key.encode());
        assertFalse(result.valid());
        assertTrue(result.reason().toLowerCase().contains("signature"));
    }

    @Test
    void testIsValidMethod() {
        LicenseKey key = makeValidKeyWithSecret("test-secret");
        assertTrue(key.isValid());
    }

    @Test
    void testEncodeDecodeRoundtrip() {
        LicenseKey original = makeValidKeyWithSecret("test-secret");
        String encoded = original.encode();
        LicenseKey decoded = LicenseKey.decode(encoded);
        assertEquals(original.customerId(), decoded.customerId());
        assertEquals(original.licenseType(), decoded.licenseType());
        assertEquals(original.signature(), decoded.signature());
    }

    @Test
    void testPermitsCommercial() {
        assertTrue(validator.permitsCommercial(makeValidKeyWithSecret("test-secret")));
        LicenseKey community = new LicenseKey("lic_1", "u1", LicenseType.COMMUNITY,
            Instant.now(), Instant.now().plusSeconds(60), "sig");
        assertFalse(validator.permitsCommercial(community));
    }
}
