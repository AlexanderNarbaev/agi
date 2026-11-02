package io.matrix.cognitive;

import io.matrix.cognitive.algorithms.AlgorithmRegistry;
import io.matrix.cognitive.algorithms.AlgorithmSelector;
import io.matrix.cognitive.algorithms.CostLedger;
import io.matrix.cognitive.algorithms.Portfolio;
import io.matrix.cognitive.microworld.LampEnvironment;
import io.matrix.cognitive.microworld.MicroWorldEnvironment;
import io.matrix.cognitive.microworld.MicroWorldSession;
import io.matrix.knowledge.KnowledgeWorkbench;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.nio.file.Paths;

/**
 * W44 — the bean that owns the cognitive kernel and everything it needs.
 *
 * <p>The kernel is a plain object, not a bean: it takes its collaborators as constructor
 * arguments, which is what makes it testable without a container. This service is the one place
 * that knows how to build it.</p>
 *
 * <p>Everything is created lazily on the first use, because the kernel opens the knowledge
 * store's file and the application must not touch that file until something asks it to. The
 * store it opens is the SAME file the workbench uses, so a case written by the kernel is visible
 * to the workbench's Epistemic panel and survives a restart.</p>
 */
@ApplicationScoped
public class CognitiveService {

    @Inject
    KnowledgeWorkbench workbench;

    private CognitiveStore store;
    private CognitiveKernel kernel;
    private CognitiveSolver solver;

    /** The kernel, built on first use. */
    public synchronized CognitiveKernel kernel() {
        if (kernel == null) {
            kernel = build();
        }
        return kernel;
    }

    /** The solver over the algorithm portfolio, built on first use. */
    public synchronized CognitiveSolver solver() {
        if (solver == null) {
            kernel();
        }
        return solver;
    }

    /** The cognitive store, opened on first use. */
    public synchronized CognitiveStore store() {
        if (store == null) {
            store = new CognitiveStore(Paths.get(workbench.databasePath()));
        }
        return store;
    }

    private CognitiveKernel build() {
        MicroWorldEnvironment environment = new LampEnvironment();
        MicroWorldSession.Factory sessionFactory = new MicroWorldSession.DefaultFactory(environment);
        // ONE registry holds the whole portfolio and the two micro-world methods, and ONE
        // ledger holds the measured costs of all of them. A method's cost is only comparable
        // with another method's cost when they were measured the same way.
        CostLedger ledger = new CostLedger();
        AlgorithmRegistry registry = Portfolio.defaultRegistry()
                .register(new MicroWorldReferenceMethod())
                .register(new MicroWorldSettledModelMethod());
        this.solver = new CognitiveSolver(new AlgorithmSelector(registry, ledger), ledger);
        return new CognitiveKernel(
                workbench,
                store(),
                sessionFactory,
                environment,
                new KernelSelector(registry, ledger),
                new KernelRegulator());
    }

    /**
     * Runs one cognitive cycle for a payload.
     *
     * @param payload the assignment to reason about
     * @return the outcome
     */
    public CognitiveOutcome observe(String payload) {
        CognitiveSignal signal = CognitiveSignal.structured("sig-" + payload.hashCode(),
                payload, "the cognitive endpoint", nextSequence());
        return kernel().process(signal);
    }

    private long sequence;

    private synchronized long nextSequence() {
        sequence++;
        return sequence;
    }

    @PreDestroy
    void shutdown() {
        if (store != null) {
            store.close();
            store = null;
        }
        kernel = null;
    }
}
