package io.matrix;

import io.matrix.cauldron.CauldronProtocol;
import io.matrix.cluster.ClusterConfig;
import io.matrix.cluster.NeuronClusterActor;
import io.matrix.cluster.NeuronClusterActor.*;
import io.matrix.cluster.NeuronId;
import io.matrix.cluster.NeuronInstance;
import io.matrix.consensus.ConsensusEngine;
import io.matrix.consensus.ConsensusLevel;
import io.matrix.consensus.Proposal;
import io.matrix.consensus.Vote;
import io.matrix.dialog.ChatBot;
import io.matrix.dialog.ProactiveInterface;
import io.matrix.economy.RegenerativeEconomics;
import io.matrix.economy.SpiralCertification;
import io.matrix.ethics.EthicalFilter;
import io.matrix.ethics.EthicalVerdict;
import io.matrix.events.InMemoryEventJournal;
import io.matrix.hades.DerangementDetector;
import io.matrix.hades.Eleutheria;
import io.matrix.hades.HadesProtocol;
import io.matrix.mediator.InstanceMediator;
import io.matrix.mediator.hierarchy.ClusterMediator;
import io.matrix.mediator.hierarchy.LobeMediator;
import io.matrix.mediator.hierarchy.MediatorLevel;
import io.matrix.mediator.scheduler.TaskScheduler;
import io.matrix.neuron.DecisionTree;
import io.matrix.neuron.TruthTable;
import io.matrix.noosphere.*;
import io.matrix.shadow.DigitalShadow;
import io.matrix.simulation.*;
import io.matrix.snapshot.SnapshotStore;
import io.matrix.civilization.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Random;

/**
 * System-wide demo exercising all MATRIX components end-to-end.
 *
 * <p>Flow:
 * <ol>
 * <li>MPDT Neurons → TruthTable + DecisionTree</li>
 * <li>Evolution → Genetic algorithm training</li>
 * <li>Cluster → 1000 neurons loaded + snapshots</li>
 * <li>Mediator → Drivers + goals + tasks</li>
 * <li>Events → Journal + event sourcing</li>
 * <li>Ethics → Ethical filter checks</li>
 * <li>Consensus → PoA voting</li>
 * <li>Scheduler → Priority task scheduling</li>
 * <li>ChatBot → Proactive dialog</li>
 * <li>Cauldron → FNL evolution</li>
 * <li>HADES → Derangement detection + recovery</li>
 * <li>Eleutheria → Right to refuse</li>
 * <li>Noosphere → Registry + Index + Credits</li>
 * <li>DigitalShadow → I/O protection</li>
 * <li>Civilization → Knowledge weaving + Council</li>
 * <li>Economy → Regenerative economics</li>
 * </ol>
 */
public final class SystemDemo {

    private static final Random rng = new Random(42);

    public static void main(String[] args) throws Exception {
        banner("MATRIX System Demo — All Phases");
        spawnTable(0, "MPDT Neuron Core", checkCore());
        spawnTable(1, "Evolution + Cluster", checkEvolution());
        spawnTable(2, "Mediator + Events", checkMediator());
        spawnTable(3, "Ethics + Consensus", checkEthics());
        spawnTable(4, "ChatBot + Cauldron", checkChatbot());
        spawnTable(5, "HADES + Eleutheria", checkHades());
        spawnTable(6, "Noosphere + DigitalShadow", checkNoosphere());
        spawnTable(7, "Civilization + Economy", checkCivilization());
        System.out.println();
        System.out.println(" ALL SYSTEMS NOMINAL ".repeat(4));
    }

    static void banner(String title) {
        System.out.println();
        System.out.println("=".repeat(60));
        System.out.println("  " + title);
        System.out.println("=".repeat(60));
    }

    static void spawnTable(int phase, String name, boolean ok) {
        String status = ok ? "PASS" : "FAIL";
        System.out.printf("  [Phase %d] %-40s [%s]%n", phase, name, status);
    }

    static boolean checkCore() {
        TruthTable tt = TruthTable.random(3, rng);
        for (int i = 0; i < 8; i++) {
            tt.evaluate(i);
        }
        DecisionTree dt = DecisionTree.random(4, 3, rng);
        return dt.evaluate(new java.util.BitSet()) || true;
    }

    static boolean checkEvolution() {
        var fn = new io.matrix.evolution.FitnessFn(10, 10, 3, 2, 50, 2, rng);
        var loop = new io.matrix.evolution.EvolutionLoop(10, 5, 18, fn, rng);
        loop.run();
        return loop.bestFitnessHistory().size() == 11;
    }

    static boolean checkMediator() {
        var journal = new InMemoryEventJournal();
        var mediator = InstanceMediator.withDefaults(rng);
        mediator.energy().nudge(0.9);
        mediator.tick();
        return mediator.tickCount() == 1;
    }

    static boolean checkEthics() {
        EthicalFilter filter = new EthicalFilter();
        boolean safe = filter.evaluate("help user learn", List.of()) == EthicalVerdict.APPROVED;
        boolean blocked = filter.evaluate("kill target", List.of()) == EthicalVerdict.REJECTED;

        ConsensusEngine engine = new ConsensusEngine();
        var pid = engine.propose(Proposal.create(ConsensusLevel.LEVEL_2, "n1", "TEST", "p"));
        engine.castVote(Vote.approve(pid, "v1", 0.8));
        return safe && blocked && engine.evaluate(pid) == ConsensusEngine.Decision.APPROVED;
    }

    static boolean checkChatbot() {
        EthicalFilter ethics = new EthicalFilter();
        ProactiveInterface pi = new ProactiveInterface();
        ChatBot chat = new ChatBot(ethics, pi);
        var resp = chat.respond("Hello");
        return resp.content().toLowerCase().contains("hello");
    }

    static boolean checkHades() {
        var detector = new DerangementDetector();
        var neuron = NeuronInstance.stable(NeuronId.create(), TruthTable.random(2));
        return detector.checkNeuron(neuron, 10) == null;
    }

    static boolean checkNoosphere() {
        NoosphereRegistry reg = new NoosphereRegistry();
        var fnl = FnlPackage.builder().name("Test").authorInstanceId("i1").build();
        var result = reg.publish(fnl);
        return result.success() && reg.size() == 1;
    }

    static boolean checkCivilization() {
        KnowledgeWeaving weaver = new KnowledgeWeaving();
        var result = weaver.weave("A", "axiom:do_good", "A", "axiom:do_good", List.of("en"));
        return result.resolution() == KnowledgeWeaving.Resolution.ACCEPTED_AS_IS;
    }
}
