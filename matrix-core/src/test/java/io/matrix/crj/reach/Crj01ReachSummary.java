package io.matrix.crj.reach;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CRJ-01 / COMPETENCE-REACH — the aggregation of the six-arm grid.
 *
 * <p><b>What this computes and what it deliberately does not.</b> It computes, for every cell
 * (a domain and a condition) and every scope strategy, the two criteria's counts side by side
 * with <b>their own denominators</b>: the admissions, the refusals, the false admissions over the
 * admissions, the false rejections over the refusals, the evidence completeness, the fallbacks
 * and the coverage. It then says whether the two criteria <b>differ</b> on that cell — and
 * nothing more.</p>
 *
 * <p><b>Why there is no winner here.</b> The pre-registration forbids a universal threshold and
 * a predetermined winner, and a summary that ranked the arms would be exactly that. The verdict
 * is one of two values — {@code DIFFERENCE OBSERVED} or {@code NOT ESTABLISHED} — and the counts
 * it is read from are published with it, so a reader who disagrees with the verdict can re-derive
 * it from the rows.</p>
 *
 * <p><b>Why the denominators are never folded together.</b> A refusal and an admission are
 * different events with different costs: a refusal falls back to a path that reads the whole
 * workspace, and an admission may serve a degraded answer. A rate that mixed them would let a
 * change in the mix masquerade as a change in the rule's quality.</p>
 */
public final class Crj01ReachSummary {

    /** The verdict of a cell whose compared quantities differ. */
    public static final String DIFFERENCE_OBSERVED = "DIFFERENCE OBSERVED";

    /** The verdict of a cell whose compared quantities do not differ. */
    public static final String NOT_ESTABLISHED = "NOT ESTABLISHED";

    private Crj01ReachSummary() {
    }

    /**
     * The aggregation of the grid.
     *
     * @param scopes the candidate scopes and their validations
     * @param rows   the measured rows
     * @return the aggregation
     */
    public static Map<String, Object> summarise(List<Crj01ReachGrid.ScopeRow> scopes,
            List<Crj01ReachGrid.Row> rows) {
        Map<String, Map<String, Map<String, List<Crj01ReachGrid.Row>>>> byCell =
                new LinkedHashMap<>();
        for (Crj01ReachGrid.Row row : rows) {
            byCell.computeIfAbsent(row.domain() + "/" + row.condition(),
                    key -> new LinkedHashMap<>())
                    .computeIfAbsent(row.strategy(),
                            key -> new LinkedHashMap<>())
                    .computeIfAbsent(row.criterion(),
                            key -> new ArrayList<>())
                    .add(row);
        }
        List<Map<String, Object>> cells = new ArrayList<>();
        for (Map.Entry<String, Map<String, Map<String, List<Crj01ReachGrid.Row>>>> cell
                : byCell.entrySet()) {
            String[] parts = cell.getKey().split("/", 2);
            String domain = parts[0];
            String condition = parts.length > 1 ? parts[1] : "";
            Map<String, Object> cellRow = new LinkedHashMap<>();
            cellRow.put("domain", domain);
            cellRow.put("condition", condition);
            List<Map<String, Object>> strategies = new ArrayList<>();
            for (Map.Entry<String, Map<String, List<Crj01ReachGrid.Row>>> strategy
                    : cell.getValue().entrySet()) {
                Map<String, Object> strategyRow = new LinkedHashMap<>();
                strategyRow.put("strategy", strategy.getKey());
                Crj01ReachGrid.ScopeRow scope = scopeOf(scopes, domain, condition,
                        strategy.getKey());
                if (scope != null) {
                    strategyRow.put("scope", scope.support());
                    strategyRow.put("scopeSize", scope.support().size());
                    strategyRow.put("validated", scope.validated());
                    strategyRow.put("validationReason", scope.validationReason());
                    strategyRow.put("validationAgreement", scope.agreement());
                    strategyRow.put("validationCases", scope.validationCases());
                    strategyRow.put("proposalNanos", scope.proposalNanos());
                    strategyRow.put("validationNanos", scope.validationNanos());
                }
                Map<String, Object> arms = new LinkedHashMap<>();
                Map<String, Object> byKind = new LinkedHashMap<>();
                for (Map.Entry<String, List<Crj01ReachGrid.Row>> arm : strategy.getValue().entrySet()) {
                    arms.put(arm.getKey(), counts(arm.getValue()));
                    // THE BREAKDOWN BY THE KIND OF THE CASE, in its own map: the arms map
                    // holds the two criteria and nothing else, so a reader (and the gate)
                    // never has to guess which key is an arm.
                    byKind.put(arm.getKey(), breakdown(arm.getValue()));
                }
                strategyRow.put("arms", arms);
                strategyRow.put("byKind", byKind);
                strategyRow.put("criterionDifference", difference(arms));
                strategies.add(strategyRow);
            }
            cellRow.put("strategies", strategies);
            cells.add(cellRow);
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("cells", cells);
        summary.put("verdictVocabulary", List.of(DIFFERENCE_OBSERVED, NOT_ESTABLISHED));
        summary.put("denominatorNote", "the false admissions are counted over the ADMISSIONS and "
                + "the false rejections over the REFUSALS; the two denominators are separate and "
                + "are never folded together");
        summary.put("noWinnerNote", "no arm is ranked here: the pre-registration forbids a "
                + "universal threshold and a predetermined winner, and the verdict of a cell says "
                + "only whether the two criteria differ on it");
        return summary;
    }

    /**
     * The counts of one arm.
     *
     * @param rows the rows of one arm
     * @return the counts
     */
    private static Map<String, Object> counts(List<Crj01ReachGrid.Row> rows) {
        int admissions = 0;
        int refusals = 0;
        int falseAdmissions = 0;
        int falseRejections = 0;
        int fallbacks = 0;
        double completeness = 0.0;
        double coverage = 0.0;
        long selection = 0L;
        long read = 0L;
        long fallbackCost = 0L;
        long total = 0L;
        for (Crj01ReachGrid.Row row : rows) {
            if (row.admitted()) {
                admissions++;
            } else {
                refusals++;
                fallbacks++;
            }
            if (row.falseAdmission()) {
                falseAdmissions++;
            }
            if (row.falseRejection()) {
                falseRejections++;
            }
            if (row.admitted()) {
                // THE COMPLETENESS OF THE ARM'S OWN PATH: a refused row was served by the
                // reference, whose completeness is 1.0 by construction.
                completeness += row.completeness();
            }
            coverage += row.coverage();
            selection += row.selectionNanos();
            read += row.readNanos();
            fallbackCost += row.fallbackNanos();
            total += row.totalNanos();
        }
        int cases = rows.size();
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("cases", cases);
        counts.put("admissions", admissions);
        counts.put("refusals", refusals);
        counts.put("falseAdmissions", falseAdmissions);
        counts.put("falseAdmissionDenominator", admissions);
        counts.put("falseAdmissionRate", rate(falseAdmissions, admissions));
        counts.put("falseRejections", falseRejections);
        counts.put("falseRejectionDenominator", refusals);
        counts.put("falseRejectionRate", rate(falseRejections, refusals));
        counts.put("fallbacks", fallbacks);
        // THE COMPLETENESS OF THE ARM'S OWN PATH: the mean is over the ADMITTED rows, and
        // the fallbacks are counted beside it.
        counts.put("meanCompleteness", admissions == 0 ? 0.0 : round(completeness / admissions));
        counts.put("meanCompletenessNote", "the mean is over the ADMITTED rows: a refused row "
                + "was served by the reference, whose completeness is 1.0 by construction, and a "
                + "mean over every row would mix the two paths");
        counts.put("meanCoverage", cases == 0 ? 0.0 : round(coverage / cases));
        Map<String, Object> cost = new LinkedHashMap<>();
        cost.put("selectionNanos", selection);
        cost.put("readNanos", read);
        cost.put("fallbackNanos", fallbackCost);
        cost.put("totalNanos", total);
        cost.put("note", "the costs are RECORDS, never assertions; the proposal and the "
                + "validation are counted separately and are not part of this path. The "
                + "restricted reading is measured ONCE per scope and shared by the two criteria "
                + "of that scope, so their read costs are the same by construction and the cost "
                + "fields must not be read as a difference between the rules. A refused row "
                + "served nothing from the scope: its read is zero and the reference reading is "
                + "counted once, as the fallback");
        counts.put("cost", cost);
        return counts;
    }

    /**
     * The breakdown of one arm by the kind of the case.
     *
     * <p>The pre-registration asks for the case kinds to be reported as a breakdown within
     * each cell: the kinds that carry the milestone's sharpest question — a second necessary
     * claim, two defensible readings, two sources that contradict, one proposition in two
     * sources, a decoy that is lexically closer than the gold — are the ones a reader must be
     * able to see.</p>
     *
     * @param rows the rows of one arm
     * @return the counts, by kind
     */
    private static Map<String, Object> breakdown(List<Crj01ReachGrid.Row> rows) {
        Map<String, Map<String, Object>> byKind = new LinkedHashMap<>();
        for (Crj01ReachGrid.Row row : rows) {
            Map<String, Object> counts = byKind.computeIfAbsent(row.kind(),
                    key -> new LinkedHashMap<>());
            counts.put("cases", intOf(counts, "cases") + 1);
            counts.put("admissions", intOf(counts, "admissions") + (row.admitted() ? 1 : 0));
            counts.put("refusals", intOf(counts, "refusals") + (row.admitted() ? 0 : 1));
            counts.put("falseAdmissions",
                    intOf(counts, "falseAdmissions") + (row.falseAdmission() ? 1 : 0));
            counts.put("falseRejections",
                    intOf(counts, "falseRejections") + (row.falseRejection() ? 1 : 0));
        }
        Map<String, Object> breakdown = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Object>> entry : byKind.entrySet()) {
            Map<String, Object> counts = entry.getValue();
            counts.put("falseAdmissionDenominator", counts.get("admissions"));
            counts.put("falseRejectionDenominator", counts.get("refusals"));
            breakdown.put(entry.getKey(), counts);
        }
        return breakdown;
    }

    /**
     * The comparison of the two criteria of one strategy.
     *
     * @param arms the arms' counts, by criterion
     * @return the comparison
     */
    private static Map<String, Object> difference(Map<String, Object> arms) {
        Map<String, Object> anchor = countsOf(arms, io.matrix.knowledge.ScopeCriterion.ANCHOR.name());
        Map<String, Object> sufficiency =
                countsOf(arms, io.matrix.knowledge.ScopeCriterion.CLAIM_SUFFICIENCY.name());
        Map<String, Object> difference = new LinkedHashMap<>();
        if (anchor == null || sufficiency == null) {
            difference.put("verdict", NOT_ESTABLISHED);
            difference.put("note", "one of the two criteria produced no rows on this cell");
            return difference;
        }
        int admissions = intOf(sufficiency, "admissions") - intOf(anchor, "admissions");
        int falseAdmissions = intOf(sufficiency, "falseAdmissions") - intOf(anchor, "falseAdmissions");
        int falseRejections = intOf(sufficiency, "falseRejections") - intOf(anchor, "falseRejections");
        double completeness = doubleOf(sufficiency, "meanCompleteness")
                - doubleOf(anchor, "meanCompleteness");
        // THE VERDICT READS THE ADMISSIONS TOO. Two arms can carry the same number of false
        // admissions and the same completeness and still differ in how many cases they
        // admitted at all — and that difference IS a difference of the two rules.
        boolean differs = admissions != 0 || falseAdmissions != 0 || falseRejections != 0
                || completeness != 0.0;
        difference.put("admissions", admissions);
        difference.put("falseAdmissions", falseAdmissions);
        difference.put("falseRejections", falseRejections);
        difference.put("meanCompleteness", round(completeness));
        difference.put("verdict", differs ? DIFFERENCE_OBSERVED : NOT_ESTABLISHED);
        difference.put("note", "the differences are SUFFICIENCY minus ANCHOR on the same scope and "
                + "the same cases; the scope-sufficiency rule is STRICTLY STRONGER, so a negative "
                + "difference in the false admissions and a positive one in the false rejections "
                + "are its signature, not an accident");
        return difference;
    }

    /**
     * The counts of one criterion.
     *
     * @param arms     the arms' counts
     * @param criterion the criterion's name
     * @return the counts, or null when the criterion produced no rows
     */
    private static Map<String, Object> countsOf(Map<String, Object> arms, String criterion) {
        Object counts = arms.get(criterion);
        return counts instanceof Map<?, ?> map ? castMap(map) : null;
    }

    /**
     * The scope row of a strategy.
     *
     * @param scopes    the scope rows
     * @param domain    the domain
     * @param condition the condition
     * @param strategy  the strategy
     * @return the scope row, or null
     */
    private static Crj01ReachGrid.ScopeRow scopeOf(List<Crj01ReachGrid.ScopeRow> scopes,
            String domain, String condition, String strategy) {
        for (Crj01ReachGrid.ScopeRow scope : scopes) {
            if (scope.domain().equals(domain) && scope.condition().equals(condition)
                    && scope.strategy().equals(strategy)) {
                return scope;
            }
        }
        return null;
    }

    /**
     * A rate, or null when the denominator is zero.
     *
     * @param numerator   the numerator
     * @param denominator the denominator
     * @return the rate
     */
    private static Double rate(int numerator, int denominator) {
        if (denominator == 0) {
            return null;
        }
        return round((double) numerator / (double) denominator);
    }

    /**
     * A number, rounded for a reader.
     *
     * @param value the value
     * @return the value, to three decimals
     */
    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    /**
     * A field of a map, as an int.
     *
     * @param map   the map
     * @param key   the field
     * @return the number, or zero
     */
    private static int intOf(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof Number number ? number.intValue() : 0;
    }

    /**
     * A field of a map, as a double.
     *
     * @param map the map
     * @param key the field
     * @return the number, or zero
     */
    private static double doubleOf(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }

    /**
     * A map of unknown keys and values, as a map of strings and objects.
     *
     * @param map the map
     * @return the casted map
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }
}
