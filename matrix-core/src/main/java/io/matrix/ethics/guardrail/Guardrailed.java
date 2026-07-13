package io.matrix.ethics.guardrail;

import jakarta.ws.rs.NameBinding;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks REST endpoints that require EU AI Act guardrail protection.
 *
 * <p>When applied to a JAX-RS resource class or method, the
 * {@link EthicalGuardrailInterceptor} will automatically:
 * <ol>
 *   <li>Filter input through all input guards before processing</li>
 *   <li>Validate output through all output guards after processing</li>
 *   <li>Log all guardrail decisions to the audit trail</li>
 *   <li>Block requests that violate guardrail policy</li>
 * </ol>
 *
 * <p>Usage:
 * <pre>{@literal @}Path("/v1/chat")
 * @Guardrailed
 * public class ChatResource { ... }
 * </pre>
 */
@NameBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface Guardrailed {
}
