package io.matrix.brain.runtime.actuation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.function.LongSupplier;

/**
 * W35.2 — the only path from a decision to a device.
 *
 * <h2>One invariant, and everything else follows from it</h2>
 *
 * <p>No adapter method is ever called except from {@link #dispatch()}, and {@code dispatch}
 * only calls an adapter after {@link SafetyGate#evaluate} has returned a permitting verdict.
 * There is no second entry point, no "execute directly" convenience overload, and no way for a
 * caller to reach a device without passing the gate. That is the whole design; the queue, the
 * priority and the TTL are convenience on top of it.</p>
 *
 * <h2>The clock is injected, never read</h2>
 *
 * <p>TTL expiry needs time. This class never calls {@code System.currentTimeMillis()} or
 * {@code nanoTime()} — it takes a {@link LongSupplier} and reads from it. CONSTITUTION Article I
 * forbids wall-clock in the runtime decision path, and a TTL check driven by a real clock would
 * put one there. Production passes a real clock explicitly at the composition root; tests pass
 * a sequence and get fully deterministic expiry.</p>
 *
 * <h2>Refusals are recorded, not merely rejected</h2>
 *
 * <p>A refused command never enters the queue, but it does enter {@link #refusals()}. The
 * distinction matters: a bus that silently drops refusals leaves an operator unable to tell a
 * policy decision from a broken pipeline, which is the same invisible-failure shape the inbox
 * had in W32.1.</p>
 */
public final class CommandBus {

    /** One accepted or refused command, with what happened to it. */
    public record Entry(DeviceCommand command, SafetyGate.Verdict verdict,
                        boolean dispatched, String detail, long atMillis) { }

    private final CommandGate gate;
    private final LongSupplier clock;
    private final Map<String, DeviceAdapter> adapters = new LinkedHashMap<>();

    /**
     * Priority queue; ties broken by enqueue sequence so ordering is total and reproducible.
     */
    private final PriorityQueue<Queued> queue =
            new PriorityQueue<>(Comparator.comparingInt((Queued q) -> -q.command.priority())
                    .thenComparingLong(Queued::seq));

    private final List<Entry> history = new ArrayList<>();
    private final List<Entry> refusals = new ArrayList<>();
    private final Map<String, DeviceCommand> awaitingConfirmation = new LinkedHashMap<>();
    private long sequence;

    private record Queued(DeviceCommand command, long enqueuedAtMillis, long seq) { }

    public CommandBus(CommandGate gate) {
        this(gate, () -> 0L);
    }

    /**
     * @param gate  the interceptor every command must pass
     * @param clock injected time source; never read from the environment by this class
     */
    public CommandBus(CommandGate gate, LongSupplier clock) {
        this.gate = gate == null ? new SafetyGate() : gate;
        this.clock = clock == null ? () -> 0L : clock;
    }

    /**
     * Register a device adapter.
     *
     * @param adapter the adapter to register
     * @return this bus, for chaining
     */
    public CommandBus register(DeviceAdapter adapter) {
        adapters.put(adapter.deviceId().toLowerCase(Locale.ROOT), adapter);
        return this;
    }

    /**
     * Offer a command to the bus.
     *
     * <p>Refused commands are recorded and never queued. A command that passes but needs
     * confirmation is parked in {@link #awaitingConfirmation()} rather than queued, so a
     * device never acts on an unconfirmed request.</p>
     *
     * @param command the command to offer
     * @return the verdict the gate produced
     */
    public SafetyGate.Verdict submit(DeviceCommand command) {
        SafetyGate.Verdict verdict;
        try {
            verdict = gate.evaluate(command);
        } catch (RuntimeException gateFailure) {
            // Defence in depth, and the reason the fail-closed property lives here rather than
            // only inside SafetyGate. SafetyGate catches its own internal failures, but the bus
            // cannot know that every CommandGate implementation will, and a gate that throws
            // must not become a gate that passes. Measured: before this catch, a throwing gate
            // propagated out of submit() and the test asserting refusal failed — the command
            // reached the caller with no verdict at all.
            verdict = SafetyGate.Verdict.refuse(
                    "safety gate failed closed: " + gateFailure.getMessage());
        }
        if (verdict == null) {
            // A gate returning null is a bug in the gate. Treating it as "allow" would be the
            // worst possible reading of a missing answer, so it is a refusal.
            verdict = SafetyGate.Verdict.refuse("safety gate returned no verdict");
        }
        long now = clock.getAsLong();

        if (!verdict.allowed()) {
            Entry e = new Entry(command, verdict, false, verdict.reason(), now);
            history.add(e);
            refusals.add(e);
            return verdict;
        }
        if (verdict.needsConfirm()) {
            awaitingConfirmation.put(command.fingerprint(), command);
            history.add(new Entry(command, verdict, false, verdict.reason(), now));
            return verdict;
        }
        enqueue(command);
        return verdict;
    }

    /**
     * Confirm a parked command so it may be dispatched.
     *
     * @param fingerprint the identity from {@link DeviceCommand#fingerprint()}
     * @return true when a parked command was confirmed and queued
     */
    public boolean confirm(String fingerprint) {
        DeviceCommand command = awaitingConfirmation.remove(fingerprint);
        if (command == null) {
            return false;
        }
        enqueue(command);
        return true;
    }

    private void enqueue(DeviceCommand command) {
        queue.add(new Queued(command, clock.getAsLong(), sequence++));
    }

    /**
     * Dispatch every queued command that is still live.
     *
     * <p>Expired commands are dropped and recorded, never dispatched: a TTL exists to bound
     * how stale an instruction may be, and honouring a stale one defeats the purpose.</p>
     *
     * @return one entry per command considered, in dispatch order
     */
    public List<Entry> dispatch() {
        List<Entry> out = new ArrayList<>();
        long now = clock.getAsLong();

        while (!queue.isEmpty()) {
            Queued head = queue.peek();
            if (isExpired(head, now)) {
                queue.poll();
                Entry e = new Entry(head.command(), gate.evaluate(head.command()), false,
                        "expired after " + head.command().ttlMillis() + "ms", now);
                history.add(e);
                refusals.add(e);
                out.add(e);
                continue;
            }
            queue.poll();
            out.add(attempt(head.command(), now));
        }
        return out;
    }

    private boolean isExpired(Queued q, long now) {
        long ttl = q.command().ttlMillis();
        return ttl > 0 && (now - q.enqueuedAtMillis()) >= ttl;
    }

    private Entry attempt(DeviceCommand command, long now) {
        // Re-gated at dispatch, not only at submit. Between submission and dispatch the
        // adapter set or the policy may have changed, and the gate is cheap; a command that
        // was acceptable a minute ago is not thereby acceptable now.
        SafetyGate.Verdict verdict;
        try {
            verdict = gate.evaluate(command);
        } catch (RuntimeException gateFailure) {
            verdict = SafetyGate.Verdict.refuse(
                    "safety gate failed closed: " + gateFailure.getMessage());
        }
        if (verdict == null || !verdict.allowed() || verdict.needsConfirm()) {
            Entry e = new Entry(command, verdict, false, verdict.reason(), now);
            history.add(e);
            refusals.add(e);
            return e;
        }

        DeviceAdapter adapter = adapters.get(command.deviceId().toLowerCase(Locale.ROOT));
        if (adapter == null) {
            Entry e = new Entry(command, verdict, false, "no adapter for device '"
                    + command.deviceId() + "'", now);
            history.add(e);
            refusals.add(e);
            return e;
        }
        if (!adapter.supports(command)) {
            Entry e = new Entry(command, verdict, false, "adapter cannot perform '"
                    + command.action() + "'", now);
            history.add(e);
            refusals.add(e);
            return e;
        }
        DeviceAdapter.Outcome outcome = adapter.execute(command);
        Entry e = new Entry(command, verdict, outcome.succeeded(), outcome.detail(), now);
        history.add(e);
        return e;
    }

    /** Commands parked awaiting explicit confirmation, by fingerprint. */
    public Map<String, DeviceCommand> awaitingConfirmation() {
        return Map.copyOf(awaitingConfirmation);
    }

    /** Every command considered, with its outcome. */
    public List<Entry> history() {
        return List.copyOf(history);
    }

    /** Every command that did not execute, with the reason. */
    public List<Entry> refusals() {
        return List.copyOf(refusals);
    }

    /** Commands currently queued and awaiting dispatch. */
    public int queued() {
        return queue.size();
    }
}