package io.matrix.knowledge.concept.research;

import io.matrix.knowledge.Claim;
import io.matrix.knowledge.concept.ConceptAlias;
import io.matrix.knowledge.ClaimSpan;
import io.matrix.knowledge.Concept;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * RESEARCH ONLY — the resolver of the referent key.
 *
 * <p><b>What this is.</b> The instrument that turns a workspace's claims into the
 * <b>referents</b> the policy of {@link ReferentPolicy} recognises, and nothing else.
 * It reads a snapshot, it writes nothing, and it migrates nothing: the concept ids
 * of the store are not read, not derived and not changed, and no claim is moved.
 *
 * <p><b>The resolution, in the order it is applied.</b>
 * <ol>
 *   <li><b>The rejection.</b> A surface form that is not a referent (the
 *       {@link ReferentPolicy#rejection(String) reject-only} rule) gets no key. Its
 *       claim keeps its own text — the claim is never dropped, and the retrieval
 *       still sees it.</li>
 *   <li><b>The within-document cluster.</b> Two accepted claims with the same lemma
 *       in the same document are one referent.</li>
 *   <li><b>The across-document cluster.</b> Two clusters with the same lemma in
 *       different documents are one referent when the lemma is SPECIFIC, or — for a
 *       BARE head — when their context signatures overlap. This is where
 *       <i>"the same word in different contexts"</i> is decided.</li>
 *   <li><b>The alias layer.</b> A declared SYNONYM, PARAPHRASE or SPELLING entry
 *       merges two clusters whose lemmas it links; a declared DISTINCT entry forbids
 *       a merge whatever else would allow it.</li>
 * </ol>
 *
 * <p><b>The key of a cluster</b> is the key of its EARLIEST member: the lemma, the
 * source document and the anchor span of the first mention. The anchor is what makes
 * a key verifiable — a reader can be sent to the characters the referent was read
 * at — and the earliest member is chosen so that the key does not move when a later
 * mention is added.
 *
 * <p><b>Nothing in this class is wired into production.</b> It is an instrument; the
 * write-up is {@code docs-v2/engineering/EPISTEMIC-REFERENT-KEY.md}.
 */
public final class ReferentResolver {

    /**
     * One claim as the policy sees it.
     *
     * @param claimId the claim's id
     * @param surface the subject phrase the production importer produced
     * @param lemma the normalised lemma
     * @param head the normalised head
     * @param sourceId the document the claim was extracted from
     * @param anchorStart the first character of the claim's span
     * @param anchorEnd the character after the last one of the claim's span
     * @param signature the context signature of the claim's own sentence
     * @param rejection why the surface form is not a referent, or NONE
     */
    public record Occurrence(String claimId, String surface, String lemma, String head,
                             String sourceId, long anchorStart, long anchorEnd,
                             String signature, ReferentPolicy.Rejection rejection) {
        /** Whether the policy accepted this surface form as a referent. */
        public boolean accepted() {
            return rejection == ReferentPolicy.Rejection.NONE;
        }
    }

    /**
     * One referent: the claims the policy resolved to it.
     *
     * @param key the referent key
     * @param displayName the name the referent is shown under
     * @param aliases the other surface forms the referent is known by
     * @param claimIds the claims of this referent
     * @param sourceIds the documents the referent was read in
     * @param signature the context signature the referent was resolved with
     */
    public record Cluster(ReferentKey key, String displayName, List<String> aliases,
                          List<String> claimIds, List<String> sourceIds, String signature) {
    }

    /**
     * The outcome of the resolution.
     *
     * @param claims how many claims were read
     * @param accepted how many of them the policy accepted as referents
     * @param rejected how many it did not
     * @param clusters how many referents the resolution produced
     * @param rejections the count per rejection reason
     * @param occurrences every claim as the policy saw it
     * @param clustersDetail the referents themselves
     */
    public record Result(int claims, int accepted, int rejected, int clusters,
                         Map<ReferentPolicy.Rejection, Integer> rejections,
                         List<Occurrence> occurrences, List<Cluster> clustersDetail) {
    }

    /**
     * Resolves a workspace's claims into referents.
     *
     * @param claims the claims of the workspace
     * @param concepts the concepts of the workspace, used only to report how many of
     *                 them the resolution would replace
     * @return the outcome of the resolution
     */
    public static Result resolve(List<Claim> claims, List<Concept> concepts) {
        return resolve(claims, concepts, true, ReferentPolicy.Strictness.LENIENT);
    }

    /**
     * Resolves a workspace's claims into referents at a given strictness.
     *
     * @param claims the claims of the workspace
     * @param concepts the concepts of the workspace
     * @param applyRejection whether a surface form that is not a referent is rejected
     * @param strictness the strictness of the policy
     * @return the outcome of the resolution
     */
    public static Result resolve(List<Claim> claims, List<Concept> concepts,
                                 boolean applyRejection) {
        return resolve(claims, concepts, applyRejection, ReferentPolicy.Strictness.LENIENT);
    }

    /**
     * Resolves a workspace's claims into referents at a given strictness.
     *
     * @param claims the claims of the workspace
     * @param concepts the concepts of the workspace
     * @param applyRejection whether a surface form that is not a referent is rejected
     * @param strictness the strictness of the policy
     * @return the outcome of the resolution
     */
    public static Result resolve(List<Claim> claims, List<Concept> concepts,
                                 boolean applyRejection, ReferentPolicy.Strictness strictness) {
        return resolve(claims, concepts, applyRejection, strictness,
                ReferentPolicy.DECLARED_ALIASES);
    }

    /**
     * Resolves a workspace's claims into referents, under an ALIAS TABLE.
     *
     * <p>The alias layer is DATA in the committed schema (the owner's decision
     * OD-064), so the resolver takes the table as an argument: the declared list is
     * the seed, and the store's rows are what a migrated workspace actually holds.</p>
     *
     * @param claims the claims of the workspace
     * @param concepts the concepts of the workspace
     * @param applyRejection whether a surface form that is not a referent is rejected
     * @param strictness the strictness of the policy
     * @param aliases the alias table
     * @return the outcome of the resolution
     */
    public static Result resolve(List<Claim> claims, List<Concept> concepts,
                                 boolean applyRejection, ReferentPolicy.Strictness strictness,
                                 List<ConceptAlias> aliases) {
        List<Occurrence> occurrences = new ArrayList<>();
        List<ConceptAlias> aliasTable = aliases;
        Map<ReferentPolicy.Rejection, Integer> rejections = new TreeMap<>();
        int accepted = 0;
        for (Claim claim : claims) {
            String surface = claim.scope() == null ? "" : claim.scope().strip();
            ReferentPolicy.Rejection rejection = applyRejection
                    ? ReferentPolicy.rejection(surface, strictness)
                    : ReferentPolicy.Rejection.NONE;
            rejections.merge(rejection, 1, Integer::sum);
            String lemma = ReferentPolicy.lemma(surface);
            long start = ReferentKey.UNKNOWN_ANCHOR;
            long end = ReferentKey.UNKNOWN_ANCHOR;
            if (claim.context() != null && !ClaimSpan.isWholeDocument(claim.context())) {
                io.matrix.contract.Provenance.Span span = ClaimSpan.parse(claim.context());
                if (span != null) {
                    start = span.start();
                    end = span.end() == null ? ReferentKey.UNKNOWN_ANCHOR : span.end();
                }
            }
            String signature = ReferentPolicy.contextSignature(claim.proposition(), lemma);
            occurrences.add(new Occurrence(claim.id(), surface, lemma,
                    ReferentPolicy.head(surface), claim.introducedBySourceId(), start, end,
                    signature, rejection));
            if (rejection == ReferentPolicy.Rejection.NONE) {
                accepted++;
            }
        }

        // ── the within-document clusters ──────────────────────────────────────
        Map<String, List<Occurrence>> byLemmaAndSource = new LinkedHashMap<>();
        for (Occurrence occurrence : occurrences) {
            if (!occurrence.accepted()) {
                continue;
            }
            String key = ReferentPolicy.canonicalLemma(aliases, occurrence.lemma()) + "\u0000"
                    + occurrence.sourceId();
            byLemmaAndSource.computeIfAbsent(key, ignored -> new ArrayList<>()).add(occurrence);
        }

        // ── the across-document resolution ────────────────────────────────────
        List<List<Occurrence>> groups = new ArrayList<>();
        for (List<Occurrence> group : byLemmaAndSource.values()) {
            groups.add(group);
        }
        boolean merged = true;
        while (merged) {
            merged = false;
            for (int i = 0; i < groups.size(); i++) {
                for (int j = i + 1; j < groups.size(); j++) {
                    if (mergeable(groups.get(i), groups.get(j), aliasTable)) {
                        groups.get(i).addAll(groups.get(j));
                        groups.remove(j);
                        merged = true;
                        break;
                    }
                }
                if (merged) {
                    break;
                }
            }
        }

        // ── the referents ─────────────────────────────────────────────────────
        List<Cluster> clusters = new ArrayList<>();
        for (List<Occurrence> group : groups) {
            List<Occurrence> ordered = new ArrayList<>(group);
            ordered.sort(Comparator.comparingLong(Occurrence::anchorStart)
                    .thenComparing(Occurrence::claimId));
            Occurrence earliest = ordered.get(0);
            ReferentKey key = new ReferentKey(earliest.lemma(), earliest.sourceId(),
                    earliest.anchorStart(), earliest.anchorEnd());
            Map<String, Integer> frequencies = new LinkedHashMap<>();
            Set<String> sourceIds = new LinkedHashSet<>();
            Set<String> claimIds = new LinkedHashSet<>();
            for (Occurrence occurrence : ordered) {
                frequencies.merge(occurrence.surface(), 1, Integer::sum);
                sourceIds.add(occurrence.sourceId());
                claimIds.add(occurrence.claimId());
            }
            String displayName = displayNameOf(frequencies);
            List<String> surfaceForms = new ArrayList<>();
            for (String surface : frequencies.keySet()) {
                if (!surface.equals(displayName)) {
                    surfaceForms.add(surface);
                }
            }
            for (ConceptAlias alias : aliasTable) {
                if (alias.kind() == ConceptAlias.AliasKind.DISTINCT) {
                    continue;
                }
                if (alias.canonicalLemma().equals(earliest.lemma())
                        && !surfaceForms.contains(alias.variantLemma())
                        && !alias.variantLemma().equals(displayName)) {
                    surfaceForms.add(alias.variantLemma());
                }
            }
            clusters.add(new Cluster(key, displayName, surfaceForms, new ArrayList<>(claimIds),
                    new ArrayList<>(sourceIds), earliest.signature()));
        }
        clusters.sort(Comparator.comparing(cluster -> cluster.key().identity()));
        return new Result(claims.size(), accepted, claims.size() - accepted, clusters.size(),
                rejections, occurrences, clusters);
    }

    /**
     * Whether two clusters are the same referent.
     *
     * @param first the first cluster
     * @param second the second cluster
     * @return whether they are one referent
     */
    private static boolean mergeable(List<Occurrence> first, List<Occurrence> second,
                                     List<ConceptAlias> aliasTable) {
        Occurrence a = first.get(0);
        Occurrence b = second.get(0);
        String lemmaA = a.lemma();
        String lemmaB = b.lemma();
        if (lemmaA.equals(lemmaB)) {
            if (ReferentPolicy.declaredDistinct(aliasTable, lemmaA, lemmaB)) {
                return false;
            }
            if (a.sourceId().equals(b.sourceId())) {
                return true;
            }
            // Across documents: a bare head needs the context to agree; a modified
            // phrase is specific enough on its own.
            if (!a.lemma().equals(a.head()) || !b.lemma().equals(b.head())) {
                return ReferentPolicy.specific(lemmaA);
            }
            return overlaps(a.signature(), b.signature());
        }
        ConceptAlias.AliasKind declared = ReferentPolicy.declaredAs(aliasTable, lemmaA, lemmaB);
        if (declared == ConceptAlias.AliasKind.DISTINCT) {
            return false;
        }
        if (declared != null) {
            return true;
        }
        // An alias entry may link the two clusters through their canonical lemmas.
        return ReferentPolicy.canonicalLemma(aliasTable, lemmaA)
                .equals(ReferentPolicy.canonicalLemma(aliasTable, lemmaB));
    }

    /**
     * Whether two context signatures share at least one content word.
     *
     * @param first the first signature
     * @param second the second signature
     * @return whether they overlap
     */
    private static boolean overlaps(String first, String second) {
        if (first == null || second == null || first.isBlank() || second.isBlank()) {
            return false;
        }
        Set<String> left = new LinkedHashSet<>(List.of(first.split(" ")));
        for (String word : second.split(" ")) {
            if (left.contains(word)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The name a referent is shown under: its most frequent surface form, and the
     * lexicographically smallest one when several are equally frequent.
     *
     * @param frequencies the surface forms of the referent and their counts
     * @return the display name
     */
    private static String displayNameOf(Map<String, Integer> frequencies) {
        String best = null;
        int bestCount = -1;
        for (Map.Entry<String, Integer> entry : frequencies.entrySet()) {
            int count = entry.getValue();
            String name = entry.getKey();
            if (count > bestCount || (count == bestCount && best != null
                    && name.compareTo(best) < 0)) {
                best = name;
                bestCount = count;
            }
        }
        return best == null ? "" : best;
    }

    /**
     * The lower-cased key of a lemma, used only for the reports.
     *
     * @param lemma the lemma
     * @return the key
     */
    public static String reportKey(String lemma) {
        return lemma == null ? "" : lemma.toLowerCase(Locale.ROOT);
    }

    private ReferentResolver() {
    }
}
