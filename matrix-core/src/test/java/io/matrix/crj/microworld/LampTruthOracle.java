package io.matrix.crj.microworld;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The domain-B oracle of CRJ-01: the <b>independent truth table</b> of the lamp world.
 *
 * <p><b>What question of truth it answers.</b> What is the lamp world's outcome for a given
 * assignment?</p>
 *
 * <p><b>Where it comes from.</b> From the <b>specification</b>, not from the code. The world is
 * documented as "two inputs, one output, one hidden rule", and the hypothesis space documents
 * the conjunction the lamp world obeys as "the function that is true only at
 * {@code power=1, switch=1}". The four rows below are that function, written out as
 * <b>literals</b> — four constants in a table, not an expression that could have been copied
 * from the world's private method.</p>
 *
 * <p><b>What this class does NOT do.</b> It does not import the lamp environment, it does not
 * name its type, and it cannot ask it anything: the whole point is that the truth is known
 * <b>without</b> the solver's own instrument. The two meet in exactly one place — the
 * {@link Crosscheck} — and the check is over <b>data</b> the harness read from the world.</p>
 *
 * <p><b>Why the cross-check is binding.</b> A second implementation that silently agreed with a
 * broken world would be worthless. So before any arm runs, the oracle and the world are
 * compared on <b>all four</b> assignments; the result is recorded as
 * {@code instrument_crosscheck: {assignments: 4, agreements: 4}}. A disagreement is not scored
 * and not re-run to a nicer number: the run is <b>VOID</b> with the reason
 * {@code INSTRUMENT_MISMATCH}, and the mismatch is a finding in its own right.</p>
 */
public final class LampTruthOracle {

    /** The first input variable's name, as the specification names it. */
    public static final String POWER_VARIABLE = "power";

    /** The second input variable's name, as the specification names it. */
    public static final String SWITCH_VARIABLE = "switch";

    /** The outcome variable's name, as the specification names it. */
    public static final String LAMP_VARIABLE = "lamp";

    /** The reason a run is void when the two implementations disagree. */
    public static final String INSTRUMENT_MISMATCH = "INSTRUMENT_MISMATCH";

    /**
     * The truth table, as four literal rows.
     *
     * <p>The key is the assignment and the value is the outcome. There is no expression here
     * that could have been copied from the world's private rule: the rows were written out,
     * one by one, from the specification's description of the conjunction.</p>
     */
    private static final Map<String, Boolean> TRUTH_TABLE = literalTable();

    /**
     * One row of the truth table.
     *
     * <p>The second input is named {@code toggle} rather than {@code switch}, because
     * {@code switch} is a Java keyword and a record component may not be one.</p>
     *
     * @param power  the power input, as "0" or "1"
     * @param toggle the switch input, as "0" or "1"
     * @param lamp   the outcome
     */
    public record Row(String power, String toggle, boolean lamp) {
        /** The row's assignment, as the key the table is written under. */
        public String assignment() {
            return POWER_VARIABLE + "=" + power + "," + SWITCH_VARIABLE + "=" + toggle;
        }
    }

    /**
     * The result of comparing the oracle with the world on every assignment.
     *
     * @param assignments   how many assignments were compared
     * @param agreements    how many of them the two implementations agreed on
     * @param disagreements the assignments they disagreed on
     */
    public record Crosscheck(int assignments, int agreements, List<String> disagreements) {
        public Crosscheck {
            disagreements = List.copyOf(disagreements == null ? List.of() : disagreements);
        }

        /** Whether the two implementations may be trusted against each other. */
        public boolean agrees() {
            return assignments == 4 && agreements == 4 && disagreements.isEmpty();
        }

        /** The reason a run is void, or the empty string when it is not. */
        public String voidReason() {
            return agrees() ? "" : INSTRUMENT_MISMATCH;
        }

        /** The record the freeze and the run carry. */
        public String asRecord() {
            return "instrument_crosscheck: {assignments: " + assignments + ", agreements: "
                    + agreements + "}";
        }
    }

    /**
     * What the lamp does for one assignment.
     *
     * @param power  the power input
     * @param toggle the switch input
     * @return whether the lamp is lit
     */
    public boolean lit(boolean power, boolean toggle) {
        return TRUTH_TABLE.get(key(power, toggle));
    }

    /**
     * The truth table's four rows, in the order the specification lists them.
     *
     * @return the rows
     */
    public List<Row> rows() {
        return List.of(
                new Row("0", "0", false),
                new Row("0", "1", false),
                new Row("1", "0", false),
                new Row("1", "1", true));
    }

    /**
     * The four assignments, in the order the specification lists them.
     *
     * @return the assignments
     */
    public List<String> assignments() {
        return List.of("power=0,switch=0", "power=0,switch=1",
                "power=1,switch=0", "power=1,switch=1");
    }

    /**
     * Compares the oracle with the world on every assignment.
     *
     * <p>The world's answers arrive as <b>data</b>: a map from the assignment to the outcome the
     * world produced. The oracle does not ask the world anything, and it cannot — it does not
     * know the world exists.</p>
     *
     * @param observed the world's answers, keyed by the assignment
     * @return the comparison
     */
    public Crosscheck crosscheck(Map<String, Boolean> observed) {
        int agreements = 0;
        List<String> disagreements = new java.util.ArrayList<>();
        for (Row row : rows()) {
            String assignment = row.assignment();
            Boolean world = observed == null ? null : observed.get(assignment);
            if (world == null) {
                disagreements.add(assignment + " (the world did not answer it)");
                continue;
            }
            if (world == row.lamp()) {
                agreements++;
            } else {
                disagreements.add(assignment + " (the oracle says " + row.lamp()
                        + ", the world says " + world + ")");
            }
        }
        return new Crosscheck(rows().size(), agreements, disagreements);
    }

    /** The key one assignment is written under. */
    private static String key(boolean power, boolean toggle) {
        return POWER_VARIABLE + "=" + (power ? "1" : "0") + "," + SWITCH_VARIABLE + "="
                + (toggle ? "1" : "0");
    }

    /** The table, written out row by row. */
    private static Map<String, Boolean> literalTable() {
        Map<String, Boolean> table = new LinkedHashMap<>();
        table.put("power=0,switch=0", Boolean.FALSE);
        table.put("power=0,switch=1", Boolean.FALSE);
        table.put("power=1,switch=0", Boolean.FALSE);
        table.put("power=1,switch=1", Boolean.TRUE);
        return Map.copyOf(table);
    }
}
