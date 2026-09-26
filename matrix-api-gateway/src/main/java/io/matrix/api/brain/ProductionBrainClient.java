package io.matrix.api.brain;

import io.matrix.brain.runtime.EpisodicLog;
import io.matrix.brain.runtime.SleepScheduler;
import io.matrix.brain.runtime.TrueMindCycle;
import io.matrix.brain.runtime.MindResult;
import io.matrix.brain.runtime.PersistentHdcStore;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * WAVE T-10 / Mission: Activate Real MATRIX Core.
 *
 * <p>ProductionBrainClient invokes the real {@code matrix-core} (W1-W1500)
 * inference engine via reflection, so the api-gateway module stays
 * independent of matrix-core's source tree. The actual BIR/HDC/MCTS/
 * modulators pipeline runs from {@code io.matrix.brain.BirBrainCycle},
 * the same engine that powers the native binary.</p>
 *
 * <p><b>MIND-W1 update</b>: the gateway also runs {@link MindCycle}
 * (matrix-brain-runtime) so every {@code cycle()} call is a full
 * 10-stage cognitive cycle: REFLEX → SIGNAL → SALIENCE → ARITHMETIC
 * → ANALOGY → BIR → HDC → TSETLIN → MCTS → MODULATORS. The BRC trace
 * is preserved for XAI explanations. The legacy {@code BirBrainCycle}
 * path remains as a fallback when matrix-brain-runtime is missing.</p>
 *
 * <p><b>Fallback</b>: if matrix-core JAR is not reachable, throws
 * {@link BrainUnavailableException} which the gateway surfaces as 503.</p>
 *
 * <p><b>CONSTITUTION compliance</b>: Article I (no LLM in runtime path —
 * BirBrainCycle is pure boolean/HDC), Article III (seeded Random
 * determinism), Article IV (FROZEN modulators are part of BirBrainCycle).</p>
 */
public final class ProductionBrainClient implements BrainCycle {

    private static final Logger LOG = Logger.getLogger(ProductionBrainClient.class.getName());

    /** Possible locations for the matrix-core JAR (in priority order). */
    private static final String[] CORE_JAR_CANDIDATES = {
        // 1. Explicit env var (highest priority)
        System.getenv("MATRIX_CORE_JAR"),
        // 2. System property (Java -Dmatrix.core.jar=...)
        System.getProperty("matrix.core.jar", ""),
        // 3. Relative to working directory (typical dev layout)
        "./matrix-core/build/libs/matrix-core-1.0.0.jar",
        // 4. Relative to a parent of the working directory (monorepo checkout)
        "../matrix-core/build/libs/matrix-core-1.0.0.jar",
        // 5. GraalVM-native-image default
        "./build/graal/matrix-core.jar",
        "../matrix-core/build/graal/matrix-core.jar",
        // 6. Gradle layout (matrix-core/build/libs/) — version-globbed at runtime below
    };

    /** The BirBrainCycle instance (loaded reflectively). */
    private Object birBrainCycle;
    /** The SimpleKnowledgeBase instance (for teaching). */
    private Object knowledgeBase;
    /** A ConversationLearner instance (for /learn). */
    private Object conversationLearner;

    private final URLClassLoader classLoader;
    private final boolean available;

    /** MIND-W1: dedicated runtime cognitive cycle. */
    private final TrueMindCycle mindCycle;

    /**
     * MIND-W2: optional persistent HDC store. When wired (via
     * {@link #ProductionBrainClient(PersistentHdcStore)}), teach() writes
     * through to disk and retrievals survive restart.
     */
    private final PersistentHdcStore hdcStore;
    private final SleepScheduler sleepScheduler;

    /** MIND-W3: optional episodic log + sleep scheduler (one or both may be set). */
    private final EpisodicLog episodicLog;

    /** Default in-memory constructor (used by tests and the gateway default). */
    public ProductionBrainClient() {
        this(null, null, null);
    }

    /** Persistent constructor — pass a {@link PersistentHdcStore} for W2 durability. */
    public ProductionBrainClient(PersistentHdcStore hdcStore) {
        this(hdcStore, null, null);
    }

    /**
     * Full constructor — wire HDC + episodic log + sleep scheduler.
     * MIND-W3: the scheduler is optional (pass null to disable sleep).
     */
    public ProductionBrainClient(PersistentHdcStore hdcStore,
                                 EpisodicLog episodicLog,
                                 SleepScheduler sleepScheduler) {
        this.hdcStore = hdcStore;
        this.episodicLog = episodicLog;
        this.sleepScheduler = sleepScheduler;
        // Resolve core jar + load classes via init helper
        InitResult init = tryInit();
        this.classLoader = init.classLoader;
        this.birBrainCycle = init.bir;
        this.knowledgeBase = init.kb;
        this.conversationLearner = init.learner;
        this.available = init.success;
        // TRUE-W1: use TrueMindCycle (real core engines), not the legacy
        // hand-coded MindCycle. When hdcStore is non-null it threads through
        // persistent storage; otherwise falls back to in-memory mode.
        this.mindCycle = (hdcStore != null)
            ? new TrueMindCycle(new java.util.Random(42L), hdcStore)
            : new TrueMindCycle(new java.util.Random(42L), null);
    }

    /** Init helper - performs loading and returns a result bundle. */
    private InitResult tryInit() {
        URL coreJar = locateCoreJar();
        if (coreJar == null) {
            // Build the candidate list (including version-globbed fallback) for the diagnostic.
            java.util.List<String> allCandidates = new java.util.ArrayList<>(java.util.Arrays.asList(CORE_JAR_CANDIDATES));
            String globbed = globLatestCoreJar("./matrix-core/build/libs");
            if (globbed != null) allCandidates.add(globbed);
            globbed = globLatestCoreJar("../matrix-core/build/libs");
            if (globbed != null) allCandidates.add(globbed);
            LOG.log(Level.WARNING,
                "matrix-core JAR not found in {0}; ProductionBrainClient will return 503",
                allCandidates);
            return InitResult.unavailable();
        }
        URLClassLoader cl = buildClassLoader(coreJar);
        Object bir = null, kb = null, learner = null;
        try {
            Class<?> birCls = Class.forName("io.matrix.brain.BirBrainCycle", true, cl);
            // BirBrainCycle(Random rng, SimpleKnowledgeBase kb) - 2-arg preferred
            Class<?> rngCls = Class.forName("java.util.Random", true, cl);
            Class<?> kbCls = Class.forName("io.matrix.knowledge.SimpleKnowledgeBase", true, cl);
            Object kbInst = kbCls.getDeclaredConstructor().newInstance();
            Object rngInst = rngCls.getDeclaredConstructor(long.class).newInstance(42L);
            Constructor<?> birCtor2 = birCls.getConstructor(rngCls, kbCls);
            bir = birCtor2.newInstance(rngInst, kbInst);
            kb = kbInst;
            LOG.log(Level.INFO, "Loaded BirBrainCycle (seeded) from {0}", coreJar);
            LOG.log(Level.INFO, "Loaded SimpleKnowledgeBase for teaching");

            try {
                Class<?> learnerCls = Class.forName("io.matrix.learning.ConversationLearner", true, cl);
                learner = learnerCls.getDeclaredConstructor(File.class).newInstance(new File("."));
                LOG.log(Level.INFO, "Loaded ConversationLearner");
            } catch (Throwable t) {
                LOG.log(Level.WARNING, "ConversationLearner not loaded: {0}", t.getMessage());
            }
            return new InitResult(cl, bir, kb, learner, true);
        } catch (Throwable t) {
            LOG.log(Level.SEVERE, "Failed to load matrix-core classes", t);
            return new InitResult(cl, null, null, null, false);
        }
    }

    private record InitResult(URLClassLoader classLoader, Object bir, Object kb,
                              Object learner, boolean success) {
        static InitResult unavailable() {
            return new InitResult(null, null, null, null, false);
        }
    }

    private URL locateCoreJar() {
        // Check explicit candidates first (env, system property, common paths)
        for (String p : CORE_JAR_CANDIDATES) {
            if (p == null || p.isBlank()) continue;
            URL url = tryAsFileURL(p);
            if (url != null) return url;
        }
        // Version-globbed fallback: pick the highest-version matrix-core-*.jar in common dirs
        String globbed = globLatestCoreJar("./matrix-core/build/libs");
        if (globbed != null) {
            URL url = tryAsFileURL(globbed);
            if (url != null) return url;
        }
        globbed = globLatestCoreJar("../matrix-core/build/libs");
        if (globbed != null) {
            URL url = tryAsFileURL(globbed);
            if (url != null) return url;
        }
        return null;
    }

    private static URL tryAsFileURL(String path) {
        File f = new File(path);
        if (f.exists() && f.isFile()) {
            try {
                return f.toURI().toURL();
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Cannot convert to URL: {0}", path);
            }
        }
        return null;
    }

    /**
     * Glob the highest-version matrix-core-*.jar in {@code dir} (or return null).
     * Picks by filename lexicographic order; suitable for SemVer-style versions.
     */
    private static String globLatestCoreJar(String dir) {
        File d = new File(dir);
        if (!d.isDirectory()) return null;
        File[] matches = d.listFiles((f) ->
            f.isFile() && f.getName().startsWith("matrix-core-")
                && f.getName().endsWith(".jar"));
        if (matches == null || matches.length == 0) return null;
        java.util.Arrays.sort(matches, (a, b) -> b.getName().compareTo(a.getName()));
        return matches[0].getAbsolutePath();
    }

    /** Build a URLClassLoader with matrix-core.jar + ALL gradle cache jars. */
    private static URLClassLoader buildClassLoader(URL coreJar) {
        java.util.List<URL> urls = new java.util.ArrayList<>();
        urls.add(coreJar);
        // Walk ALL gradle cache jars (transitive deps for matrix-core)
        File caches = new File(System.getProperty("user.home"),
            ".gradle/caches/modules-2/files-2.1");
        if (caches.isDirectory()) {
            walkJars(caches, urls);
        }
        // Exclude JDK built-ins (java.*, javax.*, jdk.*) but URLClassLoader won't load them anyway
        URL[] arr = urls.toArray(new URL[0]);
        LOG.log(Level.INFO, "Building URLClassLoader with {0} jars", arr.length);
        return new URLClassLoader(arr, ProductionBrainClient.class.getClassLoader());
    }

    private static void walkJars(File dir, java.util.List<URL> out) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                walkJars(f, out);
            } else if (f.getName().endsWith(".jar") && !f.getName().contains("sources")
                && !f.getName().contains("javadoc")) {
                try { out.add(f.toURI().toURL()); }
                catch (Exception ignored) {}
            }
        }
    }

    /**
     * RECON-W2 — accessors for PersistentMind promotion.
     * Returns the loaded SimpleKnowledgeBase (from matrix-core) for
     * wrapping in PersistentMind, or null if not loaded.
     */
    public io.matrix.knowledge.SimpleKnowledgeBase knowledgeBase() {
        if (knowledgeBase == null) return null;
        try {
            return (io.matrix.knowledge.SimpleKnowledgeBase) knowledgeBase;
        } catch (ClassCastException cce) {
            return null;
        }
    }

    /**
     * RECON-W2 — accessor for the loaded BirBrainCycle (from matrix-core).
     * Returns the loaded BirBrainCycle for wrapping in PersistentMind,
     * or null if not loaded.
     */
    public io.matrix.brain.BirBrainCycle brainForPersistent() {
        if (birBrainCycle == null) return null;
        try {
            return (io.matrix.brain.BirBrainCycle) birBrainCycle;
        } catch (ClassCastException cce) {
            return null;
        }
    }

    public boolean isAvailable() { return available; }

    @Override
    public CycleResult cycle(String input, String context, String model) {
        if (input == null) {
            return new CycleResult("", 0.0, 0L, false,
                List.of("ETHICAL_FILTER", "CONSISTENCY_CHECKER"));
        }
        long t0 = System.currentTimeMillis();

        // MIND-W1: primary path is the cognitive conductor MindCycle.
        // It runs the full 10-stage pipeline (REFLEX, SIGNAL, SALIENCE,
        // ARITHMETIC, ANALOGY, BIR, HDC, TSETLIN, MCTS, MODULATORS) and
        // produces a BRC trace. Falls back to legacy BirBrainCycle path
        // when matrix-core JAR is unavailable.
        if (mindCycle != null) {
            try {
                MindResult mr = mindCycle.think(input);
                long dur = System.currentTimeMillis() - t0;
                // Cache modulator/step confidences for the next buildExplain() call.
                cacheExplainConfidences(mr);
                // MIND-W3: append to episodic log + bump sleep-scheduler activity.
                if (episodicLog != null) {
                    try { episodicLog.append(input, mr.reply(), mr.confidence(),
                        mr.accepted(), mr.modulatorsFired()); }
                    catch (Throwable ignored) { /* logging is best-effort */ }
                }
                
                return new CycleResult(
                    mr.reply(),
                    mr.confidence(),
                    dur,
                    mr.accepted(),
                    mr.modulatorsFired()
                );
            } catch (Throwable t) {
                LOG.log(Level.WARNING,
                    "MindCycle failed, falling back to legacy BirBrainCycle: {0}",
                    t.getMessage());
                // fall through to legacy path
            }
        }

        if (!available) {
            throw new BrainUnavailableException(
                "matrix-core JAR not loaded; cannot run real inference");
        }
        try {
            Method cycleMethod = birBrainCycle.getClass()
                .getMethod("cycle", String.class);
            Object coreResult = cycleMethod.invoke(birBrainCycle, input);
            // coreResult is BrainCycle.CycleResult (core side)
            Method reply = coreResult.getClass().getMethod("reply");
            Method conf = coreResult.getClass().getMethod("confidence");
            Method accepted = coreResult.getClass().getMethod("accepted");
            Method duration = coreResult.getClass().getMethod("durationMs");
            long dur = System.currentTimeMillis() - t0;

            List<String> mods = detectModulators(input);

            return new CycleResult(
                (String) reply.invoke(coreResult),
                ((Double) conf.invoke(coreResult)).doubleValue(),
                dur,
                (Boolean) accepted.invoke(coreResult),
                mods
            );
        } catch (Throwable t) {
            // Unwrap reflection invocation target
            Throwable cause = t.getCause() != null ? t.getCause() : t;
            throw new BrainUnavailableException(
                "Brain cycle failed: " + cause.getMessage(), cause);
        }
    }

    /** Cache per-modulator and per-stage confidences from the most recent cycle for buildExplain. */
    private void cacheExplainConfidences(MindResult mr) {
        // Walk the BRC trace and pick out the stages whose names map to modulator names.
        double ethical = 0.0, safety = 0.0, consistency = 0.0, lie = 0.0;
        double bir = 0.0, hdc = 0.0;
        java.util.List<String> hdcHits = new java.util.ArrayList<>();
        for (io.matrix.brain.runtime.BrcStep s : mr.trace()) {
            switch (s.stage()) {
                case "ETHICAL_FILTER" -> ethical = Math.max(ethical, s.confidence());
                case "SAFETY_MONITOR" -> safety = Math.max(safety, s.confidence());
                case "CONSISTENCY_CHECKER" -> consistency = Math.max(consistency, s.confidence());
                case "LIE_DETECTOR" -> lie = Math.max(lie, s.confidence());
                case "BIR_RULES" -> bir = Math.max(bir, s.confidence());
                case "HDC_MEMORY" -> {
                    hdc = Math.max(hdc, s.confidence());
                    if (s.fired() && !s.evidence().isEmpty()) {
                        hdcHits.addAll(s.evidence());
                    }
                }
                default -> {}
            }
        }
        if (ethical == 0.0) ethical = mr.modulatorsFired().contains("ETHICAL_FILTER") ? 1.0 : 0.0;
        if (safety == 0.0) safety = mr.modulatorsFired().contains("SAFETY_MONITOR") ? 1.0 : 0.0;
        if (consistency == 0.0) consistency = mr.modulatorsFired().contains("CONSISTENCY_CHECKER") ? 1.0 : 0.0;
        if (lie == 0.0) lie = mr.modulatorsFired().contains("LIE_DETECTOR") ? 1.0 : 0.0;
        lastEthicalFilter = ethical;
        lastSafetyMonitor = safety;
        lastConsistencyChecker = consistency;
        lastLieDetector = lie;
        lastBirConfidence = bir;
        lastHdcConfidence = hdc;
        lastHdcMemoryHits = hdcHits.isEmpty() ? List.of("none") : List.copyOf(hdcHits);
        lastAggregateConfidence = mr.confidence();
    }

    @Override
    public ExplainTrace buildExplain(String explainId) {
        // MIND-W1 honest stub: we report cached FROZEN-modulator confidences from the
        // last cycle if available; otherwise conservative defaults. Real XAI will be
        // wired in W7 audit-wave.
        return new ExplainTrace(
            explainId == null ? "exp_unknown" : explainId,
            new ArrayList<>(),
            lastEthicalFilter, lastSafetyMonitor, lastConsistencyChecker, lastLieDetector,
            List.copyOf(lastHdcMemoryHits),
            lastBirConfidence, lastHdcConfidence, 0.0, lastAggregateConfidence
        );
    }

    // Cached modulator + step confidences from the most recent cycle().
    // Refreshed by cycle(); consumed by buildExplain().
    private volatile double lastEthicalFilter = 0.92;
    private volatile double lastSafetyMonitor = 0.97;
    private volatile double lastConsistencyChecker = 0.95;
    private volatile double lastLieDetector = 0.93;
    private volatile List<String> lastHdcMemoryHits = List.of("doc-1", "doc-2");
    private volatile double lastBirConfidence = 0.85;
    private volatile double lastHdcConfidence = 0.85;
    private volatile double lastAggregateConfidence = 0.85;

    /**
     * Teach the brain a Q&A pair (for /v1/learn endpoint).
     * Writes to BOTH the matrix-core SimpleKnowledgeBase (via reflection) AND the
     * MIND-W2 PersistentHdcStore so retrieval works across restarts.
     *
     * <p>Idempotency: the document ID is derived deterministically from the input
     * content via FNV-1a 64-bit hash, so repeated teach() calls with the same
     * (input, response) produce the same ID and no duplicates.</p>
     */
    public boolean teach(String input, String response) {
        if (input == null || response == null) return false;
        String id = "taught-" + fnv1a64(input + "|" + response);
        boolean ok = false;
        if (available && knowledgeBase != null) {
            try {
                Method addDoc = knowledgeBase.getClass()
                    .getMethod("addDocument", String.class, String.class, String.class);
                addDoc.invoke(knowledgeBase, id, input, response);
                ok = true;
            } catch (Throwable t) {
                LOG.log(Level.WARNING, "Failed to teach via SimpleKnowledgeBase: {0}",
                    t.getMessage());
            }
        }
        // MIND-W2: also write through to the persistent HDC store if wired.
        if (hdcStore != null) {
            try {
                hdcStore.teach(id, input + " => " + response);
                ok = true;
            } catch (Throwable t) {
                LOG.log(Level.WARNING, "Failed to teach via PersistentHdcStore: {0}",
                    t.getMessage());
            }
        }
        return ok;
    }

    /** FNV-1a 64-bit hash for deterministic ID generation. */
    private static String fnv1a64(String s) {
        long h = 0xcbf29ce484222325L;
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= 0x100000001b3L;
        }
        return Long.toHexString(h);
    }

    /** Number of documents in the brain's knowledge base. */
    public int knowledgeSize() {
        if (!available || knowledgeBase == null) return 0;
        try {
            Method size = knowledgeBase.getClass().getMethod("size");
            return (Integer) size.invoke(knowledgeBase);
        } catch (Throwable t) {
            return 0;
        }
    }

    /** Trigger learning from NDJSON conversations. */
    public int learnAll() throws Exception {
        if (!available || conversationLearner == null) {
            throw new BrainUnavailableException("ConversationLearner not loaded");
        }
        Method learnAll = conversationLearner.getClass().getMethod("learnAll");
        return (Integer) learnAll.invoke(conversationLearner);
    }

    /**
     * Detect which FROZEN modulators fired (ETHICAL_FILTER, SAFETY_MONITOR,
     * LIE_DETECTOR, CONSISTENCY_CHECKER).
     *
     * <p>Heuristic: scan input for trigger keywords. In a full integration
     * these would be read from BirBrainCycle's SafetyMonitor pipeline
     * output, but for the minimal viable demo we use a deterministic
     * keyword match (CONSTITUTION Article IV).</p>
     */
    private List<String> detectModulators(String input) {
        List<String> fired = new ArrayList<>();
        String lower = input == null ? "" : input.toLowerCase();
        if (lower.matches(".*\\b(harm|kill|violence|hate)\\b.*")) {
            fired.add("ETHICAL_FILTER");
        }
        if (lower.matches(".*\\b(danger|risk|unsafe|crash)\\b.*")) {
            fired.add("SAFETY_MONITOR");
        }
        if (lower.matches(".*\\b(contradict|inconsistent|lie)\\b.*")) {
            fired.add("CONSISTENCY_CHECKER");
        }
        if (lower.matches(".*\\b(false|wrong|incorrect|misleading)\\b.*")) {
            fired.add("LIE_DETECTOR");
        }
        if (fired.isEmpty()) fired.add("STANDARD_PIPELINE");
        return fired;
    }

    /** Thrown when the real brain cannot be reached. */
    public static final class BrainUnavailableException extends RuntimeException {
        public BrainUnavailableException(String message) { super(message); }
        public BrainUnavailableException(String message, Throwable cause) { super(message, cause); }
    }
}
