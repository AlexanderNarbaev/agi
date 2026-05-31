package io.matrix.cluster;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import io.matrix.events.ClusterEvent;
import io.matrix.events.ClusterEventType;
import io.matrix.events.EventJournal;
import io.matrix.events.InMemoryEventJournal;
import io.matrix.neuron.TruthTable;
import io.matrix.snapshot.ClusterSnapshot;
import io.matrix.snapshot.SnapshotStore;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pekko Typed Actor managing a pool of MPDT neurons with batched inference,
 * Event Sourcing journal, and snapshot support.
 *
 * <p>Ref: L3_Neurocluster_Arch.md, L6_Memory.md §3
 */
public class NeuronClusterActor extends AbstractBehavior<NeuronClusterActor.Command> {

    private static final String DEFAULT_INSTANCE_ID = "instance-0";

    // --- Command protocol ---
    public sealed interface Command permits
            LoadNeuron, UnloadNeuron, FreezeNeuron,
            InjectSignal, EvaluateTick, GetMetrics, GetNeuronCount,
            CreateSnapshot, RestoreSnapshot {}

    public record LoadNeuron(NeuronId id, TruthTable table, NeuronInstance.State state,
                              ActorRef<Response> replyTo) implements Command {}

    public record UnloadNeuron(NeuronId id, ActorRef<Response> replyTo) implements Command {}

    public record FreezeNeuron(NeuronId id, ActorRef<Response> replyTo) implements Command {}

    public record InjectSignal(Signal signal) implements Command {}

    public record EvaluateTick(ActorRef<Response> replyTo) implements Command {}

    public record GetMetrics(ActorRef<Response> replyTo) implements Command {}

    public record GetNeuronCount(ActorRef<Response> replyTo) implements Command {}

    public record CreateSnapshot(Path storeDir, ActorRef<Response> replyTo)
            implements Command {}

    public record RestoreSnapshot(Path storeDir, ActorRef<Response> replyTo)
            implements Command {}

    // --- Response protocol ---
    public sealed interface Response permits
            NeuronLoaded, NeuronUnloaded, NeuronFrozen,
            TickResult, MetricsResult, CountResult,
            SnapshotCreated, SnapshotRestored, ErrorResponse {}

    public record NeuronLoaded(NeuronId id) implements Response {}
    public record NeuronUnloaded(NeuronId id) implements Response {}
    public record NeuronFrozen(NeuronId id) implements Response {}
    public record TickResult(int evaluated, int signalsEmitted) implements Response {}
    public record MetricsResult(int activeNeurons, int frozenNeurons,
                                 int bufferSize, long eventsLogged) implements Response {}
    public record CountResult(int count) implements Response {}
    public record SnapshotCreated(String snapshotId, Path filePath) implements Response {}
    public record SnapshotRestored(String snapshotId, int neuronCount) implements Response {}
    public record ErrorResponse(String message) implements Response {}

    // --- State ---
    private final Map<NeuronId, NeuronInstance> activeNeurons = new HashMap<>();
    private final SignalBuffer inputBuffer;
    private final SignalBuffer outputBuffer;
    private final EventJournal eventJournal;
    private final ClusterConfig config;
    private final String instanceId;
    private long tickCount;

    public static Behavior<Command> create(ClusterConfig config) {
        return create(config, new InMemoryEventJournal(), DEFAULT_INSTANCE_ID);
    }

    public static Behavior<Command> create(ClusterConfig config, EventJournal journal,
                                            String instanceId) {
        return Behaviors.setup(ctx -> new NeuronClusterActor(ctx, config, journal, instanceId));
    }

    public NeuronClusterActor(ActorContext<Command> context, ClusterConfig config,
                               EventJournal eventJournal, String instanceId) {
        super(context);
        this.config = config;
        this.eventJournal = eventJournal;
        this.instanceId = instanceId;
        this.inputBuffer = new SignalBuffer(config.signalBufferCapacity());
        this.outputBuffer = new SignalBuffer(config.signalBufferCapacity());
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(LoadNeuron.class, this::onLoadNeuron)
                .onMessage(UnloadNeuron.class, this::onUnloadNeuron)
                .onMessage(FreezeNeuron.class, this::onFreezeNeuron)
                .onMessage(InjectSignal.class, this::onInjectSignal)
                .onMessage(EvaluateTick.class, this::onEvaluateTick)
                .onMessage(GetMetrics.class, this::onGetMetrics)
                .onMessage(GetNeuronCount.class, this::onGetNeuronCount)
                .onMessage(CreateSnapshot.class, this::onCreateSnapshot)
                .onMessage(RestoreSnapshot.class, this::onRestoreSnapshot)
                .build();
    }

    private Behavior<Command> onLoadNeuron(LoadNeuron cmd) {
        if (activeNeurons.size() >= config.maxNeurons()) {
            if (cmd.replyTo() != null) {
                cmd.replyTo().tell(new ErrorResponse("Cluster full: " + config.maxNeurons()));
            }
            return this;
        }
        NeuronInstance neuron = new NeuronInstance(cmd.id(), cmd.table(), cmd.state());
        activeNeurons.put(cmd.id(), neuron);
        journal(ClusterEventType.NEURON_CREATED, cmd.id(),
                "k=" + cmd.table().k() + ", state=" + cmd.state());
        if (cmd.replyTo() != null) {
            cmd.replyTo().tell(new NeuronLoaded(cmd.id()));
        }
        return this;
    }

    private Behavior<Command> onUnloadNeuron(UnloadNeuron cmd) {
        NeuronInstance removed = activeNeurons.remove(cmd.id());
        if (removed != null) {
            journal(ClusterEventType.NEURON_REMOVED, cmd.id(), "unloaded");
        }
        cmd.replyTo().tell(new NeuronUnloaded(cmd.id()));
        return this;
    }

    private Behavior<Command> onFreezeNeuron(FreezeNeuron cmd) {
        NeuronInstance neuron = activeNeurons.get(cmd.id());
        if (neuron == null) {
            cmd.replyTo().tell(new ErrorResponse("Neuron not found: " + cmd.id()));
            return this;
        }
        if (neuron.isFrozen()) {
            cmd.replyTo().tell(new ErrorResponse("Already frozen: " + cmd.id()));
            return this;
        }
        NeuronInstance frozen = NeuronInstance.frozen(cmd.id(), neuron.truthTable());
        activeNeurons.put(cmd.id(), frozen);
        journal(ClusterEventType.NEURON_FROZEN, cmd.id(), "frozen");
        cmd.replyTo().tell(new NeuronFrozen(cmd.id()));
        return this;
    }

    private Behavior<Command> onInjectSignal(InjectSignal cmd) {
        inputBuffer.push(cmd.signal());
        return this;
    }

    private Behavior<Command> onEvaluateTick(EvaluateTick cmd) {
        tickCount++;
        int evaluated = evaluateBatch();
        int emitted = flushOutputs();
        cmd.replyTo().tell(new TickResult(evaluated, emitted));
        return this;
    }

    private int evaluateBatch() {
        List<Signal> incoming = new ArrayList<>();
        inputBuffer.drainTo(incoming);

        Map<NeuronId, BitSet> currentInputs = new HashMap<>();
        for (Signal s : incoming) {
            NeuronInstance neuron = activeNeurons.get(s.targetId());
            if (neuron != null && neuron.isMutable()) {
                BitSet input = currentInputs.computeIfAbsent(s.targetId(),
                        k -> new BitSet(neuron.k()));
                if (s.value()) {
                    input.set(0);
                }
            }
        }

        int evaluated = 0;
        for (var entry : currentInputs.entrySet()) {
            NeuronInstance neuron = activeNeurons.get(entry.getKey());
            if (neuron == null) continue;

            boolean result = longEvaluate(neuron.truthTable(), entry.getValue());
            if (result != neuron.lastOutput()) {
                neuron.setLastOutput(result);
                Signal out = new Signal(neuron.id(), neuron.id(), result);
                outputBuffer.push(out);
                evaluated++;
            }
        }
        return evaluated;
    }

    private boolean longEvaluate(TruthTable table, BitSet input) {
        long[] packed = input.toLongArray();
        return table.evaluate(packed);
    }

    private int flushOutputs() {
        List<Signal> outgoing = new ArrayList<>();
        int count = outputBuffer.drainTo(outgoing);
        for (Signal s : outgoing) {
            journal(ClusterEventType.SIGNAL_EMITTED, s.sourceId(),
                    "value=" + s.value());
        }
        return count;
    }

    private Behavior<Command> onGetMetrics(GetMetrics cmd) {
        int frozen = (int) activeNeurons.values().stream()
                .filter(NeuronInstance::isFrozen).count();
        cmd.replyTo().tell(new MetricsResult(
                activeNeurons.size(), frozen,
                inputBuffer.size(), eventJournal.size()));
        return this;
    }

    private Behavior<Command> onGetNeuronCount(GetNeuronCount cmd) {
        cmd.replyTo().tell(new CountResult(activeNeurons.size()));
        return this;
    }

    private Behavior<Command> onCreateSnapshot(CreateSnapshot cmd) {
        try {
            SnapshotStore store = new SnapshotStore(cmd.storeDir(), instanceId);
            ClusterSnapshot snapshot = store.createSnapshot(activeNeurons,
                    eventJournal.size());
            Path filePath = store.save(snapshot);
            journal(ClusterEventType.SNAPSHOT_CREATED,
                    new NeuronId(java.util.UUID.randomUUID(), 0),
                    "file=" + filePath.getFileName());
            cmd.replyTo().tell(new SnapshotCreated(snapshot.snapshotId(), filePath));
        } catch (IOException e) {
            cmd.replyTo().tell(new ErrorResponse("Snapshot creation failed: "
                    + e.getMessage()));
        }
        return this;
    }

    private Behavior<Command> onRestoreSnapshot(RestoreSnapshot cmd) {
        try {
            SnapshotStore store = new SnapshotStore(cmd.storeDir(), instanceId);
            ClusterSnapshot snapshot = store.loadLatest();
            if (snapshot == null) {
                cmd.replyTo().tell(new ErrorResponse("No snapshot found"));
                return this;
            }

            activeNeurons.clear();
            List<NeuronInstance> restored = store.restoreNeurons(snapshot);
            for (NeuronInstance neuron : restored) {
                activeNeurons.put(neuron.id(), neuron);
            }

            journal(ClusterEventType.SNAPSHOT_RESTORED,
                    new NeuronId(java.util.UUID.randomUUID(), 0),
                    "snapshotId=" + snapshot.snapshotId()
                    + ", neurons=" + restored.size());
            cmd.replyTo().tell(new SnapshotRestored(
                    snapshot.snapshotId(), restored.size()));
        } catch (IOException e) {
            cmd.replyTo().tell(new ErrorResponse("Snapshot restore failed: "
                    + e.getMessage()));
        }
        return this;
    }

    private void journal(ClusterEventType type, NeuronId neuronId, String payload) {
        eventJournal.append(ClusterEvent.of(type, instanceId, neuronId, payload));
    }
}
