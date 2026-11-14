package io.matrix.cognitive.distributed;

import io.matrix.cognitive.CognitiveStore;
import io.matrix.cognitive.DispatchIntent;
import io.matrix.cognitive.RemoteReportAssessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W53 — the reason of a dispatch never stands in for the disposition of that dispatch.
 *
 * <p><b>What this test holds.</b> A dispatch row carries two different facts and keeps
 * them in two different columns. The <b>disposition</b> is chain A's domain verdict about
 * the peer's answer, and it is one of the named states of
 * {@link RemoteReportAssessor.Disposition}. The <b>reason</b> is why chain A decided that,
 * and it is free text. A dispatch that was closed without an answer never had a
 * disposition at all — there was nothing to judge — so its disposition column stays empty
 * and its reason goes into the reason column. A dispatch that was judged carries the
 * domain state in the disposition column and the explanation in the reason column.</p>
 *
 * <p><b>Why the separation matters.</b> A free-text reason written into the disposition
 * column turns a domain state into prose: the reader can no longer ask "is this dispatch
 * supported or not" without parsing an English sentence, and a downstream map that reads
 * the disposition as a state receives a value it cannot interpret. The two facts have
 * different lifetimes as well: the reason of a closure exists without any adjudication,
 * and the disposition of an adjudication exists with one.</p>
 *
 * <p><b>What it proves.</b> The closure of a dispatch writes the state and the reason and
 * leaves the disposition empty; the adjudication writes the disposition and the reason and
 * leaves the two apart. The negative control is the second case: with the domain state
 * written, the row says exactly what chain A concluded and exactly why.</p>
 */
class DispatchDispositionSeparationTest {

    /** The workspace the dispatches of this test live in. */
    private static final String WORKSPACE = "the-workspace";

    /** The instant the fixtures claim. */
    private static final String AT = "2026-11-13T10:00:00Z";

    /** The request id of the dispatch of this test. */
    private static final String REQUEST_ID = "req-disposition";

    /** The reason of the closure — free text, and nothing else. */
    private static final String CLOSURE_REASON = "the peer never answered within the budget";

    /** The reason of the adjudication — free text again. */
    private static final String ADJUDICATION_REASON =
            "the report agreed with the claim chain A could check on its own";

    @TempDir
    Path directory;

    /**
     * A dispatch that was closed without an answer has no disposition.
     */
    @Test
    @DisplayName("a dispatch closed without an answer keeps its disposition empty")
    void aClosureCarriesNoDisposition() {
        Path database = directory.resolve("cognitive.sqlite");
        try (CognitiveStore store = new CognitiveStore(database)) {
            store.recordDispatchIntent(intent());
            store.closeDispatchAsFailed(REQUEST_ID, CLOSURE_REASON, AT);

            DispatchIntent closed = store.findDispatch(REQUEST_ID).orElseThrow();

            assertThat(closed.state()).isEqualTo(DispatchIntent.State.FAILED);
            assertThat(closed.disposition()).isNullOrEmpty();
            assertThat(closed.dispositionReason()).isEqualTo(CLOSURE_REASON);
        }
    }

    /**
     * An adjudicated dispatch carries the domain state in its own column.
     */
    @Test
    @DisplayName("an adjudicated dispatch carries the domain state in the disposition")
    void anAdjudicationCarriesTheDomainState() {
        Path database = directory.resolve("cognitive.sqlite");
        try (CognitiveStore store = new CognitiveStore(database)) {
            store.recordDispatchIntent(intent());
            String disposition = RemoteReportAssessor.Disposition.SUPPORTED.name();
            store.recordDispatchDisposition(REQUEST_ID, disposition, ADJUDICATION_REASON, AT);

            DispatchIntent adjudicated = store.findDispatch(REQUEST_ID).orElseThrow();

            assertThat(adjudicated.disposition()).isEqualTo(disposition);
            assertThat(adjudicated.dispositionReason()).isEqualTo(ADJUDICATION_REASON);
            assertThat(adjudicated.state()).isEqualTo(DispatchIntent.State.ADJUDICATED);
        }
    }

    /**
     * The reason never stands in for the domain state, in either direction.
     */
    @Test
    @DisplayName("the reason is never read as the domain state")
    void theReasonIsNeverTheDomainState() {
        Path database = directory.resolve("cognitive.sqlite");
        try (CognitiveStore store = new CognitiveStore(database)) {
            store.recordDispatchIntent(intent());
            store.closeDispatchAsFailed(REQUEST_ID, CLOSURE_REASON, AT);

            DispatchIntent closed = store.findDispatch(REQUEST_ID).orElseThrow();

            assertThat(closed.disposition()).isNotEqualTo(CLOSURE_REASON);
            assertThat(closed.dispositionReason()).isNotEqualTo(closed.disposition());
        }
    }

    /**
     * The disposition of an adjudication is one of the named states of the domain.
     */
    @Test
    @DisplayName("the disposition of an adjudication is a named state of the domain")
    void theDispositionIsANamedState() {
        Path database = directory.resolve("cognitive.sqlite");
        try (CognitiveStore store = new CognitiveStore(database)) {
            store.recordDispatchIntent(intent());
            store.recordDispatchDisposition(REQUEST_ID,
                    RemoteReportAssessor.Disposition.UNRESOLVED.name(),
                    "the answer could neither be confirmed nor refuted", AT);

            DispatchIntent adjudicated = store.findDispatch(REQUEST_ID).orElseThrow();

            assertThat(adjudicated.disposition())
                    .isIn(RemoteReportAssessor.Disposition.SUPPORTED.name(),
                            RemoteReportAssessor.Disposition.QUALIFIED.name(),
                            RemoteReportAssessor.Disposition.REJECTED.name(),
                            RemoteReportAssessor.Disposition.INVALID.name(),
                            RemoteReportAssessor.Disposition.UNRESOLVED.name());
        }
    }

    /** One dispatch intent of this test. */
    private static DispatchIntent intent() {
        return new DispatchIntent(REQUEST_ID, "tsk-disposition", "ep-disposition", WORKSPACE,
                "brain.cognitive-inference", "hash-disposition",
                DispatchIntent.State.DISPATCH_PENDING, null, null, null, null, null, null,
                null, AT, AT);
    }
}
