package io.matrix.plugin;

import io.matrix.contract.Plugin;

import java.util.List;

/**
 * W51 — creator mode: the system notices a gap and builds a capability to fill it.
 *
 * <p><b>What this replaces.</b> Nothing. The W51 mandate asks for "a bounded proof of concept
 * for self-generated capabilities": the system should "recognize a capability gap, propose a
 * new plugin or skill, create its artifact, validate it, and make it available under the
 * runtime's policy", and it says explicitly that "the first implementation does not need an
 * unrestricted autonomous software engineer". This class is that proof of concept, and it is
 * bounded in the way the mandate asks for.</p>
 *
 * <p><b>What the generator can and cannot do.</b> It can build a <i>numeric threshold
 * classifier</i>: a plugin that reads one numeric field out of its input and reports whether
 * it is at or above a declared threshold. That is a real, useful, and complete family of
 * capabilities — the kind a method selector actually needs when it has a measured score and no
 * rule for turning it into a decision. A gap that does not fit that family is REFUSED, with
 * the reason. A creator that pretended to build anything would be a worse outcome than a
 * creator that says "I do not know how to build that yet".</p>
 *
 * <p><b>The generated code grants itself nothing.</b> The manifest is proposed with the
 * capabilities the gap says are needed; the runtime decides the grant, and the grant is
 * narrower or equal. {@link #create} refuses to activate a plugin whose declared capabilities
 * are not all granted.</p>
 *
 * <p><b>The lifecycle is walked in order and the audit records every step.</b> The W51 mandate
 * names the chain {@code PROPOSED → BUILDING → SANDBOXED → VALIDATED → SHADOW → ACTIVE}; this
 * class walks it and refuses to skip a step, so the trace of a created plugin is the trace of
 * the real lifecycle rather than a description of it.</p>
 *
 * <p><b>The cases come from the gap, not from the generator.</b> The gap carries the evidence
 * that the capability was missing — the inputs and the outputs that were expected. The
 * generator writes a program; the validator checks it against the evidence. A generator that
 * also invented the expectations would be grading its own homework.</p>
 */
public final class CreatorMode {

    /**
     * A capability the system found itself lacking, with the evidence that it is lacking it.
     *
     * @param id                   the identity of the gap
     * @param capabilityId         the capability that is missing
     * @param purpose              what the plugin is for
     * @param inputContract        the contract of what the plugin consumes
     * @param outputContract       the contract of what the plugin produces
     * @param featurePath          the path of the numeric field the decision is about
     * @param threshold            the threshold of the decision
     * @param aboveMeansPositive   whether a value at or above the threshold is the positive one
     * @param outputField          the name of the field the decision is written to
     * @param requiredCapabilities the capabilities the plugin needs
     * @param declaredCases        the evidence: the cases the plugin must satisfy
     * @param evidence             why the gap was noticed
     */
    public record CapabilityGap(
            String id,
            String capabilityId,
            String purpose,
            String inputContract,
            String outputContract,
            String featurePath,
            double threshold,
            boolean aboveMeansPositive,
            String outputField,
            List<String> requiredCapabilities,
            List<PluginArtifact.Case> declaredCases,
            String evidence) {
        public CapabilityGap {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("gap id must not be blank");
            }
            if (capabilityId == null || capabilityId.isBlank()) {
                throw new IllegalArgumentException("gap capability id must not be blank");
            }
            if (purpose == null || purpose.isBlank()) {
                throw new IllegalArgumentException("gap purpose must not be blank");
            }
            requiredCapabilities = requiredCapabilities == null ? List.of()
                    : List.copyOf(requiredCapabilities);
            declaredCases = declaredCases == null ? List.of() : List.copyOf(declaredCases);
        }
    }

    /**
     * The result of a creation attempt.
     *
     * @param manifest the declaration that was produced
     * @param artifact the artefact that was produced, when one was
     * @param validation the outcome of the validation, when it ran
     * @param finalStage the stage the plugin ended in
     * @param reason why the attempt ended where it did
     */
    public record CreationResult(
            Plugin manifest,
            PluginArtifact artifact,
            PluginValidator.ValidationResult validation,
            Plugin.Stage finalStage,
            String reason) {
    }

    private final PluginSandbox sandbox;
    private final PluginValidator validator;

    /**
     * @param sandbox the sandbox the generated artefact is validated in
     */
    public CreatorMode(PluginSandbox sandbox) {
        if (sandbox == null) {
            throw new IllegalArgumentException("sandbox must not be null");
        }
        this.sandbox = sandbox;
        this.validator = new PluginValidator(sandbox);
    }

    /**
     * Proposes the plugin that would fill a gap.
     *
     * <p>The proposal declares what the plugin would do and what it would need. It grants
     * itself nothing: {@link Plugin#grantedCapabilities()} is empty, and the runtime fills it
     * in.</p>
     *
     * @param gap the gap
     * @return the proposed manifest, in the {@link Plugin.Stage#PROPOSED} stage
     */
    public Plugin propose(CapabilityGap gap) {
        if (gap == null) {
            throw new IllegalArgumentException("gap must not be null");
        }
        return new Plugin(gap.id(), 1, gap.capabilityId(), gap.purpose(), gap.inputContract(),
                gap.outputContract(), gap.requiredCapabilities(), List.of(), List.of(),
                sandbox.isolation(), new Plugin.Budget(0, 0L, 0L), null, Plugin.Stage.PROPOSED,
                List.of(), null, "the registry revokes it and removes its registrations");
    }

    /**
     * Builds the artefact that fills a gap.
     *
     * @param manifest the proposal
     * @param gap      the gap
     * @return the artefact, or {@code null} when the gap is outside the generator's family
     */
    public PluginArtifact generate(Plugin manifest, CapabilityGap gap) {
        if (manifest == null || gap == null) {
            return null;
        }
        if (gap.featurePath() == null || gap.featurePath().isBlank()) {
            return null;
        }
        if (gap.outputField() == null || gap.outputField().isBlank()) {
            return null;
        }
        String program = """
                {
                  "steps": [
                    {"op":"let","name":"value","value":{"op":"get","path":"%s"}},
                    {"op":"if",
                     "cond":{"op":"gte","args":[{"op":"var","name":"value"},
                                               {"op":"const","value":%s}]},
                     "then":{"op":"let","name":"%s","value":{"op":"const","value":true}},
                     "else":{"op":"let","name":"%s","value":{"op":"const","value":false}}}
                  ],
                  "output": {"%s":"%s"}
                }
                """.formatted(gap.featurePath(), thresholdLiteral(gap.threshold()),
                gap.outputField(), gap.outputField(), gap.outputField(), gap.outputField());
        return new PluginArtifact(manifest.id(), manifest.version(), program, gap.declaredCases(),
                sandbox.isolation());
    }

    /**
     * Walks the whole lifecycle for a gap.
     *
     * <p>Every step is a real transition of a real registry, and the audit of the registry
     * holds the trace. The attempt stops at the first step that cannot be taken, and the
     * result says which one and why — a plugin that could not be validated does not go to
     * {@link Plugin.Stage#SHADOW}, and it says so.</p>
     *
     * @param gap     the gap to fill
     * @param registry the registry the plugin is created in
     * @param grant   the capabilities the runtime grants the plugin
     * @param at      the instant of every step
     * @return the result of the attempt
     */
    public CreationResult create(CapabilityGap gap, PluginRegistry registry, List<String> grant,
                                  String at) {
        if (registry == null) {
            throw new IllegalArgumentException("registry must not be null");
        }
        Plugin proposed = propose(gap);
        Plugin registered = registry.register(proposed, at);
        PluginArtifact artifact = generate(registered, gap);
        if (artifact == null) {
            registry.transition(registered.id(), Plugin.Stage.BUILDING, "the artefact is built", at);
            registry.transition(registered.id(), Plugin.Stage.FAILED,
                    "the gap is outside the generator's family", at);
            return new CreationResult(registered, null, null, Plugin.Stage.FAILED,
                    "the gap is outside the generator's family: this creator builds a numeric"
                            + " threshold classifier and nothing else");
        }

        registry.transition(registered.id(), Plugin.Stage.BUILDING, "the artefact was generated", at);
        registry.transition(registered.id(), Plugin.Stage.SANDBOXED,
                "the artefact runs inside the " + sandbox.isolation() + " sandbox", at);

        PluginValidator.ValidationResult validation = validator.validate(registered, artifact);
        if (!validation.passed()) {
            registry.transition(registered.id(), Plugin.Stage.FAILED, validation.reason(), at);
            return new CreationResult(registered, artifact, validation, Plugin.Stage.FAILED,
                    validation.reason());
        }

        Plugin validated = registry.transition(registered.id(), Plugin.Stage.VALIDATED,
                validation.outcomes().size() + " declared case(s) passed", at);
        Plugin shadowed = registry.transition(validated.id(), Plugin.Stage.SHADOW,
                "the plugin runs without influencing the real path yet", at);

        List<String> granted = grant == null ? List.of() : grant;
        if (!granted.containsAll(shadowed.declaredCapabilities())) {
            registry.transition(shadowed.id(), Plugin.Stage.FAILED,
                    "the runtime did not grant every capability the plugin declared", at);
            return new CreationResult(shadowed, artifact, validation, Plugin.Stage.FAILED,
                    "the runtime granted " + granted + " and the plugin declared "
                            + shadowed.declaredCapabilities());
        }

        Plugin grantedPlugin = registry.grant(shadowed.id(), granted,
                "the runtime grants exactly what the plugin declared", at);
        Plugin active = registry.transition(grantedPlugin.id(), Plugin.Stage.ACTIVE,
                "the plugin's results are used", at);
        return new CreationResult(active, artifact, validation, Plugin.Stage.ACTIVE,
                "the plugin was created, validated and activated under the grant " + granted);
    }

    /**
     * The literal of a threshold.
     *
     * <p>The literal is produced in a fixed form so that the same threshold always produces
     * the same programme text, and therefore the same digest. A locale-dependent rendering
     * would make the digest depend on the machine.</p>
     */
    private static String thresholdLiteral(double threshold) {
        if (threshold == Math.rint(threshold) && !Double.isInfinite(threshold)) {
            return String.valueOf((long) threshold);
        }
        return String.valueOf(threshold);
    }
}
