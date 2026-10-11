package io.matrix.auditor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Article I — marks a member as belonging to ADVERSARIAL MODE.
 *
 * <h2>What the constitution says</h2>
 *
 * <p>CONSTITUTION Article I permits unbounded randomness in exactly two situations:</p>
 *
 * <blockquote>
 *   Adversarial mode — red-teaming, robustness testing — может использовать unbounded
 *   Random, но ТОЛЬКО за build flag {@code -Pexperimental=true} или в тестах с
 *   <b>явной маркировкой</b> (explicit marking).
 * </blockquote>
 *
 * <h2>Why this is an annotation and not a comment</h2>
 *
 * <p>"Explicit marking" has to survive the thing it is marking. A javadoc comment saying
 * {@code @adversarial} would be invisible to {@link DecisionPathAuditor}, which strips comments
 * before scanning — correctly, since a comment that documents a violation must not exempt it.
 * A marker that the auditor cannot read is not a marking; it is a hope.</p>
 *
 * <p>So the exemption is code. The auditor honours this annotation and downgrades the member's
 * findings from {@code VIOLATION} to {@code ADVISORY}, which means the member is still reported,
 * still counted, and still visible in review — it simply stops blocking on a rule the
 * constitution itself exempts.</p>
 *
 * <h2>What it is not</h2>
 *
 * <p>This is not a licence. Applying it to something that is not adversarial-mode test
 * scaffolding — anything reachable from a live decision path — misrepresents a production
 * behaviour as a test convenience, and that is worse than the violation it hides. Use it only
 * where the unbounded randomness genuinely exists to search for failures.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE, ElementType.CONSTRUCTOR})
public @interface AdversarialStochasticity {

    /** Why this member needs unbounded randomness. Recorded in the audit report. */
    String value() default "";
}