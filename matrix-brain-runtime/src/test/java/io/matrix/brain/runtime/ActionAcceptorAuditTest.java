
package io.matrix.brain.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every acceptance decision is recorded in the audit chain, and the chain verifies. */
class ActionAcceptorAuditTest {

    @Test
    void everyDecisionIsRecordedAndTheChainVerifies() {
        AuditChain chain = new AuditChain();
        ActionAcceptor acceptor = new ActionAcceptor(1.0).withAuditChain(chain);
        acceptor.accept("stage-a", 0.5, 0.5);
        acceptor.accept("stage-b", 5.0, 0.0);
        assertEquals(2, chain.size());
        assertTrue(chain.verify());
    }
}
