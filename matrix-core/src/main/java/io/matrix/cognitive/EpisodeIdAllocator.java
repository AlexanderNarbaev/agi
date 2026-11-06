package io.matrix.cognitive;

/**
 * W45.0 — the authority that hands out the identities of a cognitive cycle.
 *
 * <h2>Four identities, four namespaces</h2>
 * <p>W45 Priority B found that four different things shared one identifier space, so a caller
 * could not tell them apart and neither could the store. They are separate here:</p>
 * <ul>
 *   <li><b>request id</b> — the identity of a caller's LOGICAL request. It exists only when the
 *       caller supplies an idempotency key, and it is the key itself. It is durable, in the
 *       {@code cognitive_idempotency} table.</li>
 *   <li><b>signal id</b> — the identity of the RECEIVED signal: {@code sig-} followed by a token
 *       from the {@link IdSource}. It is unique per REQUEST and is never a function of the
 *       payload.</li>
 *   <li><b>episode id</b> — the identity of ONE EXECUTION of the cycle: {@code ep-} followed by
 *       the signal id. The kernel derives it exactly this way
 *       ({@code "ep-" + signal.id()}), and this class states the derivation so that the two
 *       places that need it agree.</li>
 *   <li><b>event id</b> — the identity of an immutable event INSIDE an episode. The kernel
 *       derives it from the episode id and the position of the event in the episode, and the
 *       store enforces that both the id and the position are unique.</li>
 * </ul>
 *
 * <h2>Why the arrival number is durable</h2>
 * <p>{@link CognitiveSignal#sequence()} is the arrival order of the signals, and it is what makes
 * the event stream replayable: the same signals in the same order produce the same perception.
 * The W44 service kept that counter in a field of a bean, so a restart reset it to zero and the
 * stream could no longer be replayed across the restart. The counter therefore lives in the
 * STORE, in the {@code cognitive_sequence} table, and it is incremented inside a transaction: a
 * restart cannot reset it, and two concurrent callers cannot be handed the same number.</p>
 *
 * <p>The signal id itself is a token from the {@link IdSource}: unique per request, independent
 * of the payload, and replaceable in a test by a deterministic source.</p>
 */
public final class EpisodeIdAllocator {

    /** The prefix of every signal id. */
    public static final String SIGNAL_PREFIX = "sig-";

    /** The prefix of every episode id. */
    public static final String EPISODE_PREFIX = "ep-";

    /** The name of the durable sequence that numbers the arriving signals. */
    public static final String SIGNAL_SEQUENCE = "cognitive.signal";

    private final IdSource idSource;
    private final CognitiveStore store;

    /**
     * An allocator over a given id source and store.
     *
     * @param idSource where the unique tokens come from
     * @param store    the store that holds the durable arrival sequence
     */
    public EpisodeIdAllocator(IdSource idSource, CognitiveStore store) {
        this.idSource = idSource;
        this.store = store;
    }

    /**
     * The id of the next signal.
     *
     * <p>Unique per REQUEST. Two requests with byte-identical payloads receive different ids,
     * and two different payloads never collide by accident.</p>
     *
     * @return the signal id
     */
    public String nextSignalId() {
        return SIGNAL_PREFIX + idSource.nextId();
    }

    /**
     * The arrival number of the next signal.
     *
     * <p>Durable: it survives a restart, because it is incremented in the store rather than in
     * the memory of a bean.</p>
     *
     * @return the arrival number, starting at one
     */
    public long nextSignalSequence() {
        return store.nextSequence(SIGNAL_SEQUENCE);
    }

    /**
     * The episode a signal produces.
     *
     * <p>This is the derivation the kernel uses, stated once. The episode is ONE EXECUTION of
     * the cycle, so its identity follows the identity of the signal that caused it — and because
     * the signal id is unique per request, the episode id is unique per execution.</p>
     *
     * @param signalId the signal
     * @return the episode id
     */
    public static String episodeIdFor(String signalId) {
        return EPISODE_PREFIX + signalId;
    }
}
