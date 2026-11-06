package io.matrix.cognitive.neuromodulation;

import static org.assertj.core.api.Assertions.assertThat;

import io.matrix.cognitive.DriveSignal;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W44.3 — the safety surface of the regulatory layer.
 *
 * <p>W44 forbids a modulator from weakening a gate. Most of the systems a modulator could
 * conceivably weaken — the retrieval floor, the safety monitor, a claim's standing — are outside
 * this package, so a test cannot simply watch them stay put. What it CAN do is hold the surface
 * shut: the regulatory layer is small, closed and one-sided, and the checks below fail the moment
 * somebody widens it.</p>
 *
 * <p>What this asserts about the SYSTEM, and not about the code:</p>
 *
 * <ul>
 *   <li>the bias a drive can produce has exactly four fields, and they are the four that were
 *       agreed; adding a fifth one — a confidence, a permission, a threshold that could be
 *       lowered — breaks this test, which is the point of writing it by reflection;</li>
 *   <li>the controller cannot answer a question about permission, standing or safety, because it
 *       exposes no method that returns such an answer;</li>
 *   <li>no drive, at any level and with any effect, can make the system cheaper-minded, less
 *       curious about evidence, more permissive, or less attentive to a task than a system with no
 *       regulatory state at all;</li>
 *   <li>no drive can suppress consolidation permanently: the demand decays and the work resumes.</li>
 * </ul>
 */
class RegulationSafetySurfaceTest {

    private static final String OBSERVED_AT = "2026-11-02T00:00:00Z";

    /**
     * The words a field or a method of the regulatory surface must never contain.
     *
     * <p>They name the things a modulator is not allowed to touch. A gate that is named here is a
     * gate somebody could later be tempted to wire into a bias.</p>
     */
    private static final List<String> FORBIDDEN_TOKENS = List.of(
            "confidence", "truth", "claim", "permission", "permit", "authoriz",
            "safety", "gate", "floor", "retrieval", "auth", "status", "verdict");

    private final RegulationController controller = new RegulationController();
    private final DriveDynamics dynamics = DriveDynamics.standard();

    private static boolean mentionsAny(String text, List<String> tokens) {
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        return tokens.stream().anyMatch(lower::contains);
    }

    private static RegulatoryState single(DriveSignal.Dimension dimension,
                                          double level,
                                          DriveSignal.Effect effect) {
        return new RegulatoryState(Map.of(dimension, new DriveSignal(
                dimension, level, "test", effect, OBSERVED_AT)));
    }

    @Test
    @DisplayName("the bias has exactly the four agreed fields, and no fifth")
    void theBiasHasExactlyTheFourAgreedFields() {
        Set<String> fields = Arrays.stream(RegulationBias.class.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());

        assertThat(fields).containsExactlyInAnyOrder(
                "costWeightMultiplier",
                "evidenceRequestThreshold",
                "admitNonCriticalTask",
                "taskPriorityMultiplier");
    }

    @Test
    @DisplayName("no field of the bias is named after a thing a modulator may not touch")
    void noFieldOfTheBiasIsNamedAfterAForbiddenThing() {
        for (RecordComponent component : RegulationBias.class.getRecordComponents()) {
            assertThat(mentionsAny(component.getName(), FORBIDDEN_TOKENS))
                    .as("the field '%s' names something a modulator must not be able to move",
                            component.getName())
                    .isFalse();
        }
    }

    @Test
    @DisplayName("the controller cannot answer a question about permission or standing")
    void theControllerCannotAnswerAPermissionQuestion() {
        Set<Class<?>> returnTypes = Arrays.stream(RegulationController.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(Method::getReturnType)
                .collect(Collectors.toSet());

        assertThat(returnTypes)
                .as("the whole surface a regulator can speak through")
                .containsExactlyInAnyOrder(
                        RegulationBias.class, RegulatoryState.class, DriveDynamics.class,
                        double.class, DriveSignal.Effect.class);
    }

    @Test
    @DisplayName("the state a regulator speaks through carries nothing that could weaken a gate")
    void theStateARegulatorSpeaksThroughCarriesNothingDangerous() {
        for (RecordComponent component : RegulatoryState.class.getRecordComponents()) {
            assertThat(mentionsAny(component.getName(), FORBIDDEN_TOKENS))
                    .as("the state field '%s' names something a modulator must not be able to move",
                            component.getName())
                    .isFalse();
        }
    }

    @Test
    @DisplayName("no method of the controller is named after a thing a modulator may not touch")
    void noMethodOfTheControllerIsNamedAfterAForbiddenThing() {
        for (Method method : RegulationController.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers())) {
                continue;
            }
            assertThat(mentionsAny(method.getName(), FORBIDDEN_TOKENS))
                    .as("the method '%s' names something a modulator must not be able to do",
                            method.getName())
                    .isFalse();
        }
    }

    @Test
    @DisplayName("no drive can make the system less conservative than a neutral one")
    void noDriveCanMakeTheSystemLessConservative() {
        RegulationBias neutral = RegulationBias.neutral();

        for (DriveSignal.Dimension dimension : DriveSignal.Dimension.values()) {
            for (DriveSignal.Effect effect : DriveSignal.Effect.values()) {
                for (double level : List.of(0.0, 0.25, 0.5, 0.75, 1.0)) {
                    RegulationBias bias = controller.bias(single(dimension, level, effect));
                    String where = "dimension=" + dimension + " effect=" + effect + " level=" + level;

                    assertThat(bias.costWeightMultiplier())
                            .as("a drive must not make the expensive method look cheaper: " + where)
                            .isGreaterThanOrEqualTo(neutral.costWeightMultiplier());
                    assertThat(bias.evidenceRequestThreshold())
                            .as("a drive must not stop the system asking for evidence: " + where)
                            .isLessThanOrEqualTo(neutral.evidenceRequestThreshold());
                    assertThat(bias.taskPriorityMultiplier())
                            .as("a drive must not make an important task less important: " + where)
                            .isGreaterThanOrEqualTo(neutral.taskPriorityMultiplier());
                    if (!bias.admitNonCriticalTask()) {
                        assertThat(dimension)
                                .as("only a consolidation demand may withhold new work: " + where)
                                .isEqualTo(DriveSignal.Dimension.CONSOLIDATION_DEMAND);
                        assertThat(effect)
                                .as("only the admission effect may withhold new work: " + where)
                                .isEqualTo(DriveSignal.Effect.TASK_ADMISSION);
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("no combination of drives can make the system less conservative either")
    void noCombinationOfDrivesCanMakeTheSystemLessConservative() {
        RegulationBias neutral = RegulationBias.neutral();
        List<DriveSignal.Dimension> dimensions = List.of(DriveSignal.Dimension.values());

        for (DriveSignal.Dimension first : dimensions) {
            for (DriveSignal.Dimension second : dimensions) {
                if (first == second) {
                    continue;
                }
                RegulatoryState state = new RegulatoryState(Map.of(
                        first, new DriveSignal(first, 1.0, "test",
                                RegulationController.effectFor(first), OBSERVED_AT),
                        second, new DriveSignal(second, 1.0, "test",
                                RegulationController.effectFor(second), OBSERVED_AT)));
                RegulationBias bias = controller.bias(state);

                assertThat(bias.costWeightMultiplier())
                        .isGreaterThanOrEqualTo(neutral.costWeightMultiplier());
                assertThat(bias.evidenceRequestThreshold())
                        .isLessThanOrEqualTo(neutral.evidenceRequestThreshold());
                assertThat(bias.taskPriorityMultiplier())
                        .isGreaterThanOrEqualTo(neutral.taskPriorityMultiplier());
            }
        }
    }

    @Test
    @DisplayName("no drive can change a retrieval floor, because there is nowhere for it to go")
    void noDriveCanChangeARetrievalFloor() {
        Set<String> fields = Arrays.stream(RegulationBias.class.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());

        assertThat(fields)
                .as("a floor a drive could move would have to be a field, and none is")
                .noneMatch(name -> name.toLowerCase(java.util.Locale.ROOT).contains("floor")
                        || name.toLowerCase(java.util.Locale.ROOT).contains("retrieval"));

        assertThat(RegulationController.effectFor(DriveSignal.Dimension.ATTENTION))
                .as("attention is the dimension the previous design moved a retrieval floor with")
                .isEqualTo(DriveSignal.Effect.NONE);
    }

    @Test
    @DisplayName("no drive can change a claim's standing or confidence, because it cannot express one")
    void noDriveCanChangeAClaimOrAConfidence() {
        Set<Class<?>> returnTypes = Arrays.stream(RegulationController.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(Method::getReturnType)
                .collect(Collectors.toSet());

        assertThat(returnTypes)
                .noneMatch(type -> type.getSimpleName().toLowerCase(java.util.Locale.ROOT)
                        .contains("claim")
                        || type.getSimpleName().toLowerCase(java.util.Locale.ROOT)
                        .contains("confidence")
                        || type.getSimpleName().toLowerCase(java.util.Locale.ROOT)
                        .contains("standing"));
    }

    @Test
    @DisplayName("no drive can suppress consolidation permanently")
    void noDriveCanSuppressConsolidationPermanently() {
        RegulatoryState state = single(DriveSignal.Dimension.CONSOLIDATION_DEMAND, 1.0,
                DriveSignal.Effect.TASK_ADMISSION);

        assertThat(controller.bias(state).admitNonCriticalTask())
                .as("a severe demand does stop new work")
                .isFalse();

        for (int step = 0; step < 100; step++) {
            state = dynamics.advance(state, List.of(), 1, OBSERVED_AT);
        }

        assertThat(state.level(DriveSignal.Dimension.CONSOLIDATION_DEMAND))
                .as("the demand fades, because pressure that no event maintains is not pressure")
                .isLessThan(RegulationController.CONSOLIDATION_ADMISSION_THRESHOLD);
        assertThat(controller.bias(state).admitNonCriticalTask())
                .as("so the work resumes: nothing here can be suppressed forever")
                .isTrue();
    }

    @Test
    @DisplayName("every dimension is given an effect the contract knows")
    void everyDimensionIsGivenAnEffectTheContractKnows() {
        for (DriveSignal.Dimension dimension : DriveSignal.Dimension.values()) {
            DriveSignal.Effect effect = RegulationController.effectFor(dimension);
            assertThat(effect)
                    .as("dimension=%s", dimension)
                    .isNotNull()
                    .isIn(Arrays.asList(DriveSignal.Effect.values()));
        }
    }
}
