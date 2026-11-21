package io.matrix.cognitive.report;

import io.matrix.cognitive.algorithms.AlgorithmFamilies;
import io.matrix.cognitive.algorithms.Procedure;
import io.matrix.cognitive.algorithms.ValidationKind;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * CRJ-01 / OD-082 — the domain-A procedure: a validated support set, and the codec that
 * carries it in the portfolio's own durable record.
 *
 * <p><b>What a procedure of this family is.</b> The W44 directive's procedure is "an expensive
 * reasoning result turned into a cheaper path, with the conditions under which it may be used".
 * For the knowledge-report family the expensive part is the workspace's full retrieval: the
 * asking path reads every claim of the workspace, binds every concept and scores every term.
 * A procedure of this family says <i>which sources carried the support</i> — the set that the
 * validation showed was sufficient — and the path it stands for answers the question from that
 * set alone. It is therefore a genuinely cheaper path, and the claim that it is CHEAPER is
 * measured by the planner's own cost ledger, not asserted here.</p>
 *
 * <p><b>What it is not.</b> It is not a cached answer. The record carries no answer, no text
 * and no claim of its own: it carries the SUPPORT SET, and the support set is applied to
 * whatever question is asked. A cached answer is reusable only for equivalent conditions;
 * this is reusable for a different question in the same domain, which is the transfer the
 * CRJ-01 contract asks for ("REUSE — different related task retrieves accepted
 * knowledge/procedure and changes the choice, prediction or result").</p>
 *
 * <p><b>Why the support set lives in the applicability field.</b> The procedure's applicability
 * IS its domain, and the domain of this family is exactly the set of sources its validation
 * covered. The record's own javadoc says the applicability is "the domain it may be used in";
 * here that domain is named concretely, in a canonical form, so that the durable record is the
 * whole definition and a procedure that survives a restart is still executable. No column is
 * added to any table, and no second registry is created.</p>
 *
 * <p><b>Why the applicability is checked at use time.</b> A support set is a claim about the
 * workspace's present: if a source is gone, the domain no longer holds, the procedure is not
 * applicable, and the reference method answers instead. That is the drift rule of the existing
 * store, expressed for this family.</p>
 */
public final class ReportProcedure {

    /** The family the procedure of this kind belongs to. */
    public static final String FAMILY = AlgorithmFamilies.KNOWLEDGE_REPORT;

    /** The reference method the procedure falls back to when its domain no longer holds. */
    public static final String REFERENCE_ID = "report.journey.reference.v1";

    /** The prefix of the procedure's identity. */
    public static final String ID_PREFIX = "report.procedure.";

    /** The version of the definition this codec writes and reads. */
    public static final String VERSION = "1.0.0";

    /**
     * The fact that says a procedure's support set is present in the workspace as it is now.
     *
     * <p>The fact is per procedure, because two procedures of this family may have different
     * support sets, and one workspace may hold one of them and not the other. The selector
     * admits a method only when the request establishes every precondition the method declares,
     * so a procedure whose sources are gone is not selected — the reference method takes
     * over, which is the fallback the record names.</p>
     */
    public static final String FACT_SUPPORT_PRESENT_PREFIX = "report.procedure.support-present.";

    /** The key the support set is stored under inside the applicability field. */
    private static final String KEY_SUPPORT = "support";

    /** The key the domain's description is stored under inside the applicability field. */
    private static final String KEY_DOMAIN = "domain";

    /** The separator between the two keys of the applicability field. */
    private static final String SEPARATOR = "|";

    /**
     * The fact that says a procedure's support set is present.
     *
     * @param procedureId the procedure's identity
     * @return the fact
     */
    public static String supportPresentFact(String procedureId) {
        return FACT_SUPPORT_PRESENT_PREFIX + procedureId;
    }

    /**
     * The identity of the procedure for a support set.
     *
     * <p>The identity is derived from the SUPPORT SET, so the same support set always names
     * the same procedure — and a different support set is a different procedure, not a new
     * version of the same one. A new version of a procedure is a change of its DEFINITION, and
     * the definition is the support set.</p>
     *
     * @param support the source ids the procedure is supported by
     * @return the procedure id
     */
    public static String idFor(Set<String> support) {
        List<String> sorted = support == null ? List.of() : support.stream().sorted().toList();
        return ID_PREFIX + Integer.toHexString(sorted.hashCode()) + "."
                + Integer.toHexString(String.join(",", sorted).hashCode());
    }

    /**
     * The applicability field of a procedure with the given support set.
     *
     * @param support the source ids
     * @param domain the domain's description, in words a reader can act on
     * @return the applicability
     */
    public static String applicabilityFor(Set<String> support, String domain) {
        List<String> sorted = support == null ? List.of() : support.stream().sorted().toList();
        return KEY_SUPPORT + "=" + String.join(",", sorted)
                + SEPARATOR + KEY_DOMAIN + "="
                + (domain == null || domain.isBlank() ? "the validated support set" : domain);
    }

    /**
     * The support set a procedure carries.
     *
     * @param procedure the procedure
     * @return the source ids, or an empty set when the record carries no readable support set
     */
    public static Set<String> supportOf(Procedure procedure) {
        if (procedure == null || procedure.applicability() == null) {
            return Set.of();
        }
        for (String part : procedure.applicability().split("\\" + SEPARATOR)) {
            if (!part.startsWith(KEY_SUPPORT + "=")) {
                continue;
            }
            String value = part.substring((KEY_SUPPORT + "=").length()).trim();
            if (value.isEmpty()) {
                return Set.of();
            }
            Set<String> support = new LinkedHashSet<>();
            for (String id : value.split(",")) {
                String trimmed = id.trim();
                if (!trimmed.isEmpty()) {
                    support.add(trimmed);
                }
            }
            return support;
        }
        return Set.of();
    }

    /**
     * The procedure record of a support set, with its measured standing and its provenance.
     *
     * @param support        the source ids the validation covered
     * @param domain         the domain's description
     * @param validationCases how many held-out cases the agreement was measured over
     * @param agreement      the measured agreement with the reference, over those cases
     * @param learnedInEpisode the episodes the support set was learned from
     * @param validationNote the note that says what the standing rests on
     * @param promotedAt     the instant the procedure entered the active set
     * @return the record
     */
    public static Procedure record(Set<String> support, String domain, int validationCases,
            double agreement, String learnedInEpisode, String validationNote, String promotedAt) {
        Set<String> facts = Set.of(supportPresentFact(idFor(support)));
        return new Procedure(
                idFor(support),
                "the validated support set of " + (support == null ? 0 : support.size())
                        + " source(s)",
                FAMILY,
                VERSION,
                applicabilityFor(support, domain),
                facts,
                REFERENCE_ID,
                validationCases,
                agreement,
                0,
                0,
                promotedAt,
                null,
                ValidationKind.INDEPENDENT,
                learnedInEpisode,
                validationNote);
    }

    private ReportProcedure() {
    }
}
