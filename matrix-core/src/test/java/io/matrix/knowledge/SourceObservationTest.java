package io.matrix.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import io.matrix.contract.Provenance;
import io.matrix.knowledge.acquisition.DocumentImporter;
import java.util.List;
import org.junit.jupiter.api.Test;

class SourceObservationTest {

    private static final String WORKSPACE = "synthetic-workspace";
    private static final String STATEMENT = "The station does not send readings by satellite";
    private static final String RETIRED_SOURCE = "retired";
    private static final String HASH = "f".repeat(64);
    private static final String ACTIVE_SOURCE = DocumentImporter.sourceId(WORKSPACE, HASH);
    private static final String CANONICAL_ID = "canonical";
    private static final String OBSERVATION_ID = DocumentImporter.claimId(WORKSPACE, HASH, STATEMENT);

    @Test
    void resolvesTheActiveObservationWithoutChangingClaimStatus() {
        Claim canonical = claim(CANONICAL_ID, RETIRED_SOURCE, ClaimStatus.VERIFIED, null);
        String context = ClaimSpan.format("synthetic.txt", Provenance.Span.characters(3, 60));
        var view = view(canonical, context, STATEMENT);
        SourceObservation observation = SourceObservation.resolve(view, canonical, ACTIVE_SOURCE).orElseThrow();
        assertThat(observation.claim().id()).isEqualTo(OBSERVATION_ID);
        assertThat(observation.claim().status()).isEqualTo(ClaimStatus.SUPERSEDED);
        assertThat(observation.source().contentHash()).isEqualTo(HASH);
        assertThat(observation.span()).isEqualTo(Provenance.Span.characters(3, 60));
        assertThat(SourceObservation.resolve(view, canonical, RETIRED_SOURCE)).isEmpty();
    }

    @Test
    void rejectsMissingMalformedAndUnboundedSpansAndUnrelatedLiteralText() {
        Claim canonical = claim(CANONICAL_ID, RETIRED_SOURCE, ClaimStatus.VERIFIED, null);
        for (String context : List.of("", "[characters -2-7]", "[characters 3-]")) {
            assertThat(SourceObservation.resolve(view(canonical, context, STATEMENT), canonical, ACTIVE_SOURCE))
                    .isEmpty();
        }
        assertThat(SourceObservation.resolve(
                view(canonical, "[characters 0-50]", "Unrelated sentence"), canonical, ACTIVE_SOURCE))
                .isEmpty();
    }

    private static KnowledgeSnapshot view(Claim canonical, String context, String excerpt) {
        Claim observation = claim(OBSERVATION_ID, ACTIVE_SOURCE, ClaimStatus.SUPERSEDED, context);
        Source source = new Source(ACTIVE_SOURCE, WORKSPACE, SourceKind.HUMAN_ASSERTION,
                "Synthetic observer", null, 0, 0, null, null, HASH, null);
        return new KnowledgeSnapshot(WORKSPACE, WORKSPACE, List.of(), List.of(source),
                List.of(canonical, observation),
                List.of(support("canonical-support", canonical.id(), STATEMENT),
                        support("direct-support", observation.id(), excerpt)),
                List.of(), List.of(), List.of(), null);
    }

    private static Claim claim(String id, String source, ClaimStatus status, String context) {
        return new Claim(id, WORKSPACE, "station", STATEMENT, "station telemetry", context,
                status, 0.0, 1, source, null, null, null, null, null, null);
    }

    private static Evidence support(String id, String claimId, String text) {
        return new Evidence(id, WORKSPACE, claimId, ACTIVE_SOURCE, EvidenceRelation.SUPPORTS, text, 100, null);
    }
}
