package io.matrix.operator;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MatrixClusterReconciler {

    private static final String IMAGE = "ghcr.io/alexandernarbaev/matrix-core:latest";
    private static final String SNAPSHOT_PVC_SUFFIX = "-snapshots";
    private static final String WORKER_SUFFIX = "-workers";
    private static final String SERVICE_SUFFIX = "-svc";

    private final KubernetesClient client;

    public MatrixClusterReconciler() {
        this.client = new KubernetesClientBuilder().build();
    }

    public MatrixClusterReconciler(KubernetesClient client) {
        this.client = client;
    }

    public MatrixClusterStatus reconcile(MatrixCluster resource) {
        MatrixClusterStatus status = resource.getStatus() != null
                ? resource.getStatus() : new MatrixClusterStatus();

        List<MatrixClusterStatus.Condition> conditions = new ArrayList<>();

        try {
            MatrixClusterSpec spec = resource.getSpec();
            ObjectMeta metadata = resource.getMetadata();
            if (metadata == null) {
                throw new IllegalArgumentException("metadata must not be null");
            }
            String name = metadata.getName();
            String namespace = metadata.getNamespace();

            if (spec == null) {
                throw new IllegalArgumentException("spec must not be null");
            }

            String now = ZonedDateTime.now().format(DateTimeFormatter.ISO_INSTANT);

            ensurePersistentVolumeClaim(name, namespace, conditions, now);
            ensureDeployment(name, namespace, spec, conditions, now);
            ensureService(name, namespace, conditions, now);

            status.setPhase("Running");
            status.setActiveNeurons(spec.getNeurons());
            status.setFrozenNeurons(
                    spec.getFrozenNeurons() != null ? spec.getFrozenNeurons().size() : 0);

            conditions.add(condition("Ready", "True", "Reconciled",
                    "All resources up to date", now));

        } catch (Exception e) {
            status.setPhase("Degraded");
            conditions.add(condition("Ready", "False", "ReconcileError",
                    e.getMessage() != null ? e.getMessage() : "unknown error",
                    ZonedDateTime.now().format(DateTimeFormatter.ISO_INSTANT)));
        }

        status.setConditions(conditions);
        return status;
    }

    private void ensurePersistentVolumeClaim(String name, String namespace,
            List<MatrixClusterStatus.Condition> conditions, String now) {
        String pvcName = name + SNAPSHOT_PVC_SUFFIX;

        var existing = client.persistentVolumeClaims()
                .inNamespace(namespace)
                .withName(pvcName)
                .get();

        if (existing == null) {
            PersistentVolumeClaim pvc = new PersistentVolumeClaimBuilder()
                    .withNewMetadata()
                    .withName(pvcName)
                    .withNamespace(namespace)
                    .withLabels(Map.of("app", "matrix-cluster", "cluster", name))
                    .endMetadata()
                    .withNewSpec()
                    .withAccessModes("ReadWriteMany")
                    .withNewResources()
                    .addToRequests("storage", new io.fabric8.kubernetes.api.model.Quantity("10Gi"))
                    .endResources()
                    .withStorageClassName("standard")
                    .endSpec()
                    .build();

            client.persistentVolumeClaims().inNamespace(namespace).create(pvc);
            conditions.add(condition("PVCReady", "True", "Created",
                    "PersistentVolumeClaim " + pvcName + " created", now));
        } else {
            conditions.add(condition("PVCReady", "True", "Exists",
                    "PersistentVolumeClaim " + pvcName + " already exists", now));
        }
    }

    private void ensureDeployment(String name, String namespace, MatrixClusterSpec spec,
            List<MatrixClusterStatus.Condition> conditions, String now) {
        String deployName = name + WORKER_SUFFIX;
        String pvcName = name + SNAPSHOT_PVC_SUFFIX;

        var existing = client.apps().deployments()
                .inNamespace(namespace)
                .withName(deployName)
                .get();

        if (existing == null) {
            Container container = new ContainerBuilder()
                    .withName("matrix-core")
                    .withImage(IMAGE)
                    .withImagePullPolicy("Always")
                    .withPorts(new ContainerPortBuilder()
                            .withContainerPort(9091)
                            .withName("http")
                            .build())
                    .withEnv(
                            new EnvVar("JAVA_OPTS", "-Xms256m -Xmx512m -XX:+UseZGC", null),
                            new EnvVar("DB_HOST", "matrix-postgres", null),
                            new EnvVar("DB_PORT", "5432", null),
                            new EnvVar("KAFKA_BOOTSTRAP_SERVERS", "matrix-kafka:9092", null),
                            new EnvVar("MINIO_URL", "http://minio:9000", null),
                            new EnvVar("QUARKUS_HTTP_PORT", "9091", null)
                    )
                    .withResources(new ResourceRequirementsBuilder()
                            .addToRequests("cpu", new io.fabric8.kubernetes.api.model.Quantity("500m"))
                            .addToRequests("memory", new io.fabric8.kubernetes.api.model.Quantity("512Mi"))
                            .addToLimits("cpu", new io.fabric8.kubernetes.api.model.Quantity("2000m"))
                            .addToLimits("memory", new io.fabric8.kubernetes.api.model.Quantity("1Gi"))
                            .build())
                    .withReadinessProbe(new ProbeBuilder()
                            .withHttpGet(new HTTPGetActionBuilder()
                                    .withPath("/q/health/ready")
                                    .withNewPort(9091)
                                    .build())
                            .withInitialDelaySeconds(5)
                            .withPeriodSeconds(5)
                            .build())
                    .withLivenessProbe(new ProbeBuilder()
                            .withHttpGet(new HTTPGetActionBuilder()
                                    .withPath("/q/health/live")
                                    .withNewPort(9091)
                                    .build())
                            .withInitialDelaySeconds(15)
                            .withPeriodSeconds(10)
                            .build())
                    .withVolumeMounts(new VolumeMountBuilder()
                            .withName("snapshots")
                            .withMountPath("/data/snapshots")
                            .build())
                    .build();

            Deployment deployment = new DeploymentBuilder()
                    .withNewMetadata()
                    .withName(deployName)
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
                    .withAnnotations(Map.of(
                            "prometheus.io/scrape", "true",
                            "prometheus.io/port", "9091",
                            "prometheus.io/path", "/metrics"
                    ))
                    .endMetadata()
                    .withNewSpec()
                    .withContainers(container)
                    .withVolumes(new VolumeBuilder()
                            .withName("snapshots")
                            .withPersistentVolumeClaim(
                                    new PersistentVolumeClaimVolumeSourceBuilder()
                                            .withClaimName(pvcName)
                                            .build())
                            .build())
                    .endSpec()
                    .endTemplate()
                    .endSpec()
                    .build();

            client.apps().deployments().inNamespace(namespace).create(deployment);
            conditions.add(condition("DeploymentReady", "True", "Created",
                    "Deployment " + deployName + " created", now));
        } else {
            conditions.add(condition("DeploymentReady", "True", "Exists",
                    "Deployment " + deployName + " already exists", now));
        }
    }

    private void ensureService(String name, String namespace,
            List<MatrixClusterStatus.Condition> conditions, String now) {
        String svcName = name + SERVICE_SUFFIX;

        var existing = client.services()
                .inNamespace(namespace)
                .withName(svcName)
                .get();

        if (existing == null) {
            Service service = new ServiceBuilder()
                    .withNewMetadata()
                    .withName(svcName)
                    .withNamespace(namespace)
                    .withLabels(Map.of("app", "matrix-cluster", "cluster", name))
                    .endMetadata()
                    .withNewSpec()
                    .withType("ClusterIP")
                    .withSelector(Map.of("app", "matrix-cluster", "cluster", name))
                    .withPorts(new ServicePortBuilder()
                            .withName("http")
                            .withPort(9091)
                            .withTargetPort(new IntOrString(9091))
                            .withProtocol("TCP")
                            .build())
                    .endSpec()
                    .build();

            client.services().inNamespace(namespace).create(service);
            conditions.add(condition("ServiceReady", "True", "Created",
                    "Service " + svcName + " created", now));
        } else {
            conditions.add(condition("ServiceReady", "True", "Exists",
                    "Service " + svcName + " already exists", now));
        }
    }

    private static MatrixClusterStatus.Condition condition(
            String type, String status, String reason, String message, String lastTransitionTime) {
        MatrixClusterStatus.Condition c = new MatrixClusterStatus.Condition();
        c.setType(type);
        c.setStatus(status);
        c.setReason(reason);
        c.setMessage(message);
        c.setLastTransitionTime(lastTransitionTime);
        return c;
    }

    public void close() {
        client.close();
    }
}
