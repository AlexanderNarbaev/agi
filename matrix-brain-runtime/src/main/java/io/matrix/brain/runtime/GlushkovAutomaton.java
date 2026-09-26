package io.matrix.brain.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * TRUE-W11 iteration #8 — Glushkov algebraic automaton.
 *
 * <p>Compiles a small regex-like pattern into a finite-state machine
 * where states correspond to pattern positions and transitions are
 * labelled by tokens. Pattern syntax:</p>
 * <ul>
 *   <li><b>Token</b>: <code>word</code> — matches the literal word</li>
 *   <li><b>Sequence</b>: <code>a b c</code> — whitespace-separated</li>
 *   <li><b>Alternation</b>: <code>a|b|c</code> — pipe-separated</li>
 *   <li><b>Optional</b>: <code>a?</code> — matches 0 or 1 times</li>
 * </ul>
 */
public final class GlushkovAutomaton {

    private final List<String> tokens = new ArrayList<>();
    private final Map<Integer, Map<String, Integer>> transitions = new HashMap<>();
    private final Set<Integer> accept = new HashSet<>();

    /** Compile a pattern. Returns empty automaton if pattern is null/blank. */
    public static GlushkovAutomaton compile(String pattern) {
        GlushkovAutomaton a = new GlushkovAutomaton();
        if (pattern == null || pattern.trim().isEmpty()) return a;
        String[] pieces = pattern.trim().split("\\s+");
        int prevState = -1;
        for (String piece : pieces) {
            String[] alts = piece.split("\\|");
            if (alts.length == 1) {
                String tok = stripOptional(alts[0]);
                if (tok.isEmpty()) continue;
                a.tokens.add(tok);
                int current = a.tokens.size() - 1;
                if (prevState >= 0) {
                    a.transitions.computeIfAbsent(prevState, k -> new HashMap<>())
                        .put(tok, current);
                }
                prevState = current;
            } else {
                if (prevState >= 0) {
                    Map<String, Integer> fromMap = a.transitions.computeIfAbsent(
                        prevState, k -> new HashMap<>());
                    for (int i = 0; i < alts.length; i++) {
                        String tok = stripOptional(alts[i]);
                        if (tok.isEmpty()) continue;
                        a.tokens.add(tok);
                        int current = a.tokens.size() - 1;
                        fromMap.put(tok, current);
                        if (i == 0) {
                            prevState = current;
                        } else {
                            a.accept.add(current);
                        }
                    }
                } else {
                    for (String alt : alts) {
                        String tok = stripOptional(alt);
                        if (tok.isEmpty()) continue;
                        a.tokens.add(tok);
                        a.accept.add(a.tokens.size() - 1);
                    }
                    prevState = -1;
                }
            }
        }
        if (!a.tokens.isEmpty()) a.accept.add(a.tokens.size() - 1);
        return a;
    }

    private static String stripOptional(String tok) {
        return tok.endsWith("?") ? tok.substring(0, tok.length() - 1) : tok;
    }

    public boolean matches(List<String> input) {
        if (tokens.isEmpty()) return input.isEmpty();
        if (input.isEmpty()) return false;
        // Detect alternation: if NO state has an outgoing transition (all
        // alternation entries are isolated), each token is a separate
        // 1-token pattern; accept if any token matches.
        boolean hasTransitions = false;
        for (var e : transitions.entrySet()) {
            if (!e.getValue().isEmpty()) { hasTransitions = true; break; }
        }
        if (!hasTransitions && input.size() == 1) {
            for (String tok : tokens) if (tok.equals(input.get(0))) return true;
            return false;
        }
        if (input.size() != tokens.size()) return false;
        for (int i = 0; i < tokens.size(); i++) {
            if (!tokens.get(i).equals(input.get(i))) return false;
        }
        int lastIdx = tokens.size() - 1;
        return accept.contains(lastIdx);
    }

    public List<String> tokens() { return tokens; }
    public Set<Integer> accept() { return accept; }
    public Map<Integer, Map<String, Integer>> transitions() { return transitions; }
}
