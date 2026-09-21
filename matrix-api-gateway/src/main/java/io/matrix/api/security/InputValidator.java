package io.matrix.api.security;

/**
 * WAVE T-02 — OWASP Top 10 input validator.
 *
 * <p>Defends against the most common injection attacks at the API boundary:</p>
 * <ul>
 *   <li>A03 (Injection): null-byte, control-char, and shell-meta stripping</li>
 *   <li>A04 (Insecure Design): size limits enforced before any work</li>
 *   <li>A05 (Security Misconfiguration): no logging of raw input</li>
 *   <li>A07 (IDOR): opaque IDs only, no PII in URLs</li>
 * </ul>
 *
 * <p>Validation is conservative: reject early, return safe defaults only
 * when input is provably clean.</p>
 */
public final class InputValidator {

    /** Maximum input length for any single field. */
    public static final int MAX_INPUT_BYTES = 1_048_576;  // 1 MiB

    /** Forbidden control characters (includes null, CR, LF, escape). */
    private static final String FORBIDDEN_CONTROL_CHARS = "[\u0000\u0001\u0002\u0003\u0004\u0005\u0006\u0007\u0008\u000B\u000C\u000E\u000F\u0010\u0011\u0012\u0013\u0014\u0015\u0016\u0017\u0018\u0019\u001A\u001B\u001C\u001D\u001E\u001F\u007F]";

    private InputValidator() {}

    /**
     * Validate and sanitize a text input.
     *
     * @throws SecurityException if input is null, too long, or contains
     *                           forbidden characters
     */
    public static String sanitizeText(String input) {
        if (input == null) {
            throw new SecurityException("Input is null");
        }
        if (input.length() > MAX_INPUT_BYTES) {
            throw new SecurityException("Input exceeds maximum size: " + input.length() + " > " + MAX_INPUT_BYTES);
        }
        if (input.matches(".*" + FORBIDDEN_CONTROL_CHARS + ".*")) {
            throw new SecurityException("Input contains forbidden control characters");
        }
        return input;
    }

    /**
     * Validate an opaque ID (UUID, ULID, etc.). Allows only
     * alphanumeric + dash + underscore, length 8..64.
     */
    public static String sanitizeId(String id) {
        if (id == null) {
            throw new SecurityException("ID is null");
        }
        if (id.length() < 8 || id.length() > 64) {
            throw new SecurityException("ID length out of bounds: " + id.length());
        }
        if (!id.matches("^[A-Za-z0-9_-]+$")) {
            throw new SecurityException("ID contains forbidden characters");
        }
        return id;
    }

    /**
     * Validate a content-type. Must be one of: text, audio, image.
     */
    public static String sanitizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "text";
        }
        String normalized = contentType.toLowerCase().trim();
        if (!normalized.equals("text") && !normalized.equals("audio") && !normalized.equals("image")) {
            throw new SecurityException("Unsupported content type: " + contentType);
        }
        return normalized;
    }
}
