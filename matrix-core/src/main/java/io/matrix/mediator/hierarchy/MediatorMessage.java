package io.matrix.mediator.hierarchy;

import java.util.UUID;

/**
 * Message protocol for inter-mediator communication.
 *
 * <p>Ref: L4_Mediator.md §2.2
 */
public sealed interface MediatorMessage
        permits MediatorMessage.Command, MediatorMessage.Response {

    UUID correlationId();

    record Command(
            UUID correlationId,
            MediatorLevel sourceLevel,
            String sourceId,
            MediatorLevel targetLevel,
            String targetId,
            String action,
            String payload
    ) implements MediatorMessage {}

    record Response(
            UUID correlationId,
            boolean success,
            String result,
            String errorMessage
    ) implements MediatorMessage {
        public static Response ok(UUID correlationId, String result) {
            return new Response(correlationId, true, result, null);
        }

        public static Response error(UUID correlationId, String errorMessage) {
            return new Response(correlationId, false, null, errorMessage);
        }
    }
}
