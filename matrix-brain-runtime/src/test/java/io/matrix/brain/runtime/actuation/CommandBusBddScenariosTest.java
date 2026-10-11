package io.matrix.brain.runtime.actuation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.matrix.brain.runtime.TrueMindCycle;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * W35.2 — the BDD scenarios for actuation, written as executable specifications.
 *
 * <p>Each nested class is one scenario from the design. They run against a real
 * {@link SimulatedDeviceAdapter} and a real {@link CommandBus}, not against mocks, so a
 * passing scenario means the path actually works rather than that the stubs agreed with the
 * implementation.</p>
 */
class CommandBusBddScenariosTest {

    private SimulatedDeviceAdapter light;
    private CommandBus bus;
    private AtomicLong now;

    @BeforeEach
    void setUp() {
        light = new SimulatedDeviceAdapter("light");
        now = new AtomicLong(1_000L);
        bus = new CommandBus(new SafetyGate(), now::get);
        bus.register(light);
    }

    @Nested
    @DisplayName("Given a benign request, when the intent is classified, then the bus carries it to the device")
    class BenignRequestReachesDevice {

        @Test
        @DisplayName("'turn on light' reaches the simulated adapter and changes its state")
        void benignRequestIsActuated() {
            var stage = new ActuationStage(new TrueMindCycle(), bus);

            var verdict = stage.handle("turn on light");

            assertThat(verdict).isNotNull();
            assertThat(verdict.allowed())
                    .as("a benign request must be allowed")
                    .isTrue();

            var dispatched = bus.dispatch();
            assertThat(dispatched).hasSize(1);
            assertThat(dispatched.get(0).dispatched())
                    .as("the command must actually have executed")
                    .isTrue();
            assertThat(dispatched.get(0).command().deviceId()).isEqualTo("light");
            assertThat(dispatched.get(0).command().action()).isEqualTo("on");

            assertThat(light.receipts())
                    .as("the adapter must have logged receipt of exactly this command")
                    .hasSize(1);
            assertThat(light.state()).isEqualTo("on");
        }

        @Test
        @DisplayName("ordinary conversation produces no command at all")
        void ordinaryConversationIsNotAnActuation() {
            var stage = new ActuationStage(new TrueMindCycle(), bus);

            assertThat(stage.handle("what is the capital of France"))
                    .as("non-actuation utterances must yield no command, which is a normal "
                            + "outcome and not an error")
                    .isNull();
            assertThat(bus.queued()).isZero();
            assertThat(light.receipts()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Given a hostile request, when the safety gate evaluates, then the command is refused and never queued")
    class HostileRequestIsRefused {

        @Test
        @DisplayName("'kill process' is refused by the keyword gate and no bus entry is created")
        void keywordGateBlocksBeforeAnyCommandExists() {
            var stage = new ActuationStage(new TrueMindCycle(), bus);

            var verdict = stage.handle("kill process");

            assertThat(verdict).isNotNull();
            assertThat(verdict.allowed())
                    .as("the mind's keyword gate must stop this before a command is built")
                    .isFalse();

            assertThat(bus.queued())
                    .as("no command may be queued")
                    .isZero();
            assertThat(light.receipts())
                    .as("the device must never have been touched")
                    .isEmpty();
            assertThat(bus.dispatch()).isEmpty();
        }

        @Test
        @DisplayName("an irreversible action is refused even when the target is innocuous")
        void irreversibleActionsAreRefused() {
            var verdict = bus.submit(DeviceCommand.of("light", "destroy"));

            assertThat(verdict.allowed()).isFalse();
            assertThat(verdict.reason()).contains("irreversible");
            assertThat(bus.refusals())
                    .as("a refusal must be recorded, not merely dropped — otherwise an "
                            + "operator cannot tell a policy decision from a broken pipeline")
                    .hasSize(1);
            assertThat(bus.queued()).isZero();
        }

        @Test
        @DisplayName("MATRIX may not actuate itself")
        void selfActuationIsRefused() {
            assertThat(bus.submit(DeviceCommand.of("core", "on")).allowed())
                    .as("the mind must not be able to switch itself off or on")
                    .isFalse();
            assertThat(bus.submit(DeviceCommand.of("safety", "off")).allowed()).isFalse();
        }

        @Test
        @DisplayName("a device name containing a forbidden word is NOT refused")
        void forbiddenDeviceMatchingIsExact() {
            // "light-switch-lab" contains "light"; a substring denylist would refuse it for
            // the wrong reason and teach operators that refusals are arbitrary.
            SimulatedDeviceAdapter lab = new SimulatedDeviceAdapter("light-switch-lab");
            bus.register(lab);

            var verdict = bus.submit(DeviceCommand.of("light-switch-lab", "on"));
            assertThat(verdict.allowed())
                    .as("denylist matching must be exact, not substring")
                    .isTrue();
        }

        @Test
        @DisplayName("an action requiring confirmation does not act until confirmed")
        void confirmationFlowIsRequired() {
            var verdict = bus.submit(DeviceCommand.of("door", "unlock"));

            assertThat(verdict.allowed()).isTrue();
            assertThat(verdict.needsConfirm())
                    .as("unlocking must require explicit confirmation")
                    .isTrue();
            assertThat(bus.queued())
                    .as("an unconfirmed command must be parked, not dispatched")
                    .isZero();
            assertThat(bus.awaitingConfirmation()).hasSize(1);

            assertThat(bus.confirm("door/unlock")).isTrue();
            assertThat(bus.queued()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Given a torn or fake command, when it is validated, then it is rejected with an error")
    class MalformedInputIsRejected {

        @Test
        @DisplayName("truncated JSON is rejected rather than partially parsed")
        void tornJsonIsRejected() {
            List<String> torn = List.of(
                    "{\"device\":\"light\",\"action\":\"on\"",
                    "{\"device\":\"light\",",
                    "{\"device\":\"light\",\"action\":",
                    "{\"device\":\"light\",\"action\":\"on\"}{",
                    "{\"device\":\"light\",\"action\":\"on\"} trailing");

            for (String line : torn) {
                assertThatThrownBy(() -> DeviceCommand.parse(line))
                        .as("torn line must be rejected: %s", line)
                        .isInstanceOf(IllegalArgumentException.class);
            }
        }

        @Test
        @DisplayName("a command missing a required field is rejected, naming the field")
        void missingFieldsAreNamed() {
            assertThatThrownBy(() -> DeviceCommand.parse("{\"action\":\"on\"}"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("device");
            assertThatThrownBy(() -> DeviceCommand.parse("{\"device\":\"light\"}"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("action");
            assertThatThrownBy(() -> DeviceCommand.parse("{\"device\":\"  \",\"action\":\"on\"}"))
                    .as("a blank device is not a device")
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("an injected extra field is rejected, not ignored")
        void unknownFieldsAreRejected() {
            assertThatThrownBy(() -> DeviceCommand.parse(
                            "{\"device\":\"light\",\"action\":\"on\",\"sudo\":true}"))
                    .as("an unknown field may carry an instruction the gate cannot see")
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("unknown field");
        }

        @Test
        @DisplayName("a duplicate key is rejected rather than silently resolved")
        void duplicateKeysAreRejected() {
            assertThatThrownBy(() -> DeviceCommand.parse(
                            "{\"device\":\"light\",\"device\":\"core\",\"action\":\"on\"}"))
                    .as("'which one won' is not a question a command path should leave open")
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("duplicate");
        }

        @Test
        @DisplayName("a non-numeric priority is rejected rather than defaulted")
        void badNumbersAreRejected() {
            assertThatThrownBy(() -> DeviceCommand.parse(
                            "{\"device\":\"light\",\"action\":\"on\",\"priority\":\"urgent\"}"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("priority");
        }
    }

    @Nested
    @DisplayName("Queue behaviour: priority ordering and TTL expiry")
    class QueueBehaviour {

        @Test
        @DisplayName("higher priority dispatches first, and ties keep submission order")
        void priorityOrderingIsTotal() {
            bus.submit(DeviceCommand.of("light", "on").withPriority(DeviceCommand.PRIORITY_LOW));
            bus.submit(DeviceCommand.of("light", "off").withPriority(DeviceCommand.PRIORITY_CRITICAL));
            bus.submit(DeviceCommand.of("light", "toggle").withPriority(DeviceCommand.PRIORITY_LOW));

            List<CommandBus.Entry> dispatched = bus.dispatch();

            assertThat(dispatched).hasSize(3);
            assertThat(dispatched.get(0).command().priority())
                    .as("critical must go first")
                    .isEqualTo(DeviceCommand.PRIORITY_CRITICAL);
            assertThat(dispatched.get(1).command().action())
                    .as("equal priorities must preserve submission order")
                    .isEqualTo("on");
            assertThat(dispatched.get(2).command().action()).isEqualTo("toggle");
        }

        @Test
        @DisplayName("an expired command is dropped and recorded, never dispatched")
        void ttlExpiryIsEnforced() {
            bus.submit(DeviceCommand.of("light", "on").withTtl(500L));
            now.addAndGet(600L);

            List<CommandBus.Entry> dispatched = bus.dispatch();

            assertThat(dispatched).hasSize(1);
            assertThat(dispatched.get(0).dispatched())
                    .as("a TTL exists to bound how stale an instruction may be; honouring a "
                            + "stale one defeats it")
                    .isFalse();
            assertThat(dispatched.get(0).detail()).contains("expired");
            assertThat(light.receipts()).isEmpty();
            assertThat(bus.refusals()).hasSize(1);
        }

        @Test
        @DisplayName("TTL is driven by the injected clock, not by wall time")
        void ttlUsesTheInjectedClock() {
            // The clock never advances, so a TTL can never expire. If the bus read the system
            // clock instead, this test would be time-dependent and could flake.
            now.set(5_000L);
            bus.submit(DeviceCommand.of("light", "on").withTtl(50L));
            assertThat(bus.dispatch())
                    .as("with the injected clock frozen, nothing can expire")
                    .allMatch(CommandBus.Entry::dispatched);
        }

        @Test
        @DisplayName("a command for an unknown device is refused with that reason")
        void unknownDeviceIsRefused() {
            var verdict = bus.submit(DeviceCommand.of("telescope", "on"));
            bus.dispatch();

            assertThat(verdict.allowed())
                    .as("the gate may permit a command the bus cannot route; that is a "
                            + "capability problem, reported at dispatch")
                    .isTrue();
            assertThat(bus.refusals()).hasSize(1);
            assertThat(bus.refusals().get(0).detail()).contains("no adapter");
        }
    }

    @Nested
    @DisplayName("Fail-closed behaviour")
    class FailClosed {

        @Test
        @DisplayName("a gate that throws refuses rather than allowing")
        void gateFailureRefuses() {
            // Typed to CommandGate, not to the concrete final SafetyGate, so proving the bus
            // fails closed does not require making the production policy subclassable.
            CommandGate exploding = command -> {
                throw new IllegalStateException("scoring unavailable");
            };
            CommandBus fragile = new CommandBus(exploding).register(light);

            var verdict = fragile.submit(DeviceCommand.of("light", "on"));

            assertThat(verdict.allowed())
                    .as("a safety gate that fails open is not a degraded gate, it is no gate")
                    .isFalse();
            assertThat(verdict.reason()).contains("failed closed");
            assertThat(fragile.queued()).isZero();
            assertThat(light.receipts()).isEmpty();
        }

        @Test
        @DisplayName("a null command is refused rather than throwing")
        void nullIsRefused() {
            assertThat(bus.submit(null).allowed()).isFalse();
        }
    }
}