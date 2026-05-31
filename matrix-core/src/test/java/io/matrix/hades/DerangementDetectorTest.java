package io.matrix.hades;

import io.matrix.cluster.NeuronId;
import io.matrix.cluster.NeuronInstance;
import io.matrix.neuron.TruthTable;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DerangementDetectorTest {

    @Test
    void shouldBeCleanInitially() {
        DerangementDetector detector = new DerangementDetector();

        assertThat(detector.alerts()).isEmpty();
        assertThat(detector.maxSeverity()).isEqualTo(DerangementDetector.Severity.NONE);
    }

    @Test
    void shouldDetectExcessiveSignalRate() {
        DerangementDetector detector = new DerangementDetector();
        NeuronId id = NeuronId.create();
        NeuronInstance neuron = NeuronInstance.stable(id, TruthTable.random(3));

        var alert = detector.checkNeuron(neuron, 500);

        assertThat(alert).isNotNull();
        assertThat(alert.severity()).isEqualTo(DerangementDetector.Severity.HIGH);
        assertThat(alert.description()).contains("500");
    }

    @Test
    void shouldDetectAllZerosTruthTable() {
        DerangementDetector detector = new DerangementDetector();
        NeuronId id = NeuronId.create();
        NeuronInstance neuron = NeuronInstance.stable(id, TruthTable.fromLong(3, 0));

        var alert = detector.checkTruthTable(neuron);

        assertThat(alert).isNotNull();
        assertThat(alert.severity()).isEqualTo(DerangementDetector.Severity.MEDIUM);
    }

    @Test
    void shouldDetectAllOnesTruthTable() {
        DerangementDetector detector = new DerangementDetector();
        NeuronId id = NeuronId.create();
        NeuronInstance neuron = NeuronInstance.stable(id,
                TruthTable.fromLong(3, (1L << (1 << 3)) - 1));

        var alert = detector.checkTruthTable(neuron);

        assertThat(alert).isNotNull();
    }

    @Test
    void shouldPassNormalNeuron() {
        DerangementDetector detector = new DerangementDetector();
        NeuronId id = NeuronId.create();
        NeuronInstance neuron = NeuronInstance.stable(id, TruthTable.random(3));

        var rateAlert = detector.checkNeuron(neuron, 10);
        var ttAlert = detector.checkTruthTable(neuron);

        assertThat(rateAlert).isNull();
        assertThat(ttAlert).isNull();
    }

    @Test
    void shouldScanAllNeurons() {
        DerangementDetector detector = new DerangementDetector();
        NeuronId id = NeuronId.create();
        NeuronInstance bad = NeuronInstance.stable(id, TruthTable.fromLong(2, 0));
        NeuronInstance good = NeuronInstance.stable(NeuronId.create(), TruthTable.random(2));

        var rates = new HashMap<NeuronId, Integer>();
        rates.put(id, 150);

        var alerts = detector.scanAll(List.of(bad, good), rates);

        assertThat(alerts).isNotEmpty();
    }

    @Test
    void shouldDetectCriticalAlerts() {
        DerangementDetector detector = new DerangementDetector();
        detector.checkNeuron(NeuronInstance.stable(NeuronId.create(),
                TruthTable.random(2)), 999);

        assertThat(detector.hasHighAlerts()).isTrue();
        assertThat(detector.maxSeverity()).isEqualTo(DerangementDetector.Severity.HIGH);
    }

    @Test
    void shouldClearAlerts() {
        DerangementDetector detector = new DerangementDetector();
        detector.checkNeuron(NeuronInstance.stable(NeuronId.create(),
                TruthTable.random(2)), 999);
        detector.clearAlerts();

        assertThat(detector.alerts()).isEmpty();
    }
}
