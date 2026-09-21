package io.matrix.api;

import io.matrix.model.ModelRegistry;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HTTP surface for {@link ModelRegistry}: lists registered models and
 * exposes a prediction endpoint so external clients can call the
 * distilled BIR models directly.
 */
@Path("/v1/models-registry")
@Produces(MediaType.APPLICATION_JSON)
public class ModelRegistryResource {

    @Inject
    ModelRegistry registry;

    @GET
    public Map<String, Object> list() {
        return registry.describe();
    }

    @GET
    @Path("/{name}")
    public Response describe(@PathParam("name") String name) {
        var entry = registry.get(name);
        if (entry == null) {
            return Response.status(404)
                    .entity(Map.of("error", "no model: " + name))
                    .build();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", entry.name());
        body.put("origin", entry.origin());
        body.put("description", entry.description());
        body.put("inputBits", entry.bir().inputBits());
        return Response.ok(body).build();
    }

    /**
     * Evaluate a model on a binary input.
     * POST /v1/models-registry/{name}/eval?bits=20
     * body: a JSON object with "bits" — base-10 integer
     */
    @POST
    @Path("/{name}/eval")
    public Response eval(@PathParam("name") String name, Map<String, Object> body) {
        Object bitsObj = body == null ? null : body.get("bits");
        if (!(bitsObj instanceof Number n)) {
            return Response.status(400)
                    .entity(Map.of("error", "bits must be a number"))
                    .build();
        }
        try {
            long[] in = new long[1];
            in[0] = n.longValue();
            long[] out = registry.eval(name, in);
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("name", name);
            resp.put("input", in[0]);
            resp.put("output", out.length == 1 ? out[0] : java.util.Arrays.toString(out));
            return Response.ok(resp).build();
        } catch (IllegalArgumentException e) {
            return Response.status(404)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }
}