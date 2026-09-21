# L9 — Deployment & Operations

**Status:** normative · **Layer:** 9 (operations) · **Date:** 
/scientific tone; archive reference added.

## 1. Scope

Layer 9 specifies packaging, orchestration, autoscaling,
monitoring, and local development for the matrix-core service.
It does not specify protocols (L2) or runtime semantics
(DESIGN-03); it specifies how those components are instantiated
on a cluster.

## 2. Infrastructure Components

Kubernetes-native operators or StatefulSets host the dependencies:

- Apache Kafka via Strimzi; 3 replicas; retention 72 h; topic
 auto-create disabled.
- MinIO via MinIO Operator; pool of 4 servers × 2 volumes of
 1 TiB; auto-cert enabled.
- PostgreSQL (optional) via Cloud Native PG; Pekko Persistence
 JDBC adapter.
- Prometheus + Grafana via kube-prometheus-stack.

## 3. Instance Packaging

Multi-stage Docker build with GraalVM Native Image. Resulting
image is a Quarkus micro-image carrying the `runner` binary, the
`config/` directory, and an empty `/data` mount. Configuration via
`application.properties`; sensitive values from Kubernetes
Secrets (env vars: KAFKA_BOOTSTRAP_SERVERS, MINIO_URL,
PEKKO_SEED_NODES).

## 4. Orchestration

Deployments target the Quarkus uber-jar or native runner. Service
is headless (`clusterIP: None`) for Pekko Cluster discovery.
Readiness and liveness probes hit `/q/health/ready` and
`/q/health/live`. PVC mounted at `/data` for RocksDB and local
journals.

## 5. Operator

A Kubernetes Operator (fabric8 client, Quarkus) reconciles
MatrixCluster and MatrixLobe CRDs:

- MatrixCluster — replicas, mediator count, Kafka bootstrap,
 MinIO endpoint, storage, monitoring flags.
- MatrixLobe — cluster reference, source `.ldn`, replicas.

The operator creates Deployments, Services, ConfigMaps, Secrets,
PVCs; reacts to `neuronReplicas`; orchestrates snapshot Jobs.

## 6. Autoscaling

- HPA on CPU and the custom metric `matrix_signal_rate`
 (signals/sec). Min 2, max 100.
- VPA on stateless components in Auto mode; Off (recommendation
 only) for actors with long event-replication history.
- Cluster Autoscaler delegated to the cloud provider
 (EKS / GKE / AKS).

## 7. Observability

Quarkus Micrometer → Prometheus. Standard metrics plus
`matrix_neuron_evaluate_duration_seconds`, `matrix_signal_rate`,
`matrix_neuron_count`, `matrix_accuracy`, `matrix_hades_count`,
`matrix_ethical_violations`. Grafana panels: throughput, latency
p50 / p99, mutations per minute, HADES status, pod resource use.

## 8. Local Development

minikube or kind cluster; reuse the production manifest set
(`infra/k8s/base/`). Local image build via
`eval $(minikube docker-env)` and `./gradlew :matrix-core:
quarkusBuild -Dquarkus.package.jar.type=uber-jar`. Verify with
`/q/health/ready`, `/q/health/live`, and `/metrics`.

## 9. Network Security

NetworkPolicy restricts ingress to pods labelled
`app=matrix-mediator` or `app=matrix-neuron`. mTLS via Istio
PeerAuthentication STRICT. Kafka listener TLS on port 9093.

## 10. Backup and Restore

CronJob-driven snapshot creation into MinIO. Event journal
retention 72 h; long-term via Kafka Connect sink. Restore: load
last snapshot, replay events from the stored offset.

> framed this layer as "production readiness" with a Quick Start
> walkthrough. The v3 text compresses the same content into a
> measured operations contract; the Quick Start remains in
> `engineering/RUNBOOK.md`. Archive copy:

Next: L10 Monitoring — SLOs, alert routing, runbook links.