package io.matrix.contract.bridge;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * W51 — the HMAC-SHA256 signer the bridge ships with (BRIDGE-3).
 *
 * <p><b>Why HMAC and not a bare hash.</b> A plain digest of the message would be computable
 * by anyone who could read the message, so it would authenticate nothing: the whole point of
 * BRIDGE-3 is that chain B "holds no ambient trust" and that "the trust of a call is
 * established per call". HMAC is keyed, so only the holder of the secret can produce a
 * signature that verifies, and the reader learns nothing from the fact that the sender and
 * the reader share a host, a classpath or a network.</p>
 *
 * <p><b>Why the comparison is in constant time.</b> {@link MessageDigest#isEqual(byte[],
 * byte[])} compares two byte arrays without an early exit on the first difference, so the time
 * it takes does not reveal <i>where</i> the two differ. {@link String#equals(Object)} would
 * answer that question through its running time, and a reader that leaks it can be walked
 * towards a forged signature one byte at a time. A bridge whose authentication can be
 * brute-forced byte by byte is not authenticated.</p>
 *
 * <p><b>Why there is no default secret.</b> BRIDGE-6 says the default is "no", and the
 * construction refuses a null or empty secret rather than substituting one. A signer with a
 * default secret would be a signer whose signatures everybody can produce, and the failure
 * would be silent: the messages would verify.</p>
 *
 * <p><b>The secret is copied on the way in and never handed out.</b> There is deliberately no
 * accessor: a secret that a component can read is a secret that a component can log, and the
 * bridge has no business logging one.</p>
 */
public final class HmacBridgeSigner implements BridgeSigner {

    /** The JCA name of the algorithm. */
    private static final String ALGORITHM = "HMAC-SHA256";

    /** The JCA name of the key algorithm. */
    private static final String KEY_ALGORITHM = "HmacSHA256";

    /** The number of hex characters per byte. */
    private static final int HEX_CHARS_PER_BYTE = 2;

    private final byte[] secret;

    /**
     * A signer that holds a shared secret.
     *
     * @param secret the shared secret; it must be present and non-empty
     */
    public HmacBridgeSigner(byte[] secret) {
        if (secret == null || secret.length == 0) {
            throw new IllegalArgumentException(
                    "a bridge signer needs a secret; without one nothing can be trusted");
        }
        this.secret = secret.clone();
    }

    /**
     * A signer that holds a shared secret given as text.
     *
     * @param secret the shared secret, read as UTF-8
     */
    public HmacBridgeSigner(String secret) {
        this(secret == null ? null : secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String algorithm() {
        return ALGORITHM;
    }

    @Override
    public byte[] sign(byte[] message) {
        if (message == null) {
            throw new IllegalArgumentException("there is no message to sign");
        }
        try {
            Mac mac = Mac.getInstance(KEY_ALGORITHM);
            mac.init(new SecretKeySpec(secret, KEY_ALGORITHM));
            return mac.doFinal(message);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("this JVM does not provide " + KEY_ALGORITHM, e);
        } catch (InvalidKeyException e) {
            throw new IllegalStateException("the bridge secret is not a usable key", e);
        }
    }

    /**
     * The signature of a message, in the hex form the wire carries (BRIDGE-3).
     *
     * @param message the bytes to sign
     * @return the signature, in lower-case hex
     */
    public String signHex(byte[] message) {
        return toHex(sign(message));
    }

    @Override
    public boolean verify(byte[] message, String signatureHex) {
        if (message == null || signatureHex == null || signatureHex.isBlank()) {
            return false;
        }
        byte[] provided;
        try {
            provided = fromHex(signatureHex);
        } catch (IllegalArgumentException e) {
            // A signature that is not hex is not a signature. It is refused, not repaired.
            return false;
        }
        byte[] expected = sign(message);
        return MessageDigest.isEqual(expected, provided);
    }

    /**
     * The lower-case hex form of some bytes.
     *
     * @param bytes the bytes
     * @return the hex, two characters per byte
     */
    public static String toHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * HEX_CHARS_PER_BYTE);
        for (byte b : bytes) {
            hex.append(Character.forDigit((b >> 4) & 0x0f, 16));
            hex.append(Character.forDigit(b & 0x0f, 16));
        }
        return hex.toString();
    }

    /**
     * The bytes a hex string carries.
     *
     * @param hex the hex, in any case
     * @return the bytes
     * @throws IllegalArgumentException when the string is not an even number of hex digits
     */
    public static byte[] fromHex(String hex) {
        if (hex == null || hex.length() % HEX_CHARS_PER_BYTE != 0) {
            throw new IllegalArgumentException("a hex signature has an even number of digits");
        }
        byte[] bytes = new byte[hex.length() / HEX_CHARS_PER_BYTE];
        for (int i = 0; i < bytes.length; i++) {
            int high = Character.digit(hex.charAt(i * HEX_CHARS_PER_BYTE), 16);
            int low = Character.digit(hex.charAt(i * HEX_CHARS_PER_BYTE + 1), 16);
            if (high < 0 || low < 0) {
                throw new IllegalArgumentException("a hex signature holds only hex digits");
            }
            bytes[i] = (byte) ((high << 4) | low);
        }
        return bytes;
    }

    /**
     * A signer whose secret is this byte array's content, compared without reading it.
     *
     * <p>Used by the tests and by a deployment that wants to compare two secrets without
     * holding them as fields.</p>
     *
     * @param other the other secret
     * @return true when the two secrets are the same
     */
    public boolean sameSecretAs(byte[] other) {
        return other != null && MessageDigest.isEqual(secret, other);
    }

    @Override
    public String toString() {
        // The secret is never rendered. A signer's string form is read in logs.
        return "HmacBridgeSigner[" + ALGORITHM + "]";
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof HmacBridgeSigner that)) {
            return false;
        }
        return MessageDigest.isEqual(secret, that.secret);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(secret);
    }
}
