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
        assertThat(result.getConditions()).isNotEmpty();

        var deployment = client.apps().deployments()
                .inNamespace("matrix-ns")
                .withName("test-cluster-workers")
                .get();
        assertThat(deployment).isNotNull();
        assertThat(deployment.getSpec().getReplicas()).isEqualTo(1);

        var container = deployment.getSpec().getTemplate().getSpec().getContainers().get(0);
        assertThat(container.getReadinessProbe()).isNotNull();
        assertThat(container.getLivenessProbe()).isNotNull();
        assertThat(container.getResources().getRequests()).containsKey("cpu");
        assertThat(container.getEnv()).isNotEmpty();

        var service = client.services()
                .inNamespace("matrix-ns")
                .withName("test-cluster-svc")
                .get();
        assertThat(service).isNotNull();

        var pvc = client.persistentVolumeClaims()
                .inNamespace("matrix-ns")
                .withName("test-cluster-snapshots")
                .get();
        assertThat(pvc).isNotNull();

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

        var conditions = result.getConditions();
        assertThat(conditions).isNotEmpty();
        assertThat(conditions.stream().anyMatch(c -> "Ready".equals(c.getType())
                && "True".equals(c.getStatus()))).isTrue();

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
        assertThat(result.getConditions()).isNotEmpty();
        assertThat(result.getConditions().stream()
                .anyMatch(c -> "ReconcileError".equals(c.getReason()))).isTrue();
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
        assertThat(result.getPhase()).isEqualTo("Running");
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
        assertThat(result.getConditions()).isNotEmpty();
        reconciler.close();
    }

    @Test
    void shouldCreateServiceForCluster() {
        MatrixClusterSpec spec = new MatrixClusterSpec();
        spec.setNeurons(30);
        spec.setK(10);

        MatrixCluster cr = new MatrixCluster();
        cr.setMetadata(new ObjectMetaBuilder()
                .withName("svc-test")
                .withNamespace("matrix-ns")
                .build());
        cr.setSpec(spec);
        cr.setStatus(new MatrixClusterStatus());

        MatrixClusterReconciler reconciler = new MatrixClusterReconciler(client);
        reconciler.reconcile(cr);

        var service = client.services()
                .inNamespace("matrix-ns")
                .withName("svc-test-svc")
                .get();
        assertThat(service).isNotNull();
        assertThat(service.getSpec().getPorts()).hasSize(1);
        assertThat(service.getSpec().getPorts().get(0).getPort()).isEqualTo(9091);
        assertThat(service.getSpec().getType()).isEqualTo("ClusterIP");

        reconciler.close();
    }

    @Test
    void shouldCreatePersistentVolumeClaim() {
        MatrixClusterSpec spec = new MatrixClusterSpec();
        spec.setNeurons(20);
        spec.setK(12);

        MatrixCluster cr = new MatrixCluster();
        cr.setMetadata(new ObjectMetaBuilder()
                .withName("pvc-test")
                .withNamespace("matrix-ns")
                .build());
        cr.setSpec(spec);
        cr.setStatus(new MatrixClusterStatus());

        MatrixClusterReconciler reconciler = new MatrixClusterReconciler(client);
        reconciler.reconcile(cr);

        var pvc = client.persistentVolumeClaims()
                .inNamespace("matrix-ns")
                .withName("pvc-test-snapshots")
                .get();
        assertThat(pvc).isNotNull();
        assertThat(pvc.getSpec().getAccessModes()).contains("ReadWriteMany");
        assertThat(pvc.getSpec().getResources().getRequests().get("storage"))
                .isEqualTo(new io.fabric8.kubernetes.api.model.Quantity("10Gi"));

        reconciler.close();
    }
}
