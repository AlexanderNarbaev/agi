package io.matrix.auditor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Article I — declares that a member's wall-clock or randomness use is PERMITTED, and why.
 *
 * <h2>What it changes</h2>
 *
 * <p>CONSTITUTION Article I forbids wall-clock <em>in decision paths</em> and requires
 * determinism for replay and audit. It does not forbid recording when something happened. An
 * audit-log timestamp, a telemetry uptime reading and a latency measurement are all
 * wall-clock reads that decide nothing.</p>
 *
 * <p>The difference is not decidable from source text, so this annotation is how a human makes
 * the judgement explicit and machine-checkable. A wall-clock read inside an annotated member is
 * classified {@link DecisionPathAuditor.Permit#PERMITTED} instead of left as
 * {@link DecisionPathAuditor.Permit#UNCLASSIFIED}, and the compliance gate blocks on
 * UNCLASSIFIED so the debt cannot silently regrow.</p>
 *
 * <h2>Why an annotation and not a comment</h2>
 *
 * <p>{@link DecisionPathAuditor} strips comments before scanning, correctly — a comment
 * documenting a violation must not exempt it. So a javadoc note saying "this is fine" would be
 * invisible to the very gate meant to enforce it. This is code, it survives the strip, and it
 * cannot be added without the author writing down a reason.</p>
 *
 * <h2>The bar for using it</h2>
 *
 * <p>The value must not influence any branch, retry count, timeout, ordering or verdict. If it
 * reaches a decision, the fix is to inject a clock — as {@code InputFilterGuard}'s rate limiter
 * was — and not to annotate. Annotating a real violation to make the gate green is the failure
 * this annotation exists to make unnecessary and visible.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE, ElementType.CONSTRUCTOR, ElementType.FIELD})
public @interface ArticleIPermitted {

    /**
     * Why the wall-clock or randomness use here does not reach a decision.
     *
     * <p>Recorded verbatim in the audit report, so the justification is reviewable rather than
     * assumed. Empty strings are rejected by the gate.</p>
     */
    String value();
}