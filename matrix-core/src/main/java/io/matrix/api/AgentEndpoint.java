package io.matrix.api;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent endpoint (Phase 6 — "I want to use it as a chat, assistant,
 * agent, etc.").
 *
 * <p>Accepts a goal and decomposes it into tool calls. The
 * decomposition uses simple keyword-based heuristics (no LLM);
 * production-quality agents would replace this with a learned
 * decomposer. The endpoint demonstrates the agent routing flow
 * and is the place where new tools are added.
 */
@Path("/v1/agent")
@Produces(MediaType.APPLICATION_JSON)
public class AgentEndpoint {

    public record AgentRequest(String goal, List<String> allowedTools) {}
    public record AgentStep(int index, String tool, Map<String, Object> args, String rationale) {}
    public record AgentPlan(String goal, List<AgentStep> steps, String reasoning) {}

    /**
     * POST /v1/agent — produce a plan for a goal. The plan lists the
     * tool calls that would carry the goal out. The user (or an
     * outer loop) executes the tools and feeds results back.
     */
    @POST
    @Path("/plan")
    public AgentPlan plan(AgentRequest req) {
        String goal = req.goal() == null ? "" : req.goal().toLowerCase();
        List<AgentStep> steps = new java.util.ArrayList<>();
        String reasoning;
        // simple heuristic decomposition — match keywords to tool calls
        if (goal.contains("file") || goal.contains("read") || goal.contains("list")) {
            steps.add(new AgentStep(0, "fs.list", Map.of("path", "/"),
                    "user asked about files/directories"));
            reasoning = "file-related goal: enumerate";
        } else if (goal.contains("search") || goal.contains("find") || goal.contains("where")) {
            steps.add(new AgentStep(0, "kb.search",
                    Map.of("query", req.goal()), "search the knowledge base"));
            reasoning = "search goal: query the LTM";
        } else if (goal.contains("compute") || goal.contains("calculate")) {
            steps.add(new AgentStep(0, "calc",
                    Map.of("expr", req.goal()), "evaluate the expression"));
            reasoning = "compute goal: run calculator";
        } else if (goal.contains("train") || goal.contains("learn")) {
            steps.add(new AgentStep(0, "matrix.train",
                    Map.of("goal", "from-corpus"), "train the matrix on stored corpus"));
            reasoning = "training goal: hit /v1/train endpoint";
        } else {
            steps.add(new AgentStep(0, "kb.search",
                    Map.of("query", req.goal()), "default: search the LTM"));
            steps.add(new AgentStep(1, "chain.eval",
                    Map.of("input", req.goal()), "fallback: run the chain"));
            reasoning = "default decomposition: search + chain eval";
        }
        return new AgentPlan(req.goal(), steps, reasoning);
    }

    @POST
    @Path("/tools")
    public Map<String, Object> tools(AgentRequest req) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("goal", req.goal());
        body.put("available_tools", List.of(
                Map.of("name", "fs.list",
                        "description", "List files under a path",
                        "params", List.of("path")),
                Map.of("name", "kb.search",
                        "description", "Search the knowledge base (LTM)",
                        "params", List.of("query")),
                Map.of("name", "calc",
                        "description", "Evaluate a math expression",
                        "params", List.of("expr")),
                Map.of("name", "matrix.train",
                        "description", "Train the boolean chain on stored corpus",
                        "params", List.of("goal", "epochs")),
                Map.of("name", "chain.eval",
                        "description", "Run the boolean chain on an input",
                        "params", List.of("input"))
        ));
        return body;
    }
}