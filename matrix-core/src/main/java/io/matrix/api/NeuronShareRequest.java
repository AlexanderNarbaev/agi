package io.matrix.api;

/**
 * DTO for neuron sharing between swarm agents.
 *
 * <p>An agent uploads its best-performing neurons to the Noosphere
 * so that other agents of the same role can benefit from the shared knowledge.
 */
public class NeuronShareRequest {

    /** Agent role (miner, crafter, explorer, fighter, generalist). */
    public String role;

    /** Unique identifier of the sharing agent. */
    public String agentId;

    /** Base64-encoded neuron data (Avro-serialized truth table). */
    public String neuronData;

    /** Fitness score of this neuron (higher = better). */
    public double fitness;

    public NeuronShareRequest() {}

    public NeuronShareRequest(String role, String agentId, String neuronData, double fitness) {
        this.role = role;
        this.agentId = agentId;
        this.neuronData = neuronData;
        this.fitness = fitness;
    }
}
