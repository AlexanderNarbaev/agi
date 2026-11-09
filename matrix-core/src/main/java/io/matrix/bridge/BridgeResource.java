package io.matrix.bridge;

import io.matrix.contract.bridge.BridgeDecision;
import io.matrix.contract.bridge.BridgeEnvelope;
import io.matrix.contract.bridge.BridgeEnvelopeCodec;
import io.matrix.contract.bridge.BridgeProtocolException;
import io.matrix.knowledge.KnowledgeWorkbench;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * W51 — the HTTP surface of the bridge.
 *
 * <p><b>What this route is.</b> The one place where a message from the other chain enters
 * chain A. It is deliberately NOT under {@code /v1/cognitive} or {@code /v1/workbench}: the
 * isolation gate asserts that chain B serves no route of chain A, and a bridge route that
 * lived under one of those prefixes would make the gate's route census lie.</p>
 *
 * <p><b>What it does and does not do.</b> It decodes the envelope, hands it to the
 * {@link BridgeService}, and answers with the decision. It never writes anything itself: the
 * service owns the write path, and that is what makes the bridge a single, named, auditable
 * crossing rather than a spread of handlers.</p>
 *
 * <p><b>Fail closed.</b> A message that cannot be understood is a refusal with a reason, not
 * an exception and not a degraded service. The HTTP status reflects the decision: an accepted
 * message is 200, a refused one is 400 with the reason in the body.</p>
 *
 * <p><b>The secret is not optional.</b> When no verification secret is configured the service
 * refuses every message, and {@code GET /v1/bridge/status} says so. An operator can therefore
 * see, without reading the log, that the bridge is present and closed.</p>
 */
@Path("/v1/bridge")
public class BridgeResource {

    /** The relative path of the bridge's own durable state. */
    public static final String JOURNAL_PATH = "data/bridge/bridge.sqlite";

    @Inject
    KnowledgeWorkbench workbench;

    private BridgeService service;
    private BridgeJournal journal;
    private final BridgeEnvelopeCodec codec = new BridgeEnvelopeCodec();

    /**
     * The bridge service, built on first use.
     *
     * <p>It is built lazily because it opens the bridge's own file, and the application must
     * not create that file until something asks for the bridge.</p>
     *
     * @return the service
     */
    synchronized BridgeService service() {
        if (service == null) {
            journal = new BridgeJournal(java.nio.file.Path.of(JOURNAL_PATH));
            service = new BridgeService(workbench, BridgeConfiguration.verifier(),
                    BridgeConfiguration.granted(), BridgeConfiguration.budget(), journal, journal,
                    BridgeConfiguration.workspaceId(), () -> java.time.Instant.now().toString());
        }
        return service;
    }

    /**
     * Accepts one bridged message.
     *
     * @param body the JSON envelope
     * @return the decision
     */
    @POST
    @Path("/messages")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response messages(String body) {
        BridgeEnvelope envelope;
        try {
            envelope = codec.decode(body);
        } catch (BridgeProtocolException e) {
            Map<String, Object> refusal = new LinkedHashMap<>();
            refusal.put("status", BridgeDecision.Status.REFUSED.name());
            refusal.put("reason", e.getMessage());
            refusal.put("refusalReason", e.reason().name());
            return Response.status(Response.Status.BAD_REQUEST).entity(refusal).build();
        }
        BridgeDecision decision = service().accept(envelope);
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("status", decision.status().name());
        answer.put("requestId", decision.requestId());
        answer.put("auditId", decision.auditId());
        answer.put("reason", decision.reason());
        return Response.status(decision.accepted() ? Response.Status.OK
                : Response.Status.BAD_REQUEST).entity(answer).build();
    }

    /**
     * What the bridge is and whether it is open.
     *
     * @return the status
     */
    @GET
    @Path("/status")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("protocol", BridgeProtocolInfo.NAME);
        status.put("version", BridgeProtocolInfo.VERSION);
        status.put("configured", BridgeConfiguration.secret().isPresent());
        status.put("granted", BridgeConfiguration.granted().stream().map(Enum::name).toList());
        status.put("workspace", BridgeConfiguration.workspaceId());
        status.put("auditRecords", journal().auditCount());
        status.put("bridge", "the bridge is the only crossing between the two chains; it adds"
                + " observations, candidates and evidence links, and nothing else");
        return status;
    }

    private synchronized BridgeJournal journal() {
        service();
        return journal;
    }
}
