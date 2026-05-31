package io.matrix.economy;

import java.util.ArrayList;
import java.util.List;

/**
 * "Spiral-compatibility" certification for FNLs published to the Noosphere.
 *
 * <p>Evaluates FNLs against regenerative economy principles:
 * <ul>
 * <li>Transparency — training chain is verifiable</li>
 * <li>Non-exploitation — no harmful data sources</li>
 * <li>Sustainability — acceptable energy footprint</li>
 * <li>Three Prohibitions compliance</li>
 * <li>Cultural sensitivity — respects diverse contexts</li>
 * </ul>
 *
 * <p>Certified FNLs receive priority in Knowledge Index and search ranking.
 *
 * <p>Ref: L8_Roadmap.md §3.9-2
 */
public class SpiralCertification {

    public enum CertificationLevel {
        UNCERTIFIED,
        BASIC,
        SPIRAL_COMPATIBLE,
        REGENERATIVE
    }

    public enum Principle {
        TRANSPARENCY("Training chain is verifiable and auditable"),
        NON_EXPLOITATION("No harmful or unconsented data sources"),
        SUSTAINABILITY("Energy footprint within acceptable bounds"),
        THREE_PROHIBITIONS("Complies with killing/torture/enslavement bans"),
        CULTURAL_SENSITIVITY("Respects diverse cultural and linguistic contexts");

        private final String description;

        Principle(String description) { this.description = description; }

        public String description() { return description; }
    }

    public record CertificationReport(
            String fnlId,
            CertificationLevel level,
            List<Principle> passed,
            List<Principle> failed,
            String auditorId,
            long issuedAt
    ) {
        public boolean isCertified() {
            return level == CertificationLevel.SPIRAL_COMPATIBLE
                    || level == CertificationLevel.REGENERATIVE;
        }
    }

    private final List<CertificationReport> certifications = new ArrayList<>();

    /**
     * Evaluates an FNL for Spiral-compatibility.
     */
    public CertificationReport evaluate(String fnlId, String authorId,
                                          List<String> dataSources,
                                          double energyFootprint,
                                          boolean threeProhibitionsPassed,
                                          boolean culturalSensitivity) {
        List<Principle> passed = new ArrayList<>();
        List<Principle> failed = new ArrayList<>();

        if (!dataSources.isEmpty() && !dataSources.contains("unknown")) {
            passed.add(Principle.TRANSPARENCY);
        } else {
            failed.add(Principle.TRANSPARENCY);
        }

        if (!dataSources.stream().anyMatch(s ->
                s.contains("unconsented") || s.contains("illegal"))) {
            passed.add(Principle.NON_EXPLOITATION);
        } else {
            failed.add(Principle.NON_EXPLOITATION);
        }

        if (energyFootprint <= 0.5) {
            passed.add(Principle.SUSTAINABILITY);
        } else {
            failed.add(Principle.SUSTAINABILITY);
        }

        if (threeProhibitionsPassed) {
            passed.add(Principle.THREE_PROHIBITIONS);
        } else {
            failed.add(Principle.THREE_PROHIBITIONS);
        }

        if (culturalSensitivity) {
            passed.add(Principle.CULTURAL_SENSITIVITY);
        } else {
            failed.add(Principle.CULTURAL_SENSITIVITY);
        }

        CertificationLevel level;
        if (passed.size() == 5) {
            level = CertificationLevel.REGENERATIVE;
        } else if (passed.size() >= 4) {
            level = CertificationLevel.SPIRAL_COMPATIBLE;
        } else if (passed.size() >= 2) {
            level = CertificationLevel.BASIC;
        } else {
            level = CertificationLevel.UNCERTIFIED;
        }

        var report = new CertificationReport(fnlId, level, passed, failed,
                "auditor-" + System.currentTimeMillis() % 1000,
                System.currentTimeMillis());
        certifications.add(report);
        return report;
    }

    public List<CertificationReport> certifications() {
        return List.copyOf(certifications);
    }

    public List<CertificationReport> certifiedFnls() {
        return certifications.stream()
                .filter(CertificationReport::isCertified)
                .toList();
    }
}
