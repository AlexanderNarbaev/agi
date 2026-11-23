package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import io.matrix.knowledge.acquisition.DocumentImporter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourceObservationTest {

    @Test
    void resolvesApplicableObservationWithoutPromotingItsStoredClaim(@TempDir Path directory)
            throws Exception {
        String workspace = "synthetic-workspace";
        String statement = "The river station does not send its readings over a satellite link.";
        try (KnowledgeStore store = new KnowledgeStore(directory.resolve("knowledge.sqlite"))) {
            Path original = directory.resolve("original.txt");
            Path observer = directory.resolve("observer.txt");
            Path replacement = directory.resolve("replacement.txt");
            Files.writeString(original, statement + "\n");
            Files.writeString(observer, "Independent observation\n\n" + statement + "\n");
            Files.writeString(replacement, "The river station records air pressure twice a day.\n");
            DocumentImporter importer = new DocumentImporter(store);
            var v1 = importer.importDocument(workspace, original, "en").source();
            var corroboration = importer.importDocument(workspace, observer, "en").source();
            var v2 = importer.importDocument(workspace, replacement, "en").source();
            store.selectSourceRevision(new SourceRevisionSelection("v1", workspace, "station",
                    v1.sourceId(), v1.contentHash(), null));
            store.selectSourceRevision(new SourceRevisionSelection("v2", workspace, "station",
                    v2.sourceId(), v2.contentHash(), "v1"));
            KnowledgeSnapshot view = SourceRevisionView.project(store.exportWorkspace(workspace), null);
            String canonicalId = DocumentImporter.claimId(workspace, v1.contentHash(), statement);
            Claim canonical = view.claims().stream().filter(row -> canonicalId.equals(row.id()))
                    .findFirst().orElseThrow();
            var resolved = SourceObservation.resolve(view, canonical, corroboration.sourceId()).orElseThrow();
            assertThat(resolved.claim().id()).isEqualTo(
                    DocumentImporter.claimId(workspace, corroboration.contentHash(), statement));
            assertThat(resolved.claim().id()).isNotEqualTo(canonicalId);
            assertThat(resolved.claim().status()).isEqualTo(ClaimStatus.SUPERSEDED);
            assertThat(resolved.source().contentHash()).isEqualTo(corroboration.contentHash());
            assertThat(ClaimSpan.parse(resolved.claim().context())).isNotNull();
            assertThat(SourceObservation.resolve(view, canonical, v1.sourceId())).isEmpty();
            assertThat(SourceObservation.resolve(view, canonical, v2.sourceId())).isEmpty();
        }
    }
}
