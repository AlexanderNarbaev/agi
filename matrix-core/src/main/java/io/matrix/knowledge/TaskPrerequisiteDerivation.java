package io.matrix.knowledge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CRJ-01 / EPISTEMIC-NECESSITY — derives what a task actually needs, experimentally.
 *
 * <p><b>Why this exists.</b> The previous milestone measured that a scope holding the claim
 * the workspace's reading cites <b>first</b> may still serve an answer that is missing a claim
 * the answer needs. The rule that admitted it asked about the <b>reading</b>; the question the
 * task actually poses is about the <b>evidence</b>. This class is the answer to that question,
 * and it answers it <b>without an oracle</b>: it derives the requirement from the workspace's
 * own reading, by ablation, and it never sees a case, a label or a gold.</p>
 *
 * <p><b>Why the ablation is over SOURCES.</b> A competence's scope is a set of sources, so a
 * requirement that could only be stated at the claim level would not answer the question the
 * scope is asked. Ablating at the source level asks the question the scope asks: which
 * documents does this task rest on?</p>
 *
 * <p><b>Why the elimination order is fixed.</b> The greedy elimination is order-dependent — a
 * different order can leave a different minimal set — so the order is the one the claims were
 * cited in, and the result is therefore reproducible: the same workspace and the same question
 * yield the same requirements on every run and on every machine.</p>
 *
 * <p><b>What it never reads.</b> A word of the question, a concept name, a domain, a case
 * identifier, a confidence number or a coverage threshold. The only inputs are the workspace as
 * it is and the question's own text, and the only numbers are the retriever's own.</p>
 */
public final class TaskPrerequisiteDerivation {

    /** The workbench the derivation reads through. */
    private final KnowledgeWorkbench workbench;

    /**
     * @param workbench the workbench the derivation reads through
     */
    public TaskPrerequisiteDerivation(KnowledgeWorkbench workbench) {
        if (workbench == null) {
            throw new IllegalArgumentException("workbench must not be null");
        }
        this.workbench = workbench;
    }

    /**
     * One reading of a view: the claims it would cite and the propositions they state.
     *
     * @param claimIds    the claims the reading would cite, best first
     * @param propositions the propositions those claims state
     */
    private record Reading(List<String> claimIds, List<String> propositions) {
    }

    /**
     * Derives the evidence a task cannot be done without.
     *
     * @param view        the workspace as it is
     * @param statistics  the view whose claims define the relevance statistics; null means the
     *                    view itself
     * @param question    the question
     * @param level       the abstraction level the answer would be asked for
     * @param historical  whether the reading would be a historical one
     * @return the task's prerequisites
     */
    public TaskPrerequisites derive(KnowledgeSnapshot view, KnowledgeSnapshot statistics,
            String question, AbstractionLevel level, boolean historical) {
        if (view == null) {
            return TaskPrerequisites.none("", "there is no view to derive the task's evidence "
                    + "from");
        }
        String workspaceId = view.workspaceId();
        String taskKey = taskKey(view, question, level);
        DialogueContext context = historical ? new DialogueContext(workspaceId)
                : workbench.context(workspaceId);
        String expanded = context.expand(question);
        String asked = expanded.equals(question) ? question : expanded;
        KnowledgeSnapshot statisticsView = statistics == null ? view : statistics;
        KnowledgeStatistics statisticsIndex = workbench.statisticsOf(workspaceId, statisticsView);
        double floor = KnowledgeRetriever.configuredMinimumCoverage();
        Map<String, float[]> bindings = historical ? Map.of() : workbench.weakenedBindings(workspaceId);

        // ── THE REFERENCE READING — the workspace's own reading of the task ────────────────
        KnowledgeStatistics viewStatistics = workbench.statisticsOf(workspaceId, view);
        KnowledgeRetriever workspaceReader = new KnowledgeRetriever(view, statisticsView,
                viewStatistics, statisticsIndex, floor, bindings);
        KnowledgeRetriever.Relevance reference = workspaceReader.relevance(asked);
        List<String> cited = reference.relevantClaimIds();
        if (cited.isEmpty()) {
            return TaskPrerequisites.none(taskKey, "the workspace's own reading of this question "
                    + "reaches no claim that may be cited, so the task has no evidence the "
                    + "workspace can name");
        }
        Map<String, Claim> claimsById = claimsById(view);
        List<String> propositions = distinct(cited.stream()
                .map(id -> claimsById.get(id) == null ? null : claimsById.get(id).proposition())
                .toList());

        // ── THE CANDIDATE SOURCES ─────────────────────────────────────────────────────────
        // the sources the reading cited, and every source that holds an established claim
        // stating one of the propositions the reading stated — the second kind is an
        // ALTERNATIVE route to the same evidence.
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        for (String id : cited) {
            Claim claim = claimsById.get(id);
            if (claim != null && claim.introducedBySourceId() != null) {
                candidates.add(claim.introducedBySourceId());
            }
        }
        for (Claim claim : view.claims()) {
            if (claim.introducedBySourceId() == null) {
                continue;
            }
            if (!EpistemicPolicy.isCitable(claim.status())) {
                continue;
            }
            if (propositions.contains(claim.proposition())) {
                candidates.add(claim.introducedBySourceId());
            }
        }
        if (candidates.isEmpty()) {
            return TaskPrerequisites.none(taskKey, "the workspace's own reading cited claims that "
                    + "no source introduced, so the task's evidence has no provenance to carry it");
        }

        // ── THE BACKWARD ELIMINATION — the sources the task cannot do without ──────────────
        List<String> kept = new ArrayList<>(candidates);
        for (String candidate : candidates) {
            List<String> trial = kept.stream().filter(id -> !id.equals(candidate)).toList();
            if (trial.isEmpty()) {
                continue;
            }
            Reading reading = readOver(view, statisticsView, statisticsIndex, trial, asked, floor,
                    bindings);
            if (reading.propositions().containsAll(propositions)) {
                kept = trial;
            }
        }

        // ── THE REQUIREMENTS — the claims the kept sources cannot do without ───────────────
        List<EvidenceRequirement> necessary = requirements(view, kept, propositions);

        // ── THE ALTERNATIVES — another set of sources that does the same job ───────────────
        List<List<EvidenceRequirement>> alternatives = alternatives(view, statisticsView,
                statisticsIndex, kept, candidates, propositions, asked, floor, bindings);

        String reason = "the workspace's own reading of this question states " + propositions.size()
                + " proposition(s); " + kept.size() + " of the " + candidates.size()
                + " candidate source(s) cannot be dropped without losing one of them, and they "
                + "carry " + necessary.size() + " requirement(s)";
        return new TaskPrerequisites(taskKey, necessary, alternatives, propositions,
                TaskPrerequisites.DERIVATION, reason);
    }

    /**
     * The requirements of a set of kept sources: the established claims they hold that state
     * one of the task's propositions and that no other kept source states.
     *
     * @param view         the workspace as it is
     * @param kept         the sources the task cannot do without
     * @param propositions the propositions the task's answer states
     * @return the requirements, in the order the reading cited them
     */
    private List<EvidenceRequirement> requirements(KnowledgeSnapshot view, List<String> kept,
            List<String> propositions) {
        Map<String, Claim> bySource = new LinkedHashMap<>();
        for (Claim claim : view.claims()) {
            if (claim.introducedBySourceId() == null || !kept.contains(claim.introducedBySourceId())) {
                continue;
            }
            if (!EpistemicPolicy.isCitable(claim.status())) {
                continue;
            }
            if (!propositions.contains(claim.proposition())) {
                continue;
            }
            bySource.put(claim.id(), claim);
        }
        List<EvidenceRequirement> requirements = new ArrayList<>();
        for (String sourceId : kept) {
            for (Claim claim : view.claims()) {
                if (!sourceId.equals(claim.introducedBySourceId())) {
                    continue;
                }
                if (!bySource.containsKey(claim.id())) {
                    continue;
                }
                if (statedByAnother(claim, kept, view)) {
                    // another kept source states the same proposition: this claim is an
                    // ALTERNATIVE, not a requirement
                    continue;
                }
                requirements.add(new EvidenceRequirement(claim.id(), claim.proposition(),
                        sourceId, claim.status(),
                        "the source " + sourceId + " cannot be dropped without losing the "
                                + "proposition \"" + claim.proposition() + "\""));
            }
        }
        return List.copyOf(requirements);
    }

    /**
     * Whether another kept source states the same proposition.
     *
     * @param claim the claim
     * @param kept  the kept sources
     * @param view  the workspace as it is
     * @return whether the proposition is stated elsewhere among the kept sources
     */
    private boolean statedByAnother(Claim claim, List<String> kept, KnowledgeSnapshot view) {
        for (Claim other : view.claims()) {
            if (other.id().equals(claim.id())) {
                continue;
            }
            if (other.introducedBySourceId() == null
                    || !kept.contains(other.introducedBySourceId())
                    || other.introducedBySourceId().equals(claim.introducedBySourceId())) {
                continue;
            }
            if (!EpistemicPolicy.isCitable(other.status())) {
                continue;
            }
            if (other.proposition().equals(claim.proposition())) {
                return true;
            }
        }
        return false;
    }

    /**
     * The alternative sufficient sets: another set of sources that carries the same evidence.
     *
     * @param view            the workspace as it is
     * @param statisticsView  the view whose claims define the relevance statistics
     * @param statisticsIndex the statistics index
     * @param kept            the sources the task cannot do without
     * @param candidates      every candidate source
     * @param propositions    the propositions the task's answer states
     * @param asked           the question as the reading resolves it
     * @param floor           the coverage floor
     * @param bindings        the weakened bindings
     * @return the alternative sufficient sets
     */
    private List<List<EvidenceRequirement>> alternatives(KnowledgeSnapshot view,
            KnowledgeSnapshot statisticsView, KnowledgeStatistics statisticsIndex, List<String> kept,
            Set<String> candidates, List<String> propositions, String asked, double floor,
            Map<String, float[]> bindings) {
        List<List<EvidenceRequirement>> alternatives = new ArrayList<>();
        for (String keptSource : kept) {
            for (String candidate : candidates) {
                if (kept.contains(candidate)) {
                    continue;
                }
                List<String> trial = new ArrayList<>(kept);
                trial.remove(keptSource);
                trial.add(candidate);
                Reading reading = readOver(view, statisticsView, statisticsIndex, trial, asked, floor,
                        bindings);
                if (!reading.propositions().containsAll(propositions)) {
                    continue;
                }
                List<EvidenceRequirement> alternative = new ArrayList<>();
                for (String sourceId : trial) {
                    for (Claim claim : view.claims()) {
                        if (!sourceId.equals(claim.introducedBySourceId())) {
                            continue;
                        }
                        if (!EpistemicPolicy.isCitable(claim.status())) {
                            continue;
                        }
                        if (!propositions.contains(claim.proposition())) {
                            continue;
                        }
                        if (statedByAnother(claim, trial, view)) {
                            continue;
                        }
                        alternative.add(new EvidenceRequirement(claim.id(), claim.proposition(),
                                sourceId, claim.status(),
                                "the source " + sourceId + " carries the same evidence as the "
                                        + "source " + keptSource + " did"));
                    }
                }
                if (alternative.isEmpty()) {
                    continue;
                }
                boolean same = alternative.size() == requirements(view, kept, propositions).size();
                if (same) {
                    boolean identical = true;
                    List<EvidenceRequirement> base = requirements(view, kept, propositions);
                    for (int index = 0; index < base.size() && identical; index++) {
                        identical = base.get(index).claimId().equals(alternative.get(index).claimId());
                    }
                    if (identical) {
                        continue;
                    }
                }
                alternatives.add(List.copyOf(alternative));
            }
        }
        return List.copyOf(alternatives);
    }

    /**
     * Reads a view as a set of sources.
     *
     * @param view            the workspace as it is
     * @param statisticsView  the view whose claims define the relevance statistics
     * @param statisticsIndex the statistics index
     * @param sourceIds       the sources the reading is restricted to
     * @param asked           the question as the reading resolves it
     * @param floor           the coverage floor
     * @param bindings        the weakened bindings
     * @return the reading
     */
    private Reading readOver(KnowledgeSnapshot view, KnowledgeSnapshot statisticsView,
            KnowledgeStatistics statisticsIndex, List<String> sourceIds, String asked, double floor,
            Map<String, float[]> bindings) {
        KnowledgeSnapshot restricted = workbench.restrictedView(view, new LinkedHashSet<>(sourceIds));
        KnowledgeStatistics restrictedStatistics =
                workbench.statisticsOf(view.workspaceId(), restricted);
        KnowledgeRetriever.Relevance relevance = new KnowledgeRetriever(restricted, statisticsView,
                restrictedStatistics, statisticsIndex, floor, bindings).relevance(asked);
        Map<String, Claim> byId = claimsById(restricted);
        List<String> ids = relevance.relevantClaimIds();
        List<String> propositions = new ArrayList<>();
        for (String id : ids) {
            Claim claim = byId.get(id);
            if (claim != null) {
                propositions.add(claim.proposition());
            }
        }
        return new Reading(ids, propositions);
    }

    /**
     * The claims of a view, by identifier.
     *
     * @param view the view
     * @return the claims
     */
    private static Map<String, Claim> claimsById(KnowledgeSnapshot view) {
        Map<String, Claim> byId = new LinkedHashMap<>();
        for (Claim claim : view.claims()) {
            byId.put(claim.id(), claim);
        }
        return byId;
    }

    /**
     * The propositions of a list, without repetitions and in the order they appear.
     *
     * @param values the propositions
     * @return the distinct propositions
     */
    private static List<String> distinct(List<String> values) {
        LinkedHashSet<String> distinct = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null) {
                distinct.add(value);
            }
        }
        return List.copyOf(distinct);
    }

    /**
     * The key of a task: the question, the level and the identity of the view the derivation
     * was made against, so a derivation is never reused for another task or another view.
     *
     * @param view     the view
     * @param question the question
     * @param level    the level
     * @return the key
     */
    private static String taskKey(KnowledgeSnapshot view, String question, AbstractionLevel level) {
        StringBuilder claims = new StringBuilder();
        List<String> ids = view.claims().stream().map(Claim::id).sorted().toList();
        for (String id : ids) {
            claims.append(id).append(';');
        }
        return view.workspaceId() + "|" + (level == null ? "" : level.name()) + "|" + question + "|"
                + Integer.toHexString(claims.toString().hashCode());
    }
}
