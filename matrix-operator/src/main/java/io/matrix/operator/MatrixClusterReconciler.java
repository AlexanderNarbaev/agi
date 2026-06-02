package io.matrix.operator;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.javaoperatorsdk.operator.api.reconciler.*;
import jakarta.inject.Singleton;

import java.util.Map;

@ControllerConfiguration
@Singleton
public class MatrixClusterReconciler implements Reconciler<MatrixCluster> {

    @Override
    public UpdateControl<MatrixCluster> reconcile(MatrixCluster resource, Context<MatrixCluster> context) {
        MatrixClusterSpec spec = resource.getSpec();
        MatrixClusterStatus status = resource.getStatus();
        String name = resource.getMetadata().getName();
        String namespace = resource.getMetadata().getNamespace();

        try {
            var client = context.getClient();

            Deployment existing = client.apps().deployments()
                    .inNamespace(namespace)
                    .withName(name + "-workers")
                    .get();

            if (existing == null) {
                Deployment deployment = new DeploymentBuilder()
                        .withNewMetadata()
                        .withName(name + "-workers")
                        .withNamespace(namespace)
                        .withLabels(Map.of(
                                "app", "matrix-cluster",
                                "cluster", name))
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
                        .withArgs("simulate",
                                "-g", "50",
                                "-p", String.valueOf(spec.getNeurons()),
                                "-k", String.valueOf(spec.getK()))
                        .endContainer()
                        .endSpec()
                        .endTemplate()
                        .endSpec()
                        .build();

                client.apps().deployments()
                        .inNamespace(namespace)
                        .create(deployment);

                status.setPhase("Running");
            }

            status.setActiveNeurons(spec.getNeurons());
            status.setFrozenNeurons(spec.getFrozenNeurons() != null ? spec.getFrozenNeurons().size() : 0);

        } catch (Exception e) {
            status.setPhase("Degraded");
        }

        return UpdateControl.updateStatus(resource);
    }
}
