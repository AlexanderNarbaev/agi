package io.matrix.cognitive;

import io.matrix.cognitive.algorithms.Procedure;
import io.matrix.cognitive.algorithms.ValidationKind;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * W45.0 — the write policy of the cognitive store.
 *
 * <p>W44 wrote every record with {@code INSERT OR REPLACE}, which is the same as saying "the
 * last writer wins". These tests state the policy that replaced it, table by table: the events,
 * the predictions, the errors and the tasks are immutable and insert-only; the episode is opened
 * once and closed once; a procedure version never overwrites its predecessor; and the durable
 * sequence never hands out the same number twice.</p>
 */
class CognitiveStoreIdentityTest {

    private static final String WORKSPACE = "microworld";

    @TempDir
    Path directory;

    private CognitiveStore store;

    @BeforeEach
    void setUp() {
        store = new CognitiveStore(directory.resolve("cognitive.sqlite"));
    }

    @AfterEach
    void tearDown() {
        if (store != null) {
            store.close();
        }
    }

    // ── events ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("the same event written again is a no-op, and the stream does not grow")
    void theSameEventWrittenAgainIsANoOp() {
        store.appendEvent("evt-1", "ep-1", 0L, "PERCEIVE", "ChannelClassifier", "channel=TEXT",
                "2026-11-03T00:00:00Z");

        store.appendEvent("evt-1", "ep-1", 0L, "PERCEIVE", "ChannelClassifier", "channel=TEXT",
                "2026-11-03T00:00:00Z");

        assertThat(store.events("ep-1")).hasSize(1);
    }

    @Test
    @DisplayName("the same event id with different content is a conflict, not an overwrite")
    void theSameEventIdWithDifferentContentIsAConflict() {
        store.appendEvent("evt-1", "ep-1", 0L, "PERCEIVE", "ChannelClassifier", "channel=TEXT",
                "2026-11-03T00:00:00Z");

        assertThatThrownBy(() -> store.appendEvent("evt-1", "ep-1", 0L, "PERCEIVE",
                "ChannelClassifier", "channel=BINARY", "2026-11-03T00:00:00Z"))
                .isInstanceOf(CognitiveConflictException.class)
                .hasMessageContaining("evt-1");

        // The first event is still exactly what it was.
        assertThat(store.events("ep-1")).hasSize(1);
        assertThat(store.events("ep-1").get(0).detail()).isEqualTo("channel=TEXT");
    }

    @Test
    @DisplayName("two events cannot occupy the same position of the same episode")
    void twoEventsCannotOccupyTheSamePosition() {
        store.appendEvent("evt-1", "ep-1", 0L, "PERCEIVE", "ChannelClassifier", "channel=TEXT",
                "2026-11-03T00:00:00Z");

        assertThatThrownBy(() -> store.appendEvent("evt-2", "ep-1", 0L, "REFUSE", "CognitiveKernel",
                "the signal was malformed", "2026-11-03T00:00:00Z"))
                .isInstanceOf(CognitiveConflictException.class);

        assertThat(store.events("ep-1")).hasSize(1);
        assertThat(store.events("ep-1").get(0).phase()).isEqualTo("PERCEIVE");
    }

    @Test
    @DisplayName("the position of an event is unique in the database, not only in the code")
    void thePositionOfAnEventIsUniqueInTheDatabase() throws SQLException {
        store.appendEvent("evt-1", "ep-1", 0L, "PERCEIVE", "ChannelClassifier", "channel=TEXT",
                "2026-11-03T00:00:00Z");

        try (Connection raw = DriverManager.getConnection(
                "jdbc:sqlite:" + directory.resolve("cognitive.sqlite"));
             Statement st = raw.createStatement()) {
            st.execute("INSERT INTO cognitive_event(id, episode_id, sequence, type, actor, detail, "
                    + "occurred_at) VALUES('evt-9','ep-1',0,'X','Y','Z','2026-11-03T00:00:00Z')");
            raw.commit();
        } catch (SQLException e) {
            // The unique index refused the second event at that position, which is the point.
            assertThat(e.getMessage().toLowerCase(java.util.Locale.ROOT)).contains("unique");
        }
    }

    // ── predictions, errors and tasks ────────────────────────────────────────

    @Test
    @DisplayName("a prediction is immutable: the same content is a no-op, different content a conflict")
    void aPredictionIsImmutable() {
        Prediction prediction = prediction("prd-1");
        store.recordPrediction(prediction);
        store.recordPrediction(prediction);

        assertThat(store.predictions(WORKSPACE)).hasSize(1);

        assertThatThrownBy(() -> store.recordPrediction(
                new Prediction("prd-1", WORKSPACE, "ep-1", "lamp@power=1", "true",
                        "surviving-hypotheses", "v1", 0.5, false, List.of(), "2026-11-03T00:00:00Z")))
                .isInstanceOf(CognitiveConflictException.class);

        assertThat(store.predictions(WORKSPACE)).hasSize(1);
        assertThat(store.predictions(WORKSPACE).get(0).confidence()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("an error is immutable: the same content is a no-op, different content a conflict")
    void anErrorIsImmutable() {
        ErrorSignal error = error("err-1");
        store.recordError(error);
        store.recordError(error);

        assertThat(store.errors(WORKSPACE)).hasSize(1);

        assertThatThrownBy(() -> store.recordError(new ErrorSignal("err-1", WORKSPACE, "prd-1",
                "true", "false", 1.0, ErrorSignal.Disposition.MODEL_DEFECT, "model", "v1",
                List.of(), "2026-11-03T00:00:00Z")))
                .isInstanceOf(CognitiveConflictException.class);

        assertThat(store.errors(WORKSPACE)).hasSize(1);
        assertThat(store.errors(WORKSPACE).get(0).disposition())
                .isEqualTo(ErrorSignal.Disposition.UNRESOLVED);
    }

    @Test
    @DisplayName("a task is immutable: the same content is a no-op, different content a conflict")
    void aTaskIsImmutable() {
        CognitiveTask task = task("tsk-1");
        store.recordTask(task);
        store.recordTask(task);

        assertThat(store.tasks(WORKSPACE)).hasSize(1);

        assertThatThrownBy(() -> store.recordTask(new CognitiveTask("tsk-1", WORKSPACE,
                "prediction-error", "another reason", "keep the model consistent",
                "an explanation", "one bounded probe", "a cause is established",
                "the budget is exhausted", List.of("probe-microworld"), 250L,
                CognitiveTask.STATUS_OPEN, "2026-11-03T00:00:00Z", null)))
                .isInstanceOf(CognitiveConflictException.class);

        assertThat(store.tasks(WORKSPACE)).hasSize(1);
    }

    // ── episodes ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("an episode is opened once and closed once, and the close rewrites nothing else")
    void anEpisodeIsOpenedOnceAndClosedOnce() throws SQLException {
        store.startEpisode("ep-1", WORKSPACE, "a cognitive cycle on STRUCTURED",
                "2026-11-03T00:00:00Z");
        store.closeEpisode("ep-1", "2026-11-03T00:00:01Z");

        // Re-opening the same episode with the same content changes nothing ...
        store.startEpisode("ep-1", WORKSPACE, "a cognitive cycle on STRUCTURED",
                "2026-11-03T00:00:00Z");
        // ... and closing it with another instant is refused.
        assertThatThrownBy(() -> store.closeEpisode("ep-1", "2026-11-03T00:00:09Z"))
                .isInstanceOf(CognitiveConflictException.class);

        try (Connection raw = DriverManager.getConnection(
                "jdbc:sqlite:" + directory.resolve("cognitive.sqlite"));
             Statement st = raw.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT summary, started_at, closed_at FROM cognitive_episode WHERE id='ep-1'")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("summary")).isEqualTo("a cognitive cycle on STRUCTURED");
            assertThat(rs.getString("started_at")).isEqualTo("2026-11-03T00:00:00Z");
            assertThat(rs.getString("closed_at")).isEqualTo("2026-11-03T00:00:01Z");
        }
    }

    @Test
    @DisplayName("opening an episode that exists with a different account is a conflict")
    void openingAnEpisodeThatExistsWithADifferentAccountIsAConflict() {
        store.startEpisode("ep-1", WORKSPACE, "a cognitive cycle on STRUCTURED",
                "2026-11-03T00:00:00Z");

        assertThatThrownBy(() -> store.startEpisode("ep-1", WORKSPACE,
                "a cognitive cycle on BINARY", "2026-11-03T00:00:00Z"))
                .isInstanceOf(CognitiveConflictException.class);
    }

    @Test
    @DisplayName("closing an episode that was never opened is a conflict")
    void closingAnEpisodeThatWasNeverOpenedIsAConflict() {
        assertThatThrownBy(() -> store.closeEpisode("ep-404", "2026-11-03T00:00:01Z"))
                .isInstanceOf(CognitiveConflictException.class);
    }

    // ── procedures ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("a new version of a procedure does not overwrite its predecessor")
    void aNewVersionOfAProcedureDoesNotOverwriteItsPredecessor() {
        store.recordProcedure(WORKSPACE, procedure("proc-1", "v1", "the first way"));

        store.recordProcedure(WORKSPACE, procedure("proc-1", "v2", "the second way"));

        List<Procedure> procedures = store.procedures(WORKSPACE);
        assertThat(procedures).hasSize(2);
        assertThat(procedures).extracting(Procedure::version).containsExactlyInAnyOrder("v1", "v2");
        assertThat(procedures).extracting(Procedure::name)
                .containsExactlyInAnyOrder("the first way", "the second way");
    }

    @Test
    @DisplayName("the same version of a procedure with a different definition is a conflict")
    void theSameVersionOfAProcedureWithADifferentDefinitionIsAConflict() {
        store.recordProcedure(WORKSPACE, procedure("proc-1", "v1", "the first way"));

        assertThatThrownBy(() -> store.recordProcedure(WORKSPACE,
                procedure("proc-1", "v1", "another way")))
                .isInstanceOf(CognitiveConflictException.class);

        assertThat(store.procedures(WORKSPACE)).hasSize(1);
        assertThat(store.procedures(WORKSPACE).get(0).name()).isEqualTo("the first way");
    }

    @Test
    @DisplayName("re-promoting the same version is a no-op, and the measurement of the version stands")
    void rePromotingTheSameVersionIsANoOp() {
        store.recordProcedure(WORKSPACE, procedure("proc-1", "v1", "the first way"));
        Procedure later = new Procedure("proc-1", "the first way", "micro-world-outcome", "v1",
                "the model has settled", Set.of("MODEL_SETTLED"), "reference", 8, 1.0, 0, 0,
                "2026-11-03T00:00:09Z", null, ValidationKind.IN_SAMPLE);
        store.recordProcedure(WORKSPACE, later);

        assertThat(store.procedures(WORKSPACE)).hasSize(1);
        assertThat(store.procedures(WORKSPACE).get(0).validationCases()).isEqualTo(2);
        assertThat(store.procedures(WORKSPACE).get(0).promotedAt())
                .isEqualTo("2026-11-03T00:00:00Z");
    }

    // ── the durable sequence ─────────────────────────────────────────────────

    @Test
    @DisplayName("the sequence is durable: a restart does not reset it")
    void theSequenceIsDurable() {
        assertThat(store.nextSequence("arrivals")).isEqualTo(1L);
        assertThat(store.nextSequence("arrivals")).isEqualTo(2L);

        store.close();
        store = new CognitiveStore(directory.resolve("cognitive.sqlite"));

        assertThat(store.nextSequence("arrivals")).isEqualTo(3L);
    }

    @Test
    @DisplayName("the sequence never hands out the same number twice")
    void theSequenceNeverHandsOutTheSameNumberTwice() throws Exception {
        int callers = 8;
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        try {
            List<Callable<Long>> tasks = java.util.stream.IntStream.range(0, callers)
                    .<Callable<Long>>mapToObj(i -> () -> store.nextSequence("arrivals"))
                    .toList();
            List<Future<Long>> futures = pool.invokeAll(tasks);
            List<Long> values = new java.util.ArrayList<>();
            for (Future<Long> future : futures) {
                values.add(future.get(10, TimeUnit.SECONDS));
            }
            assertThat(values).doesNotHaveDuplicates();
            assertThat(values.stream().sorted().toList())
                    .containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L);
        } finally {
            pool.shutdownNow();
        }
    }

    // ── fixtures ─────────────────────────────────────────────────────────────

    private static Prediction prediction(String id) {
        return new Prediction(id, WORKSPACE, "ep-1", "lamp@power=1", "true",
                "surviving-hypotheses", "v1", 1.0, false, List.of(), "2026-11-03T00:00:00Z");
    }

    private static ErrorSignal error(String id) {
        return new ErrorSignal(id, WORKSPACE, "prd-1", "true", "false", 1.0,
                ErrorSignal.Disposition.UNRESOLVED, "model", "v1", List.of(),
                "2026-11-03T00:00:00Z");
    }

    private static CognitiveTask task(String id) {
        return new CognitiveTask(id, WORKSPACE, "prediction-error",
                "the prediction and the observation disagree",
                "keep the model consistent with the evidence it has collected",
                "an explanation", "one bounded probe", "a cause is established",
                "the wall-clock budget is exhausted", List.of("probe-microworld"), 250L,
                CognitiveTask.STATUS_OPEN, "2026-11-03T00:00:00Z", null);
    }

    private static Procedure procedure(String id, String version, String name) {
        return new Procedure(id, name, "micro-world-outcome", version,
                "the model has settled", Set.of("MODEL_SETTLED"), "reference", 2, 1.0, 0, 0,
                "2026-11-03T00:00:00Z", null, ValidationKind.IN_SAMPLE);
    }
}
