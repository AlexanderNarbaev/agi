package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A claim that no source introduced is in no support set — and the restriction says so
 * without throwing.
 *
 * <p><b>Why this test exists.</b> A workspace may hold a claim that names no introducing
 * source. Such a claim is in no support set, so a restricted reading leaves it out — that was
 * the behaviour, because the learner handed the reading path a null-tolerant set and a
 * null-tolerant set answers "no" about a null key. A later change made the support set an
 * <b>immutable</b> copy, and an immutable set <b>throws</b> when it is asked about a null key:
 * the restricted reading of such a workspace died instead of reading. The legacy report-learning
 * suites caught it.</p>
 *
 * <p><b>What is asserted.</b> The restriction is asked for an immutable support set — the form
 * the learner hands it — over a workspace that holds a claim with no introducing source. It
 * must not throw, and the claim with no introducing source must be <b>absent</b> from the
 * restricted view, which is the outcome the null-tolerant set gave.</p>
 *
 * <p><b>Where it runs.</b> Over a temporary database. The owner's store is never touched.</p>
 */
class RestrictedViewNullIntroducedClaimTest {

    /** The workspace the snapshot belongs to. */
    private static final String WORKSPACE = "restricted-view";

    /** The source that introduced one of the two claims. */
    private static final String SOURCE_ID = "src-restricted-view-1";

    @TempDir
    java.nio.file.Path directory;

    /** The workbenches this test opened, so that every one of them is closed. */
    private final List<KnowledgeWorkbench> workbenches = new ArrayList<>();

    @AfterEach
    void tearDown() {
        workbenches.forEach(KnowledgeWorkbench::close);
    }

    @Test
    @DisplayName("a claim that no source introduced is left out, and the restriction does not throw")
    void aClaimWithoutAnIntroducingSourceIsLeftOut() throws Exception {
        KnowledgeWorkbench bench = KnowledgeWorkbench.at(
                Files.createDirectories(directory.resolve("databases")).resolve("restricted.sqlite")
                        .toString());
        workbenches.add(bench);

        KnowledgeSnapshot snapshot = new KnowledgeSnapshot(
                WORKSPACE,
                "the restricted view",
                List.of(),
                List.of(new Source(SOURCE_ID, WORKSPACE, SourceKind.AUTHORITATIVE_REFERENCE,
                        "the document", "doc.md", 80, 80, "an excerpt", "2026-11-28T00:00:00Z",
                        "sha-256:0", "2026-11-28T00:00:00Z")),
                List.of(
                        claim("clm-restricted-view-1", "the introduced claim", SOURCE_ID),
                        claim("clm-restricted-view-2", "the claim no source introduced", null)),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                "2026-11-28T00:00:00Z");

        // THE IMMUTABLE SUPPORT SET — the form the learner hands to the reading path.
        Set<String> support = Set.of(SOURCE_ID);

        assertThatCode(() -> bench.restrictedView(snapshot, support))
                .as("the restricted reading of a workspace that holds a claim with no "
                        + "introducing source must read, not throw")
                .doesNotThrowAnyException();

        KnowledgeSnapshot restricted = bench.restrictedView(snapshot, support);
        assertThat(restricted.claims())
                .as("the claim the source introduced is in the restricted view")
                .extracting(Claim::id)
                .contains("clm-restricted-view-1");
        assertThat(restricted.claims())
                .as("the claim that no source introduced is in no support set and is left out")
                .extracting(Claim::id)
                .doesNotContain("clm-restricted-view-2");
    }

    /**
     * A claim of the workspace.
     *
     * @param id        the claim's id
     * @param text      what the claim says
     * @param sourceId  the source that introduced it; null means no source did
     * @return the claim
     */
    private static Claim claim(String id, String text, String sourceId) {
        return new Claim(id, WORKSPACE, "concept-" + id, text, "scope", "context",
                ClaimStatus.SUPPORTED, 0.9, 1, sourceId, "extracted", "validated",
                ClaimWarrant.OBSERVED, DerivationStatus.NOT_DERIVED, "2026-11-28T00:00:00Z",
                "2026-11-28T00:00:00Z");
    }
}
