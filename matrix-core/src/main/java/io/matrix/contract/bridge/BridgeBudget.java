package io.matrix.contract.bridge;

import java.time.Duration;

/**
 * W51 — the budget the bridge enforces before it touches the store (BRIDGE-9).
 *
 * <p><b>What a budget is for.</b> BRIDGE-9: "The bridge has a budget: a rate, a size, a batch
 * size and a per-day ceiling, and the budget is enforced before the store is touched. A bridge
 * that can flood the epistemic store is a bridge that has changed the system it was supposed
 * to observe." The sentence that matters is the last one. A bridge that can be flooded has
 * stopped being an observer and become a writer, and no amount of authentication or
 * provenance makes that safe.</p>
 *
 * <p><b>Why the counters count what is admitted.</b> The counters are not a token bucket:
 * they are the number of messages <i>admitted</i> inside the window and inside the day, and
 * the limits are hard caps. A flood of small messages is bounded by the rate counter, which
 * stops at the limit and refuses everything until the window rolls; a flood of large messages
 * is bounded by the size limit, which refuses each of them before the counters are touched
 * at all. Neither needs a refusal to consume a slot, because a counter that already sits at
 * its limit refuses everything that arrives.</p>
 *
 * <p><b>Why the order of the checks is fixed.</b> The size and the batch are properties of
 * the message and of the delivery, and they are checked first because they cost nothing and
 * because a message that is too large must never reach the store whatever the budget says. The
 * daily ceiling is a hard cap on the day's total, so it is checked before the rate window:
 * when the day is spent, nothing is admitted whatever the rate says. The rate window is
 * checked last, and it is the check that consumes the slots. A message that passes all four
 * has consumed exactly one slot of each.</p>
 *
 * <p><b>Why the time is a parameter.</b> The window and the day are counted from the instant
 * the caller supplies. Nothing here reads a clock, so the same sequence of calls with the same
 * arguments always produces the same verdicts — which is what makes the budget testable, and
 * what stops a decision from depending on when the machine happened to run it.</p>
 *
 * <p><b>Why the day is a UTC day.</b> The bucket is the calendar day in UTC, computed from the
 * epoch millis. A local day would make the ceiling depend on the time zone of the host that
 * runs the bridge, and two bridges with the same budget would then have different days. The
 * bridge protocol is meant to be reproducible between two processes, so the day is not.</p>
 */
public final class BridgeBudget {

    /** The number of messages the bridge admits inside one rate window, by default. */
    public static final int DEFAULT_MESSAGES_PER_WINDOW = 60;

    /** The width of the rate window, by default. */
    public static final Duration DEFAULT_WINDOW = Duration.ofMinutes(1);

    /** The largest single message the bridge admits, in bytes, by default. */
    public static final int DEFAULT_MAX_MESSAGE_BYTES = 64 * 1024;

    /** The largest batch the bridge admits, by default. */
    public static final int DEFAULT_MAX_BATCH = 10;

    /** The number of messages the bridge admits in one day, by default. */
    public static final int DEFAULT_PER_DAY_CEILING = 10_000;

    /** The length of a day, in milliseconds. */
    private static final long MILLIS_PER_DAY = Duration.ofDays(1).toMillis();

    private final int messagesPerWindow;
    private final Duration window;
    private final int maxMessageBytes;
    private final int maxBatch;
    private final int perDayCeiling;

    private long windowStartMillis;
    private int messagesInWindow;
    private long currentDay;
    private int messagesToday;

    /**
     * A budget with the given limits.
     *
     * @param messagesPerWindow the number of messages admitted inside one window
     * @param window           the width of the rate window
     * @param maxMessageBytes  the largest single message, in bytes
     * @param maxBatch         the largest batch
     * @param perDayCeiling    the number of messages admitted in one day
     */
    public BridgeBudget(int messagesPerWindow, Duration window, int maxMessageBytes,
                        int maxBatch, int perDayCeiling) {
        if (messagesPerWindow < 1) {
            throw new IllegalArgumentException("the rate window must admit at least one message");
        }
        if (window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("the rate window must have a positive width");
        }
        if (maxMessageBytes < 1) {
            throw new IllegalArgumentException("the largest message must be at least one byte");
        }
        if (maxBatch < 1) {
            throw new IllegalArgumentException("the largest batch must hold at least one message");
        }
        if (perDayCeiling < 1) {
            throw new IllegalArgumentException("the daily ceiling must admit at least one message");
        }
        this.messagesPerWindow = messagesPerWindow;
        this.window = window;
        this.maxMessageBytes = maxMessageBytes;
        this.maxBatch = maxBatch;
        this.perDayCeiling = perDayCeiling;
    }

    /**
     * The budget this build ships with (BRIDGE-9).
     *
     * @return the default budget
     */
    public static BridgeBudget standard() {
        return new BridgeBudget(DEFAULT_MESSAGES_PER_WINDOW, DEFAULT_WINDOW,
                DEFAULT_MAX_MESSAGE_BYTES, DEFAULT_MAX_BATCH, DEFAULT_PER_DAY_CEILING);
    }

    /**
     * The number of messages admitted inside one rate window.
     *
     * @return the limit
     */
    public int messagesPerWindow() {
        return messagesPerWindow;
    }

    /**
     * The width of the rate window.
     *
     * @return the window
     */
    public Duration window() {
        return window;
    }

    /**
     * The largest single message, in bytes.
     *
     * @return the limit
     */
    public int maxMessageBytes() {
        return maxMessageBytes;
    }

    /**
     * The largest batch.
     *
     * @return the limit
     */
    public int maxBatch() {
        return maxBatch;
    }

    /**
     * The number of messages admitted in one day.
     *
     * @return the limit
     */
    public int perDayCeiling() {
        return perDayCeiling;
    }

    /**
     * Whether a message may be admitted, and what it costs (BRIDGE-9).
     *
     * <p>This is the check that runs before the store is touched. It is
     * {@code synchronized} because the counters it maintains are shared state, and a budget
     * that two threads could interleave through would admit twice what it says it admits.</p>
     *
     * @param envelope  the message
     * @param batchSize the number of messages this delivery carries
     * @param nowMillis the instant of the delivery, in epoch milliseconds
     * @return the verdict
     */
    public synchronized Verdict check(BridgeEnvelope envelope, int batchSize, long nowMillis) {
        if (envelope == null) {
            return Verdict.refused(BridgeRefusalReason.MALFORMED_MESSAGE,
                    "there is no message to check");
        }
        int size = envelope.canonicalBytes().length;
        if (size > maxMessageBytes) {
            return Verdict.refused(BridgeRefusalReason.MESSAGE_TOO_LARGE,
                    "the message carries " + size + " bytes and the limit is " + maxMessageBytes);
        }
        if (batchSize > maxBatch) {
            return Verdict.refused(BridgeRefusalReason.BATCH_TOO_LARGE,
                    "the batch carries " + batchSize + " messages and the limit is " + maxBatch);
        }
        long day = Math.floorDiv(nowMillis, MILLIS_PER_DAY);
        if (day != currentDay) {
            currentDay = day;
            messagesToday = 0;
        }
        if (messagesToday >= perDayCeiling) {
            return Verdict.refused(BridgeRefusalReason.DAILY_CEILING_EXCEEDED,
                    "the bridge has admitted " + messagesToday + " messages today and the ceiling is "
                            + perDayCeiling);
        }
        long windowMillis = window.toMillis();
        if (nowMillis - windowStartMillis >= windowMillis) {
            windowStartMillis = nowMillis;
            messagesInWindow = 0;
        }
        if (messagesInWindow >= messagesPerWindow) {
            return Verdict.refused(BridgeRefusalReason.RATE_LIMIT_EXCEEDED,
                    "the bridge has admitted " + messagesInWindow + " messages in the current window and the limit is "
                            + messagesPerWindow);
        }
        messagesInWindow++;
        messagesToday++;
        return Verdict.admitted();
    }

    /**
     * The answer of a budget check.
     *
     * @param allowed whether the message may be admitted
     * @param reason  why it may not, when it may not
     * @param detail  what was wrong, in words
     */
    public record Verdict(boolean allowed, BridgeRefusalReason reason, String detail) {

        public Verdict {
            if (!allowed && reason == null) {
                throw new IllegalArgumentException("a refusal needs a reason");
            }
        }

        /** The message may be admitted. */
        public static Verdict admitted() {
            return new Verdict(true, null, "the message is within the budget");
        }

        /**
         * The message may not be admitted.
         *
         * @param reason why it may not
         * @param detail what was wrong, in words
         * @return the verdict
         */
        public static Verdict refused(BridgeRefusalReason reason, String detail) {
            return new Verdict(false, reason, detail);
        }
    }
}
