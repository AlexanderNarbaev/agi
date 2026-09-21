package io.matrix.mediator.hierarchy;

import io.matrix.cluster.NeuronClusterActor;
import io.matrix.cluster.NeuronId;
import io.matrix.mediator.DriverState;
import io.matrix.mediator.DriverType;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Cluster-level mediator as a Pekko Typed Actor.
 *
 * <p>Weight 0.5. Manages neuron loading/unloading, FNL lifecycle,
 * balance metrics, and communicates with InstanceMediator.
 *
 * <p>Ref: L4_Mediator.md §2.1
 */
public class ClusterMediator extends AbstractBehavior<ClusterMediator.Command> {

    public sealed interface Command
            permits Tick, ReceiveMessage, GetMetrics, GetActiveFnlCount {}

    public record Tick(ActorRef<ClusterMediator.Response> replyTo) implements Command {}

    public record ReceiveMessage(MediatorMessage msg,
                                   ActorRef<ClusterMediator.Response> replyTo) implements Command {}

    public record GetMetrics(ActorRef<ClusterMediator.Response> replyTo) implements Command {}

    public record GetActiveFnlCount(ActorRef<ClusterMediator.Response> replyTo) implements Command {}

    public sealed interface Response
            permits TickResult, MetricsResult, FnlCountResult, ErrorResponse {}

    public record TickResult(int actionsPerformed) implements Response {}
    public record MetricsResult(int activeFnls, int totalNeurons,
                                 double avgFitness) implements Response {}
    public record FnlCountResult(int count) implements Response {}
    public record ErrorResponse(String message) implements Response {}

    private final String id;
    private final MediatorLevel level = MediatorLevel.CLUSTER;
    private final String parentInstanceId;
    private final Random rng;
    private final List<LobeMediator> lobeMediators = new ArrayList<>();
    private final List<String> actionLog = new ArrayList<>();
    private long tickCount;
    private int activeFnls;
    private int totalNeurons;

    public ClusterMediator(ActorContext<Command> context, String id,
                            String parentInstanceId, Random rng) {
        super(context);
        this.id = id;
        this.parentInstanceId = parentInstanceId;
        this.rng = rng;
    }

    public static Behavior<Command> create(String id, String parentInstanceId, Random rng) {
        return Behaviors.setup(ctx -> new ClusterMediator(ctx, id, parentInstanceId, rng));
    }

    public void registerLobe(LobeMediator lobe) {
        lobeMediators.add(lobe);
        activeFnls++;
        totalNeurons += 10;
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(Tick.class, this::onTick)
                .onMessage(ReceiveMessage.class, this::onReceiveMessage)
                .onMessage(GetMetrics.class, this::onGetMetrics)
                .onMessage(GetActiveFnlCount.class, this::onGetActiveFnlCount)
                .build();
    }

    private Behavior<Command> onTick(Tick cmd) {
        tickCount++;
        int actions = 0;

        for (LobeMediator lobe : lobeMediators) {
            var lobeActions = lobe.tick();
            actions += lobeActions.size();
            actionLog.addAll(lobeActions);
        }

        actionLog.add("CLUSTER:" + id + ":tick=" + tickCount + ",lobes=" + lobeMediators.size());
        cmd.replyTo().tell(new TickResult(actions));
        return this;
    }

    private Behavior<Command> onReceiveMessage(ReceiveMessage cmd) {
        if (cmd.msg() instanceof MediatorMessage.Command msg) {
            actionLog.add("MSG:" + msg.action() + " from " + msg.sourceLevel());
            cmd.replyTo().tell(new TickResult(1));
        }
        return this;
    }

    private Behavior<Command> onGetMetrics(GetMetrics cmd) {
        cmd.replyTo().tell(new MetricsResult(activeFnls, totalNeurons, 0.5));
        return this;
    }

    private Behavior<Command> onGetActiveFnlCount(GetActiveFnlCount cmd) {
        cmd.replyTo().tell(new FnlCountResult(activeFnls));
        return this;
    }

    public String id() { return id; }
    public MediatorLevel level() { return level; }
    public String parentInstanceId() { return parentInstanceId; }
    public long tickCount() { return tickCount; }
    public List<String> actionLog() { return List.copyOf(actionLog); }
    public List<LobeMediator> lobeMediators() { return List.copyOf(lobeMediators); }
}
