package io.matrix.consciousness;

import java.util.function.Predicate;

/**
 * RUN 286 — CycleFilter (input filtering).
 *
 * <p>Filters inputs before they enter the cognitive loop.
 * Returns true if the input should proceed.
 */
public final class CycleFilter {

    private final Predicate<String> filter;

    public CycleFilter(Predicate<String> filter) {
        this.filter = filter;
    }

    public boolean accepts(String input) {
        if (input == null) return false;
        return filter.test(input);
    }

    /** Reject empty/blank inputs. */
    public static CycleFilter nonEmpty() {
        return new CycleFilter(s -> !s.isBlank());
    }

    /** Reject adversarial inputs. */
    public static CycleFilter safe() {
        return new CycleFilter(s -> {
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '\u0001' || c == '\u0002' || c == '\u0003') return false;
                if (i + 1 < s.length() && c == '$' && s.charAt(i + 1) == '(') return false;
            }
            return true;
        });
    }

    /** Reject inputs over max length. */
    public static CycleFilter maxLength(int max) {
        return new CycleFilter(s -> s.length() <= max);
    }

    /** Combine multiple filters (all must pass). */
    public static CycleFilter all(CycleFilter... filters) {
        return new CycleFilter(s -> {
            for (CycleFilter f : filters) {
                if (!f.accepts(s)) return false;
            }
            return true;
        });
    }
}
