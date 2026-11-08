package io.matrix.plugin;

import io.matrix.contract.Plugin;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * W51 — the lifecycle a plugin moves through, and the moves that are legal.
 *
 * <p><b>What this replaces.</b> Nothing: the repository has no plugin system at all. The W51
 * reconnaissance established that there is no {@code ServiceLoader}, no {@code META-INF/services},
 * no extension point and no classpath scanning in any module, and no sandbox-capable
 * dependency in any build file. What exists is a family of in-process registries populated by
 * hard-coded constructors inside their own modules, none of which can be extended from
 * outside the jar. The lifecycle is therefore built from nothing, and the shape of it is
 * dictated by the W51 mandate: {@code PROPOSED → BUILDING → SANDBOXED → VALIDATED → SHADOW →
 * ACTIVE}, with failure, revocation and retirement as the other states.</p>
 *
 * <p><b>Why the chain is long.</b> Each step answers a different question, and the questions
 * are not the same question asked twice. {@code SANDBOXED} says the artefact runs inside a
 * boundary; it says nothing about whether it is CORRECT. {@code VALIDATED} says it is correct
 * against declared cases; it says nothing about whether it is safe to use in anger.
 * {@code SHADOW} says it runs alongside the real path without influencing it. Only
 * {@code ACTIVE} says its results are used. Collapsing the last three is how an unvalidated
 * artefact ends up answering real questions.</p>
 *
 * <p><b>A plugin cannot skip a step.</b> The transition table below is the whole rule, and it
 * is enforced rather than documented: a move that is not in the table is refused, and the
 * refusal says which move was attempted. There is no administrative override, because an
 * override would be a way around the guarantee that nobody notices.</p>
 *
 * <p><b>Failure and revocation are reachable from everywhere that matters, and they are
 * terminal.</b> A plugin that failed can be rebuilt; a revoked plugin cannot come back, and
 * the runtime must leave nothing of it behind when it goes.</p>
 */
public final class PluginLifecycle {

    private static final Map<Plugin.Stage, Set<Plugin.Stage>> ALLOWED = buildTable();

    private PluginLifecycle() {
        throw new AssertionError("no instances");
    }

    private static Map<Plugin.Stage, Set<Plugin.Stage>> buildTable() {
        EnumMap<Plugin.Stage, Set<Plugin.Stage>> table = new EnumMap<>(Plugin.Stage.class);
        // A plugin may be revoked from ANY stage it has not left yet: a plugin that was
        // never activated must be removable just as cleanly as one that was, and the only
        // stages from which revocation is impossible are the two terminal ones.
        table.put(Plugin.Stage.PROPOSED, EnumSet.of(Plugin.Stage.BUILDING, Plugin.Stage.REVOKED));
        table.put(Plugin.Stage.BUILDING, EnumSet.of(Plugin.Stage.SANDBOXED, Plugin.Stage.FAILED,
                Plugin.Stage.REVOKED));
        table.put(Plugin.Stage.SANDBOXED, EnumSet.of(Plugin.Stage.VALIDATED, Plugin.Stage.FAILED,
                Plugin.Stage.REVOKED));
        table.put(Plugin.Stage.VALIDATED, EnumSet.of(Plugin.Stage.SHADOW, Plugin.Stage.FAILED,
                Plugin.Stage.REVOKED));
        table.put(Plugin.Stage.SHADOW, EnumSet.of(Plugin.Stage.ACTIVE, Plugin.Stage.FAILED,
                Plugin.Stage.RETIRED, Plugin.Stage.REVOKED));
        table.put(Plugin.Stage.ACTIVE, EnumSet.of(Plugin.Stage.REVOKED, Plugin.Stage.RETIRED,
                Plugin.Stage.FAILED));
        table.put(Plugin.Stage.FAILED, EnumSet.of(Plugin.Stage.BUILDING, Plugin.Stage.REVOKED));
        table.put(Plugin.Stage.REVOKED, EnumSet.noneOf(Plugin.Stage.class));
        table.put(Plugin.Stage.RETIRED, EnumSet.noneOf(Plugin.Stage.class));
        return Map.copyOf(table);
    }

    /**
     * Whether a move is legal.
     *
     * @param from the stage the plugin is in
     * @param to   the stage it would move to
     * @return true when the move is allowed
     */
    public static boolean canTransition(Plugin.Stage from, Plugin.Stage to) {
        if (from == null || to == null) {
            return false;
        }
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    /**
     * The stages a plugin in a given stage may move to.
     *
     * @param from the stage the plugin is in
     * @return the legal moves
     */
    public static Set<Plugin.Stage> legalMoves(Plugin.Stage from) {
        return ALLOWED.getOrDefault(from, Set.of());
    }

    /**
     * Checks a move and explains the refusal.
     *
     * @param from the stage the plugin is in
     * @param to   the stage it would move to
     * @return the reason the move is refused, or {@code null} when it is allowed
     */
    public static String refusal(Plugin.Stage from, Plugin.Stage to) {
        if (from == null || to == null) {
            return "the move names no stage";
        }
        if (!canTransition(from, to)) {
            return "the move from " + from + " to " + to + " is not legal; the legal moves from "
                    + from + " are " + legalMoves(from);
        }
        return null;
    }
}
