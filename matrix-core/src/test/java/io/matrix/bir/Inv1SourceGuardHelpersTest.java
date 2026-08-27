package io.matrix.bir;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Focused unit tests for the INV-1 alias-detection helpers. The full path
 * is exercised end-to-end by {@link Inv1SourceGuardTest} against the live
 * source tree; these tests pin down the heuristics in isolation so future
 * refactors don't silently regress the alias coverage.
 */
class Inv1SourceGuardHelpersTest {

    @Test
    void collectsDeclaredReceiversAcrossLocalAndParameterForms() {
        List<String> lines = List.of(
                "void f() {",
                "    TruthTable tt = TruthTable.of(3);",
                "    Bir bir = compile(tt);",
                "    TtForm small = (TtForm) bir;",
                "    int unrelated = 7;",
                "    DecisionTree tree = DecisionTree.empty();",
                "}");
        Set<String> names = invokeTypedReceivers(lines);
        assertThat(names).contains("tt", "bir", "small", "tree");
        assertThat(names).doesNotContain("unrelated");
    }

    @Test
    void nameRegexCatchesConventionalAlias() {
        assertThat(matchesForbiddenName("    truthTable.evaluate(bits);")).isTrue();
        assertThat(matchesForbiddenName("    tree.evaluate(bits);")).isTrue();
    }

    @Test
    void nameRegexMissesArbitraryAlias() {
        // Conventional regex does NOT catch this — that's why the typed-alias scan exists.
        assertThat(matchesForbiddenName("    myBir.evaluate(bits);")).isFalse();
    }

    @Test
    void typedAliasScanCatchesArbitraryAlias() {
        // Verify the typed-receiver → .evaluate() chain works.
        List<String> file = List.of(
                "    TruthTable myBir = TruthTable.of(2);",
                "    myBir.evaluate(new long[]{0b11});");
        Set<String> names = invokeTypedReceivers(file);
        boolean flagged = false;
        for (String line : file) {
            if (matchesTypedAliasCall(line, names)) {
                flagged = true;
                break;
            }
        }
        assertThat(flagged).isTrue();
    }

    @Test
    void typedAliasScanIgnoresSingleLetterNames() {
        // Single-letter names share regex character classes with the type
        // pattern itself; require ≥ 2 chars to avoid false positives on
        // structural constructs (e.g. `Bir b = ...;` is allowed since `b`
        // matches the `[A-Za-z_][A-Za-z0-9_]*` group but is filtered out).
        List<String> file = List.of(
                "    Bir b = Bir.empty();",
                "    b.evaluate(new long[]{0});");
        Set<String> names = invokeTypedReceivers(file);
        assertThat(matchesTypedAliasCall("    b.evaluate(new long[]{0});", names)).isFalse();
    }

    @Test
    void commentLinesAreIgnored() {
        // The helper strips // comments before scanning for typed declarations.
        // Indirect verification: a commented declaration should not produce
        // a name in the receiver set.
        List<String> lines = List.of(
                "    // TruthTable hidden = TruthTable.of(2);",
                "    TruthTable real = TruthTable.of(2);");
        Set<String> names = invokeTypedReceivers(lines);
        assertThat(names).contains("real");
        assertThat(names).doesNotContain("hidden");
    }

    // --- reflection plumbing (package-private helpers invoked via reflection) ---

    private static Set<String> invokeTypedReceivers(List<String> lines) {
        try {
            var m = Inv1SourceGuardTest.class.getDeclaredMethod("collectTypedReceivers", List.class);
            m.setAccessible(true);
            @SuppressWarnings("unchecked")
            Set<String> out = (Set<String>) m.invoke(null, lines);
            return out;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean matchesTypedAliasCall(String line, Set<String> names) {
        try {
            var m = Inv1SourceGuardTest.class.getDeclaredMethod(
                    "matchesTypedAliasCall", String.class, Set.class);
            m.setAccessible(true);
            return (boolean) m.invoke(null, line, names);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean matchesForbiddenName(String line) {
        try {
            var f = Inv1SourceGuardTest.class.getDeclaredField("FORBIDDEN_NAMES");
            f.setAccessible(true);
            java.util.regex.Pattern p = (java.util.regex.Pattern) f.get(null);
            return p.matcher(line).find();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}