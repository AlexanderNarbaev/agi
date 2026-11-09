package io.matrix.contract;

/**
 * W51 — a command crossing a boundary.
 *
 * <p><b>What this replaces.</b> The two serving chains had no shared request format at all.
 * Chain A's Quarkus resources and chain B's JDK {@code HttpServer} each had their own JSON
 * shapes, and nothing said what would happen when one side learned a field the other side did
 * not understand. The W51 requirement — "versioned service interfaces, typed event and command
 * envelopes, request/response contracts, correlation IDs and causality metadata, idempotent
 * commands, explicit retry and timeout policies, backpressure and bounded queues, capability
 * checks, provenance and audit records, health and availability reporting" — is the list of the
 * properties of THIS record and the two that follow it.</p>
 *
 * <p><b>A command asks for something to happen.</b> An event reports that something happened.
 * The distinction is not cosmetic: a command is safe to retry only when it carries an
 * idempotency key, and an event is never to be replayed as a command.</p>
 *
 * <p><b>The envelope is closed.</b> The payload is an opaque JSON document, because the
 * envelope's job is to govern the message, not to understand its content. The content's schema
 * belongs to the contract named in {@link #contract()}.</p>
 *
 * @param contract    the name of the contract the payload speaks
 * @param version     the version of that contract the payload speaks
 * @param commandId   the identity of this command; unique per attempt series
 * @param correlation the causality metadata
 * @param target      the component or peer the command addresses
 * @param payload     the JSON payload of the command
 * @param timeoutMillis the time the caller is willing to wait for a reply
 * @param retry       the policy that governs a second attempt
 * @param issuedAt    when the command was issued; a record of WHEN, never a decision input
 */
public record CognitiveCommand(
        String contract,
        int version,
        String commandId,
        Correlation correlation,
        String target,
        String payload,
        long timeoutMillis,
        RetryPolicy retry,
        String issuedAt) {

    /** The timeout a command gets when the caller names none. */
    public static final long DEFAULT_TIMEOUT_MILLIS = 5_000L;

    public CognitiveCommand {
        if (contract == null || contract.isBlank()) {
            throw new IllegalArgumentException("command contract must not be blank");
        }
        if (version < 1) {
            throw new IllegalArgumentException("command contract version must be at least one");
        }
        if (commandId == null || commandId.isBlank()) {
            throw new IllegalArgumentException("command id must not be blank");
        }
        if (correlation == null) {
            throw new IllegalArgumentException("command correlation must not be null");
        }
        if (payload == null) {
            payload = "{}";
        }
        if (timeoutMillis <= 0L) {
            throw new IllegalArgumentException("command timeout must be positive");
        }
        if (retry == null) {
            retry = RetryPolicy.NONE;
        }
    }

    /**
     * A command with the default timeout and no retries.
     *
     * @param contract    the contract the payload speaks
     * @param version     the version of that contract
     * @param commandId   the identity of the command
     * @param correlation the causality metadata
     * @param target      the component or peer the command addresses
     * @param payload     the JSON payload
     * @return the command
     */
    public static CognitiveCommand of(String contract, int version, String commandId,
                                      Correlation correlation, String target, String payload) {
        return new CognitiveCommand(contract, version, commandId, correlation, target, payload,
                DEFAULT_TIMEOUT_MILLIS, RetryPolicy.NONE, null);
    }

    /** True when the payload speaks a contract version this build implements. */
    public boolean contractSupported(int supportedVersion) {
        return CognitiveContract.compatible(version, supportedVersion);
    }

    @Override
    public String toString() {
        return "CognitiveCommand[" + contract + " v" + version + " " + commandId
                + " -> " + target + "]";
    }
}
