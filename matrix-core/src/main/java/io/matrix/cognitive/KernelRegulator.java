package io.matrix.cognitive;

import io.matrix.cognitive.neuromodulation.DriveDynamics;
import io.matrix.cognitive.neuromodulation.RegulationBias;
import io.matrix.cognitive.neuromodulation.RegulationController;
import io.matrix.cognitive.neuromodulation.RegulatoryEvent;
import io.matrix.cognitive.neuromodulation.RegulatoryState;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * W44 — the adapter between the kernel and the regulator.
 *
 * <p>The kernel speaks in drive DIMENSIONS; the regulator speaks in event KINDS. The adapter is
 * the only place that translation happens, which keeps the kernel's vocabulary independent of
 * the regulator's internals and keeps the regulator's bounds in one place.</p>
 *
 * <p>The regulatory state lives HERE, in the adapter, because the regulator itself is a pure
 * function of (state, events). Holding the state in the adapter means the kernel does not have
 * to, and means a test can build an adapter with a fixed state and a fixed clock.</p>
 */
final class KernelRegulator implements CognitiveKernel.RegulatoryBridge {

    private final DriveDynamics dynamics;
    private final RegulationController controller;
    private final Supplier<String> timestamps;

    private RegulatoryState state = RegulatoryState.neutral();
    private List<RegulatoryEvent> lastEvents = List.of();

    /** Creates the adapter with the standard dynamics and a wall-clock stamp. */
    KernelRegulator() {
        this(DriveDynamics.standard(), new RegulationController(),
                () -> java.time.Instant.now().toString());
    }

    /** Creates the adapter with explicit collaborators, for tests. */
    KernelRegulator(DriveDynamics dynamics, RegulationController controller,
                    Supplier<String> timestamps) {
        this.dynamics = dynamics;
        this.controller = controller;
        this.timestamps = timestamps;
    }

    @Override
    public RegulatoryState state() {
        return state;
    }

    @Override
    public RegulatoryState evolve(RegulatoryState current, List<CognitiveKernel.RegulatoryEvent> events) {
        List<RegulatoryEvent> mapped = new ArrayList<>();
        for (CognitiveKernel.RegulatoryEvent event : events) {
            mapped.add(new RegulatoryEvent(event.kind(), event.magnitude(), event.source()));
        }
        this.lastEvents = List.copyOf(mapped);
        this.state = controller.evolve(current, mapped, timestamps.get());
        return this.state;
    }

    @Override
    public RegulationBias bias(RegulatoryState state, DriveSignal.Effect effect) {
        return controller.bias(state, lastEvents);
    }

    /** The events of the last update, for the diagnostic surface. */
    List<RegulatoryEvent> lastEvents() {
        return lastEvents;
    }
}
