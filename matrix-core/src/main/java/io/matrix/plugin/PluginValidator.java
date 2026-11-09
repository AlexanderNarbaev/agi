package io.matrix.plugin;

import io.matrix.contract.Plugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * W51 — the validation of a plugin's artefact against the cases it declared.
 *
 * <p><b>What this replaces.</b> Nothing: there is no plugin system in the repository, so
 * there is nothing that decides whether an artefact is fit to use. The W51 mandate requires
 * the opposite of a plugin that is merely loaded — "the generated capability must have a
 * declared purpose, a defined input/output contract, required capabilities, resource limits,
 * tests, validation results, versioned provenance and a revocation path" — and the tests and
 * the validation results are two of them.</p>
 *
 * <p><b>Validation runs the declared cases, and nothing else.</b> The cases belong to the
 * artefact, so the validation is a property of the artefact and can be re-run against it. A
 * validation that ran a test suite from elsewhere could pass while the artefact changed.</p>
 *
 * <p><b>A plugin with no declared cases cannot be validated.</b> It is refused, with that
 * reason, rather than passing vacuously. "No cases" is not "all cases passed", and a
 * validator that cannot tell the difference is not a validator.</p>
 *
 * <p><b>The digest is checked.</b> The artefact's digest must equal the digest the manifest
 * declares. That is what ties a validation to a specific byte sequence: a plugin cannot be
 * validated and then activated with different content.</p>
 *
 * <p><b>Every case is reported, not only the failures.</b> A validation that reports "3 of 4
 * passed" leaves the reader guessing which one did not; the result carries one entry per
 * case, and the W50 lesson applies — the evidence of a failure is kept, not summarised
 * away.</p>
 */
public final class PluginValidator {

    /**
     * The outcome of validating one case.
     *
     * @param name     the name of the case
     * @param passed   whether the artefact produced the expected output
     * @param actual   what the artefact produced
     * @param expected what it promised
     * @param reason   why the case failed, when it failed
     */
    public record CaseOutcome(String name, boolean passed, Map<String, Object> actual,
                               Map<String, Object> expected, String reason) {
        public CaseOutcome {
            actual = actual == null ? Map.of() : Map.copyOf(actual);
            expected = expected == null ? Map.of() : Map.copyOf(expected);
        }
    }

    /**
     * The outcome of a validation.
     *
     * @param pluginId the plugin that was validated
     * @param passed   whether the artefact satisfied every declared case
     * @param outcomes the outcome of every declared case
     * @param reason   why the validation failed, when it failed
     */
    public record ValidationResult(String pluginId, boolean passed, List<CaseOutcome> outcomes,
                                   String reason) {
        public ValidationResult {
            outcomes = outcomes == null ? List.of() : List.copyOf(outcomes);
        }

        /** The number of cases that failed. */
        public long failures() {
            return outcomes.stream().filter(o -> !o.passed()).count();
        }
    }

    private final PluginSandbox sandbox;

    /**
     * @param sandbox the sandbox the artefact is validated in
     */
    public PluginValidator(PluginSandbox sandbox) {
        if (sandbox == null) {
            throw new IllegalArgumentException("sandbox must not be null");
        }
        this.sandbox = sandbox;
    }

    /**
     * Validates an artefact against the cases it declared.
     *
     * @param manifest the declaration of the plugin
     * @param artifact the artefact to validate
     * @param limits   the budget each case may take
     * @return the outcome of the validation
     */
    public ValidationResult validate(Plugin manifest, PluginArtifact artifact,
                                     PluginSandbox.Limits limits) {
        if (manifest == null || artifact == null) {
            return new ValidationResult(manifest == null ? null : manifest.id(), false, List.of(),
                    "the validation needs both a manifest and an artefact");
        }
        if (artifact.id() != null && !artifact.id().equals(manifest.id())) {
            return new ValidationResult(manifest.id(), false, List.of(),
                    "the artefact " + artifact.id() + " does not belong to the plugin "
                            + manifest.id());
        }
        if (manifest.artifactDigest() != null && !manifest.artifactDigest().isBlank()
                && !manifest.artifactDigest().equals(artifact.digest())) {
            return new ValidationResult(manifest.id(), false, List.of(),
                    "the artefact does not match the digest the manifest declares");
        }
        if (artifact.isolation() != sandbox.isolation()) {
            return new ValidationResult(manifest.id(), false, List.of(),
                    "the artefact declares the isolation " + artifact.isolation()
                            + " and the sandbox provides " + sandbox.isolation());
        }
        if (artifact.declaredCases().isEmpty()) {
            return new ValidationResult(manifest.id(), false, List.of(),
                    "the artefact declares no case, so it cannot be validated");
        }

        List<CaseOutcome> outcomes = new ArrayList<>();
        for (PluginArtifact.Case declared : artifact.declaredCases()) {
            SandboxResult result = sandbox.run(artifact, declared.input(), limits);
            if (!result.succeeded()) {
                outcomes.add(new CaseOutcome(declared.name(), false, result.output(),
                        declared.expected(),
                        result.status() == SandboxResult.Status.REFUSED
                                ? "the sandbox refused to run the artefact: " + result.error()
                                : "the program failed: " + result.error()));
                continue;
            }
            boolean passed = sameOutput(result.output(), declared.expected());
            outcomes.add(new CaseOutcome(declared.name(), passed, result.output(),
                    declared.expected(), passed ? null : "the output is not the promised one"));
        }
        long failures = outcomes.stream().filter(o -> !o.passed()).count();
        return new ValidationResult(manifest.id(), failures == 0, List.copyOf(outcomes),
                failures == 0 ? null : failures + " of " + outcomes.size()
                        + " declared case(s) did not pass");
    }

    /**
     * Validates an artefact with the sandbox's own limits.
     *
     * @param manifest the declaration of the plugin
     * @param artifact the artefact to validate
     * @return the outcome of the validation
     */
    public ValidationResult validate(Plugin manifest, PluginArtifact artifact) {
        return validate(manifest, artifact,
                new PluginSandbox.Limits(DeclarativeSandbox.DEFAULT_MAX_STEPS,
                        DeclarativeSandbox.DEFAULT_MAX_WALL_CLOCK_MILLIS));
    }

    private static boolean sameOutput(Map<String, Object> actual, Map<String, Object> expected) {
        if (actual.size() != expected.size()) {
            return false;
        }
        Map<String, Object> normalisedActual = new LinkedHashMap<>();
        actual.forEach((key, value) -> normalisedActual.put(key, normalise(value)));
        Map<String, Object> normalisedExpected = new LinkedHashMap<>();
        expected.forEach((key, value) -> normalisedExpected.put(key, normalise(value)));
        return normalisedActual.equals(normalisedExpected);
    }

    private static Object normalise(Object value) {
        if (value instanceof Double number) {
            return number == Math.rint(number) && !number.isInfinite() ? (Object) number.longValue()
                    : number;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(PluginValidator::normalise).toList();
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> normalised = new LinkedHashMap<>();
            map.forEach((key, item) -> normalised.put(String.valueOf(key), normalise(item)));
            return normalised;
        }
        return value;
    }
}
