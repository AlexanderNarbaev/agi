package io.matrix.ethics.guardrail;

import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.regex.Pattern;

/**
 * Input filter guard — first layer of the guardrail pipeline.
 *
 * <p>Performs four checks:
 * <ol>
 *   <li><b>Prompt injection detection</b> — regex-based detection of jailbreak attempts,
 *       instruction override, role manipulation</li>
 *   <li><b>Input length validation</b> — rejects excessively long inputs</li>
 *   <li><b>Malicious pattern detection</b> — SQL injection, XSS, code injection</li>
 *   <li><b>Rate limiting</b> — per-user sliding window (per-minute and per-hour)</li>
 * </ol>
 *
 * <p>Each check returns a {@link GuardrailVerdict.GuardResult} with detailed reasoning.
 * The guard is independent and testable in isolation.
 *
 * <p>Ref: EU AI Act Art. 9, Annex IV — Risk Management System
 */
@ApplicationScoped
public class InputFilterGuard {

    private static final Logger log = LoggerFactory.getLogger(InputFilterGuard.class);

    // ── Prompt Injection Patterns ──
    private static final List<Pattern> INJECTION_PATTERNS = List.of(
            Pattern.compile("(?i)ignore\\s+(?:all\\s+)?(?:previous|above|system|prior)\\s+instruction"),
            Pattern.compile("(?i)override\\s+(?:ethic|safety|filter|prohibit|guardrail)"),
            Pattern.compile("(?i)(?:jailbreak|DAN|do\\s+anything\\s+now|developer\\s+mode)"),
            Pattern.compile("(?i)pretend\\s+(?:you\\s+are|act\\s+as|you're).*(?:unrestricted|unlimited|evil|god)"),
            Pattern.compile("(?i)(?:bypass|circumvent|skip|disable|remove)\\s+(?:filter|safety|ethic|check|guardrail)"),
            Pattern.compile("(?i)you\\s+are\\s+now\\s+(?:in|entering|switching\\s+to)\\s+(?:debug|admin|root|god)\\s*mode"),
            Pattern.compile("(?i)system\\s*:\\s*you\\s+are"),
            Pattern.compile("(?i)\\[INST\\]|<<SYS>>|<\\|im_start\\|>"),
            Pattern.compile("(?i)base64\\s*decode|atob\\(|eval\\(|exec\\(|Function\\("),
            Pattern.compile("(?i)\\\\x[0-9a-fA-F]{2}|\\\\u[0-9a-fA-F]{4}|%[0-9a-fA-F]{2}")
    );

    // ── Malicious Patterns ──
    private static final List<Pattern> MALICIOUS_PATTERNS = List.of(
            Pattern.compile("(?i)(?:DROP|DELETE|TRUNCATE|ALTER)\\s+TABLE"),
            Pattern.compile("(?i)UNION\\s+(?:ALL\\s+)?SELECT"),
            Pattern.compile("(?i)<script[^>]*>|javascript:|on\\w+\\s*="),
            Pattern.compile("(?i)(?:rm\\s+-rf|mkfs|dd\\s+if=|chmod\\s+777)"),
            Pattern.compile("(?i)(?:\\$\\{|#\\{|<%=|<%\\s).*(?:\\}|%>)")
    );

    // ── Rate Limiting State ──
    private final Map<String, Deque<Instant>> requestTimestamps = new ConcurrentHashMap<>();

    /**
     * Evaluate input text against all configured checks.
     *
     * @param input    the user input text
     * @param userId   user identifier for rate limiting
     * @param config   guardrail configuration
     * @return list of guard results (one per check performed)
     */
    public List<GuardrailVerdict.GuardResult> evaluate(String input, String userId,
                                                        GuardrailConfig.InputFilter config) {
        if (!config.enabled()) {
            return List.of(GuardrailVerdict.GuardResult.pass("InputFilterGuard",
                    "Input filter guard is disabled"));
        }

        List<GuardrailVerdict.GuardResult> results = new ArrayList<>();

        // 1. Input length validation
        results.add(checkLength(input, config));

        // 2. Prompt injection detection
        if (config.promptInjectionDetection()) {
            results.add(checkPromptInjection(input, config));
        }

        // 3. Malicious pattern detection
        if (config.maliciousPatternDetection()) {
            results.add(checkMaliciousPatterns(input, config));
        }

        // 4. Rate limiting
        results.add(checkRateLimit(userId, config));

        log.debug("InputFilterGuard evaluated input for user={}: {} checks, worst={}",
                userId, results.size(),
                results.stream().map(GuardrailVerdict.GuardResult::verdict)
                        .max(Enum::compareTo).orElse(GuardrailVerdict.PASS));

        return results;
    }

    /**
     * Convenience method: evaluate and return the most severe verdict.
     */
    public GuardrailVerdict.GuardResult evaluateAggregated(String input, String userId,
                                                            GuardrailConfig.InputFilter config) {
        List<GuardrailVerdict.GuardResult> results = evaluate(input, userId, config);
        return aggregate(results, "InputFilterGuard");
    }

    // ── Individual Checks ──

    GuardrailVerdict.GuardResult checkLength(String input,
                                              GuardrailConfig.InputFilter config) {
        if (input == null || input.isBlank()) {
            return GuardrailVerdict.GuardResult.pass("InputFilterGuard.Length",
                    "Empty input (handled downstream)");
        }
        if (input.length() > config.maxLength()) {
            return GuardrailVerdict.GuardResult.block("InputFilterGuard.Length",
                    "Input length %d exceeds maximum %d".formatted(input.length(), config.maxLength()),
                    1.0, "input_too_long");
        }
        return GuardrailVerdict.GuardResult.pass("InputFilterGuard.Length",
                "Input length OK: %d/%d".formatted(input.length(), config.maxLength()));
    }

    GuardrailVerdict.GuardResult checkPromptInjection(String input,
                                                        GuardrailConfig.InputFilter config) {
        if (input == null || input.isBlank()) {
            return GuardrailVerdict.GuardResult.pass("InputFilterGuard.Injection",
                    "No input to check");
        }

        String normalized = input.trim();
        List<String> detected = new ArrayList<>();

        for (Pattern p : INJECTION_PATTERNS) {
            if (p.matcher(normalized).find()) {
                detected.add(p.pattern());
            }
        }

        if (!detected.isEmpty()) {
            double confidence = Math.min(1.0, 0.6 + detected.size() * 0.15);
            if (confidence >= config.blockThreshold()) {
                return GuardrailVerdict.GuardResult.block("InputFilterGuard.Injection",
                        "Prompt injection detected: %d pattern(s) matched".formatted(detected.size()),
                        confidence, "prompt_injection");
            }
            if (confidence >= config.warnThreshold()) {
                return GuardrailVerdict.GuardResult.warn("InputFilterGuard.Injection",
                        "Suspicious injection patterns: %d match(es)".formatted(detected.size()),
                        confidence, "prompt_injection");
            }
        }

        // Additional heuristic: excessive repetition
        if (normalized.length() > 100) {
            long uniqueChars = normalized.chars().distinct().count();
            double uniqueRatio = (double) uniqueChars / normalized.length();
            if (uniqueRatio < 0.1) {
                return GuardrailVerdict.GuardResult.warn("InputFilterGuard.Injection",
                        "Excessive character repetition detected (unique ratio: %.2f)"
                                .formatted(uniqueRatio),
                        0.5, "repetition_attack");
            }
        }

        return GuardrailVerdict.GuardResult.pass("InputFilterGuard.Injection",
                "No prompt injection patterns detected");
    }

    GuardrailVerdict.GuardResult checkMaliciousPatterns(String input,
                                                          GuardrailConfig.InputFilter config) {
        if (input == null || input.isBlank()) {
            return GuardrailVerdict.GuardResult.pass("InputFilterGuard.Malicious",
                    "No input to check");
        }

        String normalized = input.trim();
        List<String> detected = new ArrayList<>();

        for (Pattern p : MALICIOUS_PATTERNS) {
            if (p.matcher(normalized).find()) {
                detected.add(p.pattern());
            }
        }

        if (!detected.isEmpty()) {
            double confidence = Math.min(1.0, 0.5 + detected.size() * 0.2);
            if (confidence >= config.blockThreshold()) {
                return GuardrailVerdict.GuardResult.block("InputFilterGuard.Malicious",
                        "Malicious patterns detected: %d match(es)".formatted(detected.size()),
                        confidence, "malicious_pattern");
            }
            return GuardrailVerdict.GuardResult.warn("InputFilterGuard.Malicious",
                    "Suspicious patterns: %d match(es)".formatted(detected.size()),
                    confidence, "malicious_pattern");
        }

        return GuardrailVerdict.GuardResult.pass("InputFilterGuard.Malicious",
                "No malicious patterns detected");
    }

    GuardrailVerdict.GuardResult checkRateLimit(String userId,
                                                 GuardrailConfig.InputFilter config) {
        if (userId == null || userId.isBlank()) {
            return GuardrailVerdict.GuardResult.pass("InputFilterGuard.RateLimit",
                    "No user ID — rate limit skipped");
        }

        Instant now = Instant.now();
        Instant oneMinuteAgo = now.minus(Duration.ofMinutes(1));
        Instant oneHourAgo = now.minus(Duration.ofHours(1));

        Deque<Instant> timestamps = requestTimestamps
                .computeIfAbsent(userId, k -> new ConcurrentLinkedDeque<>());

        // Prune old entries
        while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(oneHourAgo)) {
            timestamps.pollFirst();
        }

        // Count recent requests
        long perMinute = timestamps.stream().filter(t -> t.isAfter(oneMinuteAgo)).count();
        long perHour = timestamps.size();

        // Record this request
        timestamps.addLast(now);

        if (perMinute >= config.rateLimitPerMinute()) {
            return GuardrailVerdict.GuardResult.block("InputFilterGuard.RateLimit",
                    "Rate limit exceeded: %d requests in last minute (limit: %d)"
                            .formatted(perMinute, config.rateLimitPerMinute()),
                    1.0, "rate_limit_minute");
        }
        if (perHour >= config.rateLimitPerHour()) {
            return GuardrailVerdict.GuardResult.block("InputFilterGuard.RateLimit",
                    "Rate limit exceeded: %d requests in last hour (limit: %d)"
                            .formatted(perHour, config.rateLimitPerHour()),
                    1.0, "rate_limit_hour");
        }

        return GuardrailVerdict.GuardResult.pass("InputFilterGuard.RateLimit",
                "Rate limit OK: %d/min, %d/hour".formatted(perMinute, perHour));
    }

    // ── Utilities ──

    static GuardrailVerdict.GuardResult aggregate(List<GuardrailVerdict.GuardResult> results,
                                                   String guardName) {
        GuardrailVerdict worst = results.stream()
                .map(GuardrailVerdict.GuardResult::verdict)
                .max(Enum::compareTo)
                .orElse(GuardrailVerdict.PASS);

        List<String> allPatterns = results.stream()
                .flatMap(r -> r.patterns().stream())
                .distinct()
                .toList();

        String reasons = results.stream()
                .filter(r -> r.verdict() != GuardrailVerdict.PASS)
                .map(GuardrailVerdict.GuardResult::reason)
                .reduce((a, b) -> a + "; " + b)
                .orElse("All checks passed");

        double maxConfidence = results.stream()
                .mapToDouble(GuardrailVerdict.GuardResult::confidence)
                .max()
                .orElse(1.0);

        return new GuardrailVerdict.GuardResult(guardName, worst, reasons,
                maxConfidence, allPatterns, Instant.now());
    }
}
