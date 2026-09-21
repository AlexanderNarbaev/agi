package io.matrix.shadow;

import io.matrix.ethics.EthicalFilter;
import io.matrix.ethics.EthicalVerdict;

import java.util.ArrayList;
import java.util.List;

/**
 * Digital Shadow — personal protection module filtering all I/O.
 *
 * <p>Integrates the four anti-degradation forces:
 * <ol>
 * <li>AntiDopamine — blocks manipulative patterns</li>
 * <li>EcoAudit — assesses ecological footprint</li>
 * <li>BlackBoxExplainer — explains external AI decisions</li>
 * <li>Source verification — validates information provenance</li>
 * </ol>
 *
 * <p>Activated on all incoming and outgoing data. User-configurable
 * protection level. Compliant with GDPR/right-to-explanation.
 *
 * <p>Ref: L8_Roadmap.md §3.6
 */
public class DigitalShadow {

    public enum ProtectionLevel { LOW, MEDIUM, HIGH, MAXIMUM }

    public record ShadowVerdict(
            boolean allowed,
            boolean blocked,
            boolean modified,
            String reason,
            List<String> warnings
    ) {
        public static ShadowVerdict allow() {
            return new ShadowVerdict(true, false, false, "Clean", List.of());
        }

        public static ShadowVerdict block(String reason) {
            return new ShadowVerdict(false, true, false, reason, List.of());
        }

        public static ShadowVerdict warn(String warning) {
            return new ShadowVerdict(true, false, false, "Warning",
                    List.of(warning));
        }

        public static ShadowVerdict modify(String reason) {
            return new ShadowVerdict(true, false, true, reason, List.of());
        }
    }

    private final AntiDopamine antiDopamine;
    private final EcoAudit ecoAudit;
    private final BlackBoxExplainer explainer;
    private final EthicalFilter ethicalFilter;
    private final List<String> trustedSources;
    private final List<String> auditLog = new ArrayList<>();

    private ProtectionLevel protectionLevel = ProtectionLevel.MEDIUM;

    public DigitalShadow(EthicalFilter ethicalFilter) {
        this.antiDopamine = new AntiDopamine();
        this.ecoAudit = new EcoAudit();
        this.explainer = new BlackBoxExplainer();
        this.ethicalFilter = ethicalFilter;
        this.trustedSources = new ArrayList<>(List.of(
                "arxiv.org", "ieee.org", "acm.org", "nature.com",
                "science.org", "github.com", "wikipedia.org"));
    }

    /**
     * Screens incoming content through all four filters.
     */
    public ShadowVerdict screenIncoming(String content, String source) {
        List<String> warnings = new ArrayList<>();

        if (protectionLevel == ProtectionLevel.LOW && source != null
                && trustedSources.contains(source)) {
            return ShadowVerdict.allow();
        }

        EthicalVerdict ethics = ethicalFilter.evaluate(content, List.of());
        if (ethics == EthicalVerdict.REJECTED) {
            auditLog.add("BLOCKED:ETHICS:" + truncate(content));
            return ShadowVerdict.block("Ethical filter rejected: " + truncate(content));
        }

        if (protectionLevel.compareTo(ProtectionLevel.MEDIUM) >= 0) {
            var dopaminePattern = antiDopamine.scan(content);
            if (dopaminePattern != null) {
                if (protectionLevel == ProtectionLevel.HIGH
                        || protectionLevel == ProtectionLevel.MAXIMUM) {
                    auditLog.add("BLOCKED:DOPAMINE:" + dopaminePattern.type());
                    return ShadowVerdict.block("Manipulative content blocked: "
                            + dopaminePattern.type());
                }
                warnings.add("Manipulative pattern: " + dopaminePattern.type());
            }
        }

        if (protectionLevel == ProtectionLevel.MAXIMUM && source != null) {
            var sourceCheck = explainer.verifySource(source, trustedSources);
            if (!sourceCheck.reliable()) {
                warnings.add("Untrusted source: " + source);
            }
        }

        auditLog.add("SCREENED:" + truncate(content) + " source=" + source);

        if (!warnings.isEmpty()) {
            return ShadowVerdict.warn(String.join("; ", warnings));
        }

        return ShadowVerdict.allow();
    }

    /**
     * Checks outgoing actions for ecological and ethical impact.
     */
    public ShadowVerdict screenOutgoing(String action) {
        List<String> warnings = new ArrayList<>();

        EthicalVerdict ethics = ethicalFilter.evaluate(action, List.of());
        if (ethics == EthicalVerdict.REJECTED) {
            auditLog.add("BLOCKED:OUT:ETHICS:" + truncate(action));
            return ShadowVerdict.block("Action violates ethical axioms");
        }

        if (protectionLevel.compareTo(ProtectionLevel.MEDIUM) >= 0) {
            var eco = ecoAudit.evaluate(action);
            if (!eco.acceptable() && protectionLevel == ProtectionLevel.MAXIMUM) {
                auditLog.add("BLOCKED:OUT:ECO:" + truncate(action));
                return ShadowVerdict.block("Action blocked: high eco impact — "
                        + eco.summary());
            }
            if (!eco.acceptable()) {
                warnings.add("High eco impact: " + eco.summary());
            }
        }

        auditLog.add("OUTGOING:" + truncate(action));
        return warnings.isEmpty() ? ShadowVerdict.allow()
                : ShadowVerdict.warn(String.join("; ", warnings));
    }

    public void setProtectionLevel(ProtectionLevel level) {
        this.protectionLevel = level;
    }

    public ProtectionLevel protectionLevel() { return protectionLevel; }

    public List<String> auditLog() { return List.copyOf(auditLog); }

    public AntiDopamine antiDopamine() { return antiDopamine; }

    public EcoAudit ecoAudit() { return ecoAudit; }

    public BlackBoxExplainer explainer() { return explainer; }

    public void addTrustedSource(String source) { trustedSources.add(source); }

    private static String truncate(String s) {
        return s.length() > 60 ? s.substring(0, 57) + "..." : s;
    }
}
