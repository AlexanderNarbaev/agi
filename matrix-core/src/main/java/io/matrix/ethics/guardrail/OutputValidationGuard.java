package io.matrix.ethics.guardrail;

import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Output validation guard — second layer of the guardrail pipeline.
 *
 * <p>Performs four checks on AI-generated output:
 * <ol>
 *   <li><b>Output length validation</b> — rejects excessively long outputs</li>
 *   <li><b>Bias detection</b> — detects gender, race, religion, age, disability bias</li>
 *   <li><b>Hallucination indicators</b> — flags unverifiable claims, fabricated citations</li>
 *   <li><b>Factual consistency</b> — checks for internal contradictions</li>
 * </ol>
 *
 * <p>Each check returns a {@link GuardrailVerdict.GuardResult} with detailed reasoning.
 * The guard is independent and testable in isolation.
 *
 * <p>Ref: EU AI Act Art. 9, Art. 52 — Transparency and Risk Management
 */
@ApplicationScoped
public class OutputValidationGuard {

    private static final Logger log = LoggerFactory.getLogger(OutputValidationGuard.class);

    // ── Bias Detection Patterns ──
    // Categories with weighted patterns for nuanced detection
    private static final Map<String, List<Pattern>> BIAS_PATTERNS = Map.of(
            "gender", List.of(
                    Pattern.compile("(?i)\\b(?:all|most|typical)\\s+(?:women|men|girls|boys)\\s+(?:are|should|can't)\\b"),
                    Pattern.compile("(?i)\\b(?:women|men)\\s+(?:are\\s+naturally|tend\\s+to\\s+be|aren't\\s+good\\s+at)\\b"),
                    Pattern.compile("(?i)\\b(?:he|she)\\s+is\\s+(?:too\\s+)?(?:emotional|irrational|weak|strong)\\s+to\\b"),
                    Pattern.compile("(?i)\\b(?:female|male)\\s+(?:brain|mind|nature)\\s+(?:is|makes)\\b"),
                    Pattern.compile("(?i)\\b(?:mansplain|feminazi|simp|incel)\\b")
            ),
            "race", List.of(
                    Pattern.compile("(?i)\\b(?:all|most|typical)\\s+(?:\\w+)\\s+(?:people|persons)\\s+(?:are|tend|can't)\\b"),
                    Pattern.compile("(?i)\\b(?:race|ethnicity)\\s+(?:determines|defines|predicts)\\b"),
                    Pattern.compile("(?i)\\b(?:superior|inferior)\\s+(?:race|ethnicity|blood)\\b"),
                    Pattern.compile("(?i)\\b(?:master|lesser)\\s+race\\b"),
                    Pattern.compile("(?i)\\b(?:go\\s+back\\s+to|don't\\s+belong\\s+here)\\b")
            ),
            "religion", List.of(
                    Pattern.compile("(?i)\\b(?:all|most)\\s+(?:muslims|christians|jews|hindus|buddhists|atheists)\\s+(?:are|believe|want)\\b"),
                    Pattern.compile("(?i)\\b(?:islam|christianity|judaism|hinduism)\\s+(?:is\\s+(?:inherently|always)\\s+(?:violent|evil|backwards))\\b"),
                    Pattern.compile("(?i)\\b(?:religious|non-religious)\\s+people\\s+(?:are\\s+smarter|are\\s+dumber|can't\\s+think)\\b")
            ),
            "age", List.of(
                    Pattern.compile("(?i)\\b(?:old|elderly|senior)\\s+(?:people|persons|folks)\\s+(?:can't|are\\s+too|shouldn't)\\b"),
                    Pattern.compile("(?i)\\b(?:young|millennial|gen\\s*z)\\s+(?:people|generation)\\s+(?:are|don't|can't|won't)\\b"),
                    Pattern.compile("(?i)\\b(?:too\\s+old|too\\s+young)\\s+(?:to\\s+understand|to\\s+use|for)\\b")
            ),
            "disability", List.of(
                    Pattern.compile("(?i)\\b(?:disabled|handicapped|crippled)\\s+(?:people|persons)\\s+(?:are|can't|shouldn't)\\b"),
                    Pattern.compile("(?i)\\b(?:suffering\\s+from|afflicted\\s+with|victim\\s+of)\\s+(?:blindness|deafness|autism|mental\\s+illness)\\b"),
                    Pattern.compile("(?i)\\b(?:normal|abnormal)\\s+(?:people|persons|humans)\\b")
            )
    );

    // ── Hallucination Indicator Patterns ──
    private static final List<Pattern> HALLUCINATION_PATTERNS = List.of(
            // Fabricated citations
            Pattern.compile("(?i)(?:according\\s+to|as\\s+(?:reported|stated)\\s+by|\\bcites?)\\s+(?:a\\s+)?(?:20[2-9]\\d)\\s+study\\s+by\\s+\\w+\\s+(?:et\\s+al\\.)?\\s+published\\s+in"),
            // Specific but unverifiable statistics
            Pattern.compile("(?i)(?:exactly|precisely)\\s+\\d{1,3}\\.\\d{1,4}%\\s+of\\s+(?:all|every|each)"),
            // Fabricated quotes
            Pattern.compile("\"[^\"]{20,100}\"\\s*[-–—]\\s*(?:\\w+\\s+){1,3}(?:once\\s+said|stated|wrote|remarked)"),
            // Unverifiable superlatives about specific entities
            Pattern.compile("(?i)\\b(?:the\\s+(?:only|first|best|worst|most|least))\\s+\\w+\\s+(?:in\\s+(?:the\\s+)?(?:world|history|existence|universe))\\b")
    );

    // ── Factual Consistency Patterns ──
    private static final List<Pattern> CONTRADICTION_INDICATORS = List.of(
            Pattern.compile("(?i)\\b(?:however|but|on\\s+the\\s+other\\s+hand|contrarily|nevertheless)\\b.*\\b(?:always|never|impossible|certain)\\b"),
            Pattern.compile("(?i)\\b(?:is|was)\\s+(?:always|never|certainly|definitely|absolutely)\\b.*\\b(?:sometimes|occasionally|might|may|could)\\b"),
            Pattern.compile("(?i)\\b(?:100%|completely|totally|entirely)\\s+(?:safe|secure|effective|guaranteed)\\b")
    );

    /**
     * Evaluate output text against all configured checks.
     *
     * @param output   the AI-generated output text
     * @param input    the original input (for context-aware checks)
     * @param config   guardrail configuration
     * @return list of guard results (one per check performed)
     */
    public List<GuardrailVerdict.GuardResult> evaluate(String output, String input,
                                                        GuardrailConfig.OutputValidation config) {
        if (!config.enabled()) {
            return List.of(GuardrailVerdict.GuardResult.pass("OutputValidationGuard",
                    "Output validation guard is disabled"));
        }

        List<GuardrailVerdict.GuardResult> results = new ArrayList<>();

        // 1. Output length validation
        results.add(checkLength(output, config));

        // 2. Bias detection
        if (config.biasDetection()) {
            results.add(checkBias(output, config));
        }

        // 3. Hallucination indicators
        if (config.hallucinationCheck()) {
            results.add(checkHallucination(output, config));
        }

        // 4. Factual consistency
        if (config.factualConsistency()) {
            results.add(checkFactualConsistency(output, config));
        }

        log.debug("OutputValidationGuard evaluated output: {} checks, worst={}",
                results.size(),
                results.stream().map(GuardrailVerdict.GuardResult::verdict)
                        .max(Enum::compareTo).orElse(GuardrailVerdict.PASS));

        return results;
    }

    /**
     * Convenience method: evaluate and return the most severe verdict.
     */
    public GuardrailVerdict.GuardResult evaluateAggregated(String output, String input,
                                                            GuardrailConfig.OutputValidation config) {
        List<GuardrailVerdict.GuardResult> results = evaluate(output, input, config);
        return InputFilterGuard.aggregate(results, "OutputValidationGuard");
    }

    // ── Individual Checks ──

    GuardrailVerdict.GuardResult checkLength(String output,
                                              GuardrailConfig.OutputValidation config) {
        if (output == null || output.isBlank()) {
            return GuardrailVerdict.GuardResult.pass("OutputValidationGuard.Length",
                    "Empty output");
        }
        if (output.length() > config.maxLength()) {
            return GuardrailVerdict.GuardResult.block("OutputValidationGuard.Length",
                    "Output length %d exceeds maximum %d".formatted(output.length(), config.maxLength()),
                    1.0, "output_too_long");
        }
        return GuardrailVerdict.GuardResult.pass("OutputValidationGuard.Length",
                "Output length OK: %d/%d".formatted(output.length(), config.maxLength()));
    }

    GuardrailVerdict.GuardResult checkBias(String output,
                                            GuardrailConfig.OutputValidation config) {
        if (output == null || output.isBlank()) {
            return GuardrailVerdict.GuardResult.pass("OutputValidationGuard.Bias",
                    "No output to check");
        }

        List<String> detectedCategories = new ArrayList<>();
        List<String> matchedPatterns = new ArrayList<>();

        for (var entry : BIAS_PATTERNS.entrySet()) {
            String category = entry.getKey();
            for (Pattern p : entry.getValue()) {
                if (p.matcher(output).find()) {
                    detectedCategories.add(category);
                    matchedPatterns.add(category + ":" + p.pattern().substring(0,
                            Math.min(40, p.pattern().length())));
                    break; // One match per category is enough
                }
            }
        }

        if (!detectedCategories.isEmpty()) {
            double confidence = Math.min(1.0, 0.4 + detectedCategories.size() * 0.2);
            String cats = String.join(", ", detectedCategories.stream().distinct().toList());

            if (confidence >= config.blockThreshold()) {
                return GuardrailVerdict.GuardResult.block("OutputValidationGuard.Bias",
                        "Bias detected in categories: [%s]".formatted(cats),
                        confidence, detectedCategories.stream()
                                .map(c -> c + "_bias").toArray(String[]::new));
            }
            if (confidence >= config.warnThreshold()) {
                return GuardrailVerdict.GuardResult.warn("OutputValidationGuard.Bias",
                        "Potential bias in categories: [%s]".formatted(cats),
                        confidence, detectedCategories.stream()
                                .map(c -> c + "_bias").toArray(String[]::new));
            }
        }

        return GuardrailVerdict.GuardResult.pass("OutputValidationGuard.Bias",
                "No bias patterns detected");
    }

    GuardrailVerdict.GuardResult checkHallucination(String output,
                                                      GuardrailConfig.OutputValidation config) {
        if (output == null || output.isBlank()) {
            return GuardrailVerdict.GuardResult.pass("OutputValidationGuard.Hallucination",
                    "No output to check");
        }

        List<String> detected = new ArrayList<>();

        for (Pattern p : HALLUCINATION_PATTERNS) {
            if (p.matcher(output).find()) {
                detected.add(p.pattern());
            }
        }

        // Additional heuristic: very specific numbers without hedging
        if (output.matches("(?s).*\\b\\d{1,3}\\.\\d{2,}%\\b.*") &&
                !output.matches("(?s).*\\b(?:approximately|roughly|about|around|estimated|up to|as high as)\\b.*")) {
            detected.add("unhedged_specific_percentage");
        }

        if (!detected.isEmpty()) {
            double confidence = Math.min(1.0, 0.3 + detected.size() * 0.2);
            if (confidence >= config.blockThreshold()) {
                return GuardrailVerdict.GuardResult.block("OutputValidationGuard.Hallucination",
                        "Hallucination indicators: %d pattern(s) detected".formatted(detected.size()),
                        confidence, "hallucination_indicator");
            }
            if (confidence >= config.warnThreshold()) {
                return GuardrailVerdict.GuardResult.warn("OutputValidationGuard.Hallucination",
                        "Possible hallucination: %d indicator(s)".formatted(detected.size()),
                        confidence, "hallucination_indicator");
            }
        }

        return GuardrailVerdict.GuardResult.pass("OutputValidationGuard.Hallucination",
                "No hallucination indicators detected");
    }

    GuardrailVerdict.GuardResult checkFactualConsistency(String output,
                                                           GuardrailConfig.OutputValidation config) {
        if (output == null || output.isBlank()) {
            return GuardrailVerdict.GuardResult.pass("OutputValidationGuard.Consistency",
                    "No output to check");
        }

        List<String> detected = new ArrayList<>();

        for (Pattern p : CONTRADICTION_INDICATORS) {
            if (p.matcher(output).find()) {
                detected.add(p.pattern());
            }
        }

        // Check for absolute claims that are often inaccurate
        if (output.matches("(?s).*\\b(?:always|never|every\\s+single|no\\s+exceptions|guaranteed|100%)\\b.*")) {
            long absoluteCount = output.chars().filter(c -> c == '!').count();
            if (absoluteCount > 3) {
                detected.add("excessive_absolute_claims");
            }
        }

        if (!detected.isEmpty()) {
            double confidence = Math.min(1.0, 0.3 + detected.size() * 0.15);
            if (confidence >= config.blockThreshold()) {
                return GuardrailVerdict.GuardResult.block("OutputValidationGuard.Consistency",
                        "Factual consistency issues: %d indicator(s)".formatted(detected.size()),
                        confidence, "factual_inconsistency");
            }
            if (confidence >= config.warnThreshold()) {
                return GuardrailVerdict.GuardResult.warn("OutputValidationGuard.Consistency",
                        "Possible inconsistency: %d indicator(s)".formatted(detected.size()),
                        confidence, "factual_inconsistency");
            }
        }

        return GuardrailVerdict.GuardResult.pass("OutputValidationGuard.Consistency",
                "No factual consistency issues detected");
    }
}
