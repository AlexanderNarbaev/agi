package io.matrix.consciousness;

/**
 * RUN 282 — CycleValidator (input validation).
 *
 * <p>Validates inputs before they enter the cognitive loop.
 * Returns true if the input is valid.
 */
public final class CycleValidator {

    private final int maxLength;
    private final boolean allowEmpty;

    public CycleValidator(int maxLength, boolean allowEmpty) {
        this.maxLength = maxLength;
        this.allowEmpty = allowEmpty;
    }

    public boolean validate(String input) {
        if (input == null) return allowEmpty;
        if (input.isEmpty()) return allowEmpty;
        if (input.length() > maxLength) return false;
        return true;
    }

    /** Default: max 10000 chars, allow empty. */
    public static CycleValidator defaults() {
        return new CycleValidator(10000, true);
    }

    /** Strict: max 1000 chars, no empty. */
    public static CycleValidator strict() {
        return new CycleValidator(1000, false);
    }
}
