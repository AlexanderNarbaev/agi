package io.matrix.bridge;

import io.matrix.contract.bridge.BridgeProtocol;

/**
 * W51 — the name and the version of the bridge protocol, as chain A sees it.
 *
 * <p>The protocol itself is declared in {@code io.matrix.contract.bridge}, which is a package
 * both chains may read. This class exists so that the chain A surface can name the protocol
 * without the reader having to know which package declares it.</p>
 */
public final class BridgeProtocolInfo {

    /** The name of the bridge protocol. */
    public static final String NAME = BridgeProtocol.NAME;

    /** The version of the bridge protocol this build speaks. */
    public static final int VERSION = BridgeProtocol.VERSION;

    private BridgeProtocolInfo() {
        throw new AssertionError("no instances");
    }
}
