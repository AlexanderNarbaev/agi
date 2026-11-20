package io.matrix.knowledge.concept.research;

/**
 * RESEARCH ONLY — the referent key of the concept layer.
 *
 * <p><b>What this is.</b> The identity half of the concept-model repair
 * (the owner's directive of 2026-11-15, §3 P1) needs a key that identifies the
 * <b>referent</b> of a claim rather than the text the subject extractor happened to
 * cut. The research that measured the defect
 * ({@code docs-v2/engineering/EPISTEMIC-CONCEPT-IDENTITY.md}) recommended the
 * <b>shape</b>: a normalised lemma + the source document + the anchor span, with a
 * display name and an alias list on top. It also said, in the same sentence, that
 * <i>"the resolution and alias policy is the unsolved part"</i>.
 *
 * <p>This class is the <b>shape</b>, nothing else. It is a value object over three
 * components and it changes nothing: it is not referenced by any production class, it
 * is not written to any store, and no concept id is derived from it. It exists so that
 * the policy in {@link ReferentPolicy} and the resolver in {@link ReferentResolver}
 * have a type to talk about, and so that a reviewer can read the shape in one place.
 *
 * <p><b>Why the three components and not one.</b>
 * <ul>
 *   <li>the <b>lemma</b> says WHAT is denoted, in a normalised form, so that a
 *       spelling variant, a plural and a determiner do not make three referents;</li>
 *   <li>the <b>source document</b> says WHERE the reference occurs, so that the same
 *       word in two documents is two <i>occurrences</i> — the policy decides whether
 *       they are one referent, and the key alone does not decide it;</li>
 *   <li>the <b>anchor span</b> says WHICH PART of the document, so that a key is
 *       verifiable: a reader can be sent to the exact characters the referent was
 *       read at. This is the same span convention the repository already declares
 *       ({@link io.matrix.knowledge.ClaimSpan}, characters of the extracted text).</li>
 * </ul>
 *
 * <p><b>What it does NOT do.</b> It does not merge anything, it does not decide
 * whether two keys are the same referent, and it does not claim that the corpus can
 * support a general concept-identification quality claim. The measurements live in
 * {@code matrix-core/src/test/java/io/matrix/knowledge/concept/research/**} and the
 * write-up in {@code docs-v2/engineering/EPISTEMIC-REFERENT-KEY.md}.
 */
public record ReferentKey(String lemma, String sourceId, long anchorStart, long anchorEnd) {

    /**
     * The marker that says "the anchor is not known" in the key's identity.
     *
     * <p>A claim whose context does not carry a span (the whole document was read)
     * still gets a key; the anchor is then this marker, and the key says so rather
     * than inventing an offset.</p>
     */
    public static final long UNKNOWN_ANCHOR = -1L;

    /**
     * The key's identity string.
     *
     * <p>It is the key's own three components and nothing else, so two keys are equal
     * exactly when they denote the same occurrence in the same place.</p>
     *
     * @return the identity of this key
     */
    public String identity() {
        return lemma + "@" + sourceId + "#" + anchorStart + "-" + anchorEnd;
    }

    /**
     * Whether this key carries an anchor that points at real characters.
     *
     * @return whether the anchor is known
     */
    public boolean anchored() {
        return anchorStart != UNKNOWN_ANCHOR;
    }
}
