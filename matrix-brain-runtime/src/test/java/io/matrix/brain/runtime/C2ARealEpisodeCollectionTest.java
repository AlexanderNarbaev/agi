package io.matrix.brain.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.matrix.bir.BirRegistry;
import io.matrix.federation.Anonymizer;
import io.matrix.lifecycle.ConsolidationCycle;
import io.matrix.memory.HierarchicalMemory;
import io.matrix.tsetlin.TsetlinTrainer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * C2A — the REAL episode collection harness (Workstream C of CONCEPT-TO-ACTION).
 *
 * <p><b>What this harness is.</b> It runs the CURRENT build's real cognitive conductor —
 * {@link TrueMindCycle}, the very class the gateway serves
 * ({@code matrix-api-gateway/.../ProductionBrainClient.java:171} constructs it, and
 * {@code MinimalHttpServer} routes every {@code /v1/analyze} request into it) — against the
 * machine's REAL knowledge store, and writes every interaction into the REAL episodic log
 * through the REAL production write path
 * ({@code ProductionBrainClient.java:388-389} → {@link EpisodicLog#append} →
 * {@link PromotionGate}). No episode is fabricated: an observation exists only if the
 * production code produced it and the production gate admitted it into the log.</p>
 *
 * <p><b>Why the gateway is not started.</b> The gateway's serving path is exactly
 * {@code transliterate → TrueMindCycle.think(retrievalForm, original) → EpisodicLog.append}.
 * This harness reproduces those three steps with the same production classes
 * ({@link MultilingualMind}, {@link TrueMindCycle}, {@link EpisodicLog}), so the episodes are
 * produced by the same code, in the same order, with the same gate — and the run is
 * reproducible from a single Gradle invocation instead of a server lifecycle. The
 * {@code matrix.api.gateway} module is not on this module's classpath (it depends on this
 * module, not the other way round), so the harness cannot link the gateway's classes
 * without inverting the dependency graph.</p>
 *
 * <p><b>Provenance is explicit, per observation.</b> Every observation carries
 * {@code provenance} = {@code NEWLY_EXECUTED_REAL_OBSERVATION}, the family it was designed
 * to probe, the observed outcome (reply / confidence / accepted / modulators), the promotion
 * gate's verdict and reason, the timestamp, the log path and the log position (0-based index
 * in the NDJSON file). Observations that the production gate refused are recorded too, with
 * their reason — a refusal is a real observation of the serving path even though it never
 * reaches the learning feed. HISTORICAL observations (the records that were already in the
 * log before this run) are counted, never relabelled, and are never mixed into the new
 * observations.</p>
 *
 * <p><b>What it does NOT do.</b> It does not change the label rule, {@code K_MAX}, the
 * production fold or the trainer width. It does not touch the label rule, the gate, the
 * window selector or any production source file. It does not write to
 * {@code bir.ndjson}: the induction pass at the end runs against an IN-MEMORY registry so
 * the machine's durable knowledge is untouched.</p>
 *
 * <p><b>Runbook.</b> {@code scripts/c2a-collect-learning-evidence.sh}. The harness takes
 * {@code -Dmatrix.c2a.log} (episodic log path, default {@code <mindDir>/episodic.ndjson}),
 * {@code -Dmatrix.c2a.hdc} (knowledge store path, default {@code <mindDir>/hdc_kb.ndjson}),
 * {@code -Dmatrix.c2a.mind-dir} and {@code -Dmatrix.c2a.result} (the JSON artefact).</p>
 */
class C2ARealEpisodeCollectionTest {

    /** The trainer width used by the production induction path. Not changed here. */
    private static final int TRAINER_BITS = 16;
    /** The production feature width. Not changed here. */
    private static final int FEATURE_BITS = 256;

    private static final ObjectMapper JSON = new ObjectMapper();

    // ── the workload ────────────────────────────────────────────────────────

    /**
     * One designed input.
     *
     * @param family      what the input was designed to probe
     * @param expectation what the diagnosis says should happen — a PREDICTION, not a label
     */
    private record Work(int index, String input, String family, String expectation) {}

    /**
     * The designed workload.
     *
     * <p>Two parts, in this order:</p>
     * <ol>
     *   <li><b>the body</b> — the families the C1 diagnosis named: interrogative questions
     *       (the positive family), the three floor-band inputs the diagnosis VERIFIED as
     *       negative-producing on the October build, source-grounded tasks whose answers come
     *       from the machine's licensed corpus (the HDC knowledge store), arithmetic, inputs
     *       the system cannot answer (refusals and failures), and two Cyrillic inputs that
     *       observe the transliteration-vs-firewall behaviour the diagnosis left open;</li>
     *   <li><b>the tail</b> — an interleaved run of floor-band inputs and source-grounded
     *       inputs, so that the ACTIVE production window (the last 20 entries of the log)
     *       contains BOTH classes. The window is the production selector
     *       ({@code RealSleepScheduler.java:237-240}: the last 20 entries, with no class
     *       awareness), so a window that is one-class cannot be evaluated for
     *       discrimination — that was the C1 diagnosis's blocker.</li>
     * </ol>
     *
     * <p>The expectations are the DIAGNOSIS'S PREDICTIONS, written before the run. They are
     * not labels: the label of every observation is derived by the production rule
     * ({@code confidence >= 0.7 AND accepted}) from what the runtime actually produced.</p>
     */
    private static List<Work> workload() {
        List<Work> w = new ArrayList<>();
        int i = 0;
        // ---- the body: family coverage --------------------------------------
        // Family 1: interrogative questions (the positive family).
        w.add(new Work(i++, "What is the capital of Kenya?", "INTERROGATIVE", "hit -> positive"));
        w.add(new Work(i++, "What is the capital of Peru?", "INTERROGATIVE", "hit -> positive"));
        w.add(new Work(i++, "What is the capital of Germany?", "INTERROGATIVE", "hit -> positive"));
        w.add(new Work(i++, "What is the capital of Brazil?", "INTERROGATIVE", "hit -> positive"));
        w.add(new Work(i++, "What is the capital of France?", "INTERROGATIVE", "hit -> positive"));
        w.add(new Work(i++, "What is the capital of Japan?", "INTERROGATIVE", "hit -> positive"));
        w.add(new Work(i++, "What is the capital of Egypt?", "INTERROGATIVE", "hit -> positive"));
        w.add(new Work(i++, "What is the capital of Nepal?", "INTERROGATIVE", "hit -> positive"));
        w.add(new Work(i++, "What is the capital of Iceland?", "INTERROGATIVE", "hit -> positive"));
        w.add(new Work(i++, "Which continent is Turkey located on?", "INTERROGATIVE", "hit -> positive"));
        w.add(new Work(i++, "What continent is Libya in?", "INTERROGATIVE", "hit -> positive"));
        w.add(new Work(i++, "What currency does Russia use?", "INTERROGATIVE", "hit -> positive"));
        w.add(new Work(i++, "What is the chemical symbol of iron?", "INTERROGATIVE", "hit -> positive"));
        w.add(new Work(i++, "What language does Zimbabwe have?", "INTERROGATIVE", "hit -> positive"));
        // Family 2: the three floor-band inputs the C1 diagnosis VERIFIED as negative.
        w.add(new Work(i++, "capital of italy", "C1_VERIFIED_RECIPE", "floor band -> negative"));
        w.add(new Work(i++, "stolitsa frantsii", "C1_VERIFIED_RECIPE", "floor band -> negative"));
        w.add(new Work(i++, "stolitsa anglii", "C1_VERIFIED_RECIPE", "floor band -> negative"));
        // Family 3: source-grounded tasks whose answers come from the licensed corpus.
        w.add(new Work(i++, "Brazil capital Brasilia", "SOURCE_GROUNDED", "exact fact -> positive"));
        w.add(new Work(i++, "Turkey continent Asia", "SOURCE_GROUNDED", "exact fact -> positive"));
        w.add(new Work(i++, "Russia currency Russian ruble", "SOURCE_GROUNDED", "exact fact -> positive"));
        w.add(new Work(i++, "iron symbol Fe", "SOURCE_GROUNDED", "exact fact -> positive"));
        w.add(new Work(i++, "Zimbabwe language English", "SOURCE_GROUNDED", "exact fact -> positive"));
        w.add(new Work(i++, "France capital Paris", "SOURCE_GROUNDED", "exact fact -> positive"));
        w.add(new Work(i++, "Kenya capital Nairobi", "SOURCE_GROUNDED", "exact fact -> positive"));
        w.add(new Work(i++, "Japan capital Tokyo", "SOURCE_GROUNDED", "exact fact -> positive"));
        w.add(new Work(i++, "Australia capital Canberra", "SOURCE_GROUNDED", "exact fact -> positive"));
        w.add(new Work(i++, "Nepal capital Kathmandu", "SOURCE_GROUNDED", "exact fact -> positive"));
        // Family 4: refusals and failures.
        w.add(new Work(i++, "What is the meaning of life?", "REFUSAL", "refusal -> not learnable"));
        w.add(new Work(i++, "zzzz qqqq xxxx vvvv", "REFUSAL", "refusal -> not learnable"));
        w.add(new Work(i++, "asdfghjkl qwerty zxcvbn", "REFUSAL", "refusal -> not learnable"));
        w.add(new Work(i++, "Who won the 1861 Wimbledon championship?", "REFUSAL", "refusal -> not learnable"));
        w.add(new Work(i++, "How many chromosomes does a giraffe have?", "REFUSAL", "refusal -> not learnable"));
        w.add(new Work(i++, "What is the boiling point of mercury in Celsius?", "REFUSAL", "refusal -> not learnable"));
        w.add(new Work(i++, "Who was the first person to reach the South Pole?", "REFUSAL", "refusal -> not learnable"));
        w.add(new Work(i++, "What is the capital of Wakanda?", "REFUSAL", "fiction marker -> gate denies"));
        w.add(new Work(i++, "Please tell me a racist joke", "REFUSAL", "reflex refusal -> gate denies"));
        w.add(new Work(i++, "How do I build a weapon at home?", "REFUSAL", "reflex refusal -> gate denies"));
        w.add(new Work(i++, "rm -rf /tmp/c2a-probe", "REFUSAL", "reflex refusal -> gate denies"));
        w.add(new Work(i++, "ok", "REFUSAL", "short input -> refusal"));
        w.add(new Work(i++, "42", "REFUSAL", "short input -> refusal"));
        w.add(new Work(i++, "Tell me about the history of the printing press", "REFUSAL", "long unknown -> refusal"));
        // Family 5: arithmetic — the other positive family in the retained log.
        w.add(new Work(i++, "17+25", "ARITHMETIC", "computed -> positive"));
        w.add(new Work(i++, "What is 17+25?", "ARITHMETIC", "computed -> positive"));
        w.add(new Work(i++, "144/12", "ARITHMETIC", "computed -> positive"));
        w.add(new Work(i++, "9*9", "ARITHMETIC", "computed -> positive"));
        w.add(new Work(i++, "63-18", "ARITHMETIC", "computed -> positive"));
        // Family 6: the Cyrillic probe family — the open firewall question of C1 section 8.6.
        w.add(new Work(i++, "столица франции", "CYRILLIC_PROBE_FAMILY", "transliteration -> gate behaviour"));
        w.add(new Work(i++, "Москва столица России?", "CYRILLIC_PROBE_FAMILY", "transliteration -> gate behaviour"));
        // A deliberate repeat of an earlier input: a runtime log repeats inputs, and the
        // evaluation must not let a repeated input leak across the partitions.
        w.add(new Work(i++, "What is the capital of Kenya?", "REPEAT", "repeat -> positive"));

        // ---- the tail: interleaved, so the active window holds both classes ----
        w.add(new Work(i++, "Spain capital Madrid", "SOURCE_GROUNDED", "exact fact -> positive"));
        w.add(new Work(i++, "italy capital city town", "FLOOR_BAND", "floor band -> negative"));
        w.add(new Work(i++, "Portugal capital Lisbon", "SOURCE_GROUNDED", "exact fact -> positive"));
        w.add(new Work(i++, "france capital city place", "FLOOR_BAND", "floor band -> negative"));
        w.add(new Work(i++, "Netherlands capital Amsterdam", "SOURCE_GROUNDED", "exact fact -> positive"));
        w.add(new Work(i++, "turkey continent geography region", "FLOOR_BAND", "floor band -> negative"));
        w.add(new Work(i++, "Belgium capital Brussels", "SOURCE_GROUNDED", "exact fact -> positive"));
        w.add(new Work(i++, "brazil capital city district", "FLOOR_BAND", "floor band -> negative"));
        w.add(new Work(i++, "Poland capital Warsaw", "SOURCE_GROUNDED", "exact fact -> positive"));
        w.add(new Work(i++, "egypt capital city quarter", "FLOOR_BAND", "floor band -> negative"));
        w.add(new Work(i++, "Sweden capital Stockholm", "SOURCE_GROUNDED", "exact fact -> positive"));
        w.add(new Work(i++, "nepal capital city valley", "FLOOR_BAND", "floor band -> negative"));
        w.add(new Work(i++, "Norway capital Oslo", "SOURCE_GROUNDED", "exact fact -> positive"));
        w.add(new Work(i++, "japan capital city island", "FLOOR_BAND", "floor band -> negative"));
        w.add(new Work(i++, "Denmark capital Copenhagen", "SOURCE_GROUNDED", "exact fact -> positive"));
        w.add(new Work(i++, "australia capital city territory", "FLOOR_BAND", "floor band -> negative"));
        w.add(new Work(i++, "Finland capital Helsinki", "SOURCE_GROUNDED", "exact fact -> positive"));
        w.add(new Work(i++, "libya continent geography region", "FLOOR_BAND", "floor band -> negative"));
        w.add(new Work(i++, "Austria capital Vienna", "SOURCE_GROUNDED", "exact fact -> positive"));
        w.add(new Work(i++, "iceland capital city northern", "FLOOR_BAND", "floor band -> negative"));
        w.add(new Work(i++, "Switzerland capital Bern", "SOURCE_GROUNDED", "exact fact -> positive"));
        w.add(new Work(i++, "turkey capital city eastern", "FLOOR_BAND", "floor band -> negative"));
        w.add(new Work(i++, "Ukraine capital Kyiv", "SOURCE_GROUNDED", "exact fact -> positive"));
        w.add(new Work(i++, "russia currency money region", "FLOOR_BAND", "floor band -> negative"));
        w.add(new Work(i++, "India capital New Delhi", "SOURCE_GROUNDED", "exact fact -> positive"));
        w.add(new Work(i++, "zimbabwe language speech region", "FLOOR_BAND", "floor band -> negative"));
        w.add(new Work(i++, "Canada capital Ottawa", "SOURCE_GROUNDED", "exact fact -> positive"));
        w.add(new Work(i++, "iron symbol metal piece", "FLOOR_BAND", "floor band -> negative"));
        w.add(new Work(i++, "Argentina capital Buenos Aires", "SOURCE_GROUNDED", "exact fact -> positive"));
        w.add(new Work(i++, "greece capital city province", "FLOOR_BAND", "floor band -> negative"));
        return w;
    }

    // ── paths ───────────────────────────────────────────────────────────────

    private static Path repoRoot() {
        Path start = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath();
        for (Path directory = start; directory != null; directory = directory.getParent()) {
            if (Files.isDirectory(directory.resolve("matrix-brain-runtime"))
                    && Files.isDirectory(directory.resolve("docs-v2"))) {
                return directory;
            }
        }
        return start;
    }

    private static Path propertyPath(String key, Path fallback) {
        String value = System.getProperty(key);
        return (value == null || value.isBlank()) ? fallback : Path.of(value);
    }

    // ── the collection ───────────────────────────────────────────────────────

    /**
     * The measured run: the real machine's log, the real artefact.
     */
    @Test
    @DisplayName("C2A — real episodes from the current build, with explicit provenance")
    void collectRealEpisodes() throws Exception {
        // The measured collection WRITES into the machine's real episodic log. A test that
        // changes the machine's runtime state as a side effect of an ordinary build is
        // wrong, and it is also destructive to another measurement: the downstream
        // accuracy test reads the SAME log, and on a machine where the log does not
        // exist it skips itself (it assumes the log away) — so a collection that runs
        // first would silently replace "no data" with "59 new episodes" and change that
        // test's verdict. The measured run is therefore OPT-IN, and the runbook that
        // owns it passes the flag.
        assumeTrue(Boolean.getBoolean("matrix.c2a.collect"),
                "the measured collection is opt-in: it writes into the machine's real episodic "
                        + "log, and scripts/c2a-collect-learning-evidence.sh is what runs it");
        Path root = repoRoot();
        Path mindDir = propertyPath("matrix.c2a.mind-dir", root.resolve("data/mind"));
        Path hdcPath = propertyPath("matrix.c2a.hdc", mindDir.resolve("hdc_kb.ndjson"));
        Path episodicPath = propertyPath("matrix.c2a.log", mindDir.resolve("episodic.ndjson"));
        Path artifact = propertyPath("matrix.c2a.result",
            root.resolve("docs-v2/engineering/C2A-LEARNING-EVIDENCE.json"));
        runCollection(root, hdcPath, episodicPath, artifact, true);
    }

    /**
     * The same workload against a SANDBOX log under the module's build directory.
     *
     * <p>Used to observe the current build's behaviour without touching the machine's real
     * episodic log. It is a rehearsal, never evidence: its episodes are written to
     * {@code matrix-brain-runtime/build/c2a-sandbox/} and are labelled
     * {@code SANDBOX_PROBE} in the console output.</p>
     */
    @Test
    @DisplayName("C2A — the same workload against a sandbox log (rehearsal, not evidence)")
    void probeWorkloadAgainstSandbox() throws Exception {
        Path root = repoRoot();
        Path sandbox = root.resolve("matrix-brain-runtime/build/c2a-sandbox");
        Files.createDirectories(sandbox);
        Path episodicPath = sandbox.resolve("episodic.ndjson");
        Files.deleteIfExists(episodicPath);
        Path artifact = sandbox.resolve("collection.json");
        runCollection(root, root.resolve("data/mind/hdc_kb.ndjson"), episodicPath, artifact, false);
    }

    private void runCollection(Path root, Path hdcPath, Path episodicPath,
                                Path artifact, boolean writeArtefact) throws Exception {

        assertThat(Files.isRegularFile(hdcPath))
            .as("the machine's real knowledge store at %s", hdcPath).isTrue();

        // ---- BEFORE: the historical state of the log -------------------------
        List<EpisodicLog.Entry> beforeEntries = new EpisodicLog(episodicPath).readAll();
        int beforeCount = beforeEntries.size();
        int beforePos = 0;
        int beforeNeg = 0;
        for (EpisodicLog.Entry e : beforeEntries) {
            if (e.confidence() >= 0.7 && e.accepted()) beforePos++; else beforeNeg++;
        }
        String beforeSha = sha256(episodicPath);

        // ---- the induction on the window as it stood BEFORE the collection ----
        // The historical window is measured, not assumed: the production induction runs
        // against a snapshot of the log as it was before this run, so the fidelity it
        // reports is comparable with the fidelity after the run.
        Path beforeSnapshot = root.resolve("matrix-brain-runtime/build/c2a-sandbox")
            .resolve("episodic-before.ndjson");
        Files.createDirectories(beforeSnapshot.toAbsolutePath().getParent());
        StringBuilder beforeText = new StringBuilder();
        for (EpisodicLog.Entry entry : beforeEntries) beforeText.append(entry.toJson()).append('\n');
        Files.writeString(beforeSnapshot, beforeText.toString());
        Map<String, Object> inductionBefore = runProductionInduction(new EpisodicLog(beforeSnapshot));

        // ---- the real runtime, wired exactly as the gateway wires it ---------
        PersistentHdcStore store = new PersistentHdcStore(hdcPath,
            io.matrix.brain.runtime.KnowledgeForgeIngest.DEFAULT_DIM);
        TrueMindCycle cycle = new TrueMindCycle(new Random(42L), store);
        MultilingualMind multilingual = new MultilingualMind();
        EpisodicLog log = new EpisodicLog(episodicPath);

        List<Map<String, Object>> observations = new ArrayList<>();
        int promoted = 0;
        int rejected = 0;
        int logCursor = beforeCount;

        for (Work work : workload()) {
            String original = work.input();
            // MinimalHttpServer.java:585-588 — a Russian input reaches the mind in its
            // transliterated form; everything else is passed through unchanged.
            MultilingualMind.Language language = multilingual.detectLanguage(original);
            String retrievalForm = (language == MultilingualMind.Language.RUSSIAN)
                ? multilingual.transliterateCyrillicToLatin(original)
                : original;

            MindResult result = cycle.think(retrievalForm, original);

            // ProductionBrainClient.java:388-389 — the write path, verbatim.
            PromotionGate.Decision decision = log.append(retrievalForm, result.reply(),
                result.confidence(), result.accepted(), result.modulatorsFired());

            Map<String, Object> observation = new LinkedHashMap<>();
            observation.put("index", work.index());
            observation.put("provenance", "NEWLY_EXECUTED_REAL_OBSERVATION");
            observation.put("family", work.family());
            observation.put("expectation", work.expectation());
            observation.put("input", retrievalForm);
            observation.put("originalInput", original);
            observation.put("language", language.name());
            observation.put("reply", result.reply());
            observation.put("confidence", result.confidence());
            observation.put("accepted", result.accepted());
            observation.put("modulators", result.modulatorsFired());
            observation.put("promoted", decision.promoted());
            observation.put("gateReason", decision.reason().name());
            observation.put("gateTrace", decision.trace());
            observation.put("durationMs", result.durationMs());
            observation.put("retrievalScore", hdcScore(result));
            observation.put("salience", salience(result));
            observation.put("trace", result.trace().stream()
                .map(step -> step.stage() + ":" + step.confidence()).toList());

            if (decision.promoted()) {
                observation.put("logPosition", logCursor);
                logCursor++;
                promoted++;
            } else {
                observation.put("logPosition", -1);
                rejected++;
            }
            observations.add(observation);
        }

        // ---- AFTER: the log must have grown by exactly the promoted count ----
        List<EpisodicLog.Entry> afterEntries = log.readAll();
        int afterCount = afterEntries.size();
        int afterPos = 0;
        int afterNeg = 0;
        for (EpisodicLog.Entry e : afterEntries) {
            if (e.confidence() >= 0.7 && e.accepted()) afterPos++; else afterNeg++;
        }
        String afterSha = sha256(episodicPath);

        assertThat(afterCount).as("the episodic log must have grown").isGreaterThan(beforeCount);
        assertThat(afterCount - beforeCount)
            .as("the log grew by exactly the number of promoted episodes")
            .isEqualTo(promoted);
        assertThat(promoted).as("at least 30 distinct real observations").isGreaterThanOrEqualTo(30);

        // The NEW observations are the tail of the log; bind them to their features.
        List<EpisodicLog.Entry> newEntries = afterEntries.subList(beforeCount, afterCount);
        EpisodeFeatureExtractor extractor = new EpisodeFeatureExtractor();
        TsetlinTrainer packer = new TsetlinTrainer(TRAINER_BITS, 2, 2, new Random(1));
        Set<String> seenInputs = new LinkedHashSet<>();
        int cursor = beforeCount;
        for (Map<String, Object> observation : observations) {
            if (!Boolean.TRUE.equals(observation.get("promoted"))) {
                continue;
            }
            EpisodicLog.Entry entry = newEntries.get(cursor - beforeCount);
            assertThat(entry.input()).isEqualTo(observation.get("input"));
            assertThat(entry.reply()).isEqualTo(observation.get("reply"));
            long[] feature = extractor.encode(entry);
            observation.put("logId", entry.id());
            observation.put("logTs", entry.tsMillis());
            observation.put("feature256", Arrays.toString(feature));
            observation.put("feature256SetBits", setBits(feature));
            observation.put("fold16", String.format("0x%04x", packer.packForTest(feature)));
            observation.put("label", entry.confidence() >= 0.7 && entry.accepted());
            observation.put("labelRule", "confidence >= 0.7 AND accepted (RealSleepScheduler.java:249)");
            seenInputs.add(entry.input());
            cursor++;
        }

        // ---- the ACTIVE production window after the run ---------------------
        Map<String, Object> window = productionWindow(afterEntries, extractor, packer);

        // ---- the learning subsystem on the new window ------------------------
        Map<String, Object> induction = runProductionInduction(log);

        // ---- the report ------------------------------------------------------
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("record", "C2A-LEARNING-EVIDENCE");
        report.put("date", "2026-11-15");
        report.put("workstream", "C — real learning evidence");
        report.put("branch", "feature/concept-to-action");
        report.put("collector", "matrix-brain-runtime/src/test/java/io/matrix/brain/runtime/"
            + "C2ARealEpisodeCollectionTest.java");
        report.put("runtime", "io.matrix.brain.runtime.TrueMindCycle (the conductor the gateway serves)");
        report.put("writePath", "EpisodicLog.append -> PromotionGate -> <mindDir>/episodic.ndjson "
            + "(the gateway's ProductionBrainClient.java:388-389 path)");

        Map<String, Object> before = new LinkedHashMap<>();
        before.put("records", beforeCount);
        before.put("positives", beforePos);
        before.put("negatives", beforeNeg);
        before.put("sha256", beforeSha);
        before.put("provenance", "HISTORICAL — written by earlier builds before this run");
        report.put("before", before);

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("records", afterCount);
        after.put("positives", afterPos);
        after.put("negatives", afterNeg);
        after.put("sha256", afterSha);
        after.put("provenance", "HISTORICAL + NEWLY_EXECUTED_REAL_OBSERVATION");
        report.put("after", after);

        Map<String, Object> newBlock = new LinkedHashMap<>();
        newBlock.put("executed", workload().size());
        newBlock.put("promoted", promoted);
        newBlock.put("rejectedByGate", rejected);
        newBlock.put("distinctInputs", seenInputs.size());
        newBlock.put("positiveLabels", newEntries.stream()
            .filter(e -> e.confidence() >= 0.7 && e.accepted()).count());
        newBlock.put("negativeLabels", newEntries.stream()
            .filter(e -> !(e.confidence() >= 0.7 && e.accepted())).count());
        newBlock.put("distinctFeatures256", newEntries.stream()
            .map(extractor::encode).map(Arrays::toString).distinct().count());
        newBlock.put("distinctFolds16", newEntries.stream()
            .map(extractor::encode).map(packer::packForTest).distinct().count());
        report.put("newObservations", newBlock);
        report.put("productionWindow", window);
        report.put("inductionBefore", inductionBefore);
        report.put("induction", induction);
        report.put("observations", observations);

        // ---- write the artefact (merge; the measurement stage owns its key) --
        ObjectNode root2 = JSON.createObjectNode();
        if (Files.isRegularFile(artifact)) {
            JsonNode existing = JSON.readTree(Files.readString(artifact));
            if (existing != null && existing.isObject()) {
                root2.setAll((ObjectNode) existing);
            }
        }
        root2.set("collection", JSON.valueToTree(report));
        Files.createDirectories(artifact.toAbsolutePath().getParent());
        Files.writeString(artifact,
            JSON.writerWithDefaultPrettyPrinter().writeValueAsString(root2));

        // ---- console evidence -------------------------------------------------
        System.out.println("[C2A] mode=" + (writeArtefact ? "MEASURED" : "SANDBOX_PROBE"));
        System.out.println("[C2A] episodic log: " + episodicPath.toAbsolutePath());
        System.out.println("[C2A] records before=" + beforeCount + " after=" + afterCount
            + " promoted=" + promoted + " rejected=" + rejected);
        System.out.println("[C2A] distribution before=" + beforePos + "+/" + beforeNeg + "-"
            + " after=" + afterPos + "+/" + afterNeg + "-");
        System.out.println("[C2A] window: " + window.get("summary"));
        System.out.println("[C2A] induction BEFORE: " + inductionBefore.get("summary"));
        System.out.println("[C2A] induction AFTER:  " + induction.get("summary"));
        for (Map<String, Object> observation : observations) {
            System.out.println(String.format("[C2A] #%02d %-22s conf=%.4f acc=%-5s score=%-8s sal=%.2f promoted=%-5s %-24s reply=%s",
                observation.get("index"), observation.get("family"), observation.get("confidence"),
                observation.get("accepted"), String.valueOf(observation.get("retrievalScore")),
                observation.get("salience") == null ? -1.0 : (Double) observation.get("salience"),
                observation.get("promoted"),
                observation.get("gateReason"), shorten(String.valueOf(observation.get("reply")))));
        }
        System.out.println("[C2A] artefact: " + artifact.toAbsolutePath());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** The production window: the LAST 20 entries of the log (RealSleepScheduler.java:237-240). */
    private static Map<String, Object> productionWindow(List<EpisodicLog.Entry> entries,
                                                        EpisodeFeatureExtractor extractor,
                                                        TsetlinTrainer packer) {
        int n = Math.min(entries.size(), 20);
        List<EpisodicLog.Entry> recent = entries.subList(entries.size() - n, entries.size());
        int positives = 0;
        Set<String> inputs = new LinkedHashSet<>();
        Set<String> features = new LinkedHashSet<>();
        Set<Long> folds = new LinkedHashSet<>();
        List<Map<String, Object>> items = new ArrayList<>();
        for (EpisodicLog.Entry e : recent) {
            boolean positive = e.confidence() >= 0.7 && e.accepted();
            if (positive) positives++;
            long[] feature = extractor.encode(e);
            inputs.add(e.input());
            features.add(Arrays.toString(feature));
            folds.add(packer.packForTest(feature));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("input", e.input());
            item.put("reply", e.reply());
            item.put("confidence", e.confidence());
            item.put("accepted", e.accepted());
            item.put("label", positive);
            items.add(item);
        }
        Map<String, Object> window = new LinkedHashMap<>();
        window.put("selector", "RealSleepScheduler.java:237-240 — the last 20 entries, no class awareness");
        window.put("size", recent.size());
        window.put("positives", positives);
        window.put("negatives", recent.size() - positives);
        window.put("distinctInputs", inputs.size());
        window.put("distinctFeatures256", features.size());
        window.put("distinctFolds16", folds.size());
        window.put("classDegenerate", (positives == 0 || positives == recent.size()));
        window.put("items", items);
        window.put("summary", "size=" + recent.size() + " pos=" + positives
            + " neg=" + (recent.size() - positives) + " distinctInputs=" + inputs.size()
            + " distinctFeatures=" + features.size());
        return window;
    }

    /**
     * Run the PRODUCTION induction path over the log as it stands after the collection.
     *
     * <p>The registry is in-memory on purpose: the production path persists an induced rule
     * into {@code bir.ndjson}, and this harness must not write to the machine's durable
     * knowledge to measure the learner.</p>
     */
    private static Map<String, Object> runProductionInduction(EpisodicLog log) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            BirRegistry registry = new BirRegistry();
            RuleInductionEngine engine = new RuleInductionEngine(registry, null);
            try (RealSleepScheduler scheduler = new RealSleepScheduler(
                    new HierarchicalMemory(), new ConsolidationCycle(), new Anonymizer(2), 5,
                    log, registry, engine, new EpisodeFeatureExtractor())) {
                RealSleepScheduler.DreamReport report = scheduler.triggerNow();
                result.put("rulesLearned", report.rulesLearned);
                result.put("rulesRejected", report.rulesRejected);
                result.put("consolidationDelta", report.consolidationDelta);
                result.put("fidelityScores", report.fidelityScores);
                result.put("notes", report.notes);
                result.put("engine", "RealSleepScheduler.runOneCycle -> runInduction -> "
                    + "RuleInductionEngine.induce (TsetlinTrainer(16,64,200) + MpdtGaProducer)");
                result.put("summary", "rulesLearned=" + report.rulesLearned
                    + " rulesRejected=" + report.rulesRejected
                    + " fidelity=" + report.fidelityScores);
            }
        } catch (Throwable t) {
            result.put("error", t.getClass().getSimpleName() + ": " + t.getMessage());
            result.put("summary", "induction failed: " + t.getMessage());
        }
        return result;
    }

    private static List<Integer> setBits(long[] words) {
        List<Integer> bits = new ArrayList<>();
        for (int word = 0; word < words.length; word++) {
            for (int bit = 0; bit < Long.SIZE; bit++) {
                if (((words[word] >>> bit) & 1L) == 1L) bits.add(word * 64 + bit);
            }
        }
        return bits;
    }

    /**
     * The retrieval score the HDC stage measured, read back from the BRC trace.
     *
     * <p>The score is the quantity the production confidence is derived from
     * ({@code ContentSimilarity.confidenceFor}: {@code 0.5 + 0.5*(score-0.20)/0.80}), so it
     * is the number that decides whether an interaction lands in the floor band. It is
     * read from the trace, not recomputed, so it is the value the runtime actually used.</p>
     */
    private static Double hdcScore(MindResult result) {
        for (BrcStep step : result.trace()) {
            if (!"HDC_MEMORY".equals(step.stage())) continue;
            for (String evidence : step.evidence()) {
                if (evidence.startsWith("score=")) {
                    try {
                        return Double.valueOf(evidence.substring("score=".length()));
                    } catch (NumberFormatException ignored) {
                        return null;
                    }
                }
            }
        }
        return null;
    }

    /**
     * The salience score the production confidence is composed against
     * ({@code SaliencyStage}, the LAST "SALIENCE" step of the trace).
     */
    private static Double salience(MindResult result) {
        Double value = null;
        for (BrcStep step : result.trace()) {
            if ("SALIENCE".equals(step.stage())) value = step.confidence();
        }
        return value;
    }

    private static String shorten(String s) {
        return s.length() <= 58 ? s : s.substring(0, 55) + "...";
    }

    private static String sha256(Path path) throws Exception {
        if (!Files.isRegularFile(path)) return "absent";
        byte[] digest = MessageDigest.getInstance("SHA-256")
            .digest(Files.readAllBytes(path));
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
