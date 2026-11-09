package io.matrix.bridge;

import io.matrix.contract.bridge.BridgeAuditRecord;

/**
 * W51 — the port the bridge writes its audit trail to.
 *
 * <p>BRIDGE-7 says that every call the bridge makes or refuses is written to the append-only
 * audit chain, with its request id, its decision and the reason for the decision. The refusal
 * is recorded as carefully as the acceptance.</p>
 *
 * <p>The port exists so that the service can own the audit independently of the transport: a
 * message that arrives over HTTP and a message that arrives inside the same process must
 * leave the same trail. A transport that audited on its own would produce a different record
 * depending on how the message arrived, and the audit would be a property of the transport
 * rather than of the bridge.</p>
 */
public interface BridgeAudit {

    /**
     * Appends a record.
     *
     * @param record the record to append
     */
    void append(BridgeAuditRecord record);
}
