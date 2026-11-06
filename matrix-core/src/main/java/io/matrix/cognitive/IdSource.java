package io.matrix.cognitive;

/**
 * W45.0 — the source of the identifiers the cognitive stack hands out.
 *
 * <p>W45 Priority B found that the identity of a signal was derived from the payload's
 * {@code String.hashCode()}. That is not an identity: two identical payloads produced the SAME
 * signal id, and different payloads collided. Because every write in the store was an
 * {@code INSERT OR REPLACE}, a repeated request overwrote the previous episode, its prediction,
 * its error, its task and its events — the history of the system was destroyed by a repeat.</p>
 *
 * <p>This interface is the seam that fixes it. The production source mints a UUID, which is
 * unique per REQUEST and is not a function of the content; a test replaces it with a
 * deterministic source, so a test can say exactly which id the Nth request received. Nothing in
 * the stack calls {@link java.util.UUID#randomUUID()} inline any more: the randomness lives in
 * exactly one place, and that place can be replaced.</p>
 *
 * <p>An id source is NOT a decision path in the sense of the constitution's Article I: the id
 * carries no meaning, decides nothing, and is never compared for ordering. It is a NAME, and a
 * name has to be unique.</p>
 */
@FunctionalInterface
public interface IdSource {

    /**
     * The next identifier.
     *
     * @return an identifier that has never been returned by this source before
     */
    String nextId();

    /**
     * The production source: a random UUID.
     *
     * <p>The UUID is generated with {@link java.util.UUID#randomUUID()}, which is safe for
     * concurrent use and does not depend on a seed, a clock or a process-wide counter.</p>
     *
     * @return the production id source
     */
    static IdSource random() {
        return () -> java.util.UUID.randomUUID().toString();
    }
}
