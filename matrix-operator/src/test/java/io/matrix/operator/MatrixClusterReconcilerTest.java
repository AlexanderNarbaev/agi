package io.matrix.operator;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@EnableKubernetesMockClient(crud = true)
class MatrixClusterReconcilerTest {

    private KubernetesClient client;

    @Test
    void shouldCreateDeploymentWhenNotExists() {
        MatrixClusterSpec spec = new MatrixClusterSpec();
        spec.setNeurons(100);
        spec.setK(18);
        spec.setFrozenNeurons(List.of("f1", "f2"));

        MatrixCluster cr = new MatrixCluster();
        cr.setMetadata(new ObjectMetaBuilder()
                .withName("test-cluster")
                .withNamespace("matrix-ns")
                .build());
        cr.setSpec(spec);
        cr.setStatus(new MatrixClusterStatus());

        MatrixClusterReconciler reconciler = new MatrixClusterReconciler(client);
        MatrixClusterStatus result = reconciler.reconcile(cr);

        assertThat(result.getPhase()).isEqualTo("Running");
        assertThat(result.getActiveNeurons()).isEqualTo(100);
        assertThat(result.getFrozenNeurons()).isEqualTo(2);

        var deployment = client.apps().deployments()
                .inNamespace("matrix-ns")
                .withName("test-cluster-workers")
                .get();
        assertThat(deployment).isNotNull();
        assertThat(deployment.getSpec().getReplicas()).isEqualTo(1);

        reconciler.close();
    }

    @Test
    void shouldUpdateStatusForExistingDeployment() {
        MatrixClusterSpec spec = new MatrixClusterSpec();
        spec.setNeurons(200);
        spec.setK(16);

        MatrixCluster cr = new MatrixCluster();
        cr.setMetadata(new ObjectMetaBuilder()
                .withName("existing-cluster")
                .withNamespace("matrix-ns")
                .build());
        cr.setSpec(spec);
        cr.setStatus(new MatrixClusterStatus());

        MatrixClusterReconciler reconciler = new MatrixClusterReconciler(client);
        reconciler.reconcile(cr);

        MatrixClusterStatus result = reconciler.reconcile(cr);

        assertThat(result.getPhase()).isEqualTo("Running");
        assertThat(result.getActiveNeurons()).isEqualTo(200);
        reconciler.close();
    }

    @Test
    void shouldSetPhaseToDegradedOnError() {
        MatrixClusterSpec spec = new MatrixClusterSpec();
        spec.setNeurons(0);
        spec.setK(0);

        MatrixCluster cr = new MatrixCluster();
        cr.setMetadata(null);
        cr.setSpec(spec);
        cr.setStatus(new MatrixClusterStatus());

        MatrixClusterReconciler reconciler = new MatrixClusterReconciler(client);
        MatrixClusterStatus result = reconciler.reconcile(cr);

        assertThat(result.getPhase()).isEqualTo("Degraded");
        reconciler.close();
    }

    @Test
    void shouldHandleNullFrozenNeurons() {
        MatrixClusterSpec spec = new MatrixClusterSpec();
        spec.setNeurons(50);
        spec.setK(8);

        MatrixCluster cr = new MatrixCluster();
        cr.setMetadata(new ObjectMetaBuilder()
                .withName("no-frozen")
                .withNamespace("matrix-ns")
                .build());
        cr.setSpec(spec);
        cr.setStatus(new MatrixClusterStatus());

        MatrixClusterReconciler reconciler = new MatrixClusterReconciler(client);
        MatrixClusterStatus result = reconciler.reconcile(cr);

        assertThat(result.getFrozenNeurons()).isEqualTo(0);
        reconciler.close();
    }

    @Test
    void shouldUseDefaultStatusWhenNull() {
        MatrixClusterSpec spec = new MatrixClusterSpec();
        spec.setNeurons(10);
        spec.setK(4);

        MatrixCluster cr = new MatrixCluster();
        cr.setMetadata(new ObjectMetaBuilder()
                .withName("no-status")
                .withNamespace("matrix-ns")
                .build());
        cr.setSpec(spec);
        cr.setStatus(null);

        MatrixClusterReconciler reconciler = new MatrixClusterReconciler(client);
        MatrixClusterStatus result = reconciler.reconcile(cr);

        assertThat(result.getPhase()).isEqualTo("Running");
        reconciler.close();
    }
}
