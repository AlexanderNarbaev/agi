package io.matrix.consensus;

import io.matrix.hades.DerangementDetector;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FaultDetectorTest {

    @Test
    void shouldBeCleanInitially() {
        FaultDetector detector = new FaultDetector();

        assertThat(detector.alerts()).isEmpty();
        assertThat(detector.suspectedNodes()).isEmpty();
    }

    @Test
    void shouldDetectEquivocation() {
        FaultDetector detector = new FaultDetector();

        ByzantineMessage vote1 = ByzantineMessage.vote("bad-node", "value", 1, true);
        ByzantineMessage vote2 = ByzantineMessage.vote("bad-node", "value", 1, false);

        List<FaultDetector.FaultAlert> alerts1 = detector.recordMessage(vote1);
        List<FaultDetector.FaultAlert> alerts2 = detector.recordMessage(vote2);

        assertThat(alerts2).isNotEmpty();
        assertThat(alerts2.stream()
                .anyMatch(a -> a.type() == FaultDetector.FaultType.EQUIVOCATION)).isTrue();
        assertThat(detector.isSuspected("bad-node")).isTrue();
    }

    @Test
    void shouldDetectContradiction() {
        FaultDetector detector = new FaultDetector();

        ByzantineMessage proposal = ByzantineMessage.propose("node-1", "my-value", 1);
        ByzantineMessage vote = ByzantineMessage.vote("node-1", "my-value", 1, false);

        detector.recordMessage(proposal);
        List<FaultDetector.FaultAlert> alerts = detector.recordMessage(vote);

        assertThat(alerts).isNotEmpty();
        assertThat(alerts.stream()
                .anyMatch(a -> a.type() == FaultDetector.FaultType.CONTRADICTION)).isTrue();
    }

    @Test
    void shouldDetectHallucination() {
        FaultDetector detector = new FaultDetector();

        ByzantineMessage prop1 = ByzantineMessage.propose("node-1", "value-A", 1);
        ByzantineMessage prop2 = ByzantineMessage.propose("node-1", "value-B", 2);

        detector.recordMessage(prop1);
        List<FaultDetector.FaultAlert> alerts = detector.recordMessage(prop2);

        assertThat(alerts).isNotEmpty();
        assertThat(alerts.stream()
                .anyMatch(a -> a.type() == FaultDetector.FaultType.HALLUCINATION)).isTrue();
    }

    @Test
    void shouldNotFlagConsistentBehavior() {
        FaultDetector detector = new FaultDetector();

        ByzantineMessage prop1 = ByzantineMessage.propose("node-1", "same", 1);
        ByzantineMessage vote1 = ByzantineMessage.vote("node-1", "same", 1, true);

        List<FaultDetector.FaultAlert> alerts1 = detector.recordMessage(prop1);
        List<FaultDetector.FaultAlert> alerts2 = detector.recordMessage(vote1);

        assertThat(alerts1).isEmpty();
        assertThat(alerts2).isEmpty();
        assertThat(detector.isSuspected("node-1")).isFalse();
    }

    @Test
    void shouldReturnSuspectedNodes() {
        FaultDetector detector = new FaultDetector();

        detector.recordMessage(ByzantineMessage.vote("bad-1", "v", 1, true));
        detector.recordMessage(ByzantineMessage.vote("bad-1", "v", 1, false));
        detector.recordMessage(ByzantineMessage.vote("bad-2", "v", 1, true));
        detector.recordMessage(ByzantineMessage.vote("bad-2", "v", 1, false));

        List<String> suspected = detector.suspectedNodes();

        assertThat(suspected).contains("bad-1", "bad-2");
    }

    @Test
    void shouldCountFaultsPerNode() {
        FaultDetector detector = new FaultDetector();

        detector.recordMessage(ByzantineMessage.propose("node-1", "A", 1));
        detector.recordMessage(ByzantineMessage.propose("node-1", "B", 2));

        assertThat(detector.faultCount("node-1")).isEqualTo(1);
        assertThat(detector.faultCount("node-2")).isEqualTo(0);
    }

    @Test
    void shouldFilterAlertsByType() {
        FaultDetector detector = new FaultDetector();

        detector.recordMessage(ByzantineMessage.propose("node-1", "A", 1));
        detector.recordMessage(ByzantineMessage.propose("node-1", "B", 2));
        detector.recordMessage(ByzantineMessage.vote("node-2", "v", 1, true));
        detector.recordMessage(ByzantineMessage.vote("node-2", "v", 1, false));

        List<FaultDetector.FaultAlert> hallucinations = detector.alertsByType(
                FaultDetector.FaultType.HALLUCINATION);
        List<FaultDetector.FaultAlert> equivocations = detector.alertsByType(
                FaultDetector.FaultType.EQUIVOCATION);

        assertThat(hallucinations).isNotEmpty();
        assertThat(equivocations).isNotEmpty();
    }

    @Test
    void shouldIntegrateWithDerangementDetector() {
        DerangementDetector dd = new DerangementDetector();
        FaultDetector detector = new FaultDetector(dd);

        assertThat(detector.derangementDetector()).isSameAs(dd);
    }

    @Test
    void shouldClearAllAlerts() {
        FaultDetector detector = new FaultDetector();

        detector.recordMessage(ByzantineMessage.vote("node-1", "v", 1, true));
        detector.recordMessage(ByzantineMessage.vote("node-1", "v", 1, false));

        assertThat(detector.alerts()).isNotEmpty();

        detector.clear();

        assertThat(detector.alerts()).isEmpty();
        assertThat(detector.suspectedNodes()).isEmpty();
    }

    @Test
    void shouldNotDetectHallucinationForDifferentMessageTypes() {
        FaultDetector detector = new FaultDetector();

        ByzantineMessage propose = ByzantineMessage.propose("node-1", "value", 1);
        ByzantineMessage vote = ByzantineMessage.vote("node-1", "other", 1, true);

        detector.recordMessage(propose);
        List<FaultDetector.FaultAlert> alerts = detector.recordMessage(vote);

        assertThat(alerts.stream()
                .anyMatch(a -> a.type() == FaultDetector.FaultType.HALLUCINATION)).isFalse();
    }
}
