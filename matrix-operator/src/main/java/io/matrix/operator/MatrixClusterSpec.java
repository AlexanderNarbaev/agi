package io.matrix.operator;

import java.util.List;

public class MatrixClusterSpec {

    private int neurons;
    private int k;
    private List<String> frozenNeurons;
    private String instanceId;
    private MediatorConfig mediatorConfig;

    public int getNeurons() { return neurons; }
    public void setNeurons(int neurons) { this.neurons = neurons; }
    public int getK() { return k; }
    public void setK(int k) { this.k = k; }
    public List<String> getFrozenNeurons() { return frozenNeurons; }
    public void setFrozenNeurons(List<String> frozenNeurons) { this.frozenNeurons = frozenNeurons; }
    public String getInstanceId() { return instanceId; }
    public void setInstanceId(String instanceId) { this.instanceId = instanceId; }
    public MediatorConfig getMediatorConfig() { return mediatorConfig; }
    public void setMediatorConfig(MediatorConfig mediatorConfig) { this.mediatorConfig = mediatorConfig; }

    public static class MediatorConfig {
        private long tickIntervalMs = 1000;
        private double resourceFactorStart = 0.8;
        private int maxActiveGoals = 10;

        public long getTickIntervalMs() { return tickIntervalMs; }
        public void setTickIntervalMs(long tickIntervalMs) { this.tickIntervalMs = tickIntervalMs; }
        public double getResourceFactorStart() { return resourceFactorStart; }
        public void setResourceFactorStart(double resourceFactorStart) { this.resourceFactorStart = resourceFactorStart; }
        public int getMaxActiveGoals() { return maxActiveGoals; }
        public void setMaxActiveGoals(int maxActiveGoals) { this.maxActiveGoals = maxActiveGoals; }
    }
}
