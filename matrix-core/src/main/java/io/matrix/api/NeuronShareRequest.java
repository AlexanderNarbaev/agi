package io.matrix.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * DTO for neuron sharing between swarm agents.
 *
 * <p>An agent uploads its best-performing neurons to the Noosphere
 * so that other agents of the same role can benefit from the shared knowledge.
 */
public class NeuronShareRequest {

    /** Agent role (miner, crafter, explorer, fighter, generalist). */
    @Size(max = 64)
    public String role;

    /** Unique identifier of the sharing agent. */
    @Size(max = 64)
    public String agentId;

    /** Base64-encoded neuron data (Avro-serialized truth table). */
    @NotNull
    @Size(max = 65536)
    public String neuronData;

    /** Fitness score of this neuron (higher = better). */
    @Min(0) @Max(1_000_000)
    public double fitness;

    public NeuronShareRequest() {}

    public NeuronShareRequest(String role, String agentId, String neuronData, double fitness) {
        this.role = role;
        this.agentId = agentId;
        this.neuronData = neuronData;
        this.fitness = fitness;
    }
}
