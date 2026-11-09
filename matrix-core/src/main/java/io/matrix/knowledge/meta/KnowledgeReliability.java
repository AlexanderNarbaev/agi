package io.matrix.knowledge.meta;

import io.matrix.contract.Evaluation;
import io.matrix.knowledge.Claim;
import io.matrix.knowledge.ClaimStatus;
import io.matrix.knowledge.KnowledgeRevision;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * W51 §4 — the meta-memory: what the system knows about the reliability of its own
 * knowledge.
 *
 * <p><b>What this closes.</b> The W51 mandate names six memory roles, and the W51 audit
 * found that the two that had no carrier were the world state and the meta-memory. The
 * world state was closed by {@code io.matrix.cognitive.worldstate}. This closes the
 * meta-memory on the KNOWLEDGE side: the system's own record of which of its sources and
 * which of its validation methods have earned trust, and which have not.</p>
 *
 * <p><b>It is a projection, not a seventh database.</b> It is computed from the claims and
 * the revisions the epistemic store already holds, so it cannot disagree with them and it
 * survives a restart without anything of its own being written.</p>
 *
 * <p><b>A source that has been revised often is not a bad source; it is a source the system
 * has revised often.</b> The measure is deliberately plain: how many of the claims a source
 * introduced were later revised, and how many of the claims a validation method validated
 * were later revised. It says nothing about WHY, and it does not say that a source is
 * wrong — the reasons are carried with it, because a number without the reason is a number
 * nobody can act on.</p>
 *
 * <p><b>What it is for.</b> The answer path can qualify an answer with the reliability of
 * the source it rests on, and an acquisition cycle can notice that one source keeps
 * producing claims that have to be revised. Nothing here changes what is citable: that is
 * the {@code EpistemicPolicy}'s business, and it is decided by the claim's own status.</p>
 */
public final class KnowledgeReliability {

    /**
     * What the system knows about one source.
     *
     * @param sourceId          the source
     * @param claimsIntroduced  how many claims the source introduced
     * @param claimsRevised     how many of them were revised or superseded afterwards
     * @param revisionRate      the ratio, from 0.0 to 1.0
     * @param reasons           why the revisions happened, as the revisions said
     */
    public record SourceReliability(String sourceId, int claimsIntroduced, int claimsRevised,
                                    double revisionRate, List<String> reasons) {
        public SourceReliability {
            reasons = reasons == null ? List.of() : List.copyOf(reasons);
        }
    }

    /**
     * What the system knows about one validation method.
     *
     * @param method           the validation method
     * @param claimsValidated  how many claims it validated
     * @param claimsRevised    how many of them were revised or superseded afterwards
     * @param revisionRate     the ratio, from 0.0 to 1.0
     */
    public record MethodReliability(String method, int claimsValidated, int claimsRevised,
                                     double revisionRate) {
    }

    private final Map<String, SourceReliability> sources;
    private final Map<String, MethodReliability> methods;
    private final Map<String, Integer> statusCounts;

    private KnowledgeReliability(Map<String, SourceReliability> sources,
                                 Map<String, MethodReliability> methods,
                                 Map<String, Integer> statusCounts) {
        this.sources = sources;
        this.methods = methods;
        this.statusCounts = statusCounts;
    }

    /**
     * Computes the reliability of what the store holds.
     *
     * @param claims    the claims of the workspace
     * @param revisions the revisions of the workspace
     * @return the reliability
     */
    public static KnowledgeReliability of(List<Claim> claims, List<KnowledgeRevision> revisions) {
        List<Claim> claimList = claims == null ? List.of() : claims;
        List<KnowledgeRevision> revisionList = revisions == null ? List.of() : revisions;

        Map<String, Integer> statusCounts = new LinkedHashMap<>();
        Map<String, Integer> introducedBySource = new LinkedHashMap<>();
        Map<String, Integer> validatedByMethod = new LinkedHashMap<>();
        Map<String, Claim> claimsById = new LinkedHashMap<>();
        for (Claim claim : claimList) {
            claimsById.put(claim.id(), claim);
            statusCounts.merge(claim.status().name(), 1, Integer::sum);
            if (claim.introducedBySourceId() != null && !claim.introducedBySourceId().isBlank()) {
                introducedBySource.merge(claim.introducedBySourceId(), 1, Integer::sum);
            }
            if (claim.validationMethod() != null && !claim.validationMethod().isBlank()) {
                validatedByMethod.merge(claim.validationMethod(), 1, Integer::sum);
            }
        }

        // A claim is "revised" when the store holds a revision against it, or when its
        // status says it was superseded or refuted afterwards.
        Map<String, List<String>> reasonsByClaim = new LinkedHashMap<>();
        for (KnowledgeRevision revision : revisionList) {
            if (revision.targetId() != null) {
                reasonsByClaim.computeIfAbsent(revision.targetId(), key -> new ArrayList<>())
                        .add(revision.kind() + ": " + revision.summary());
            }
        }
        Map<String, Integer> revisedBySource = new LinkedHashMap<>();
        Map<String, Integer> revisedByMethod = new LinkedHashMap<>();
        Map<String, List<String>> reasonsBySource = new LinkedHashMap<>();
        for (Claim claim : claimList) {
            List<String> reasons = reasonsByClaim.get(claim.id());
            boolean superseded = claim.status() == ClaimStatus.SUPERSEDED
                    || claim.status() == ClaimStatus.REFUTED;
            if ((reasons == null || reasons.isEmpty()) && !superseded) {
                continue;
            }
            String source = claim.introducedBySourceId();
            if (source != null && !source.isBlank()) {
                revisedBySource.merge(source, 1, Integer::sum);
                List<String> collected = reasonsBySource.computeIfAbsent(source,
                        key -> new ArrayList<>());
                if (reasons != null) {
                    collected.addAll(reasons);
                }
                if (superseded) {
                    collected.add("the claim is now " + claim.status().name());
                }
            }
            String method = claim.validationMethod();
            if (method != null && !method.isBlank()) {
                revisedByMethod.merge(method, 1, Integer::sum);
            }
        }

        Map<String, SourceReliability> sources = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : introducedBySource.entrySet()) {
            String sourceId = entry.getKey();
            int introduced = entry.getValue();
            int revised = revisedBySource.getOrDefault(sourceId, 0);
            sources.put(sourceId, new SourceReliability(sourceId, introduced, revised,
                    ratio(revised, introduced),
                    reasonsBySource.getOrDefault(sourceId, List.of())));
        }

        Map<String, MethodReliability> methods = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : validatedByMethod.entrySet()) {
            String method = entry.getKey();
            int validated = entry.getValue();
            int revised = revisedByMethod.getOrDefault(method, 0);
            methods.put(method, new MethodReliability(method, validated, revised,
                    ratio(revised, validated)));
        }

        return new KnowledgeReliability(sources, methods, statusCounts);
    }

    /** What the system knows about each source. */
    public Map<String, SourceReliability> sources() {
        return sources;
    }

    /** What the system knows about each validation method. */
    public Map<String, MethodReliability> methods() {
        return methods;
    }

    /** How many claims there are of each status. */
    public Map<String, Integer> statusCounts() {
        return statusCounts;
    }

    /**
     * The evaluation of the workspace's own reliability.
     *
     * <p>The metrics are the ones a reader compares: how much of what the system holds is
     * established, how much was revised, and how many of the sources were revised at all.</p>
     *
     * @param workspaceId the workspace the evaluation is about
     * @param at          when the evaluation was made
     * @return the evaluation
     */
    public Evaluation evaluation(String workspaceId, String at) {
        int established = 0;
        int revised = 0;
        for (Map.Entry<String, Integer> entry : statusCounts.entrySet()) {
            ClaimStatus status = ClaimStatus.valueOf(entry.getKey());
            if (status == ClaimStatus.SUPPORTED || status == ClaimStatus.VERIFIED) {
                established += entry.getValue();
            }
        }
        for (SourceReliability source : sources.values()) {
            revised += source.claimsRevised();
        }
        int total = statusCounts.values().stream().mapToInt(Integer::intValue).sum();
        Map<String, Evaluation.Metric> metrics = new LinkedHashMap<>();
        metrics.put("claims.established", new Evaluation.Metric(established, total));
        metrics.put("claims.revised", new Evaluation.Metric(revised, total));
        metrics.put("sources.known", new Evaluation.Metric(sources.size(), Math.max(sources.size(), 1)));
        return new Evaluation("meta-" + workspaceId, workspaceId, "epistemic-workspace",
                Map.of("workspace", workspaceId), metrics, null,
                "the reliability of what this workspace holds, computed from its own claims and "
                        + "revisions", at);
    }

    private static double ratio(int part, int whole) {
        return whole <= 0 ? 0.0 : (double) part / (double) whole;
    }

    @Override
    public String toString() {
        return "KnowledgeReliability[sources=" + sources.size() + " methods=" + methods.size()
                + " claims=" + statusCounts.values().stream().mapToInt(Integer::intValue).sum()
                + "]";
    }
}
