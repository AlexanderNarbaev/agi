package io.matrix.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AgentGenomeTest {

    @Test
    void shouldCreateDefaultGenome() {
        AgentGenome genome = AgentGenome.defaults();

        assertThat(genome.id()).isEqualTo("genome-initial-0");
        assertThat(genome.status()).isEqualTo(AgentGenome.Status.CANDIDATE);
        assertThat(genome.generation()).isZero();
        assertThat(genome.parentId()).isNull();
        assertThat(genome.stageOrder()).containsExactly("observe", "think", "act", "verify");
    }

    @Test
    void shouldHaveDefaultMemoryConfig() {
        AgentGenome genome = AgentGenome.defaults();
        AgentGenome.MemoryConfig mem = genome.memoryConfig();

        assertThat(mem.maxEntries()).isEqualTo(1000);
        assertThat(mem.compactionStrategy()).isEqualTo("HYBRID");
        assertThat(mem.importanceThreshold()).isEqualTo(0.3);
        assertThat(mem.driftDetection()).isTrue();
    }

    @Test
    void shouldHaveDefaultRagConfig() {
        AgentGenome genome = AgentGenome.defaults();
        AgentGenome.RagConfig rag = genome.ragConfig();

        assertThat(rag.topK()).isEqualTo(5);
        assertThat(rag.adaptiveContext()).isTrue();
        assertThat(rag.kneeSensitivity()).isEqualTo(0.5);
        assertThat(rag.strongThreshold()).isEqualTo(0.32);
        assertThat(rag.borderlineThreshold()).isEqualTo(0.25);
        assertThat(rag.hybridSearch()).isTrue();
        assertThat(rag.structureAware()).isTrue();
    }

    @Test
    void shouldHaveDefaultSafetyConstraints() {
        AgentGenome genome = AgentGenome.defaults();
        AgentGenome.SafetyConstraints safety = genome.safetyConstraints();

        assertThat(safety.removedTools()).contains("delete_database", "drop_table", "format_disk");
        assertThat(safety.gatedOperations()).contains("deploy_production", "modify_ethics");
        assertThat(safety.maxAutonomy()).isEqualTo(0.7);
        assertThat(safety.structuralBlocking()).isTrue();
    }

    @Test
    void shouldMutateWithPromptPatch() {
        AgentGenome original = AgentGenome.defaults();
        AgentGenome mutated = original.withPromptPatch("think", "Use more aggressive reasoning");

        assertThat(mutated.id()).isNotEqualTo(original.id());
        assertThat(mutated.promptPatches()).containsEntry("think", "Use more aggressive reasoning");
        assertThat(mutated.generation()).isEqualTo(1);
        assertThat(mutated.parentId()).isEqualTo(original.id());
        assertThat(mutated.status()).isEqualTo(AgentGenome.Status.CANDIDATE);
    }

    @Test
    void shouldMutateWithStageOrder() {
        AgentGenome original = AgentGenome.defaults();
        AgentGenome mutated = original.withStageOrder(List.of("observe", "act", "think", "verify"));

        assertThat(mutated.stageOrder()).containsExactly("observe", "act", "think", "verify");
        assertThat(mutated.generation()).isEqualTo(1);
    }

    @Test
    void shouldUpdateFitness() {
        AgentGenome genome = AgentGenome.defaults();
        AgentGenome.FitnessScore fitness = AgentGenome.FitnessScore.compute(0.9, 0.8, 0.7, 0.3);
        AgentGenome evaluated = genome.withFitness(fitness);

        assertThat(evaluated.status()).isEqualTo(AgentGenome.Status.EVALUATED);
        assertThat(evaluated.fitness()).isNotNull();
        assertThat(evaluated.fitness().quality()).isEqualTo(0.9);
        assertThat(evaluated.fitness().overall()).isGreaterThan(0);
    }

    @Test
    void shouldUpdateStatus() {
        AgentGenome genome = AgentGenome.defaults();
        AgentGenome approved = genome.withStatus(AgentGenome.Status.ACTIVE);

        assertThat(approved.status()).isEqualTo(AgentGenome.Status.ACTIVE);
    }

    @Test
    void shouldComputeFitnessScore() {
        AgentGenome.FitnessScore score = AgentGenome.FitnessScore.compute(0.9, 0.8, 0.7, 0.3);

        // overall = 0.4*0.9 + 0.3*0.8 + 0.2*0.7 + 0.1*(1.0-0.3)
        //        = 0.36 + 0.24 + 0.14 + 0.07 = 0.81
        assertThat(score.overall()).isEqualTo(0.81, org.assertj.core.api.Assertions.within(0.01));
    }

    @Test
    void shouldHaveToolSets() {
        AgentGenome genome = AgentGenome.defaults();

        assertThat(genome.toolSets()).containsKey("observe");
        assertThat(genome.toolSets()).containsKey("think");
        assertThat(genome.toolSets()).containsKey("act");
        assertThat(genome.toolSets()).containsKey("verify");
        assertThat(genome.toolSets().get("think")).contains("boolean_reason", "rag_query");
    }
}
