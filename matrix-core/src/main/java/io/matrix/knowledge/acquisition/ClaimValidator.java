package io.matrix.knowledge.acquisition;

import io.matrix.knowledge.Claim;
import io.matrix.knowledge.ClaimStatus;
import io.matrix.knowledge.Evidence;
import io.matrix.knowledge.EvidenceRelation;
import io.matrix.knowledge.Ids;
import io.matrix.knowledge.KnowledgeWorkbench;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The validation stage of the acquisition (RECON-W42.7).
 *
 * <p>The mission's §5.4 names the promotion layers — RAW, EXTRACTED,
 * CANDIDATE, SUPPORTED, VERIFIED, DISPUTED, SUPERSEDED, INVALIDATED — and
 * §5.5 names the rule that decides what counts as a confirmation:
 *
 * > "Ten documents copying the same source do not constitute ten
 * > independent confirmations."
 *
 * <p><b>What the validator does.</b> The workspace may hold several claims
 * that state the same thing — the package says it, and a document says it
 * too. Those claims are grouped by their proposition, and the group is
 * measured by the <b>distinct content hashes</b> of the sources behind it.
 * A group with two independent sources is a claim the workspace can
 * promote, and the duplicates are superseded by the canonical one.
 *
 * <p><b>What it refuses to do.</b> It does not invent a confidence. The
 * confidence of a claim is an authored field, and the system has no
 * calibrated way to compute one — the mission's §16 forbids presenting a
 * heuristic as a probability without calibration, and a promotion that
 * raised the confidence would be exactly that. The promotion raises the
 * <b>status</b> and the <b>evidence</b>, and the confidence stays what it
 * was.
 *
 * <p><b>The path is legal.</b> The status moves only along the transitions
 * the stage machine allows, and every step is checked with
 * {@link PromotionPolicy#mayPromote}. A move the policy refuses is
 * reported, not performed.
 */
public final class ClaimValidator {

    /** What one promotion did. */
    public record Promotion(
            String claimId,
            String proposition,
            ClaimStatus from,
            ClaimStatus to,
            int independentSources,
            String reason) {
    }

    /** What the validation did. */
    public record Result(
            int groups,
            int promoted,
            int superseded,
            List<Promotion> promotions,
            List<Promotion> supersessions,
            List<String> refusals) {

        public Result {
            promotions = List.copyOf(promotions == null ? List.of() : promotions);
            supersessions = List.copyOf(supersessions == null ? List.of() : supersessions);
            refusals = List.copyOf(refusals == null ? List.of() : refusals);
        }

        /** Whether the validation changed anything. */
        public boolean changedAnything() {
            return promoted > 0 || superseded > 0;
        }
    }

    private ClaimValidator() {
    }

    /**
     * Validates a workspace.
     *
     * @param workbench the workspace
     * @param workspaceId the workspace
     * @return what the validation did
     */
    public static Result validate(KnowledgeWorkbench workbench, String workspaceId) {
        return validate(workbench.storeForValidation(), workspaceId);
    }

    /**
     * Validates a workspace through its store.
     *
     * @param store the store
     * @param workspaceId the workspace
     * @return what the validation did
     */
    public static Result validate(io.matrix.knowledge.KnowledgeStore store, String workspaceId) {
        io.matrix.knowledge.KnowledgeSnapshot snapshot = store.exportWorkspace(workspaceId);
        List<Claim> claims = snapshot.claims();
        List<Evidence> evidence = snapshot.evidence();
        Map<String, String> sourceHashes = sourceHashes(store, workspaceId);

        // The evidence of each claim.
        Map<String, List<Evidence>> byClaim = new LinkedHashMap<>();
        for (Evidence item : evidence) {
            byClaim.computeIfAbsent(item.claimId(), key -> new ArrayList<>()).add(item);
        }

        // The claims grouped by their proposition.
        Map<String, List<Claim>> byProposition = new LinkedHashMap<>();
        for (Claim claim : claims) {
            if (claim.status() == ClaimStatus.REFUTED || claim.status() == ClaimStatus.SUPERSEDED) {
                // A refuted or superseded claim is not a candidate for a
                // promotion: it is the record of a resolution.
                continue;
            }
            byProposition.computeIfAbsent(normalise(claim.proposition()), key -> new ArrayList<>())
                    .add(claim);
        }

        List<Promotion> promotions = new ArrayList<>();
        List<Promotion> supersessions = new ArrayList<>();
        List<String> refusals = new ArrayList<>();
        List<Claim> changedClaims = new ArrayList<>();
        List<Evidence> addedEvidence = new ArrayList<>();

        for (Map.Entry<String, List<Claim>> entry : byProposition.entrySet()) {
            List<Claim> group = entry.getValue();
            if (group.size() < 2) {
                continue;
            }
            // The independent sources behind the group: the DISTINCT
            // content hashes, not the number of evidence records.
            Set<String> hashes = new LinkedHashSet<>();
            for (Claim claim : group) {
                for (Evidence item : byClaim.getOrDefault(claim.id(), List.of())) {
                    String hash = sourceHashes.get(item.sourceId());
                    if (hash != null) {
                        hashes.add(hash);
                    }
                }
            }
            int independent = PromotionPolicy.independentSources(new ArrayList<>(hashes));
            if (independent < 2) {
                // One source, seen twice, is not a confirmation.
                continue;
            }

            // The canonical claim: the one with the highest confidence, and
            // the earliest id when the confidences are equal. The tie-break
            // makes the choice deterministic.
            List<Claim> ordered = new ArrayList<>(group);
            ordered.sort(Comparator
                    .comparingDouble((Claim claim) -> -claim.confidence())
                    .thenComparing(Claim::id));
            Claim canonical = ordered.get(0);

            // The canonical's evidence grows: the duplicates' evidence is
            // re-pointed at it, so that the claim carries the record of
            // every source that states it.
            Set<String> canonicalSources = new LinkedHashSet<>();
            for (Evidence item : byClaim.getOrDefault(canonical.id(), List.of())) {
                canonicalSources.add(item.sourceId());
            }
            for (Claim duplicate : ordered.subList(1, ordered.size())) {
                for (Evidence item : byClaim.getOrDefault(duplicate.id(), List.of())) {
                    if (!canonicalSources.add(item.sourceId())) {
                        // The canonical already carries this source: the
                        // evidence is not added twice, because the record's
                        // identity is the claim and the source.
                        continue;
                    }
                    addedEvidence.add(new Evidence(
                            Ids.digest(canonical.id() + "|evidence|" + item.sourceId()),
                            item.workspaceId(), canonical.id(), item.sourceId(),
                            item.relation(), item.excerpt(), item.quality(),
                            item.recordedAt()));
                }
            }

            // The promotion, along the legal path. The claim is REBUILT
            // with its new status: the first version of this added the old
            // claim to the changes, and the status never moved - the
            // evidence was written and the promotion was not.
            //
            // A claim that is ALREADY verified is left alone: the
            // validation is a stage of the import, and the import runs it
            // on every document - so the second run over the same
            // workspace must not try to promote the same claim again and
            // report a refusal for it.
            if (canonical.status() != ClaimStatus.VERIFIED) {
                Promotion promotion = promote(canonical, independent, promotions, refusals);
                if (promotion != null) {
                    changedClaims.add(withStatus(canonical, ClaimStatus.VERIFIED,
                            "the validation: " + promotion.reason()));
                }
            }

            // The duplicates are superseded by the canonical one.
            for (Claim duplicate : ordered.subList(1, ordered.size())) {
                Promotion supersession = supersede(duplicate, canonical, supersessions, refusals);
                if (supersession != null) {
                    changedClaims.add(withStatus(duplicate, ClaimStatus.SUPERSEDED,
                            "the validation: " + supersession.reason()));
                }
            }
        }

        if (!changedClaims.isEmpty() || !addedEvidence.isEmpty()) {
            store.applyChanges(workspaceId, new io.matrix.knowledge.KnowledgeChanges(List.of(),
                    changedClaims, addedEvidence, List.of(), List.of()));
        }

        return new Result(byProposition.size(), promotions.size(), supersessions.size(),
                promotions, supersessions, refusals);
    }

    /**
     * Promotes a claim along the legal path.
     *
     * @param claim the claim
     * @param independent the number of independent sources
     * @param promotions the promotions that were made
     * @param refusals the refusals
     * @return the promotion, or null when the policy refused
     */
    private static Promotion promote(Claim claim, int independent, List<Promotion> promotions,
                                     List<String> refusals) {
        ClaimStatus current = claim.status();
        // The path starts from the claim's CURRENT status and follows the
        // stage machine: CANDIDATE -> SUPPORTED -> VERIFIED, and
        // SUPPORTED -> VERIFIED. The first version of this always started
        // at CANDIDATE, and it refused to promote the claims the packages
        // already hold - which are SUPPORTED - with the reason "SUPPORTED
        // does not move to SUPPORTED".
        List<ClaimStatus> path = current == ClaimStatus.SUPPORTED
                ? List.of(ClaimStatus.VERIFIED)
                : List.of(ClaimStatus.SUPPORTED, ClaimStatus.VERIFIED);
        for (ClaimStatus step : path) {
            boolean allowed = PromotionPolicy.mayPromote(stageOf(current), stageOf(step),
                    independent, true, false);
            if (!allowed) {
                refusals.add("the promotion of " + claim.id() + " to " + step.name()
                        + " was refused by the policy: "
                        + PromotionPolicy.refusalReason(stageOf(current), stageOf(step),
                        independent, true));
                return null;
            }
            current = step;
        }
        Promotion promotion = new Promotion(claim.id(), claim.proposition(), claim.status(),
                ClaimStatus.VERIFIED, independent,
                "the claim is stated by " + independent + " independent sources");
        promotions.add(promotion);
        return promotion;
    }

    /**
     * Supersedes a duplicate claim.
     *
     * @param duplicate the duplicate
     * @param canonical the claim that supersedes it
     * @param supersessions the supersessions that were made
     * @param refusals the refusals
     * @return the supersession, or null when the policy refused
     */
    private static Promotion supersede(Claim duplicate, Claim canonical,
                                        List<Promotion> supersessions, List<String> refusals) {
        ClaimStatus current = duplicate.status();
        // The path starts from the duplicate's CURRENT status:
        // CANDIDATE -> SUPPORTED -> SUPERSEDED, and SUPPORTED ->
        // SUPERSEDED.
        List<ClaimStatus> path = current == ClaimStatus.SUPPORTED
                ? List.of(ClaimStatus.SUPERSEDED)
                : List.of(ClaimStatus.SUPPORTED, ClaimStatus.SUPERSEDED);
        for (ClaimStatus step : path) {
            boolean allowed = PromotionPolicy.mayPromote(stageOf(current), stageOf(step),
                    1, true, false);
            if (!allowed) {
                refusals.add("the supersession of " + duplicate.id() + " was refused by the "
                        + "policy at " + step.name());
                return null;
            }
            current = step;
        }
        Promotion supersession = new Promotion(duplicate.id(), duplicate.proposition(),
                duplicate.status(), ClaimStatus.SUPERSEDED, 1,
                "the claim is stated by " + canonical.id() + " as well");
        supersessions.add(supersession);
        return supersession;
    }

    /**
     * The claim with a new status.
     *
     * <p>The claim is rebuilt rather than mutated: the record is
     * immutable, and the confidence is carried over untouched - the
     * confidence is an authored field, and the system has no calibrated
     * way to compute one.
     *
     * @param claim the claim
     * @param status the new status
     * @param method the validation method that changed it
     * @return the claim with the new status
     */
    @io.matrix.auditor.ArticleIPermitted(value = "the timestamp of a claim whose status the "
            + "validation changed; it records WHEN the validation ran, and the status itself is "
            + "decided by the evidence and not by the clock")
    private static Claim withStatus(Claim claim, ClaimStatus status, String method) {
        return new Claim(claim.id(), claim.workspaceId(), claim.conceptId(),
                claim.proposition(), claim.scope(), claim.context(), status,
                claim.confidence(), claim.version(), claim.introducedBySourceId(),
                claim.extractionMethod(), method, claim.warrant(), claim.derivationStatus(),
                claim.createdAt(), java.time.Instant.now().toString());
    }

    /** The acquisition's stage of a claim status. */
    private static PromotionStage stageOf(ClaimStatus status) {
        return switch (status) {
            case DRAFT -> PromotionStage.RAW;
            case CANDIDATE -> PromotionStage.CANDIDATE;
            case SUPPORTED -> PromotionStage.SUPPORTED;
            case VERIFIED -> PromotionStage.VERIFIED;
            case CONTESTED -> PromotionStage.DISPUTED;
            case SUPERSEDED -> PromotionStage.SUPERSEDED;
            case REFUTED -> PromotionStage.INVALIDATED;
            case UNKNOWN -> PromotionStage.RAW;
            // RECON-W43.0: the epistemic statuses the ADR added, mapped onto
            // the promotion layers. An OBSERVATION is the rawest thing the
            // system holds; a HYPOTHESIS is a candidate; a claim whose
            // evidence does not decide is NOT invalidated — it is still a
            // candidate, because nothing has refuted it; and a REFINED claim
            // has been superseded by a narrower one.
            case OBSERVED -> PromotionStage.RAW;
            case HYPOTHESIS -> PromotionStage.CANDIDATE;
            case INSUFFICIENT_EVIDENCE -> PromotionStage.CANDIDATE;
            case REFINED -> PromotionStage.SUPERSEDED;
        };
    }

    /** The content hash of every source of the workspace. */
    private static Map<String, String> sourceHashes(io.matrix.knowledge.KnowledgeStore store,
                                                    String workspaceId) {
        Map<String, String> hashes = new LinkedHashMap<>();
        for (io.matrix.knowledge.Source source : store.sources(workspaceId)) {
            hashes.put(source.id(), source.contentHash());
        }
        return hashes;
    }

    /** The proposition, normalised for comparison. */
    private static String normalise(String proposition) {
        if (proposition == null) {
            return "";
        }
        return proposition.toLowerCase(Locale.ROOT).replaceAll("[^a-zа-я0-9]+", " ").trim();
    }
}
