package io.matrix.lifecycle;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Cauldron Protocol (DESIGN-07 §2): controlled creation of new elements
 * with budgets, quarantine, and rollback.
 *
 * <p>Cauldron is the "birth" mechanism for FNL (Functionally Novel Lobes),
 * TaskCells, and other new elements. Each creation is:
 * <ul>
 *   <li>Budgeted: CPU/memory/time limits enforced</li>
 *   <li>Quarantined: new element isolated until validated</li>
 *   <li>Rollbackable: can be destroyed if validation fails</li>
 * </ul>
 *
 * <p>Per CONSTITUTION III: every creation has a declared monotone functional Φ.
 * If Φ degrades on validation, the creation is rolled back.
 */
public final class CauldronProtocol {

    private final Map<String, Element> elements = new ConcurrentHashMap<>();
    private final List<CauldronEvent> events = new CopyOnWriteArrayList<>();
    private final AtomicLong totalCreations = new AtomicLong();
    private final AtomicLong totalRollbacks = new AtomicLong();

    /** Create a new element with budget and quarantine. */
    public Element create(String id, String type, Map<String, Object> config,
                          Budget budget, double phi) {
        var element = new Element(id, type, config, budget, phi,
                Instant.now(), Status.QUARANTINED);
        elements.put(id, element);
        totalCreations.incrementAndGet();
        events.add(new CauldronEvent(id, "created", Instant.now(), "quarantine started"));
        return element;
    }

    /** Validate a quarantined element. If valid → PROMOTED. If invalid → ROLLED_BACK. */
    public ValidationResult validate(String id, double measuredPhi) {
        Element e = elements.get(id);
        if (e == null) return ValidationResult.notFound(id);
        if (e.status() != Status.QUARANTINED) {
            return ValidationResult.error("not in quarantine: " + e.status());
        }

        if (measuredPhi >= e.phi()) {
            // Promote
            elements.put(id, e.withStatus(Status.PROMOTED));
            events.add(new CauldronEvent(id, "promoted", Instant.now(),
                    "phi=" + measuredPhi + " >= " + e.phi()));
            return ValidationResult.promoted(id, measuredPhi);
        } else {
            // Rollback
            elements.put(id, e.withStatus(Status.ROLLED_BACK));
            totalRollbacks.incrementAndGet();
            events.add(new CauldronEvent(id, "rolled_back", Instant.now(),
                    "phi=" + measuredPhi + " < " + e.phi()));
            return ValidationResult.rolledBack(id, measuredPhi);
        }
    }

    /** Get element status. */
    public Status status(String id) {
        Element e = elements.get(id);
        return e == null ? null : e.status();
    }

    /** Get element. */
    public Element get(String id) { return elements.get(id); }

    /** List all elements. */
    public List<Element> listElements() { return List.copyOf(elements.values()); }

    /** List events. */
    public List<CauldronEvent> events() { return List.copyOf(events); }

    public long totalCreations() { return totalCreations.get(); }
    public long totalRollbacks() { return totalRollbacks.get(); }

    /** Element status. */
    public enum Status { QUARANTINED, PROMOTED, ROLLED_BACK, RETIRED }

    /** Element record. */
    public record Element(String id, String type, Map<String, Object> config,
                          Budget budget, double phi, Instant created, Status status) {
        public Element withStatus(Status newStatus) {
            return new Element(id, type, config, budget, phi, created, newStatus);
        }
    }

    /** Budget for element creation. */
    public record Budget(long cpuMs, long memoryBytes, long wallTimeMs) {
        public static Budget unlimited() {
            return new Budget(Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE);
        }
    }

    /** Cauldron event (creation, promotion, rollback). */
    public record CauldronEvent(String elementId, String action, Instant timestamp, String details) {}

    /** Validation result. */
    public record ValidationResult(String elementId, boolean promoted, double phi, String reason) {
        public static ValidationResult promoted(String id, double phi) {
            return new ValidationResult(id, true, phi, "promoted");
        }
        public static ValidationResult rolledBack(String id, double phi) {
            return new ValidationResult(id, false, phi, "rolled back");
        }
        public static ValidationResult notFound(String id) {
            return new ValidationResult(id, false, 0.0, "not found");
        }
        public static ValidationResult error(String reason) {
            return new ValidationResult(null, false, 0.0, reason);
        }
    }
}
