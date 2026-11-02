package io.matrix.cognitive.algorithms;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * W44.2 — the catalogue of methods the selector may choose from.
 *
 * <p>There was no such catalogue before this package. The algorithms lived in
 * {@code io.matrix.neuron} as free-standing classes that nothing called, and the only registry
 * in the repository ({@code io.matrix.bir.BirRegistry}) stores ARTIFACTS — compiled boolean
 * functions with a lineage — not capabilities. A selector needs a different thing: a set of
 * methods that each declare what they can do, so that the choice can be made from the
 * declarations rather than from a caller's memory.</p>
 *
 * <p>A method is indexed under every family its capability declares, so a method that serves two
 * families is offered to both. Registration is by capability id, and a duplicate id is refused:
 * two methods claiming the same id would make the decision record ambiguous, and the decision
 * record is the thing that has to stay readable.</p>
 *
 * <p>Thread safety: all mutating and reading methods are synchronized on the registry. The
 * registry is written once at start-up and read on every decision, so contention is not a
 * concern; the guarantee is that a decision never observes a half-built catalogue.</p>
 */
public final class AlgorithmRegistry {

    private final Map<String, AlgorithmMethod<?, ?>> byId = new LinkedHashMap<>();
    private final Map<String, List<AlgorithmMethod<?, ?>>> byFamily = new LinkedHashMap<>();

    /**
     * Registers a method.
     *
     * @param method the method to register
     * @return this registry, so registrations can be chained
     * @throws IllegalStateException when the capability id is already taken
     */
    public synchronized AlgorithmRegistry register(AlgorithmMethod<?, ?> method) {
        if (method == null) {
            throw new IllegalArgumentException("method must not be null");
        }
        AlgorithmCapability capability = method.capability();
        if (capability == null) {
            throw new IllegalArgumentException("method must declare a capability");
        }
        AlgorithmMethod<?, ?> existing = byId.get(capability.id());
        if (existing != null) {
            throw new IllegalStateException("algorithm id already registered: " + capability.id());
        }
        byId.put(capability.id(), method);
        for (String family : capability.problemFamilies()) {
            byFamily.computeIfAbsent(family, key -> new ArrayList<>()).add(method);
        }
        return this;
    }

    /**
     * The methods that serve a family.
     *
     * @param family the problem family
     * @return the methods offered to that family, in registration order; empty when none serve it
     */
    public synchronized List<AlgorithmMethod<?, ?>> methodsFor(String family) {
        if (family == null) {
            return List.of();
        }
        List<AlgorithmMethod<?, ?>> methods = byFamily.get(family);
        return methods == null ? List.of() : List.copyOf(methods);
    }

    /**
     * The method with the given capability id.
     *
     * @param id the capability id
     * @return the method, or {@code null} when nothing is registered under that id
     */
    public synchronized AlgorithmMethod<?, ?> byId(String id) {
        return id == null ? null : byId.get(id);
    }

    /** The ids of every registered method. */
    public synchronized Set<String> ids() {
        return Collections.unmodifiableSet(Set.copyOf(byId.keySet()));
    }

    /** How many methods are registered. */
    public synchronized int size() {
        return byId.size();
    }

    /** The families that at least one method serves. */
    public synchronized Set<String> families() {
        return Collections.unmodifiableSet(Set.copyOf(byFamily.keySet()));
    }
}
