package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 309 — CycleDispatcher unit tests. */
class CycleDispatcherTest {

    @Test
    void dispatchWithoutHandler() {
        var d = new CycleDispatcher();
        // Should not throw
        d.dispatch(CycleDispatcher.Event.CYCLE_START);
    }

    @Test
    void dispatchToHandler() {
        var d = new CycleDispatcher();
        List<CycleDispatcher.Event> received = new ArrayList<>();
        d.register(received::add);
        d.dispatch(CycleDispatcher.Event.CYCLE_START);
        d.dispatch(CycleDispatcher.Event.CYCLE_END);
        assertThat(received).containsExactly(
                CycleDispatcher.Event.CYCLE_START,
                CycleDispatcher.Event.CYCLE_END);
    }

    @Test
    void replaceHandler() {
        var d = new CycleDispatcher();
        List<CycleDispatcher.Event> first = new ArrayList<>();
        List<CycleDispatcher.Event> second = new ArrayList<>();
        d.register(first::add);
        d.dispatch(CycleDispatcher.Event.CYCLE_START);
        d.register(second::add);
        d.dispatch(CycleDispatcher.Event.CYCLE_END);
        assertThat(first).hasSize(1);
        assertThat(second).hasSize(1);
    }

    @Test
    void eventEnum() {
        assertThat(CycleDispatcher.Event.values()).hasSize(4);
    }
}
