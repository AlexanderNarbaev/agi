package io.matrix.cognitive;

import io.matrix.contract.bridge.CognitiveTaskReport;
import io.matrix.contract.bridge.CognitiveTaskRequest;
import io.matrix.contract.bridge.CognitiveTaskResponse;

import java.util.List;
import java.util.Locale;

/**
 * W53 — chain A's own evaluation of what the peer said.
 *
 * <p><b>The rule this class exists to enforce.</b> The mandate's central rule is that a
 * remote cognitive process is a capability provider and its output is a report that must
 * be evaluated, not an automatic source of truth. A completed HTTP exchange is not a
 * result; a peer's confidence is not evidence; a report is not a belief. This class is
 * where that rule is made mechanical.</p>
 *
 * <p><b>What it decides.</b> Given the request, the peer's response, the peer's report,
 * what chain A already knows about the proposition, and what chain A itself supplied to
 * the peer earlier, it produces one disposition and one reason. The dispositions are the
 * ones the mandate names:</p>
 * <ul>
 *   <li>{@code INVALID} — the answer does not answer the task: a different request, a
 *       payload that does not match the one that was sent, a missing provenance, or a
 *       result that never arrived;</li>
 *   <li>{@code REJECTED} — chain A's own evidence contradicts the proposition;</li>
 *   <li>{@code QUALIFIED} — the proposition is supported, but the report is not
 *       independent: it rests on what chain A supplied to the peer, so it is the same
 *       information coming back and not a second observation of it;</li>
 *   <li>{@code SUPPORTED} — chain A's own evidence supports the proposition and the
 *       report is independent of what chain A supplied;</li>
 *   <li>{@code UNRESOLVED} — the report arrived, it is well formed, and chain A's own
 *       evidence settles nothing;</li>
 *   <li>{@code ABSTAINED} — chain A declines to answer.</li>
 * </ul>
 *
 * <p><b>What it refuses to do.</b> It never promotes a report to an established belief,
 * it never treats a peer's confidence as support, and it never says "supported" on the
 * strength of the peer's own word. The remote result can affect the original task only
 * through a disposition that chain A wrote itself.</p>
 *
 * <p><b>Why the evidence is a port.</b> The assessor does not read the knowledge store
 * itself: it asks for the support and the contradiction that chain A's own records
 * carry about the proposition. The coordinator wires that port to the store, and the
 * tests wire it to a list. The rule stays the same either way.</p>
 */
public final class RemoteReportAssessor {

    /**
     * What chain A knows about a proposition.
     *
     * <p>The two counts are separate on purpose. A record that supports a proposition and
     * a record that contradicts it are different evidence, and a system that adds them
     * together has thrown away the only thing that makes an evaluation an evaluation.</p>
     */
    public interface LocalEvidence {

        /**
         * The records that support the proposition.
         *
         * @param proposition the proposition
         * @return the number of supporting records
         */
        int supporting(String proposition);

        /**
         * The records that contradict the proposition.
         *
         * @param proposition the proposition
         * @return the number of contradicting records
         */
        int contradicting(String proposition);

        /**
         * The propositions of the records this process supplied to the peer.
         *
         * <p><b>Why the question is asked.</b> A peer may state the very proposition this
         * process supplied and leave its lineage undeclared. The lineage is the peer's
         * word about where the information came from; the proposition is the thing itself.
         * A report that states what this process already sent is the same information
         * coming back, and it must not be counted as an independent observation — whether
         * or not the peer declared it.</p>
         *
         * @param recordIds the records this process supplied
         * @return their propositions, which may be empty
         */
        default List<String> propositionsOf(List<String> recordIds) {
            return List.of();
        }

        /** The evidence of a chain A that knows nothing. */
        LocalEvidence NONE = new LocalEvidence() {
            @Override
            public int supporting(String proposition) {
                return 0;
            }

            @Override
            public int contradicting(String proposition) {
                return 0;
            }
        };
    }

    /**
     * The verdict.
     *
     * @param disposition what chain A decided
     * @param reason      why, in words that a reader can check
     * @param independent whether the report was independent of what chain A supplied
     * @param returning   the records that came back, when the report is not independent
     */
    public record Verdict(Disposition disposition, String reason, boolean independent,
                          List<String> returning) {
    }

    /**
     * The verdicts chain A can reach.
     */
    public enum Disposition {
        /** The answer does not answer the task. */
        INVALID,
        /** Chain A's own evidence contradicts the proposition. */
        REJECTED,
        /** The proposition is supported, but the report is not independent. */
        QUALIFIED,
        /** Chain A's own evidence supports the proposition and the report is independent. */
        SUPPORTED,
        /** Nothing settles the proposition. */
        UNRESOLVED,
        /** Chain A declines to answer. */
        ABSTAINED
    }

    private final LocalEvidence evidence;

    /**
     * The assessor.
     *
     * @param evidence what chain A knows about propositions
     */
    public RemoteReportAssessor(LocalEvidence evidence) {
        this.evidence = evidence == null ? LocalEvidence.NONE : evidence;
    }

    /**
     * Evaluates the peer's answer.
     *
     * @param request  the request that was sent
     * @param response the peer's response, when there was one
     * @param report   the peer's report, when there was one
     * @param supplied the records chain A supplied to the peer, for the circularity check
     * @return the verdict
     */
    public Verdict assess(CognitiveTaskRequest request, CognitiveTaskResponse response,
                           CognitiveTaskReport report, List<String> supplied) {
        if (request == null) {
            return new Verdict(Disposition.INVALID, "there was no request to judge the "
                    + "answer against", false, List.of());
        }
        if (response == null) {
            return new Verdict(Disposition.INVALID, "the peer answered the message but "
                    + "produced no result", false, List.of());
        }
        if (!request.requestId().equals(response.requestId())) {
            return new Verdict(Disposition.INVALID, "the response answers a different "
                    + "request than the one that was sent", false, List.of());
        }
        if (!request.payloadHash().equals(response.requestPayloadHash())) {
            return new Verdict(Disposition.INVALID, "the response answers a different "
                    + "payload than the one that was sent", false, List.of());
        }
        if (!request.requiredCapability().equals(response.capabilityId())) {
            return new Verdict(Disposition.INVALID, "the peer answered with a different "
                    + "capability than the one that was asked for", false, List.of());
        }
        if (response.provenance() == null || response.provenance().recordedAt() == null) {
            return new Verdict(Disposition.INVALID, "the response carries no provenance",
                    false, List.of());
        }
        if (response.status() != CognitiveTaskResponse.Status.COMPLETED) {
            return new Verdict(Disposition.UNRESOLVED, "the peer did not complete the "
                    + "execution: " + response.status(), false, List.of());
        }
        if (report == null) {
            return new Verdict(Disposition.UNRESOLVED, "the peer returned a result but no "
                    + "report, so there is nothing to evaluate", false, List.of());
        }
        String proposition = report.subjectProposition();
        int contradicting = evidence.contradicting(proposition);
        int supporting = evidence.supporting(proposition);
        if (contradicting > 0 && contradicting >= supporting) {
            return new Verdict(Disposition.REJECTED, "chain A's own evidence contradicts "
                    + "the proposition: " + contradicting + " contradicting record(s) against "
                    + supporting + " supporting one(s)", false, List.of());
        }
        List<String> returning = report.returnedRecords(supplied);
        // The lineage the peer declared, and the proposition itself, are two facts. A
        // peer that states the proposition this process supplied has returned the same
        // information whether it says so or not.
        boolean restatesSupplied = restates(report.subjectProposition(), supplied);
        boolean independent = returning.isEmpty() && !restatesSupplied;
        if (restatesSupplied) {
            // The report states the proposition this process supplied. That is the
            // reason it is not independent, and it is said before the counts, so the
            // reason names the real cause rather than an empty lineage.
            return new Verdict(Disposition.QUALIFIED, "the report states the proposition "
                    + "this process supplied to the peer, so it is the same information "
                    + "coming back and not an independent observation", false, returning);
        }
        if (supporting > 0 && independent) {
            return new Verdict(Disposition.SUPPORTED, "chain A's own evidence supports the "
                    + "proposition (" + supporting + " record(s)) and the report is "
                    + "independent of what chain A supplied", true, List.of());
        }
        if (supporting > 0) {
            return new Verdict(Disposition.QUALIFIED, "the proposition is supported by "
                    + "chain A's own evidence, but the report rests on " + returning.size()
                    + " record(s) this process supplied to the peer, so it is the same "
                    + "information coming back and not an independent observation",
                    false, returning);
        }
        if (report.confidenceBasis() == CognitiveTaskReport.ConfidenceBasis.DECLARED
                || report.confidenceBasis() == CognitiveTaskReport.ConfidenceBasis.MEASURED) {
            return new Verdict(Disposition.UNRESOLVED, "the peer's confidence rests on "
                    + report.confidenceBasis() + ", which is not independent evidence, and "
                    + "chain A's own records settle nothing", independent, returning);
        }
        return new Verdict(Disposition.UNRESOLVED, "chain A's own records settle nothing "
                + "about the proposition", independent, returning);
    }

    /**
     * Whether the report states the proposition this process supplied.
     *
     * @param proposition the report's proposition
     * @param supplied    the records this process supplied
     * @return true when the report restates what was supplied
     */
    private boolean restates(String proposition, List<String> supplied) {
        if (proposition == null || proposition.isBlank() || supplied == null
                || supplied.isEmpty()) {
            return false;
        }
        String wanted = normalize(proposition);
        for (String candidate : evidence.propositionsOf(supplied)) {
            if (wanted.equals(normalize(candidate))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Two propositions that differ only in case, spacing and a trailing full stop are
     * the same proposition for this purpose.
     *
     * @param proposition the proposition
     * @return its comparable form
     */
    private static String normalize(String proposition) {
        String normalized = proposition == null ? "" : proposition.trim().toLowerCase(Locale.ROOT);
        normalized = normalized.replaceAll("\\s+", " ");
        while (normalized.endsWith(".") || normalized.endsWith("!")
                || normalized.endsWith("?")) {
            normalized = normalized.substring(0, normalized.length() - 1).trim();
        }
        return normalized;
    }
}
