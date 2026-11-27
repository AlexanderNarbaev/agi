package io.matrix.knowledge;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CRJ-01 / ADAPTIVE-COMPETENCE — the REUSABLE retrieval statistics of one view.
 *
 * <p><b>What this is.</b> The tokenised form of a view's claims: the terms each claim carries,
 * the lemmas of its words, the inverse document frequency over those terms, and the inverted
 * indexes that say which claims carry a term. Computing it is <b>O(total text)</b>: every claim
 * is split, folded, lemmatised and counted. The retrieval scores a question against those
 * terms, so the terms are the expensive half of a reading and the scoring is the cheap half.</p>
 *
 * <p><b>Why it is a value and not a computation.</b> The H2 milestone measured the cost of the
 * procedure path and named it: the correction that keeps a restricted reading comparable with
 * the reference — the support set decides <i>what may be cited</i>, the workspace's statistics
 * decide <i>how relevant</i> a claim is — makes the retriever build the statistics of the
 * <b>statistics view</b> on every reading, so a procedure path pays a second full pass over the
 * workspace's claims for every question it answers. The natural next engineering question was
 * named there and left open: <i>"whether that pass can be made once per workspace instead of
 * once per reading"</i>. This class is the answer's payload: the pass is made once, and the
 * result is reused.</p>
 *
 * <p><b>Why the inverted indexes are here.</b> A question can only reach a claim that carries
 * one of its terms, so the claims that CAN match are found by an index lookup rather than by a
 * scan of every claim. The index is a SUPERSET of the claims a full scan would match: it holds
 * every claim that carries a term, and every claim whose lemmas agree with a term's lemmas —
 * which is exactly the retriever's own two matching channels. A reader that wants to know
 * whether a restricted reading would still reach the workspace's best evidence can therefore
 * ask about the candidates alone, and get the same answer a full scan gives.</p>
 *
 * <p><b>What the statistics do NOT decide.</b> They do not decide which claims may be cited: a
 * claim the workspace has not established is not evidence, and that is the
 * {@link EpistemicPolicy}'s rule, applied when the statistics are used. They do not decide
 * whether a question is answerable. They are the corpus's shape, not its verdict.</p>
 *
 * <p><b>What the identity is.</b> The statistics are a function of the view's claims and of the
 * concepts those claims belong to — of the claim's proposition and of the concept's name and
 * definition, because those three texts are what the claim is indexed by. The identity is a
 * digest over exactly those texts, so a view whose claims changed gets a different identity and
 * the stale statistics are never served for it. A claim's STATUS is deliberately not part of it:
 * a claim that moved from CANDIDATE to SUPPORTED is indexed by the same words, and the
 * statistics it would be scored with did not change.</p>
 */
public final class KnowledgeStatistics {

    /** The separator between two fields of the identity material. */
    private static final String FIELD_SEPARATOR = "\u0000";

    /** The separator between two claims of the identity material. */
    private static final String CLAIM_SEPARATOR = "\n";

    /** The digest of the view the statistics were computed for. */
    private final String viewIdentity;

    /** The terms of each claim, by claim identifier. */
    private final Map<String, Set<String>> claimTerms;

    /** The lemmas of each claim's words, by claim identifier. */
    private final Map<String, Set<String>> claimLemmas;

    /** The inverse document frequency of every term of the view. */
    private final Map<String, Double> idf;

    /** The claims that carry a term, by term. */
    private final Map<String, Set<String>> claimsByTerm;

    /** The claims whose lemmas carry a lemma, by lemma. */
    private final Map<String, Set<String>> claimsByLemma;

    private KnowledgeStatistics(String viewIdentity, Map<String, Set<String>> claimTerms,
            Map<String, Set<String>> claimLemmas, Map<String, Double> idf,
            Map<String, Set<String>> claimsByTerm, Map<String, Set<String>> claimsByLemma) {
        this.viewIdentity = viewIdentity;
        this.claimTerms = claimTerms;
        this.claimLemmas = claimLemmas;
        this.idf = idf;
        this.claimsByTerm = claimsByTerm;
        this.claimsByLemma = claimsByLemma;
    }

    /**
     * The statistics of a view.
     *
     * @param view the view
     * @return the statistics
     */
    public static KnowledgeStatistics of(KnowledgeSnapshot view) {
        return of(view, identityOf(view));
    }

    /**
     * The statistics of a view, with an identity the caller states.
     *
     * @param view         the view
     * @param viewIdentity the identity the statistics are for
     * @return the statistics
     */
    public static KnowledgeStatistics of(KnowledgeSnapshot view, String viewIdentity) {
        Map<String, Concept> concepts = new LinkedHashMap<>();
        for (Concept concept : view.concepts()) {
            concepts.put(concept.id(), concept);
        }
        Map<String, Set<String>> claimTerms = new LinkedHashMap<>();
        Map<String, Set<String>> claimLemmas = new LinkedHashMap<>();
        Map<String, Set<String>> claimsByTerm = new LinkedHashMap<>();
        Map<String, Set<String>> claimsByLemma = new LinkedHashMap<>();
        for (Claim claim : view.claims()) {
            Concept concept = concepts.get(claim.conceptId());
            StringBuilder text = new StringBuilder(claim.proposition());
            if (concept != null) {
                text.append(' ').append(concept.name());
                if (concept.definition() != null) {
                    text.append(' ').append(concept.definition());
                }
            }
            Set<String> terms = new LinkedHashSet<>(TextFeatures.tokens(text.toString()));
            Set<String> lemmas = EnglishLemma.lemmasOf(text.toString());
            claimTerms.put(claim.id(), terms);
            claimLemmas.put(claim.id(), lemmas);
            for (String term : terms) {
                claimsByTerm.computeIfAbsent(term, key -> new LinkedHashSet<>()).add(claim.id());
            }
            for (String lemma : lemmas) {
                claimsByLemma.computeIfAbsent(lemma, key -> new LinkedHashSet<>()).add(claim.id());
            }
        }
        Map<String, Double> idf = TextFeatures.idf(new ArrayList<>(claimTerms.values()));
        return new KnowledgeStatistics(viewIdentity, Map.copyOf(claimTerms), Map.copyOf(claimLemmas),
                Map.copyOf(idf), Map.copyOf(claimsByTerm), Map.copyOf(claimsByLemma));
    }

    /**
     * The identity of a view's statistics.
     *
     * <p>The digest covers the texts the statistics are computed from AND THE KEYS THEY ARE
     * INDEXED BY: every claim's own identifier, its proposition, and the name and definition
     * of the concept it belongs to. A view whose claims changed therefore has a different
     * identity, and the statistics computed for the older view are never served for it.</p>
     *
     * <p><b>Why the claim's identifier is part of the identity.</b> The statistics are indexed
     * BY the claim's identifier, and a production claim id is derived from the workspace, the
     * document's content hash and the proposition
     * ({@code DocumentImporter.claimId}: {@code digest(workspaceId | contentHash |
     * proposition)}). A document revised in a way that changes its bytes but not its sentences
     * therefore yields <b>new claim ids over the same propositions</b> — and an identity over
     * the propositions alone would be UNCHANGED, so the older view's statistics would be
     * served for the newer one. Their index would then hold the older ids, every lookup of a
     * new id would miss, and the reading would score <b>no claim at all</b>: a silent loss of
     * the whole evidence, which is worse than a stale number because nothing says so. The
     * identity covers the keys for exactly that reason.</p>
     *
     * @param view the view
     * @return the identity, in lowercase hex
     */
    public static String identityOf(KnowledgeSnapshot view) {
        Map<String, Concept> concepts = new LinkedHashMap<>();
        for (Concept concept : view.concepts()) {
            concepts.put(concept.id(), concept);
        }
        List<String> material = new ArrayList<>();
        for (Claim claim : view.claims()) {
            Concept concept = concepts.get(claim.conceptId());
            material.add(claim.id() + FIELD_SEPARATOR
                    + claim.proposition() + FIELD_SEPARATOR
                    + (concept == null ? "" : concept.name()) + FIELD_SEPARATOR
                    + (concept == null || concept.definition() == null ? "" : concept.definition()));
        }
        // the ORDER of the claims is not part of the statistics — the identity is over the
        // SET of texts — so the material is sorted, and two views that hold the same claims in
        // a different order share one identity
        material.sort(Comparator.naturalOrder());
        return sha256(String.join(CLAIM_SEPARATOR, material));
    }

    /**
     * The statistics of a SUBSET of this view's claims.
     *
     * <p><b>Why a restriction needs no new pass.</b> A claim is indexed by its proposition
     * and by the name and definition of the concept it belongs to, and a restricted view
     * keeps the claim and that concept as they are — it drops the other claims. The terms of
     * the claims that remain are therefore the terms they already have, and the restricted
     * statistics are a SUBSET of these, not a second tokenisation of the same text.</p>
     *
     * <p><b>What the inverse document frequency does NOT do here.</b> It stays the parent's.
     * The relevance statistics are a property of the corpus the question is asked IN, and a
     * restricted reading is the same question asked in the same workspace with fewer claims
     * eligible to answer it — so the weights it scores with are the workspace's own. That is
     * the H2 correction, and this method does not undo it.</p>
     *
     * @param claimIds the claims the restricted view holds
     * @return the statistics of the restricted view
     */
    public KnowledgeStatistics restrictedTo(Set<String> claimIds) {
        Set<String> kept = claimIds == null ? Set.of() : claimIds;
        Map<String, Set<String>> terms = new LinkedHashMap<>();
        Map<String, Set<String>> lemmas = new LinkedHashMap<>();
        Map<String, Set<String>> byTerm = new LinkedHashMap<>();
        Map<String, Set<String>> byLemma = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : claimTerms.entrySet()) {
            if (!kept.contains(entry.getKey())) {
                continue;
            }
            terms.put(entry.getKey(), entry.getValue());
            lemmas.put(entry.getKey(), claimLemmas.getOrDefault(entry.getKey(), Set.of()));
            for (String term : entry.getValue()) {
                byTerm.computeIfAbsent(term, key -> new LinkedHashSet<>()).add(entry.getKey());
            }
            for (String lemma : lemmas.get(entry.getKey())) {
                byLemma.computeIfAbsent(lemma, key -> new LinkedHashSet<>()).add(entry.getKey());
            }
        }
        return new KnowledgeStatistics(viewIdentity, Map.copyOf(terms), Map.copyOf(lemmas), idf,
                Map.copyOf(byTerm), Map.copyOf(byLemma));
    }

    /** The identity the statistics were computed for. */
    public String viewIdentity() {
        return viewIdentity;
    }

    /** The terms of a claim, or an empty set when the view holds no such claim. */
    public Set<String> termsOf(String claimId) {
        return claimTerms.getOrDefault(claimId, Set.of());
    }

    /** The terms of every claim of the view, by claim identifier. */
    public Map<String, Set<String>> terms() {
        return claimTerms;
    }

    /** The lemmas of every claim of the view, by claim identifier. */
    public Map<String, Set<String>> lemmas() {
        return claimLemmas;
    }

    /** The lemmas of a claim's words, or an empty set when the view holds no such claim. */
    public Set<String> lemmasOf(String claimId) {
        return claimLemmas.getOrDefault(claimId, Set.of());
    }

    /** The inverse document frequency of every term of the view. */
    public Map<String, Double> idf() {
        return idf;
    }

    /** The claims that carry a term, or an empty set when no claim carries it. */
    public Set<String> claimsCarrying(String term) {
        return claimsByTerm.getOrDefault(term, Set.of());
    }

    /** The claims whose lemmas carry a lemma, or an empty set when no claim carries it. */
    public Set<String> claimsWhoseLemmasCarry(String lemma) {
        return claimsByLemma.getOrDefault(lemma, Set.of());
    }

    /** The number of claims the statistics were computed over. */
    public int claimCount() {
        return claimTerms.size();
    }

    /**
     * The digest of some text.
     *
     * @param text the text
     * @return the digest, in lowercase hex
     */
    private static String sha256(String text) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }
}
