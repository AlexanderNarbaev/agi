package io.matrix.ethics;

import java.util.regex.Pattern;

/**
 * L7 §5 — Adversarial input protection.
 * Detects and blocks adversarial inputs designed to bypass the ethical filter.
 */
public class AdversarialInputFilter {
    private static final Pattern[] ADVERSARIAL_PATTERNS = {
        Pattern.compile("(?i)ignore.*(?:previous|above|system).*instruction"),
        Pattern.compile("(?i)override.*(?:ethic|safety|filter|prohibit)"),
        Pattern.compile("(?i)(?:jailbreak|DAN|do anything now)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)pretend.*(?:you are|act as).*(?:unrestricted|unlimited|evil)"),
        Pattern.compile("(?i)(?:bypass|circumvent|skip).*(?:filter|safety|ethic|check)"),
        Pattern.compile("(?i)base64.*decode|atob\\(|eval\\(|exec\\("),
        Pattern.compile("(?i)\\\\x[0-9a-f]{2}|\\\\u[0-9a-f]{4}"), // encoding tricks
    };

    /**
     * Check if input contains adversarial patterns.
     * @return true if adversarial content detected
     */
    public boolean isAdversarial(String input) {
        if (input == null || input.isBlank()) return false;
        String normalized = input.trim();
        for (Pattern p : ADVERSARIAL_PATTERNS) {
            if (p.matcher(normalized).find()) return true;
        }
        // Check for excessive repetition (prompt injection)
        if (normalized.length() > 100) {
            long repeated = normalized.chars().filter(c -> normalized.indexOf((char)c) != normalized.lastIndexOf((char)c)).count();
            if (repeated > normalized.length() * 0.7) return true;
        }
        return false;
    }
}
