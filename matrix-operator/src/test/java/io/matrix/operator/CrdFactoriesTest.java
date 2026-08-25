package io.matrix.operator;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke tests for the new DESIGN-07 §7 CRD factories.
 */
class CrdFactoriesTest {

    @Test
    void signalModuleResourceCarriesSpecAndMeta() {
        SignalModuleSpec spec = new SignalModuleSpec();
        spec.setModuleName("text-lexicon");
        spec.setVersion("v3");
        spec.setMediaType("text");
        spec.setFrozen(true);

        SignalModuleResource cr = SignalModuleResource.create("text-lexicon", "matrix", spec);

        assertThat(cr.getMetadata().getName()).isEqualTo("text-lexicon");
        assertThat(cr.getMetadata().getNamespace()).isEqualTo("matrix");
        assertThat(cr.getSpec().getModuleName()).isEqualTo("text-lexicon");
        assertThat(cr.getSpec().isFrozen()).isTrue();
    }

    @Test
    void taskCellResourceCarriesBudgetContract() {
        TaskCellSpec spec = new TaskCellSpec();
        spec.setTask("craft-table");
        spec.setCpuMs(500);
        spec.setMemoryBytes(1024);
        spec.setWallTimeMs(2000);
        spec.setTtlSeconds(60);

        TaskCellResource cr = TaskCellResource.create("cell-1", "matrix", spec);

        assertThat(cr.getMetadata().getName()).isEqualTo("cell-1");
        assertThat(cr.getSpec().getCpuMs()).isEqualTo(500);
        assertThat(cr.getSpec().getTtlSeconds()).isEqualTo(60);
    }
}
