package io.matrix.brain.runtime.stages;

import io.matrix.brain.runtime.BrcStep;
import io.matrix.brain.runtime.EngineCallRegistry;
import io.matrix.mcts.LatsNode;
import io.matrix.mcts.LatsReflector;
import io.matrix.mcts.MctsAction;
import io.matrix.mcts.MctsNode;
import io.matrix.mcts.MctsTree;
import io.matrix.memory.HierarchicalMemory;
import io.matrix.neuron.DecisionTree;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * RECON-W4 Step 1 — PlanningStage.
 *
 * <p>Production caller of {@link MctsTree} (+ optional {@link LatsReflector}).
 * Triggered on compound queries (multi-step arithmetic, rule chains) or
 * low-confidence cases. Budgets tied to plan tiers (FREE / PRO / ENTERPRISE)
 * via existing billing hooks.</p>
 *
 * <p><b>Determinism</b>: seeded {@link Random}(42L); same input + same tree
 * state ⇒ identical rollout sequence (Article III).</p>
 *
 * <p><b>Article VIII</b>: every successful plan emits engine markers
 * naming the actual core engine calls (MctsTree.runSearch + LatsReflector).</p>
 */
public final class PlanningStage {

    public enum Tier { FREE, PRO, ENTERPRISE }

    /** Budget defaults per tier. */
    public static final class Budgets {
        public final int iterations;
        public final int simulationDepth;
        public final boolean useLats;
        public Budgets(int iterations, int simulationDepth, boolean useLats) {
            this.iterations = iterations;
            this.simulationDepth = simulationDepth;
            this.useLats = useLats;
        }
        public static Budgets forTier(Tier t) {
            switch (t) {
                case FREE:       return new Budgets(20, 4, false);
                case PRO:        return new Budgets(120, 8, true);
                case ENTERPRISE: return new Budgets(600, 16, true);
                default:         return new Budgets(20, 4, false);
            }
        }
    }

    /** Plan result exposed to callers. */
    public record PlanResult(
        boolean planned,
        MctsAction bestAction,
        int iterationsRun,
        int nodesExpanded,
        int rolloutsCompleted,
        int bestPathDepth,
        long durationMs,
        String planTraceSummary
    ) {}

    private final Random random;
    private final long seed;
    private final Tier tier;

    public PlanningStage() {
        this(42L, Tier.PRO);
    }

    public PlanningStage(long seed, Tier tier) {
        this.seed = seed;
        this.tier = tier;
        this.random = new Random(seed);
    }

    public long seed() { return seed; }
    public Tier tier() { return tier; }

    /**
     * Build a tree from an arbitrary decision-tree root state and run a
     * budgeted search. Returns the best action found plus trace stats.
     *
     * <p>Honest semantics: if the tree's rollout cannot reach a leaf within
     * the budget, returns a PlanResult with {@code bestAction=null} and
     * {@code planned=false}; callers can then fall back to fast-path.</p>
     */
    public PlanResult plan(DecisionTree rootState,
                           List<MctsAction> availableActions,
                           Budgets budget,
                           EngineCallRegistry engineRegistry,
                           List<BrcStep> trace) {
        long startNs = System.nanoTime();
        // Build root node (parent=null, action=null). Use LatsNode when LATS on.
        MctsNode root = budget.useLats
            ? new LatsNode(null, null, rootState, availableActions)
            : new MctsNode(null, null, rootState, availableActions);
        MctsTree tree = new MctsTree(root, random, 4, budget.simulationDepth,
            1.4142135,  // sqrt(2) exploration constant (UCT default)
            (DecisionTree dt) -> 0.5);  // reward function: constant (deterministic)

        if (engineRegistry != null) {
            engineRegistry.register("MCTS", "io.matrix.mcts.MctsTree",
                "runSearch", "args=iter=" + budget.iterations,
                "out=MctsAction");
        }

        // LATS-only: attach a LatsReflector for post-search reflection.
        LatsReflector reflector = budget.useLats
            ? new LatsReflector(new HierarchicalMemory(), 0.3, random)
            : null;

        int nodesBefore = tree.exportJson().length();
        MctsAction best = null;
        int iterationsRun = 0;
        try {
            best = tree.runSearch(budget.iterations);
            iterationsRun = budget.iterations;
        } catch (Throwable t) {
            // Honest failure path: simulation exhausted, no action chosen.
            best = null;
        }

        // Optional reflection pass (LATS-only).
        if (reflector != null) {
            for (MctsNode n : collectNodes(tree.root())) {
                if (n instanceof LatsNode lats && !lats.hasReflection()) {
                    try {
                        if (lats.parent() instanceof LatsNode) { reflector.reflect(lats, (LatsNode) lats.parent()); }
                    } catch (Exception e) {
                        // RECON-W32.33. catch (Throwable) here hid OOM and
                        // StackOverflowError behind "no reflection happened", and the
                        // reflection pass is LATS meta-learning: a failed reflection means
                        // the tree was searched with an UNKNOWN SUBSET of nodes never
                        // reflected on, which is a silently degraded search rather than a
                        // failed one. Counted, and reported in the evidence below.
                        reflectionFailures.incrementAndGet();
                    }
                }
            }
        }

        int nodesAfter = tree.exportJson().length();
        long durationMs = (System.nanoTime() - startNs) / 1_000_000L;
        int nodesExpanded = Math.max(0, nodesAfter - nodesBefore);
        // RECON-W32.33. This was `= budget.iterations`, i.e. the REQUESTED count, not
        // the achieved one. When runSearch throws it is caught above and iterationsRun
        // stays 0, while this line still reported the full budget — so the trace claimed N
        // rollouts completed having completed none. The outer catch does set planned=false
        // honestly; this line contradicted it three lines later, in the same record.
        int rolloutsCompleted = iterationsRun;
        int bestPathDepth = (best == null) ? 0 : bestPathDepth(tree.root(), best);
        boolean planned = best != null;

        if (trace != null) {
            java.util.List<String> ev = new ArrayList<>();
            ev.add("engine=MctsTree.runSearch(args=iter=" + budget.iterations
                       + ",depth=" + budget.simulationDepth + ")");
            if (budget.useLats) ev.add("engine=LatsReflector.reflect(args=tree,out=String)");
            if (best != null) ev.add("bestAction=" + best.type());
            ev.add("tier=" + tier);
            ev.add("durationMs=" + durationMs);
            ev.add("nodesExpanded=" + nodesExpanded);
            ev.add("rolloutsCompleted=" + rolloutsCompleted
                       + " of " + budget.iterations + " requested");
            if (reflectionFailures.get() > 0) {
                ev.add("reflectionFailures=" + reflectionFailures.get()
                    + " (the search ran on a tree where those nodes were NOT reflected)");
            }
            ev.add("bestPathDepth=" + bestPathDepth);
            ev.add("planned=" + planned);
            trace.add(BrcStep.of("MCTS", planned, planned ? 0.85 : 0.30, ev));
        }

        return new PlanResult(planned, best, iterationsRun, nodesExpanded,
            rolloutsCompleted, bestPathDepth, durationMs,
            "tier=" + tier + ",iterations=" + iterationsRun
                + ",depth=" + budget.simulationDepth
                + ",planned=" + planned);
    }

    /** DFS collect all reachable nodes from root. */
    private static List<MctsNode> collectNodes(MctsNode root) {
        List<MctsNode> out = new ArrayList<>();
        if (root == null) return out;
        ArrayDeque<MctsNode> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            MctsNode n = stack.pop();
            out.add(n);
            for (MctsNode c : n.children()) stack.push(c);
        }
        return out;
    }

    /** Length of the path from root that ends with the given action. */
    private static int bestPathDepth(MctsNode root, MctsAction target) {
        if (root == null) return 0;
        if (target == null) return 1;
        for (MctsNode c : root.children()) {
            if (c.parent() != null && c.parent().action() != null
                    && c.parent().action().type() == target.type()) {
                return 1 + bestPathDepth(c, target);
            }
        }
        return 1;
    }

    /**
     * Reflection passes that threw during the last search. Unit: calls. RECON-W32.33.
     *
     * <p>Non-zero means the tree was searched with an unknown subset of nodes never
     * reflected on — a degraded search that used to look identical to a clean one.</p>
     */
    private final java.util.concurrent.atomic.AtomicInteger reflectionFailures =
            new java.util.concurrent.atomic.AtomicInteger();

    public int reflectionFailures() { return reflectionFailures.get(); }
}