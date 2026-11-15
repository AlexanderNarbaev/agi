package io.matrix.knowledge;

import io.matrix.actions.ArtifactAction;
import io.matrix.actions.ArtifactActionService;
import io.matrix.actions.ArtifactWriter;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CONCEPT-TO-ACTION — the one product route that turns a question into a real file.
 *
 * <p><b>What this route is.</b> It asks a workspace a question through the workbench's own
 * retrieval, assembles a report from the answer the workspace gave, and hands that report to
 * the project's existing bounded file action ({@code report.artifact.write}). The bytes come
 * from the workspace's own claims and sources; the route never accepts content, a path, a
 * command or a tool name from the caller.</p>
 *
 * <p><b>What the route is not.</b> It is not a general action interface. There is one
 * action, one destination root and one consent. The root is the server's configuration; the
 * action identity is the server's derivation; the only thing the caller chooses is the
 * question, the name it would like the report to have, and whether it consents.</p>
 *
 * <p><b>What the answer means.</b> The response keeps apart five statements that are easy to
 * confuse: the action executed; the artifact exists and carries the digest that was
 * measured; the answer is supported by the workspace's evidence; a proposition is unresolved;
 * and the report may have come from an earlier request. A report is a <i>record</i> of what
 * the workspace said — it is not evidence for its own contents, and the response says so.</p>
 */
@Path("/v1/workbench/workspaces/{id}/reports")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ArtifactReportResource {

    @Inject
    KnowledgeWorkbench workbench;

    @Inject
    ArtifactActionService actions;

    /** The body of a report request. */
    public record ReportRequest(String question, String reportName, Boolean consent,
                                String idempotencyKey, String level) {
    }

    /** One cited piece of evidence. */
    public record EvidenceReference(String claimId, String proposition, String status,
                                    String sourceId, String sourceTitle) {
    }

    /** The outcome of a report request. */
    public record ReportResponse(String taskId,
                                 String actionName,
                                 int actionVersion,
                                 String disposition,
                                 String artifactName,
                                 String artifactLocator,
                                 String sha256,
                                 List<EvidenceReference> evidence,
                                 String epistemicDisposition,
                                 String traceReference,
                                 List<String> limitations,
                                 List<String> refusalReasons) {

        public ReportResponse {
            evidence = List.copyOf(evidence == null ? List.of() : evidence);
            limitations = List.copyOf(limitations == null ? List.of() : limitations);
            refusalReasons = List.copyOf(refusalReasons == null ? List.of() : refusalReasons);
        }
    }

    /**
     * Asks the workspace and writes the report.
     *
     * @param workspaceId the workspace the question is addressed to
     * @param request the request
     * @return the outcome
     */
    @POST
    public Response report(@PathParam("id") String workspaceId, ReportRequest request) {
        if (request == null || request.question() == null || request.question().isBlank()) {
            return refused(400, "question_required", "a question is required");
        }
        if (request.question().length() > ArtifactActionService.MAX_QUESTION_LENGTH) {
            return refused(400, "question_too_long", "the question is longer than "
                    + ArtifactActionService.MAX_QUESTION_LENGTH + " characters");
        }
        if (request.idempotencyKey() != null
                && request.idempotencyKey().length() > ArtifactActionService.MAX_IDEMPOTENCY_KEY_LENGTH) {
            return refused(400, "idempotency_key_too_long", "the idempotency key is longer than "
                    + ArtifactActionService.MAX_IDEMPOTENCY_KEY_LENGTH + " characters");
        }
        if (!Boolean.TRUE.equals(request.consent())) {
            // the consent is the caller's own statement that a file may be written; a request
            // without it is refused before anything is read, let alone written
            return refused(403, "consent_required",
                    "the request must carry consent: true — the report is written to the "
                            + "server's output root, and the consent says so");
        }

        ArtifactActionService.Gate gate = actions.gate();
        if (!gate.allowed()) {
            int status = "local_only_action".equals(gate.code()) ? 403 : 503;
            return refused(status, gate.code(), gate.detail());
        }

        if (!workbench.workspaces().contains(workspaceId)) {
            return refused(404, "workspace_not_found", "no workspace named " + workspaceId);
        }

        String identity = actions.identity(workspaceId, request.idempotencyKey(),
                request.question());
        String reportName = request.reportName();
        if (reportName == null || reportName.isBlank()) {
            reportName = actions.defaultReportName(identity);
        } else if (!plainReportName(reportName)) {
            return refused(400, "invalid_report_name",
                    "the report name must be a plain file name ending in .md, without a "
                            + "directory part, a parent reference or a reserved prefix");
        }

        AbstractionLevel level = parseLevel(request.level());
        Answer answer = workbench.ask(workspaceId, request.question(), level);
        byte[] content = report(workspaceId, request.question(), answer).getBytes(StandardCharsets.UTF_8);

        ArtifactWriter.Result written;
        try {
            written = actions.writer().write(identity, reportName, content);
        } catch (IllegalStateException unusable) {
            return refused(503, "output_root_unavailable", unusable.getMessage());
        }
        if (!written.written()) {
            return refused(statusFor(written.disposition()), written.disposition().name(),
                    written.reason());
        }

        List<EvidenceReference> evidence = new ArrayList<>();
        Map<String, String> sourceTitles = new LinkedHashMap<>();
        for (Source source : answer.sources()) {
            sourceTitles.put(source.id(), source.title());
        }
        for (Claim claim : answer.claims()) {
            evidence.add(new EvidenceReference(claim.id(), claim.proposition(),
                    String.valueOf(claim.status()), claim.introducedBySourceId(),
                    sourceTitles.get(claim.introducedBySourceId())));
        }

        List<String> limitations = new ArrayList<>(answer.limitations());
        limitations.add("a written report is a record of what the workspace said; it is not "
                + "independent evidence for the propositions it repeats");
        if (!answer.answered()) {
            limitations.add("the question was not answered; the absence of an answer is the "
                    + "epistemic result, not a defect of the file action");
        }
        List<String> refusals = written.written() ? List.of() : List.of(written.reason());

        ReportResponse response = new ReportResponse(
                identity,
                ArtifactAction.NAME,
                ArtifactAction.VERSION,
                written.disposition().name(),
                written.fileName(),
                written.fileName(),
                written.sha256(),
                evidence,
                answer.answered() ? "ANSWERED" : "ABSTAINED",
                "artifact-index:" + identity,
                limitations,
                refusals);
        int status = written.disposition() == ArtifactWriter.Disposition.REPLAYED ? 200 : 201;
        return Response.status(status).entity(response).build();
    }

    /**
     * The report, assembled only from what the workspace answered.
     *
     * <p>The sentences in the report are the workspace's claims, with their identifiers and
     * their statuses. Nothing is added that the citations do not carry, and a question the
     * workspace could not answer is written as a refusal to answer.</p>
     */
    private String report(String workspaceId, String question, Answer answer) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# Evidence report\n\n");
        markdown.append("**Question.** ").append(question).append("\n\n");
        markdown.append("**Workspace.** ").append(workspaceId)
                .append(" · **Sources.** ").append(answer.sources().size())
                .append(" · **Claims.** ").append(answer.claims().size()).append("\n\n");
        markdown.append("## Answer\n\n");
        if (answer.answered()) {
            markdown.append(answer.statement()).append("\n\n");
        } else {
            markdown.append("The workspace could not answer this question: ")
                    .append(answer.abstentionReason()).append("\n\n");
            markdown.append("**UNGROUNDED** — no answer is asserted.\n\n");
        }
        markdown.append("## Supported claims\n\n");
        if (answer.claims().isEmpty()) {
            markdown.append("_None: the workspace holds no established claim for this question._\n\n");
        }
        Map<String, String> sourceById = new LinkedHashMap<>();
        answer.sources().forEach(source -> sourceById.put(source.id(), source.title()));
        for (Claim claim : answer.claims()) {
            markdown.append("- `").append(claim.id()).append("` ").append(claim.proposition())
                    .append(" — ").append(claim.status());
            String sourceTitle = sourceById.get(claim.introducedBySourceId());
            if (sourceTitle != null) {
                markdown.append(" — ").append(sourceTitle);
            }
            markdown.append("\n");
        }
        markdown.append("\n## Sources\n\n");
        for (Source source : answer.sources()) {
            markdown.append("- ").append(source.title()).append(" (").append(source.id())
                    .append(")\n");
        }
        markdown.append("\n## Limitations\n\n");
        for (String limitation : answer.limitations()) {
            markdown.append("- ").append(limitation).append("\n");
        }
        if (!answer.unmatchedTerms().isEmpty()) {
            markdown.append("\n## Not covered by the workspace\n\n");
            for (String term : answer.unmatchedTerms()) {
                markdown.append("- `").append(term).append("`\n");
            }
        }
        markdown.append("\n## Epistemic status\n\n");
        markdown.append("- The claims above are the workspace's own, with their ids and their "
                + "statuses; this report asserts nothing the citations do not carry.\n");
        markdown.append("- A report is a record, not evidence: it does not support the "
                + "propositions it repeats.\n");
        if (!answer.answered()) {
            markdown.append("- The question is **not answered**; the absence of an answer is "
                    + "the epistemic result, not a defect of the file action.\n");
        }
        return markdown.toString();
    }

    /**
     * Whether a requested report name is a plain file name.
     *
     * <p>The writer refuses such names too; the route refuses them earlier, so that a
     * traversal attempt is answered as a bad request instead of a write failure.</p>
     */
    private static boolean plainReportName(String name) {
        if (name.isBlank() || name.length() > 128) {
            return false;
        }
        if (name.chars().anyMatch(c -> c < 0x20 || c == 0x7f)) {
            return false;
        }
        if (name.contains("/") || name.contains("\\") || name.contains("..")) {
            return false;
        }
        if (name.startsWith(".")) {
            return false;
        }
        return name.endsWith(".md");
    }

    /** The HTTP status a refusal of the writer carries. */
    private static int statusFor(ArtifactWriter.Disposition disposition) {
        return switch (disposition) {
            case REFUSED_IDENTITY, REFUSED_WRITER_CONFLICT -> 409;
            case REFUSED_BUDGET -> 507;
            case REFUSED_OUTSIDE_ROOT, REFUSED_TRAVERSAL, REFUSED_SYMLINK_ESCAPE,
                    REFUSED_UNSUPPORTED_TYPE, REFUSED_TOO_LARGE, REFUSED_INVALID_CONTENT,
                    REFUSED_EMPTY_NAME -> 400;
            case REFUSED_INDEX_CORRUPT, REFUSED_ROOT_REPLACED, REFUSED_WRITE_FAILED -> 500;
            default -> 500;
        };
    }

    /** The level a request asks for. */
    private static AbstractionLevel parseLevel(String raw) {
        if (raw == null || raw.isBlank()) {
            return AbstractionLevel.INTRODUCTORY;
        }
        try {
            return AbstractionLevel.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return AbstractionLevel.INTRODUCTORY;
        }
    }

    /** A refusal, in the shape the route's clients read. */
    private static Response refused(int status, String code, String detail) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", code);
        body.put("detail", detail == null ? "" : detail);
        return Response.status(status).entity(body).build();
    }
}
