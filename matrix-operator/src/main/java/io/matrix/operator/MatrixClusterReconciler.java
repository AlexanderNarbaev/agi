package io.matrix.operator;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;

import java.util.Map;

public class MatrixClusterReconciler {

    private final KubernetesClient client;

    public MatrixClusterReconciler() {
        this.client = new KubernetesClientBuilder().build();
    }

    public MatrixClusterReconciler(KubernetesClient client) {
        this.client = client;
    }

    public MatrixClusterStatus reconcile(MatrixCluster resource) {
        MatrixClusterSpec spec = resource.getSpec();
        MatrixClusterStatus status = resource.getStatus() != null
                ? resource.getStatus() : new MatrixClusterStatus();

        try {
            String name = resource.getMetadata().getName();
            String namespace = resource.getMetadata().getNamespace();

            var existing = client.apps().deployments()
                    .inNamespace(namespace)
                    .withName(name + "-workers")
                    .get();

            if (existing == null) {
                var deployment = new io.fabric8.kubernetes.api.model.apps.DeploymentBuilder()
                        .withNewMetadata()
                        .withName(name + "-workers")
                        .withNamespace(namespace)
                        .withLabels(Map.of("app", "matrix-cluster", "cluster", name))
                        .endMetadata()
                        .withNewSpec()
                        .withReplicas(1)
                        .withNewSelector()
                        .withMatchLabels(Map.of("app", "matrix-cluster", "cluster", name))
                        .endSelector()
                        .withNewTemplate()
                        .withNewMetadata()
                        .withLabels(Map.of("app", "matrix-cluster", "cluster", name))
                        .endMetadata()
                        .withNewSpec()
                        .addNewContainer()
                        .withName("matrix-core")
                        .withImage("ghcr.io/matrix-ai/matrix-core:latest")
                        .withArgs("simulate", "-g", "50", "-p",
                                String.valueOf(spec.getNeurons()),
                                "-k", String.valueOf(spec.getK()))
                        .endContainer()
                        .endSpec()
                        .endTemplate()
                        .endSpec()
                        .build();

                client.apps().deployments().inNamespace(namespace).create(deployment);
                status.setPhase("Running");
            }

            status.setActiveNeurons(spec.getNeurons());
            status.setFrozenNeurons(
                    spec.getFrozenNeurons() != null ? spec.getFrozenNeurons().size() : 0);

        } catch (Exception e) {
            status.setPhase("Degraded");
        }

        return status;
    }

    public void close() {
        client.close();
    }
}
