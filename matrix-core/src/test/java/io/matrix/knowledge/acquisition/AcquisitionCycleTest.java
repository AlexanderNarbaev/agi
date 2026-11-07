package io.matrix.knowledge.acquisition;

import io.matrix.knowledge.KnowledgeSnapshot;
import io.matrix.knowledge.KnowledgeWorkbench;
import io.matrix.knowledge.ModelRevisionDecider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The acquisition cycle and the revision decision, measured on the real corpus.
 */
class AcquisitionCycleTest {

    @TempDir
    Path directory;

    @Test
    void theSeedCorpusIsImportedWithItsProvenance() throws Exception {
        KnowledgeWorkbench workbench = KnowledgeWorkbench.at(directory.resolve("matrix.db").toString());

        AcquisitionCycle.Report report =
                AcquisitionCycle.loadSeed(workbench, "plate-tectonics");

        assertThat(report.acquired())
                .containsExactly("usgs-this-dynamic-earth.md", "wikipedia-plate-tectonics.md");
        assertThat(report.steps()).allSatisfy(step -> {
            assertThat(step.licence()).isNotBlank();
            assertThat(step.sourceUrl()).isNotBlank();
            assertThat(step.accessedAt()).isNotBlank();
            assertThat(step.revision()).isNotBlank();
            assertThat(step.claimsProposed()).isGreaterThan(0);
        });
        KnowledgeSnapshot snapshot = workbench.workspace("plate-tectonics");
        assertThat(snapshot.sources()).hasSize(2);
        assertThat(snapshot.claims()).isNotEmpty();
        assertThat(snapshot.evidence()).isNotEmpty();
    }

    @Test
    void theResearchPoolIsACatalogueWithLicencesAndSnapshots() throws Exception {
        ClasspathCorpus pool = ClasspathCorpus.extract(AcquisitionCycle.RESEARCH_POOL);

        assertThat(pool.entries()).hasSizeGreaterThanOrEqualTo(7);
        assertThat(pool.entries()).allSatisfy(entry -> {
            assertThat(entry.licence()).isNotBlank();
            assertThat(entry.licenceUrl()).isNotBlank();
            assertThat(entry.sourceUrl()).isNotBlank();
            assertThat(entry.sha256()).isNotBlank();
            assertThat(entry.revision()).isNotBlank();
        });
    }

    @Test
    void theLearningCycleIsBoundedAndSaysThatItIsALocalPool() throws Exception {
        KnowledgeWorkbench workbench = KnowledgeWorkbench.at(directory.resolve("cycle.db").toString());

        AcquisitionCycle.Report report = AcquisitionCycle.run(workbench, "plate-tectonics",
                "How do we know the continents moved? What is the evidence for continental drift?", 2);

        assertThat(report.documentsAcquired()).isLessThanOrEqualTo(2);
        assertThat(report.boundary()).contains("pre-cached").contains("no request leaves the machine");
        assertThat(report.candidatesConsidered()).isNotEmpty();
        assertThat(report.steps()).allSatisfy(step -> {
            assertThat(step.licence()).isNotBlank();
            assertThat(step.sourceUrl()).isNotBlank();
        });
    }

    @Test
    void theRevisionDecisionComparesEvidenceRatherThanTopics() {
        ModelRevisionDecider.Decision weaker = new ModelRevisionDecider.Decision(false, "c", "old",
                "not stronger", 0.5, 0.9, 0.4);
        assertThat(weaker.revised()).isFalse();

        double related = ModelRevisionDecider.relatedness(
                ModelRevisionDecider.terms("slab pull drives the motion of the tectonic plates"),
                ModelRevisionDecider.terms("slab pull drives the motion of the tectonic plates"));
        assertThat(related).isEqualTo(1.0);

        double unrelated = ModelRevisionDecider.relatedness(
                ModelRevisionDecider.terms("slab pull drives the motion of the tectonic plates"),
                ModelRevisionDecider.terms("the Cambrian explosion changed marine ecosystems"));
        assertThat(unrelated).isLessThan(ModelRevisionDecider.MINIMUM_RELATEDNESS);
    }
}
