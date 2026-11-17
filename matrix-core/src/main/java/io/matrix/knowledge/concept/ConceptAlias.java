package io.matrix.knowledge.concept;

/**
 * A DECLARED alias of a referent: two lemmas that denote the same thing, or two that
 * do not.
 *
 * <p><b>What this is.</b> The alias layer of the referent key, as DATA rather than as
 * prose. The owner's decision OD-064 is that the alias table is a <b>declared</b>
 * table — written by hand, auditable, and explicitly <b>temporary</b> — and that its
 * status must be carried <b>in the data</b>, so that a later reader cannot mistake a
 * declared entry for a derived fact.</p>
 *
 * <p><b>Why the status is a column and not a sentence.</b> A table whose status lives
 * only in the document that describes it is a table that a future reader — or a future
 * migration — can mistake for a fact of the world. The {@link #declarationStatus()}
 * column is what makes the distinction checkable: the entries that carry
 * {@link DeclarationStatus#DECLARED_TEMPORARY} are hand-written claims about one
 * corpus, and the entries that would carry a derived status would have to say so.</p>
 *
 * @param canonicalLemma the lemma of the canonical referent
 * @param variantLemma the lemma of the variant
 * @param kind how the two are related
 * @param reason why the entry says what it says
 * @param declaredBy who declared the entry
 * @param declarationStatus how the entry came to be there
 * @param recordedAt when the entry was recorded
 */
public record ConceptAlias(String canonicalLemma, String variantLemma, AliasKind kind,
                            String reason, String declaredBy, DeclarationStatus declarationStatus,
                            String recordedAt) {

    /**
     * How the two lemmas are related.
     */
    public enum AliasKind {
        /** The two spellings are the same term. */
        SPELLING,
        /** The two are the same term under different names. */
        SYNONYM,
        /** The two are the same term under different wording. */
        PARAPHRASE,
        /** The two are NOT the same term: the entry exists to keep them apart. */
        DISTINCT
    }

    /**
     * How an alias entry came to be there.
     *
     * <p>The value is the declaration's status <b>in the data</b>, so that a reader can
     * tell a hand-written entry from a derived one without reading the prose.</p>
     */
    public enum DeclarationStatus {
        /**
         * A hand-written entry, fitted to one corpus, and temporary: the owner's
         * decision OD-064 records that the alias table is a stopgap until a derived
         * table can be evaluated.
         */
        DECLARED_TEMPORARY
    }

    /**
     * A DECLARED entry: the owner's decision OD-064.
     *
     * <p>The status is filled here and not at every call site, so that an entry
     * cannot be written without saying that it is declared, temporary and fitted to
     * one corpus.</p>
     *
     * @param canonicalLemma the lemma of the canonical referent
     * @param variantLemma the lemma of the variant
     * @param kind how the two are related
     * @param reason why the entry says what it says
     * @return the entry
     */
    public static ConceptAlias declared(String canonicalLemma, String variantLemma,
                                         AliasKind kind, String reason) {
        return new ConceptAlias(canonicalLemma, variantLemma, kind, reason,
                "the owner's decision OD-064", DeclarationStatus.DECLARED_TEMPORARY,
                "2026-11-16T00:00:00Z");
    }

    /**
     * The identity of the entry: the two lemmas.
     *
     * @return the identity
     */
    public String identity() {
        return canonicalLemma + "=" + variantLemma;
    }
}
