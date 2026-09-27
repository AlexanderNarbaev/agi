package io.matrix.brain.runtime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SuperAdditivityStudyTest {

    @Test
    void superadditivity_study_runs_with_both_AB_promoted() {
        SuperAdditivityStudy s = new SuperAdditivityStudy();
        SuperAdditivityStudy.StudyResult r = s.run();
        // A+B registry should have at least as many rules as A alone
        assertThat(r.rulesFromAB()).isGreaterThanOrEqualTo(r.rulesFromA());
        assertThat(r.rulesFromAB()).isGreaterThanOrEqualTo(r.rulesFromB());
        // Super-additivity: fAB should be ≥ max(fA, fB) - epsilon
        assertThat(r.superAdditivityDelta()).isGreaterThanOrEqualTo(-0.001);
    }

    @Test
    void study_result_narrative_includes_key_numbers() {
        SuperAdditivityStudy s = new SuperAdditivityStudy();
        SuperAdditivityStudy.StudyResult r = s.run();
        assertThat(r.narrative()).contains("rules(A+B)=");
        assertThat(r.narrative()).contains("fidelity");
    }
}
