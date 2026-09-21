package io.matrix.scaling;

import java.util.*;

/**
 * W1281 — Cluster Deployer.
 *
 * Ready-to-deploy scripts for Terabyte-scale clusters.
 * Helm charts for K8s, Spark-integration for batch distillation.
 */
public final class ClusterDeployer {

    public enum Platform { KUBERNETES, SPARK, DOCKER_SWARM, BARE_METAL }

    public record ClusterConfig(
            Platform platform,
            int nodeCount,
            int memoryGBPerNode,
            int cpuCoresPerNode,
            String namespace
    ) {}

    public record DeploymentPlan(
            ClusterConfig config,
            List<String> yamlManifests,
            List<String> commands,
            int estimatedPods
    ) {}

    public DeploymentPlan generatePlan(ClusterConfig config) {
        List<String> manifests = new ArrayList<>();
        List<String> commands = new ArrayList<>();

        if (config.platform() == Platform.KUBERNETES) {
            manifests.add(generateHelmChart(config));
            commands.add("helm install matrix ./matrix-chart --namespace " + config.namespace());
            commands.add("kubectl scale deployment matrix --replicas=" + config.nodeCount());
        } else if (config.platform() == Platform.SPARK) {
            commands.add("spark-submit --master spark://master:7077 --executor-memory " +
                config.memoryGBPerNode() + "g matrix-core.jar");
        }

        int pods = config.platform() == Platform.KUBERNETES ? config.nodeCount() : config.nodeCount() / 4;
        return new DeploymentPlan(config, manifests, commands, pods);
    }

    private String generateHelmChart(ClusterConfig config) {
        return String.format("""
            apiVersion: apps/v1
            kind: Deployment
            metadata:
              name: matrix
              namespace: %s
            spec:
              replicas: %d
              template:
                spec:
                  containers:
                  - name: matrix
                    image: matrix:omni
                    resources:
                      limits:
                        memory: %dGi
                        cpu: %d
            """, config.namespace(), config.nodeCount(),
                config.memoryGBPerNode(), config.cpuCoresPerNode());
    }

    /**
     * Estimate total memory for terabyte-scale deployment.
     */
    public long estimateTotalMemory(ClusterConfig config) {
        return (long) config.nodeCount() * config.memoryGBPerNode() * 1024L * 1024L * 1024L;
    }
}
