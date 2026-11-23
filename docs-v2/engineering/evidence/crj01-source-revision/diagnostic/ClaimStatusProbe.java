import io.matrix.knowledge.KnowledgeStore;
import io.matrix.knowledge.KnowledgeWorkbench;
import io.matrix.knowledge.SourceRevisionSelection;
import io.matrix.knowledge.SourceRevisionView;
import java.nio.file.Path;
import java.util.List;

public class ClaimStatusProbe {
    public static void main(String[] args) throws Exception {
        Path db = Path.of(args[0]);
        Path fixtures = Path.of(args[1]);
        String workspace = "eval-observer";
        try (var bench = KnowledgeWorkbench.at(db.toString()); var store = new KnowledgeStore(db)) {
            var old = bench.importDocument(workspace, fixtures.resolve("v1.txt"), "operations-log.txt", "en").source();
            var replacement = bench.importDocument(workspace, fixtures.resolve("v2.txt"), "operations-log.txt", "en").source();
            var observer = bench.importDocument(workspace, fixtures.resolve("independent-observer.txt"), "observer.txt", "en").source();
            store.selectSourceRevision(new SourceRevisionSelection("observer-root", workspace, "ops-ledger",
                    old.sourceId(), old.contentHash(), null));
            store.selectSourceRevision(new SourceRevisionSelection("observer-successor", workspace, "ops-ledger",
                    replacement.sourceId(), replacement.contentHash(), "observer-root"));
            var archive = bench.workspace(workspace);
            var applicable = SourceRevisionView.project(archive, null);
            var refuter = archive.claims().stream().filter(c -> old.sourceId().equals(c.introducedBySourceId()))
                    .findFirst().orElseThrow();
            for (var claim : archive.claims()) {
                if (!claim.proposition().equals(refuter.proposition())) continue;
                String source = observer.sourceId().equals(claim.introducedBySourceId()) ? "observer" : "v1";
                var links = archive.evidence().stream().filter(e -> claim.id().equals(e.claimId()))
                        .filter(e -> observer.sourceId().equals(e.sourceId())).map(e -> e.relation().name()).toList();
                boolean inView = applicable.claims().stream().anyMatch(c -> c.id().equals(claim.id()));
                boolean equalScope = java.util.Objects.equals(claim.scope(), refuter.scope());
                System.out.printf("introducer=%s claim=%s storedStatus=%s sameScope=%s observerEvidence=%s inView=%s%n",
                        source, claim.id(), claim.status(), equalScope, links, inView);
            }
        }
    }
}
