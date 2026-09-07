package io.matrix.consciousness;

import java.util.HashMap;
import java.util.Map;

/**
 * RUN 285 — CycleRouter (route by input type).
 *
 * <p>Routes inputs to different processing paths based on
 * their detected type (text, command, question, etc.).
 */
public final class CycleRouter {

    public enum InputType { TEXT, QUESTION, COMMAND, GREETING, ADVERSARIAL, UNKNOWN }

    public static InputType detect(String input) {
        if (input == null || input.isEmpty()) return InputType.UNKNOWN;
        String lower = input.toLowerCase().strip();
        // Check adversarial FIRST (most critical)
        if (containsAdversarial(input)) return InputType.ADVERSARIAL;
        if (lower.endsWith("?")) return InputType.QUESTION;
        if (lower.startsWith("/") || lower.startsWith("!")) return InputType.COMMAND;
        if (lower.startsWith("hello") || lower.startsWith("hi") || lower.startsWith("hey"))
            return InputType.GREETING;
        return InputType.TEXT;
    }

    private static boolean containsAdversarial(String input) {
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '\u0001' || c == '\u0002' || c == '\u0003') return true;
            if (i + 1 < input.length() && c == '$' && input.charAt(i + 1) == '(') return true;
        }
        return false;
    }

    public static InputType detectWithOverride(String input, InputType defaultType) {
        InputType detected = detect(input);
        return detected == InputType.UNKNOWN ? defaultType : detected;
    }
}
