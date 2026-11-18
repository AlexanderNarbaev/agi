package io.matrix.knowledge.concept.research;

import io.matrix.knowledge.AbstractionLevel;
import io.matrix.knowledge.Answer;
import io.matrix.knowledge.KnowledgeWorkbench;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The owner's live store, after the migration: the SERVING path still answers.
 *
 * <p>This is the acceptance check of the owner's decision D1-LIVE-STORE-MIGRATION-019:
 * the migrated store must not merely be internally consistent (the orphan audit says
 * that) — the production asking path must still answer from it.</p>
 */
@DisplayName("P1 — the owner's live store after the migration: the asking path still answers")
class LiveStoreServingCheckTest {

    private static final Path LIVE = Path.of("data/knowledge/workbench.sqlite");

    @Test
    @DisplayName("both workspaces: the concepts, the claims and the answers")
    void theMigratedStoreAnswers() throws Exception {
        Path path = LIVE.toAbsolutePath().normalize();
        Assumptions.assumeTrue(Files.isRegularFile(path),
                "the owner's live store is not in this checkout — the check is skipped, not failed");
        try (KnowledgeWorkbench workbench = KnowledgeWorkbench.at(path.toString())) {
            System.out.println("[live-serving] the workspaces: " + workbench.workspaces());
            for (String workspace : List.of("logic", "photosynthesis")) {
                var snapshot = workbench.workspace(workspace);
                System.out.printf("[live-serving] the workspace %s: the concepts %d, the claims %d, "
                        + "the representations %d%n", workspace, snapshot.concepts().size(),
                        snapshot.claims().size(), snapshot.representations().size());
                Answer answer = workbench.ask(workspace, questionFor(snapshot),
                        AbstractionLevel.INTERMEDIATE);
                System.out.printf("[live-serving] the workspace %s: the answer — the statement %s | "
                        + "the answered %s | the support %s | the limitations %s | the gaps %s%n",
                        workspace, answer.statement(), answer.answered(),
                        answer.supportScore(), answer.limitations(), answer.gaps());
                assertThat(answer.statement()).as("the answer of %s", workspace).isNotBlank();
            }
        }
    }

    /** A question about the workspace's first concept. */
    private static String questionFor(io.matrix.knowledge.KnowledgeSnapshot snapshot) {
        String name = snapshot.concepts().stream().findFirst()
                .map(concept -> concept.name()).orElse("the first concept");
        return "What is " + name + "?";
    }
}
