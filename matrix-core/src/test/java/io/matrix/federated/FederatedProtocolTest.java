package io.matrix.federated;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.util.List;

class FederatedProtocolTest {

    @Test
    void runRoundAggregates() {
        var protocol = new FederatedProtocol();
        var update1 = new LocalUpdate("node1", new boolean[]{true, false, true}, 10, 0.1);
        var update2 = new LocalUpdate("node2", new boolean[]{false, true, false}, 20, 0.2);
        var round = protocol.runRound(List.of(update1, update2));
        assertNotNull(round);
        assertEquals(2, round.participantCount());
        assertNotNull(round.aggregatedModel());
        assertEquals(3, round.aggregatedModel().length);
    }

    @Test
    void insufficientParticipantsThrowsException() {
        var protocol = new FederatedProtocol();
        assertThrows(IllegalStateException.class, () -> 
            protocol.runRound(List.of()));
    }
}

class SecureAggregatorTest {

    @Test
    void aggregateXorMerges() {
        var aggregator = new SecureAggregator();
        var u1 = new LocalUpdate("n1", new boolean[]{true, false, false}, 10, 0.1);
        var u2 = new LocalUpdate("n2", new boolean[]{true, false, false}, 10, 0.1);
        var result = aggregator.aggregate(List.of(u1, u2));
        assertEquals(3, result.length);
        assertFalse(result[0]); // true XOR true = false
        assertFalse(result[1]);
        assertFalse(result[2]);
    }

    @Test
    void emptyListReturnsEmpty() {
        var aggregator = new SecureAggregator();
        assertEquals(0, aggregator.aggregate(List.of()).length);
    }
}
