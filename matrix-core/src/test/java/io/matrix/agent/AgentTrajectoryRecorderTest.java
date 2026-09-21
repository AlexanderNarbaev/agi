package io.matrix.agent;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentTrajectoryRecorderTest {

    // ── Record invariants ──

    @Test
    void trajectoryStepShouldRejectNullAction() {
        assertThatThrownBy(() ->
                new AgentTrajectoryRecorder.TrajectoryStep(0, null, 0L, 1, 0L, true))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void trajectoryStepShouldRejectNegativeTick() {
        assertThatThrownBy(() ->
                new AgentTrajectoryRecorder.TrajectoryStep(-1, "move", 0L, 1, 0L, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void agentTrajectoryShouldRejectNullSessionId() {
        assertThatThrownBy(() ->
                new AgentTrajectoryRecorder.AgentTrajectory(
                        null, UUID.randomUUID(), "a", List.of(), 0, 0))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void agentTrajectoryShouldRejectNullRequestId() {
        assertThatThrownBy(() ->
                new AgentTrajectoryRecorder.AgentTrajectory(
                        UUID.randomUUID(), null, "a", List.of(), 0, 0))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void agentTrajectoryShouldRejectNullAgentId() {
        assertThatThrownBy(() ->
                new AgentTrajectoryRecorder.AgentTrajectory(
                        UUID.randomUUID(), UUID.randomUUID(), null, List.of(), 0, 0))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void agentTrajectoryShouldRejectNullSteps() {
        assertThatThrownBy(() ->
                new AgentTrajectoryRecorder.AgentTrajectory(
                        UUID.randomUUID(), UUID.randomUUID(), "a", null, 0, 0))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void agentTrajectoryShouldRejectNegativeStartTime() {
        assertThatThrownBy(() ->
                new AgentTrajectoryRecorder.AgentTrajectory(
                        UUID.randomUUID(), UUID.randomUUID(), "a", List.of(), -1, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void agentTrajectoryShouldRejectEndBeforeStart() {
        assertThatThrownBy(() ->
                new AgentTrajectoryRecorder.AgentTrajectory(
                        UUID.randomUUID(), UUID.randomUUID(), "a", List.of(), 100, 50))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void replayResultShouldRejectNullDivergentSteps() {
        assertThatThrownBy(() ->
                new AgentTrajectoryRecorder.ReplayResult(true, 1.0, null, 0))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void replayResultShouldRejectInvalidMatchRate() {
        assertThatThrownBy(() ->
                new AgentTrajectoryRecorder.ReplayResult(true, 1.5, List.of(), 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                new AgentTrajectoryRecorder.ReplayResult(true, -0.1, List.of(), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void trajectoryStepsShouldBeImmutableCopy() {
        var t = new AgentTrajectoryRecorder.AgentTrajectory(
                UUID.randomUUID(), UUID.randomUUID(), "a",
                List.of(new AgentTrajectoryRecorder.TrajectoryStep(0, "x", 1, 2, 3, true)),
                0, 100);

        assertThatThrownBy(() -> t.steps().add(
                new AgentTrajectoryRecorder.TrajectoryStep(1, "y", 0, 0, 0, false)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void replayResultDivergentStepsShouldBeImmutableCopy() {
        var result = new AgentTrajectoryRecorder.ReplayResult(false, 0.5,
                new java.util.ArrayList<>(List.of(1)), 100);

        assertThatThrownBy(() -> result.divergentSteps().add(2))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // ── Step recording ──

    @Test
    void recordStepShouldCreateAndPopulateSession() {
        var step = AgentTrajectoryRecorder.recordStep(0, "observe", 0b1010L, 7, true);

        assertThat(step.tick()).isEqualTo(0);
        assertThat(step.action()).isEqualTo("observe");
        assertThat(step.sensorBits()).isEqualTo(0b1010L);
        assertThat(step.actionCode()).isEqualTo(7);
        assertThat(step.success()).isTrue();
        assertThat(step.timestampNs()).isPositive();
    }

    @Test
    void multipleRecordStepsShouldAccumulateInOrder() {
        AgentTrajectoryRecorder.recordStep(0, "observe", 1L, 1, true);
        AgentTrajectoryRecorder.recordStep(1, "plan", 2L, 2, true);
        AgentTrajectoryRecorder.recordStep(2, "act", 3L, 3, true);

        var requestId = UUID.randomUUID();
        var trajectory = AgentTrajectoryRecorder.finishSession(requestId, "test-agent");

        assertThat(trajectory.steps()).hasSize(3);
        assertThat(trajectory.steps().get(0).action()).isEqualTo("observe");
        assertThat(trajectory.steps().get(1).action()).isEqualTo("plan");
        assertThat(trajectory.steps().get(2).action()).isEqualTo("act");
        assertThat(trajectory.steps().get(0).tick()).isEqualTo(0);
        assertThat(trajectory.steps().get(1).tick()).isEqualTo(1);
        assertThat(trajectory.steps().get(2).tick()).isEqualTo(2);
    }

    // ── Session finish ──

    @Test
    void finishSessionShouldProduceTrajectoryWithMetadata() {
        AgentTrajectoryRecorder.recordStep(0, "init", 0L, 0, true);

        var requestId = UUID.randomUUID();
        var trajectory = AgentTrajectoryRecorder.finishSession(requestId, "eva-7");

        assertThat(trajectory.sessionId()).isNotNull();
        assertThat(trajectory.requestId()).isEqualTo(requestId);
        assertThat(trajectory.agentId()).isEqualTo("eva-7");
        assertThat(trajectory.steps()).hasSize(1);
        assertThat(trajectory.startTimeMs()).isPositive();
        assertThat(trajectory.endTimeMs()).isGreaterThanOrEqualTo(trajectory.startTimeMs());
    }

    @Test
    void finishSessionShouldConsumeSession() {
        AgentTrajectoryRecorder.recordStep(0, "x", 0L, 0, true);
        AgentTrajectoryRecorder.finishSession(UUID.randomUUID(), "a");

        // After finishing, no active session
        assertThatThrownBy(() ->
                AgentTrajectoryRecorder.finishSession(UUID.randomUUID(), "a"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void finishSessionShouldRejectNullRequestId() {
        AgentTrajectoryRecorder.recordStep(0, "x", 0L, 0, true);
        assertThatThrownBy(() ->
                AgentTrajectoryRecorder.finishSession(null, "a"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void finishSessionShouldRejectNullAgentId() {
        AgentTrajectoryRecorder.recordStep(0, "x", 0L, 0, true);
        assertThatThrownBy(() ->
                AgentTrajectoryRecorder.finishSession(UUID.randomUUID(), null))
                .isInstanceOf(NullPointerException.class);
    }

    // ── Save + load roundtrip ──

    @Test
    void saveAndLoadRoundtripShouldPreserveAllFields() throws Exception {
        AgentTrajectoryRecorder.recordStep(0, "think", 0xFFL, 42, true);
        AgentTrajectoryRecorder.recordStep(1, "jump", 0x00L, 7, false);
        var original = AgentTrajectoryRecorder.finishSession(UUID.randomUUID(), "roundtrip-agent");

        Path tempDir = Files.createTempDirectory("trajectory-test");
        try {
            Path savedFile = AgentTrajectoryRecorder.save(original, tempDir);
            assertThat(savedFile).exists();
            assertThat(savedFile.getFileName().toString())
                    .isEqualTo(original.sessionId() + ".json");

            var restored = AgentTrajectoryRecorder.load(savedFile);

            assertThat(restored.sessionId()).isEqualTo(original.sessionId());
            assertThat(restored.requestId()).isEqualTo(original.requestId());
            assertThat(restored.agentId()).isEqualTo(original.agentId());
            assertThat(restored.startTimeMs()).isEqualTo(original.startTimeMs());
            assertThat(restored.endTimeMs()).isEqualTo(original.endTimeMs());
            assertThat(restored.steps()).hasSize(original.steps().size());
            for (int i = 0; i < original.steps().size(); i++) {
                var origStep = original.steps().get(i);
                var restStep = restored.steps().get(i);
                assertThat(restStep.tick()).isEqualTo(origStep.tick());
                assertThat(restStep.action()).isEqualTo(origStep.action());
                assertThat(restStep.sensorBits()).isEqualTo(origStep.sensorBits());
                assertThat(restStep.actionCode()).isEqualTo(origStep.actionCode());
                assertThat(restStep.timestampNs()).isEqualTo(origStep.timestampNs());
                assertThat(restStep.success()).isEqualTo(origStep.success());
            }
        } finally {
            Files.walk(tempDir)
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> p.toFile().delete());
        }
    }

    @Test
    void saveShouldCreateDirectoriesIfNeeded() throws Exception {
        AgentTrajectoryRecorder.recordStep(0, "x", 0L, 0, true);
        var trajectory = AgentTrajectoryRecorder.finishSession(UUID.randomUUID(), "a");

        Path tempDir = Files.createTempDirectory("trajectory-test");
        Path nestedDir = tempDir.resolve("sub1").resolve("sub2");
        try {
            AgentTrajectoryRecorder.save(trajectory, nestedDir);
            assertThat(nestedDir).isDirectory();
        } finally {
            Files.walk(tempDir)
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> p.toFile().delete());
        }
    }

    // ── Replay ──

    @Test
    void replayShouldDetectExactMatch() {
        var steps1 = List.of(
                step(0, "observe"),
                step(1, "plan"),
                step(2, "act"));
        var ref = trajectory("ref", steps1);
        var act = trajectory("act", steps1);

        var result = AgentTrajectoryRecorder.replay(ref, act);
        assertThat(result.matches()).isTrue();
        assertThat(result.stepMatchRate()).isEqualTo(1.0);
        assertThat(result.divergentSteps()).isEmpty();
    }

    @Test
    void replayShouldDetectActionDivergence() {
        var refSteps = List.of(
                step(0, "observe"),
                step(1, "plan"),
                step(2, "act"));
        var actSteps = List.of(
                step(0, "observe"),
                step(1, "wrong_action"),
                step(2, "act"));
        var ref = trajectory("ref", refSteps);
        var act = trajectory("act", actSteps);

        var result = AgentTrajectoryRecorder.replay(ref, act);

        assertThat(result.matches()).isFalse();
        assertThat(result.stepMatchRate()).isEqualTo(2.0 / 3.0);
        assertThat(result.divergentSteps()).containsExactly(1);
    }

    @Test
    void replayShouldIgnoreSensorBits() {
        var refSteps = List.of(
                new AgentTrajectoryRecorder.TrajectoryStep(0, "observe", 0b111L, 1, 0L, true));
        var actSteps = List.of(
                new AgentTrajectoryRecorder.TrajectoryStep(0, "observe", 0b000L, 1, 0L, true));

        var ref = trajectory("ref", refSteps);
        var act = trajectory("act", actSteps);

        var result = AgentTrajectoryRecorder.replay(ref, act);
        assertThat(result.matches()).isTrue();
        assertThat(result.stepMatchRate()).isEqualTo(1.0);
    }

    @Test
    void replayShouldDetectExtraSteps() {
        var refSteps = List.of(step(0, "a"), step(1, "b"));
        var actSteps = List.of(step(0, "a"), step(1, "b"), step(2, "extra"));

        var ref = trajectory("ref", refSteps);
        var act = trajectory("act", actSteps);

        var result = AgentTrajectoryRecorder.replay(ref, act);

        assertThat(result.matches()).isFalse();
        assertThat(result.stepMatchRate()).isEqualTo(2.0 / 3.0);
        assertThat(result.divergentSteps()).containsExactly(2);
    }

    @Test
    void replayShouldDetectMissingSteps() {
        var refSteps = List.of(step(0, "a"), step(1, "b"), step(2, "c"));
        var actSteps = List.of(step(0, "a"));

        var ref = trajectory("ref", refSteps);
        var act = trajectory("act", actSteps);

        var result = AgentTrajectoryRecorder.replay(ref, act);

        assertThat(result.matches()).isFalse();
        assertThat(result.stepMatchRate()).isEqualTo(1.0 / 3.0);
        assertThat(result.divergentSteps()).containsExactly(1, 2);
    }

    @Test
    void replayEmptyTrajectoriesShouldMatch() {
        var ref = emptyTrajectory("ref");
        var act = emptyTrajectory("act");

        var result = AgentTrajectoryRecorder.replay(ref, act);

        assertThat(result.matches()).isTrue();
        assertThat(result.stepMatchRate()).isEqualTo(1.0);
        assertThat(result.divergentSteps()).isEmpty();
    }

    @Test
    void replayEmptyAgainstNonEmptyShouldDiverge() {
        var ref = emptyTrajectory("ref");
        var act = trajectory("act", List.of(step(0, "x")));

        var result = AgentTrajectoryRecorder.replay(ref, act);

        assertThat(result.matches()).isFalse();
        assertThat(result.stepMatchRate()).isEqualTo(0.0);
        assertThat(result.divergentSteps()).containsExactly(0);
    }

    @Test
    void replayShouldComputeTimingDelta() {
        var ref = new AgentTrajectoryRecorder.AgentTrajectory(
                UUID.randomUUID(), UUID.randomUUID(), "a", List.of(), 100, 200);
        var act = new AgentTrajectoryRecorder.AgentTrajectory(
                UUID.randomUUID(), UUID.randomUUID(), "b", List.of(), 100, 350);

        var result = AgentTrajectoryRecorder.replay(ref, act);
        assertThat(result.timingDeltaMs()).isEqualTo(150);
    }

    // ── Empty trajectory ──

    @Test
    void emptyTrajectoryShouldHaveZeroSteps() {
        var t = emptyTrajectory("empty");
        assertThat(t.steps()).isEmpty();
    }

    @Test
    void emptyTrajectoryToJsonShouldContainEmptyStepsArray() {
        var t = emptyTrajectory("empty");
        var json = t.toJson();
        assertThat(json).contains("\"steps\"");
    }

    // ── ThreadLocal isolation ──

    @Test
    void threadLocalShouldIsolateSessions() throws Exception {
        var ref1 = new AtomicReference<AgentTrajectoryRecorder.AgentTrajectory>();
        var ref2 = new AtomicReference<AgentTrajectoryRecorder.AgentTrajectory>();
        var latch = new CountDownLatch(2);

        var t1 = new Thread(() -> {
            try {
                AgentTrajectoryRecorder.recordStep(0, "t1-step", 0L, 1, true);
                ref1.set(AgentTrajectoryRecorder.finishSession(
                        UUID.randomUUID(), "agent-t1"));
            } finally {
                latch.countDown();
            }
        });

        var t2 = new Thread(() -> {
            try {
                AgentTrajectoryRecorder.recordStep(0, "t2-step", 0L, 2, true);
                AgentTrajectoryRecorder.recordStep(1, "t2-second", 0L, 3, false);
                ref2.set(AgentTrajectoryRecorder.finishSession(
                        UUID.randomUUID(), "agent-t2"));
            } finally {
                latch.countDown();
            }
        });

        t1.start();
        t2.start();
        latch.await();

        var traj1 = ref1.get();
        var traj2 = ref2.get();

        assertThat(traj1.sessionId()).isNotEqualTo(traj2.sessionId());
        assertThat(traj1.steps()).hasSize(1);
        assertThat(traj1.steps().get(0).action()).isEqualTo("t1-step");
        assertThat(traj2.steps()).hasSize(2);
        assertThat(traj2.steps().get(1).action()).isEqualTo("t2-second");
    }

    @Test
    void finishSessionWithoutRecordShouldThrow() {
        assertThatThrownBy(() ->
                AgentTrajectoryRecorder.finishSession(UUID.randomUUID(), "agent"))
                .isInstanceOf(IllegalStateException.class);
    }

    // ── JSON format ──

    @Test
    void trajectoryJsonShouldContainExpectedFields() {
        var t = emptyTrajectory("fmt");
        var json = t.toJson();

        assertThat(json).contains("\"sessionId\"");
        assertThat(json).contains("\"requestId\"");
        assertThat(json).contains("\"agentId\"");
        assertThat(json).contains("\"steps\"");
        assertThat(json).contains("\"startTimeMs\"");
        assertThat(json).contains("\"endTimeMs\"");
    }

    @Test
    void trajectoryStepJsonShouldContainExpectedFields() {
        AgentTrajectoryRecorder.recordStep(0, "test-action", 42L, 7, true);
        var traj = AgentTrajectoryRecorder.finishSession(UUID.randomUUID(), "agent");
        var json = traj.toJson();

        assertThat(json).contains("\"tick\"");
        assertThat(json).contains("\"action\"");
        assertThat(json).contains("\"sensorBits\"");
        assertThat(json).contains("\"actionCode\"");
        assertThat(json).contains("\"timestampNs\"");
        assertThat(json).contains("\"success\"");
    }

    @Test
    void emptyStepsShouldRoundtripAsEmptyArray() {
        var original = emptyTrajectory("no-steps");
        var restored = AgentTrajectoryRecorder.AgentTrajectory.fromJson(original.toJson());

        assertThat(restored.steps()).isEmpty();
        assertThat(restored.sessionId()).isEqualTo(original.sessionId());
    }

    @Test
    void trajectoryStepAllFieldsShouldRoundtrip() {
        var step = new AgentTrajectoryRecorder.TrajectoryStep(5, "jump", 0xFF00L, 13, 1_000_000L, false);
        var traj = new AgentTrajectoryRecorder.AgentTrajectory(
                UUID.randomUUID(), UUID.randomUUID(), "o", List.of(step), 0, 100);
        var json = traj.toJson();
        var restored = AgentTrajectoryRecorder.AgentTrajectory.fromJson(json);

        var rStep = restored.steps().get(0);
        assertThat(rStep.tick()).isEqualTo(5);
        assertThat(rStep.action()).isEqualTo("jump");
        assertThat(rStep.sensorBits()).isEqualTo(0xFF00L);
        assertThat(rStep.actionCode()).isEqualTo(13);
        assertThat(rStep.timestampNs()).isEqualTo(1_000_000L);
        assertThat(rStep.success()).isFalse();
    }

    // ── Helpers ──

    private static AgentTrajectoryRecorder.TrajectoryStep step(int tick, String action) {
        return new AgentTrajectoryRecorder.TrajectoryStep(
                tick, action, 0L, 0, System.nanoTime(), true);
    }

    private static AgentTrajectoryRecorder.AgentTrajectory trajectory(
            String agentId,
            List<AgentTrajectoryRecorder.TrajectoryStep> steps) {
        return new AgentTrajectoryRecorder.AgentTrajectory(
                UUID.randomUUID(), UUID.randomUUID(), agentId, steps, 0, 100);
    }

    private static AgentTrajectoryRecorder.AgentTrajectory emptyTrajectory(String agentId) {
        return new AgentTrajectoryRecorder.AgentTrajectory(
                UUID.randomUUID(), UUID.randomUUID(), agentId, List.of(), 0, 100);
    }
}
