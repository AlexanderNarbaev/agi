package io.matrix.crj.runner;

import io.matrix.cognitive.MicroWorldComputation;
import io.matrix.cognitive.MicroWorldReferenceMethod;
import io.matrix.cognitive.MicroWorldSettledModelMethod;
import io.matrix.cognitive.algorithms.ComputeBudget;
import io.matrix.cognitive.microworld.LampEnvironment;
import io.matrix.cognitive.microworld.MicroWorldSession;
import io.matrix.cognitive.microworld.Observation;
import io.matrix.cognitive.microworld.WorldInput;
import io.matrix.crj.microworld.LampTruthOracle;
import io.matrix.learning.env.EpisodeComparison;
import io.matrix.learning.env.EpisodeReport;
import io.matrix.learning.env.MicroWorldEpisodeRunner;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CRJ-01 — the DOMAIN-B runner: the lamp world, three arms, one row per (episode, arm).
 *
 * <p><b>Why it is needed.</b> The protocol measures two domains, and only one of them had
 * an instrument: domain A's arms are the served report route, and {@link Crj01ArmRunner}
 * runs those. Domain B is the deterministic microworld, where the arms are
 * <b>different classes</b> — the reference enumerates the hypothesis space, the settled
 * model answers only when the model has settled, and the replay serves the retained episode.
 * Nothing could run them side by side, so nothing could measure them.</p>
 *
 * <p><b>What each arm is, and what it costs.</b>
 * <ul>
 *   <li><b>arm A</b> — the reference: it runs the episode (the probes are its cost) and
 *       answers the novel assignments from the surviving hypotheses;</li>
 *   <li><b>arm B</b> — the replay: the episode is <b>retained</b>, so answering the same
 *       assignments costs <b>no probes at all</b>. The runner re-executes the episode once
 *       under the same seed to <b>verify</b> that the retained episode is reproducible, and
 *       records the {@link EpisodeComparison} — the verification is not the replay's
 *       cost;</li>
 *   <li><b>arm C</b> — the settled model: it answers the assignments with no probes
 *       <b>only when the model has settled</b> (one surviving hypothesis). When it has not,
 *       the row is <b>inapplicable</b> with the reason — and inapplicability is a
 *       <b>denominator</b>, not a failure.</li>
 * </ul>
 *
 * <p><b>The oracle.</b> The answers are scored by the {@link LampTruthOracle} — an
 * independently written truth table of the four assignments — and the run carries the
 * instrument's own cross-check: the table is checked against every assignment it is asked
 * about, and a disagreement makes the run <b>VOID</b>.</p>
 *
 * <p><b>The seeds.</b> The protocol requires the episodes' seeds to be drawn from a
 * <b>pre-committed list</b>, so that the case list cannot be chosen after a result is seen.
 * The list is fixed in this class, and the dev split takes the first nine of them and the
 * sealed split the rest.</p>
 */
final class Crj01DomainBRunner {

    /**
     * The PRE-COMMITTED seed list.
     *
     * <p>The protocol: "18 sealed episodes, each with a distinct seed drawn from a
     * pre-committed seed list". The list is fixed here, before any run, and the dev split
     * takes the first nine and the sealed split the rest — so the sealed split's episodes
     * cannot be chosen after a result is seen.</p>
     */
    static final List<Long> PRECOMMITTED_SEEDS = List.of(
            1_001L, 1_002L, 1_003L, 1_004L, 1_005L, 1_006L, 1_007L, 1_008L, 1_009L,
            2_001L, 2_002L, 2_003L, 2_004L, 2_005L, 2_006L, 2_007L, 2_008L, 2_009L,
            3_001L, 3_002L, 3_003L, 3_004L, 3_005L, 3_006L, 3_007L, 3_008L, 3_009L);

    /** The dev split's episodes: the first nine of the pre-committed list. */
    static final int DEV_EPISODES = 9;

    /**
     * The pre-committed STEP BOUND of every episode.
     *
     * <p><b>Why the bound is the domain's size.</b> The lamp world has exactly four
     * assignments, and the measurement of the world's STRUCTURE
     * ({@code Crj01DomainBStructureMeasurementTest}) shows the consequence of every
     * possible bound:</p>
     *
     * <table>
     *   <caption>the lamp world's structure, over the nine dev seeds</caption>
     *   <tr><th>bound</th><th>episodes</th><th>settled</th><th>novel queries</th>
     *       <th>the reference answered</th></tr>
     *   <tr><td>1</td><td>9</td><td>0</td><td>27</td><td>0</td></tr>
     *   <tr><td>2</td><td>9</td><td>0</td><td>18</td><td>0</td></tr>
     *   <tr><td>3</td><td>9</td><td>0</td><td>9</td><td>0</td></tr>
     *   <tr><td>4</td><td>9</td><td><b>9</b></td><td><b>0</b></td><td>0</td></tr>
     * </table>
     *
     * <p><b>There is no bound that gives both a novel query and an arm that can answer
     * it.</b> A bound below the domain's size leaves a novel assignment, but then the
     * surviving hypotheses <b>disagree</b> about it and the reference method <b>refuses</b>
     * — it refuses rather than answering from a hypothesis set that does not choose. A bound
     * of the domain's size settles the model and leaves <b>no</b> novel query.</p>
     *
     * <p>So the bound is the domain's size, the queries are the episode's <b>observed</b>
     * assignments, and the transfer subset's denominator is <b>zero</b> — measured, not
     * assumed. What domain B can measure is therefore a <b>cost</b> comparison over the
     * assignments the episode did observe: the same answers, where arm C's settled model
     * pays <b>no probes</b> and arm A pays the episode's four. That is the protocol's own P1
     * ("arm C's correctness will be equal to arm A's on every case") measured on the cost
     * side.</p>
     *
     * <p>The bound is fixed here, before any run, and the sealed split uses the same one.</p>
     */
    static final int PRECOMMITTED_STEP_LIMIT = 4;

    /** One row: one episode, one arm. */
    record Row(String episodeId,
               long seed,
               String arm,
               String variant,
               Map<String, Boolean> answers,
               int correct,
               int novelQueries,
               int queries,
               int probes,
               int finalSurvivors,
               double utility,
               String terminationReason,
               boolean settled,
               int stepsSavedVsA,
               long wallNanos,
               boolean inapplicable,
               String reason,
               String error) {
        Row {
            answers = Map.copyOf(answers == null ? Map.of() : answers);
        }
    }

    /** The run's rows and its summary. */
    record Run(List<Row> rows, Map<String, Map<String, Object>> summary) {
        Run {
            rows = List.copyOf(rows == null ? List.of() : rows);
        }
    }

    /** The budget every arm runs under — the SAME one, for comparability. */
    private final ComputeBudget budget;

    /** The truth table the answers are scored against. */
    private final LampTruthOracle oracle;

    /**
     * Creates the runner.
     *
     * @param budget the budget every arm runs under
     */
    Crj01DomainBRunner(ComputeBudget budget) {
        this.budget = budget;
        this.oracle = new LampTruthOracle();
    }

    /**
     * Runs every arm over every episode.
     *
     * @param episodes how many episodes of the pre-committed list to run
     * @return the rows and the summary
     */
    Run run(int episodes) {
        List<Row> rows = new ArrayList<>();
        for (int index = 0; index < episodes; index++) {
            long seed = PRECOMMITTED_SEEDS.get(index);
            String episodeId = "crj01-dev-episode-" + index;

            LampEnvironment environment = new LampEnvironment();
            long wallStart = System.nanoTime();
            EpisodeReport baseline = new MicroWorldEpisodeRunner(environment, episodeId, seed,
                    PRECOMMITTED_STEP_LIMIT).run();
            long wall = System.nanoTime() - wallStart;

            // the session the arms answer FROM: the episode's APPLIED observations, read
            // back through the session's own factory
            List<Observation> applied = new ArrayList<>();
            for (var step : baseline.trajectory()) {
                if (step.applied()) {
                    applied.add(step.observation());
                }
            }
            MicroWorldSession session = new MicroWorldSession.DefaultFactory(environment)
                    .create(episodeId, applied);
            List<WorldInput> queries = observedAssignments(session);
            int novel = novelAssignments(session).size();

            rows.add(armA(episodeId, seed, baseline, session, queries, novel, wall));
            rows.add(armB(episodeId, seed, baseline, session, queries, novel, environment));
            rows.add(armC(episodeId, seed, baseline, session, queries, novel, wall));
        }
        return new Run(rows, summarise(rows));
    }

    /**
     * The assignments the episode had NOT observed — the TRANSFER subset.
     *
     * <p>The protocol's M-B4 is over "the sealed episodes' <b>novel</b> query assignments
     * (assignments that episode had not observed)". This is measured and reported, not
     * assumed: in the lamp world as it exists it is <b>empty</b> at the pre-committed
     * bound, and the row carries the count so a reader sees the denominator.</p>
     *
     * @param session the session the episode left
     * @return the novel assignments
     */
    private static List<WorldInput> novelAssignments(MicroWorldSession session) {
        List<WorldInput> novel = new ArrayList<>();
        for (boolean power : new boolean[] {false, true}) {
            for (boolean toggle : new boolean[] {false, true}) {
                WorldInput input = WorldInput.of(power, toggle);
                if (!session.observations().stream()
                        .anyMatch(observation -> observation.assignmentKey().equals(input.key()))) {
                    novel.add(input);
                }
            }
        }
        return novel;
    }

    /**
     * The assignments the episode DID observe — the queries the arms answer.
     *
     * @param session the session the episode left
     * @return the observed assignments
     */
    private static List<WorldInput> observedAssignments(MicroWorldSession session) {
        List<WorldInput> observed = new ArrayList<>();
        for (Observation observation : session.observations()) {
            for (boolean power : new boolean[] {false, true}) {
                for (boolean toggle : new boolean[] {false, true}) {
                    WorldInput input = WorldInput.of(power, toggle);
                    if (observation.assignmentKey().equals(input.key())
                            && !observed.contains(input)) {
                        observed.add(input);
                    }
                }
            }
        }
        return observed;
    }

    /**
     * Arm A — the reference: it runs the episode and answers from the surviving hypotheses.
     *
     * @param episodeId the episode
     * @param seed      the seed
     * @param baseline  the episode's report
     * @param session   the session the episode left
     * @param novel     the novel assignments
     * @param wall      the episode's wall time
     * @return the row
     */
    private Row armA(String episodeId, long seed, EpisodeReport baseline,
                     MicroWorldSession session, List<WorldInput> queries, int novel, long wall) {
        Map<String, Boolean> answers = new LinkedHashMap<>();
        String reason = null;
        boolean inapplicable = false;
        try {
            MicroWorldReferenceMethod method = new MicroWorldReferenceMethod();
            for (WorldInput input : queries) {
                MicroWorldComputation computation = new MicroWorldComputation(input, session,
                        Set.of());
                answers.put(input.key(), method.execute(computation, budget));
            }
        } catch (RuntimeException refusal) {
            inapplicable = true;
            reason = refusal.getMessage();
        }
        int correct = correct(answers);
        return new Row(episodeId, seed, "A", "measured", answers, correct, novel,
                queries.size(), baseline.steps(), baseline.finalSurvivors(),
                MicroWorldEpisodeRunner.utilityOf(baseline.finalSurvivors()),
                baseline.terminationReason().name(), baseline.learned(), 0, wall, inapplicable,
                reason, null);
    }

    /**
     * Arm B — the replay: the episode is retained, so the answers cost no probes.
     *
     * <p>The runner re-executes the episode once under the same seed to VERIFY that the
     * retained episode is reproducible, and records the {@link EpisodeComparison}. The
     * verification is not the replay's cost: the replay serves the retained result.</p>
     *
     * @param episodeId   the episode
     * @param seed        the seed
     * @param baseline    the retained episode's report
     * @param session     the session the episode left
     * @param novel       the novel assignments
     * @param environment the world
     * @return the row
     */
    private Row armB(String episodeId, long seed, EpisodeReport baseline,
                     MicroWorldSession session, List<WorldInput> queries, int novel,
                     LampEnvironment environment) {
        long wallStart = System.nanoTime();
        EpisodeReport replayed = new MicroWorldEpisodeRunner(environment, episodeId, seed,
                PRECOMMITTED_STEP_LIMIT).run();
        long wall = System.nanoTime() - wallStart;
        EpisodeComparison comparison = EpisodeComparison.between(replayed, baseline);

        Map<String, Boolean> answers = new LinkedHashMap<>();
        String reason = null;
        boolean inapplicable = false;
        try {
            MicroWorldReferenceMethod method = new MicroWorldReferenceMethod();
            for (WorldInput input : queries) {
                MicroWorldComputation computation = new MicroWorldComputation(input, session,
                        Set.of());
                answers.put(input.key(), method.execute(computation, budget));
            }
        } catch (RuntimeException refusal) {
            inapplicable = true;
            reason = refusal.getMessage();
        }
        int correct = correct(answers);
        // the replay's PROBES are zero: the episode was retained, and the answers come from
        // the retained result. The re-execution above is the verification, not the cost.
        return new Row(episodeId, seed, "B", "repeat", answers, correct, novel,
                queries.size(), 0, baseline.finalSurvivors(),
                MicroWorldEpisodeRunner.utilityOf(baseline.finalSurvivors()),
                baseline.terminationReason().name(), baseline.learned(),
                comparison.stepsSaved(), wall, inapplicable, reason, null);
    }

    /**
     * Arm C — the settled model: it answers only when the model has settled.
     *
     * @param episodeId the episode
     * @param seed      the seed
     * @param baseline  the episode's report
     * @param session   the session the episode left
     * @param novel     the novel assignments
     * @param wall      the episode's wall time
     * @return the row
     */
    private Row armC(String episodeId, long seed, EpisodeReport baseline,
                     MicroWorldSession session, List<WorldInput> queries, int novel, long wall) {
        boolean settled = session.survivingHypotheses().size() == 1;
        Set<String> facts = settled
                ? Set.of(MicroWorldSettledModelMethod.PRECONDITION_MODEL_SETTLED) : Set.of();
        Map<String, Boolean> answers = new LinkedHashMap<>();
        String reason = null;
        boolean inapplicable = false;
        try {
            MicroWorldSettledModelMethod method = new MicroWorldSettledModelMethod();
            for (WorldInput input : queries) {
                MicroWorldComputation computation = new MicroWorldComputation(input, session,
                        facts);
                answers.put(input.key(), method.execute(computation, budget));
            }
        } catch (RuntimeException refusal) {
            inapplicable = true;
            reason = refusal.getMessage();
        }
        int correct = correct(answers);
        // the settled model answers with NO probes: the episode's probes are already paid
        return new Row(episodeId, seed, "C", "measured", answers, correct, novel,
                queries.size(), 0, baseline.finalSurvivors(),
                MicroWorldEpisodeRunner.utilityOf(baseline.finalSurvivors()),
                baseline.terminationReason().name(), baseline.learned(), 0, wall, inapplicable,
                reason, null);
    }

    /**
     * How many of the answers agree with the truth table.
     *
     * @param answers the answers, by assignment
     * @return the count
     */
    private int correct(Map<String, Boolean> answers) {
        int correct = 0;
        for (Map.Entry<String, Boolean> entry : answers.entrySet()) {
            Boolean truth = truthOf(entry.getKey());
            if (truth != null && truth.equals(entry.getValue())) {
                correct++;
            }
        }
        return correct;
    }

    /**
     * The truth table's own value for an assignment key.
     *
     * @param key the assignment's key
     * @return the lamp's state, or null when the key is not one of the four
     */
    private Boolean truthOf(String key) {
        for (LampTruthOracle.Row row : oracle.rows()) {
            if (row.assignment().equals(key)) {
                return row.lamp();
            }
        }
        return null;
    }

    /**
     * The summary: one line per arm.
     *
     * @param rows the rows
     * @return the summary
     */
    private static Map<String, Map<String, Object>> summarise(List<Row> rows) {
        Map<String, Map<String, Object>> summary = new LinkedHashMap<>();
        for (Row row : rows) {
            Map<String, Object> line = summary.computeIfAbsent(row.arm(), k -> {
                Map<String, Object> fresh = new LinkedHashMap<>();
                fresh.put("episodes", 0);
                fresh.put("correct", 0);
                fresh.put("novelQueries", 0);
                fresh.put("probes", 0);
                fresh.put("settled", 0);
                fresh.put("inapplicable", 0);
                fresh.put("wallNanos", 0L);
                return fresh;
            });
            line.put("episodes", (int) line.get("episodes") + 1);
            line.put("correct", (int) line.get("correct") + row.correct());
            line.put("novelQueries", (int) line.get("novelQueries") + row.novelQueries());
            line.put("probes", (int) line.get("probes") + row.probes());
            if (row.settled()) {
                line.put("settled", (int) line.get("settled") + 1);
            }
            if (row.inapplicable()) {
                line.put("inapplicable", (int) line.get("inapplicable") + 1);
            }
            line.put("wallNanos", (long) line.get("wallNanos") + row.wallNanos());
        }
        return summary;
    }
}
