package io.matrix.evolution;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Generates a structured natural-language description of a class — used by
 * the self-evolution subsystem to (a) inform humans about what the system
 * is about to modify and (b) feed the {@link ProtectedSelfRewrite} gate with
 * a precise textual spec.
 *
 * <p>This produces a deterministic, hand-readable summary: package, modifiers,
 * declared public methods, parameter types, return type, and any explicit
 * {@code Deprecated} / {@code FROZEN} markers.
 *
 * <p>Ref: L7 §4.
 */
public final class SelfDescriptionService {

    public SelfDescriptionService() {}

    /**
     * Builds a {@link ClassDescription} of {@code clazz} — a serialisable
     * snapshot of the class metadata, suitable for logging, ethics-audit
     * context, and human-readable UI.
     */
    public ClassDescription describe(Class<?> clazz) {
        if (clazz == null) return new ClassDescription("?", "?", "?", List.of(), List.of());
        boolean isInterface = clazz.isInterface();
        boolean isAbstract = Modifier.isAbstract(clazz.getModifiers());
        boolean isFinal = Modifier.isFinal(clazz.getModifiers());
        String kind = isInterface ? "interface" : isAbstract ? "abstract class" : isFinal ? "final class" : "class";

        List<String> markerTags = new ArrayList<>();
        if (clazz.isAnnotationPresent(Deprecated.class)) markerTags.add("deprecated");
        if (clazz.getSimpleName().toUpperCase().contains("FROZEN")) markerTags.add("frozen");
        if (clazz.getSimpleName().toUpperCase().contains("NEURON")) markerTags.add("neuron-domain");

        List<MethodDescription> methods = new ArrayList<>();
        for (Method m : clazz.getDeclaredMethods()) {
            if (!Modifier.isPublic(m.getModifiers())) continue;
            methods.add(new MethodDescription(
                    m.getName(),
                    m.getReturnType().getSimpleName(),
                    java.util.Arrays.stream(m.getParameterTypes())
                            .map(Class::getSimpleName).collect(Collectors.toList()),
                    m.isAnnotationPresent(Deprecated.class)));
        }
        return new ClassDescription(
                clazz.getPackageName(),
                clazz.getSimpleName(),
                kind,
                List.copyOf(markerTags),
                List.copyOf(methods));
    }

    /** Human-readable, deterministic summary suitable for ethics review. */
    public String summarize(Class<?> clazz) {
        ClassDescription d = describe(clazz);
        StringBuilder sb = new StringBuilder();
        sb.append(d.kind()).append(' ').append(d.simpleName())
                .append(" (package=").append(d.packageName()).append(')');
        if (!d.markerTags().isEmpty()) sb.append(" tags=").append(d.markerTags());
        for (MethodDescription m : d.methods()) {
            sb.append("\n  - ");
            if (m.deprecated()) sb.append("[deprecated] ");
            sb.append(m.name()).append('(')
                    .append(String.join(", ", m.parameterTypes())).append(')')
                    .append(" -> ").append(m.returnType());
        }
        return sb.toString();
    }

    /** Class metadata snapshot. */
    public record ClassDescription(
            String packageName,
            String simpleName,
            String kind,
            List<String> markerTags,
            List<MethodDescription> methods) {}

    /** Public method signature snapshot. */
    public record MethodDescription(
            String name,
            String returnType,
            List<String> parameterTypes,
            boolean deprecated) {}
}
