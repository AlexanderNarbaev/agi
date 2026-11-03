package io.matrix.cognitive.algorithms;

/**
 * W45.0 — what kind of evidence a procedure's validation rests on.
 *
 * <p>The W44 wave called every agreement a "held-out validation". It was not one. The kernel
 * validated the fast path against the reference on the SAME observations the model was built
 * from, and then reported the agreement as if it had been measured on data the model had never
 * seen. An agreement over the model's own data is a CONSISTENCY CHECK: it shows that the cheap
 * path computes what the expensive path computes, and it says nothing at all about whether the
 * model is right about the world.</p>
 *
 * <p>Those are two different claims and the record has to be able to tell them apart.</p>
 *
 * <ul>
 *   <li>{@link #IN_SAMPLE} — the agreement was computed over the data the model was built
 *       from. This is a consistency check. It is worth something — it is what licenses the
 *       fast path as an algebraic shortcut of the reference — and it is NOT evidence that the
 *       model generalises.</li>
 *   <li>{@link #INDEPENDENT} — the agreement was computed over data the model was NOT built
 *       from. This is a validation, and it is the only kind that may support a claim that a
 *       procedure is validated.</li>
 * </ul>
 *
 * <p><b>Why the micro-world cannot produce independent evidence.</b> The lamp world has two
 * input variables, so its input domain is four assignments. A truth table over four rows is
 * determined only when all four have been observed, so a model that has SETTLED has necessarily
 * seen the whole domain. The set of observations the model was not built from is empty, and no
 * amount of care can make it non-empty: the domain is finite and the model has consumed it.
 * Any split of the same four rows is a split of the same data, and calling the halves
 * independent is a claim the domain cannot support.</p>
 */
public enum ValidationKind {

    /** The agreement was computed over the model's own construction data. */
    IN_SAMPLE,

    /** The agreement was computed over data the model was not built from. */
    INDEPENDENT;

    /**
     * Whether this kind of evidence may be called a validation.
     *
     * @return true only for genuinely independent evidence
     */
    public boolean isValidation() {
        return this == INDEPENDENT;
    }
}
