package io.matrix.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W45.2 — the canonical entry point of each serving application.
 *
 * <p>The owner's decision was to keep the five cycles and to NAME the canonical entry point
 * of each actual serving application, rather than to merge them or to declare a global
 * conductor. A naming that lives only in a document drifts; this test is what keeps the naming
 * true.</p>
 */
class CanonicalEntryPointTest {

    @Test
    @DisplayName("there are two serving applications, and each names its entry point")
    void thereAreTwoServingApplicationsAndEachNamesItsEntryPoint() {
        List<CanonicalEntryPoint.Declaration> serving =
                CanonicalEntryPoint.servingApplications();

        assertThat(serving).isNotEmpty();
        assertThat(serving).allSatisfy(declaration -> {
            assertThat(declaration.entryPointClass()).isNotBlank();
            assertThat(declaration.routes()).isNotBlank();
            assertThat(declaration.cycleClasses()).isNotBlank();
        });
        // The two applications of the architecture: the epistemic and workbench one, and
        // the brain runtime.
        assertThat(serving).extracting(CanonicalEntryPoint.Declaration::application)
                .contains(CanonicalEntryPoint.EPISTEMIC_APPLICATION,
                        CanonicalEntryPoint.BRAIN_RUNTIME_APPLICATION);
    }

    @Test
    @DisplayName("no cycle is the cycle of two serving applications")
    void noCycleIsTheCycleOfTwoServingApplications() {
        // The CYCLES are what must not be shared: two applications may each run their own
        // loops, but one loop may not be the loop of two applications.
        List<String> cycles = CanonicalEntryPoint.servingApplications().stream()
                .map(declaration -> declaration.cycleClasses())
                .toList();

        assertThat(cycles).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("the specialist loops are specialists, not serving entry points")
    void theSpecialistLoopsAreSpecialistsNotServingEntryPoints() {
        List<CanonicalEntryPoint.Declaration> specialists =
                CanonicalEntryPoint.declarations().stream()
                        .filter(declaration -> declaration.kind()
                                == CanonicalEntryPoint.Kind.SPECIALIST_LOOP)
                        .toList();

        // The five cycles of the archaeology: the brain runtime's TrueMindCycle and the
        // MindCycle, the BirBrainCycle, the ConsciousnessLoop and the RUN 158 loop.
        assertThat(specialists).extracting(CanonicalEntryPoint.Declaration::entryPointClass)
                .contains("io.matrix.brain.BirBrainCycle",
                        "io.matrix.reasoning.ConsciousnessLoop",
                        "io.matrix.consciousness.BrainLoopService");
        assertThat(specialists).allSatisfy(declaration ->
                assertThat(declaration.routes()).isEmpty());
    }

    @Test
    @DisplayName("the research cluster is declared as not served")
    void theResearchClusterIsDeclaredAsNotServed() {
        List<CanonicalEntryPoint.Declaration> research =
                CanonicalEntryPoint.declarations().stream()
                        .filter(declaration -> declaration.kind()
                                == CanonicalEntryPoint.Kind.RESEARCH_CLUSTER)
                        .toList();

        assertThat(research).extracting(CanonicalEntryPoint.Declaration::entryPointClass)
                .contains("io.matrix.neuron.ConsciousBrain");
    }

    @Test
    @DisplayName("every declared class that this module can resolve, resolves")
    void everyDeclaredClassThatThisModuleCanResolveResolves() {
        // The classes of the other modules are named, not linked, and this module cannot
        // resolve them. The ones it CAN resolve must resolve — a typo must not hide.
        for (CanonicalEntryPoint.Declaration declaration : CanonicalEntryPoint.declarations()) {
            for (String name : CanonicalEntryPoint.classesOf(declaration)) {
                if (name.startsWith("io.matrix.brain.")
                        || name.startsWith("io.matrix.api.MinimalHttpServer")) {
                    continue;
                }
                assertThat(canResolve(name))
                        .as("the declared class %s of %s", name, declaration.application())
                        .isTrue();
            }
        }
    }

    @Test
    @DisplayName("the brain runtime's entry point is the hand-written server, not the facade")
    void theBrainRuntimesEntryPointIsTheHandWrittenServerNotTheFacade() {
        CanonicalEntryPoint.Declaration brain = CanonicalEntryPoint
                .canonicalOf(CanonicalEntryPoint.BRAIN_RUNTIME_APPLICATION)
                .orElseThrow();

        assertThat(brain.entryPointClass()).isEqualTo("io.matrix.api.MinimalHttpServer");
        assertThat(brain.cycleClasses()).isEqualTo("io.matrix.brain.runtime.TrueMindCycle");
        // The gateway's JAX-RS facade is unmounted and constructs a stub; the decision must
        // not name it as the serving path.
        assertThat(brain.entryPointClass()).doesNotContain("BrainRuntimeResource");
    }

    @Test
    @DisplayName("the epistemic application names the workbench and the chat loop")
    void theEpistemicApplicationNamesTheWorkbenchAndTheChatLoop() {
        List<CanonicalEntryPoint.Declaration> declarations =
                CanonicalEntryPoint.of(CanonicalEntryPoint.EPISTEMIC_APPLICATION);

        assertThat(declarations).extracting(CanonicalEntryPoint.Declaration::entryPointClass)
                .contains("io.matrix.knowledge.LearningWorkbenchResource",
                        "io.matrix.api.OpenAIChatResource");
        // The learning loop of the W44 kernel is one of the cycles the application runs.
        assertThat(declarations.stream()
                .flatMap(declaration -> CanonicalEntryPoint.classesOf(declaration).stream()))
                .contains("io.matrix.cognitive.CognitiveKernel");
    }

    /** Whether the class of the given name can be loaded by this module. */
    private static boolean canResolve(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
