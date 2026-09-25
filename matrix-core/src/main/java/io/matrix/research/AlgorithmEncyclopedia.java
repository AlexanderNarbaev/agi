package io.matrix.research;

import java.util.*;

/**
 * W1376 — Algorithm Encyclopedia.
 *
 * Deep dives into every algorithm used in MATRIX.
 * Math, History, Pros/Cons, Implementation links.
 */
public final class AlgorithmEncyclopedia {

    public record AlgorithmEntry(
            String name,
            String category,
            String math,
            String history,
            String pros,
            String cons,
            String implementationLink
    ) {}

    private final Map<String, AlgorithmEntry> algorithms = new LinkedHashMap<>();

    public AlgorithmEncyclopedia() {
        registerDefaults();
    }

    private void registerDefaults() {
        algorithms.put("BIR", new AlgorithmEntry(
            "Boolean Inference for Rules",
            "Logic",
            "F(X) = ∧ᵢ (literalᵢ ∨ ¬literalᵢ)",
            "Originated in 1950s with propositional logic. Used in expert systems (MYCIN, 1976). Modern SAT solvers handle millions of clauses.",
            "Fast (sub-ms), explainable, deterministic, no training data needed",
            "Limited to propositional logic, doesn't handle uncertainty",
            "../matrix-core/src/main/java/io/matrix/bir/BirCompiler.java"
        ));

        algorithms.put("HDC", new AlgorithmEntry(
            "Hyperdimensional Computing",
            "Pattern Recognition",
            "v ⊗ w = element-wise XOR; sim(v,w) = (2·agree - D) / D",
            "Proposed by Kanerva (1988, Sparse Distributed Memory). Recent revival with trillion-bit vectors (2020s).",
            "Energy-efficient, noise-robust, learns from few examples (50 not 50,000)",
            "High memory consumption (mitigated by sparse representations)",
            "../matrix-core/src/main/java/io/matrix/neuron/HdcBrain.java"
        ));

        algorithms.put("MCTS", new AlgorithmEntry(
            "Monte Carlo Tree Search",
            "Planning",
            "UCB1 = avg_reward + c · sqrt(ln(N) / n)",
            "Developed by Rémi Coulom (2006). Famous for AlphaGo (2016) defeating world champion Lee Sedol.",
            "Effective in large state spaces, no domain knowledge needed",
            "Computationally expensive per simulation",
            "../matrix-core/src/main/java/io/matrix/federation/liquid/MCTSPlanner.java"
        ));

        algorithms.put("Stigmergy", new AlgorithmEntry(
            "Stigmergy Protocol",
            "Swarm Coordination",
            "S(t+1) = S(t) · (1 - δ); aggregate = Σ Sᵢ",
            "Discovered by Pierre-Paul Grassé (1959) studying termite nest building. Digital implementations: Ant Colony Optimization (Dorigo, 1992).",
            "Emergent coordination without central control, scalable to 1000+ nodes",
            "Convergence speed depends on pheromone decay rate tuning",
            "../matrix-core/src/main/java/io/matrix/federation/liquid/biochemistry/StigmergyProtocol.java"
        ));

        algorithms.put("FFT", new AlgorithmEntry(
            "Fast Fourier Transform",
            "Signal Processing",
            "X[k] = Σₙ x[n] · e^(-2πi·k·n/N)",
            "Discovered by Cooley & Tukey (1965). Reduces DFT from O(N²) to O(N log N).",
            "Exact, explainable, well-understood mathematics",
            "Assumes signal is stationary; not optimal for non-stationary signals",
            "../matrix-core/src/main/java/io/matrix/transcoders/AudioFFTEncoder.java"
        ));

        algorithms.put("Sobel", new AlgorithmEntry(
            "Sobel Edge Detection",
            "Vision Processing",
            "G = √(Gx² + Gy²); Gx = [-1 0 1; -2 0 2; -1 0 1]",
            "Developed by Irwin Sobel (1968). Cornerstone of classical computer vision.",
            "Simple, fast, 100% explainable, no training required",
            "Sensitive to noise, limited to edge detection (no semantic understanding)",
            "../matrix-core/src/main/java/io/matrix/transcoders/VisionEdgeEncoder.java"
        ));

        algorithms.put("BiochemicalNetwork", new AlgorithmEntry(
            "Non-Linear Biochemical Network",
            "Adaptive Behavior",
            "ΔMᵢ = Σⱼ wᵢⱼ · Mⱼ · (1 + α · Mᵢ · Mⱼ)",
            "Inspired by real neurochemistry (dopamine, cortisol, serotonin). Ashby's Law of Requisite Variety (1956).",
            "Creates emergent mood states, realistic stress responses, adaptive behavior",
            "Requires careful tuning of interaction weights",
            "../matrix-core/src/main/java/io/matrix/federation/liquid/biochemistry/BiochemicalNetwork.java"
        ));
    }

    public AlgorithmEntry get(String name) {
        return algorithms.get(name);
    }

    public Set<String> listAlgorithms() {
        return algorithms.keySet();
    }

    public Collection<AlgorithmEntry> getAll() {
        return algorithms.values();
    }
}
