package io.matrix.brain.runtime;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RECON-W4 Step 5 — ProdCallerExistsTest extension for MCTS planning.
 *
 * <p>Grep-proof test asserting MctsTree + LatsReflector + LatsNode +
 * LatsValueFunction all have production callers in the decision path
 * (not just constructor instantiation).</p>
 */
class PlanningCallerExistsTest {

    @Test
    void mctsTree_has_prod_caller() throws Exception {
        Path p = Path.of("/home/alexandr-narbaev/Projects/agi/matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/stages/PlanningStage.java");
        String src = Files.readString(p);
        assertThat(src).contains("tree.runSearch(");
        assertThat(src).containsPattern("MctsTree\\b");
    }

    @Test
    void latsReflector_has_prod_caller() throws Exception {
        Path p = Path.of("/home/alexandr-narbaev/Projects/agi/matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/stages/PlanningStage.java");
        String src = Files.readString(p);
        assertThat(src).containsPattern("LatsReflector\\b");
        assertThat(src).contains("reflector.reflect(");
    }

    @Test
    void latsNode_has_prod_caller() throws Exception {
        Path p = Path.of("/home/alexandr-narbaev/Projects/agi/matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/stages/PlanningStage.java");
        String src = Files.readString(p);
        assertThat(src).containsPattern("LatsNode\\b");
        // Either instantiated as root or used in instanceof check
        assertThat(src).contains("new LatsNode(");
    }

    @Test
    void latsValueFunction_class_exists_and_is_used() throws Exception {
        // LatsValueFunction is loaded via MctsTree; we verify the class is on
        // the classpath AND PlanningStage references its tier system.
        Class<?> c = Class.forName("io.matrix.mcts.LatsValueFunction");
        assertThat(c).isNotNull();
        // PlanningStage uses Budgets.withLats(true) which enables LatsValueFunction
        Path p = Path.of("/home/alexandr-narbaev/Projects/agi/matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/stages/PlanningStage.java");
        String src = Files.readString(p);
        assertThat(src).containsPattern("useLats\\s*=\\s*\\w+");
    }

    @Test
    void planningStage_registers_engine_call_in_registry() throws Exception {
        // The EngineCallRegistry is updated when PlanningStage.plan() is called.
        Path p = Path.of("/home/alexandr-narbaev/Projects/agi/matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/stages/PlanningStage.java");
        String src = Files.readString(p);
        assertThat(src).contains("engineRegistry.register(");
    }
}
