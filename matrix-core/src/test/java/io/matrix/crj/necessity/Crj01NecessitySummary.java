package io.matrix.crj.necessity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CRJ-01 / EPISTEMIC-NECESSITY — the confusion matrix, with every denominator stated.
 *
 * <p><b>Why the denominators are the point.</b> A false admission and a false rejection are
 * different failures with different denominators: a rule that admits everything has a false
 * admission rate of one and a false rejection rate of <b>undefined</b>, because it never
 * refused anything. Folding the two into a single "accuracy" would hide exactly the difference
 * the milestone measures. Every rate here therefore carries its numerator and its denominator,
 * and a rate whose denominator is empty is <b>UNDEFINED</b> — never {@code 0.0} and never
 * {@code 1.0}.</p>
 *
 * <p><b>What this summary does not contain.</b> A winner. The pre-registration fixes the
 * comparison, not the outcome: a rule with fewer false admissions on this corpus is not thereby
 * the rule that should be served, and the summary says where the two rules differ and what the
 * differences cost, and stops there.</p>
 */
public final class Crj01NecessitySummary {

    /**
     * One rate, with its denominator.
     *
     * @param numerator   the numerator
     * @param denominator the denominator
     * @param value       the rate, or null when the denominator is empty
     * @param label       the formula, so that a reader does not have to guess which is which
     */
    public record Rate(int numerator, int denominator, Double value, String label) {

        /** A rate whose denominator is empty. */
        static Rate undefined(int numerator, String label) {
            return new Rate(numerator, 0, null, label);
        }

        /** Whether the rate is defined. */
        public boolean defined() {
            return value != null;
        }
    }

    /**
     * The counts of one cell.
     *
     * @param tp the true positives
     * @param fp the false positives
     * @param tn the true negatives
     * @param fn the false negatives
     */
    public record Counts(int tp, int fp, int tn, int fn) {

        /** The counts of no case at all. */
        public static Counts none() {
            return new Counts(0, 0, 0, 0);
        }

        /**
         * The counts with one row added.
         *
         * @param classification the confusion-matrix cell of the row
         * @return the counts
         */
        public Counts plus(String classification) {
            return switch (classification) {
                case "TP" -> new Counts(tp + 1, fp, tn, fn);
                case "FP" -> new Counts(tp, fp + 1, tn, fn);
                case "TN" -> new Counts(tp, fp, tn + 1, fn);
                case "FN" -> new Counts(tp, fp, tn, fn + 1);
                default -> this;
            };
        }
    }

    /**
     * One cell of the summary: a rule's counts and its four rates.
     *
     * @param key            what the cell aggregates
     * @param counts         the counts
     * @param fpr            the false admission rate, over the negatives
     * @param fnr            the false rejection rate, over the positives
     * @param falseDiscovery the share of the admissions that were false
     * @param falseOmission  the share of the refusals that were false
     */
    public record Cell(String key, Counts counts, Rate fpr, Rate fnr, Rate falseDiscovery,
            Rate falseOmission) {
    }

    /**
     * One place where the two rules decided differently.
     *
     * @param domain       the domain
     * @param condition    the condition of the workspace
     * @param strategy     the scope strategy
     * @param caseId       the case
     * @param caseCondition the condition the case exists for
     * @param anchor       the incumbent rule's verdict: ADMIT or REFUSE
     * @param prerequisite the proposal's verdict: ADMIT or REFUSE
     * @param oracleAdequate whether the oracle says the scope carries the case's evidence
     */
    public record Difference(String domain, String condition, String strategy, String caseId,
            String caseCondition, String anchor, String prerequisite, boolean oracleAdequate) {
    }

    /**
     * The whole summary.
     *
     * @param cells      the cells, by rule and by condition
     * @param differences the places where the two rules decided differently
     */
    public record Summary(List<Cell> cells, List<Difference> differences) {

        public Summary {
            cells = List.copyOf(cells == null ? List.of() : cells);
            differences = List.copyOf(differences == null ? List.of() : differences);
        }
    }

    private Crj01NecessitySummary() {
    }

    /**
     * Summarises the grid.
     *
     * @param rows the measured rows
     * @return the summary
     */
    public static Summary summarise(List<Crj01NecessityGrid.Row> rows) {
        List<Crj01NecessityGrid.Row> all = rows == null ? List.of() : rows;
        Map<String, Counts> byRule = new LinkedHashMap<>();
        Map<String, Counts> byRuleAndCaseCondition = new LinkedHashMap<>();
        for (Crj01NecessityGrid.Row row : all) {
            // Map.merge would DROP the first row of a key: the remapping function is only
            // called when the key is already present, and the first row would be counted as
            // "none". computeIfAbsent counts every row, and the gate re-derives the counts
            // from the rows — a summary that loses one row per key would be caught there.
            byRule.compute(row.criterion(), (ignored, left) -> left == null
                    ? Counts.none().plus(row.classification())
                    : left.plus(row.classification()));
            String key = row.criterion() + "|" + row.caseCondition();
            byRuleAndCaseCondition.compute(key, (ignored, left) -> left == null
                    ? Counts.none().plus(row.classification())
                    : left.plus(row.classification()));
        }
        List<Cell> cells = new ArrayList<>();
        for (Map.Entry<String, Counts> entry : byRule.entrySet()) {
            cells.add(cell("rule:" + entry.getKey(), entry.getValue()));
        }
        for (Map.Entry<String, Counts> entry : byRuleAndCaseCondition.entrySet()) {
            cells.add(cell("ruleAndCaseCondition:" + entry.getKey(), entry.getValue()));
        }
        return new Summary(cells, differences(all));
    }

    /**
     * The places where the two rules decided differently.
     *
     * @param rows the measured rows
     * @return the differences
     */
    private static List<Difference> differences(List<Crj01NecessityGrid.Row> rows) {
        Map<String, List<Crj01NecessityGrid.Row>> pairs = new LinkedHashMap<>();
        for (Crj01NecessityGrid.Row row : rows) {
            pairs.computeIfAbsent(row.domain() + "|" + row.condition() + "|" + row.strategy() + "|"
                    + row.caseId(), key -> new ArrayList<>()).add(row);
        }
        List<Difference> differences = new ArrayList<>();
        for (List<Crj01NecessityGrid.Row> pair : pairs.values()) {
            Crj01NecessityGrid.Row anchor = null;
            Crj01NecessityGrid.Row prerequisite = null;
            for (Crj01NecessityGrid.Row row : pair) {
                if (row.criterion().equals("ANCHOR")) {
                    anchor = row;
                } else if (row.criterion().equals("NECESSARY_PREREQUISITE")) {
                    prerequisite = row;
                }
            }
            if (anchor == null || prerequisite == null) {
                continue;
            }
            boolean anchorAdmitted = anchor.admitted();
            boolean prerequisiteAdmitted = prerequisite.admitted();
            if (anchorAdmitted == prerequisiteAdmitted) {
                continue;
            }
            differences.add(new Difference(anchor.domain(), anchor.condition(), anchor.strategy(),
                    anchor.caseId(), anchor.caseCondition(),
                    anchorAdmitted ? "ADMIT" : "REFUSE",
                    prerequisiteAdmitted ? "ADMIT" : "REFUSE",
                    anchor.oracleAdequate()));
        }
        return differences;
    }

    /**
     * The cell of one aggregate.
     *
     * @param key    what the cell aggregates
     * @param counts the counts
     * @return the cell
     */
    private static Cell cell(String key, Counts counts) {
        Rate fpr = rate(counts.fp(), counts.fp() + counts.tn(), "FP/(FP+TN)");
        Rate fnr = rate(counts.fn(), counts.fn() + counts.tp(), "FN/(FN+TP)");
        Rate falseDiscovery = rate(counts.fp(), counts.tp() + counts.fp(), "FP/(TP+FP)");
        Rate falseOmission = rate(counts.fn(), counts.tn() + counts.fn(), "FN/(TN+FN)");
        return new Cell(key, counts, fpr, fnr, falseDiscovery, falseOmission);
    }

    /**
     * A rate with its denominator, or the explicit statement that it has none.
     *
     * @param numerator   the numerator
     * @param denominator the denominator
     * @param label       the formula
     * @return the rate
     */
    private static Rate rate(int numerator, int denominator, String label) {
        if (denominator == 0) {
            return Rate.undefined(numerator, label);
        }
        return new Rate(numerator, denominator,
                Math.round((double) numerator / (double) denominator * 10000.0) / 10000.0, label);
    }
}
