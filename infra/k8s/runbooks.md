# MATRIX Runbooks — Incident Response Procedures

## RB01: MatrixCoreDown

**Alert:** `MatrixCoreDown` (PrometheusRule)
**Severity:** Critical
**SLO Impact:** Availability < 99.9%

**Symptoms:**
- `matrix_core_up == 0` for > 5 min
- Health check `/q/health/live` returning non-200
- Grafana dashboard shows 0 replicas

**Response:**
1. Check pod status: `kubectl get pods -n matrix -l app=matrix-core`
2. Check pod logs: `kubectl logs -n matrix -l app=matrix-core --tail=100`
3. Check events: `kubectl get events -n matrix --sort-by='.lastTimestamp'`
4. Describe deployment: `kubectl describe deployment matrix-core -n matrix`

**Common causes:**
- OOMKilled: increase memory limits in VPA/deployment
- Image pull error: check `ghcr.io/matrix-ai/matrix-core:latest`
- CrashLoopBackOff: check Java heap dump from `/data/snapshots`

**Escalation:** If > 3 replicas down for > 10 min → escalate to on-call SRE.

---

## RB02: HighErrorRate

**Alert:** `HighErrorRate5xx` (PrometheusRule)
**Severity:** Warning → Critical (if sustained)
**SLO Impact:** Error budget consumption > 10%

**Symptoms:**
- HTTP 5xx rate > 5% for > 5 min
- Grafana dashboard shows red error rate panel

**Response:**
1. Identify failing endpoints: check Jaeger traces for high-error spans
2. Check Loki logs: `{app="matrix-core"} |= "ERROR"` (Grafana Explore)
3. Verify downstream dependencies (Kafka, MinIO) are healthy
4. Check recent deploys: `kubectl rollout history deployment/matrix-core -n matrix`

**Rollback:** `kubectl rollout undo deployment/matrix-core -n matrix`

---

## RB03: KafkaLag

**Alert:** `KafkaConsumerLag` (PrometheusRule)
**Severity:** Warning
**SLO Impact:** Event processing delay > 30s

**Symptoms:**
- `kafka_consumer_group_lag > 1000` for > 10 min
- Neuron snapshots delayed

**Response:**
1. Check Kafka consumer group: `kubectl exec -n matrix matrix-kafka-kafka-0 -- bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --group neuron-cluster --describe`
2. Check Kafka broker health: `kubectl get pods -n matrix -l strimzi.io/kind=Kafka`
3. Scale consumers: increase replicas in deployment
4. If partition skew: rebalance with Strimzi operator

---

## RB04: SlowResponse

**Alert:** `SlowResponseP99` (PrometheusRule)
**Severity:** Warning
**SLO Impact:** P99 latency > 500ms

**Symptoms:**
- `http_server_requests_seconds{quantile="0.99"} > 0.5`
- User-facing API sluggish

**Response:**
1. Check Jaeger for slow trace spans
2. Check GC pauses: `jvm_gc_pause_seconds_max` metric
3. Check neuron count: `matrix_neuron_count` metric — if > 100K, consider sharding
4. Check VPA recommendations: `kubectl describe vpa matrix-core-vpa -n matrix`

**Mitigation:** Increase replicas via HPA or manually: `kubectl scale deployment matrix-core -n matrix --replicas=5`

---

## RB05: SnapshotFailure

**Alert:** `SnapshotStoreFailure` (PrometheusRule)
**Severity:** Critical
**SLO Impact:** Data loss risk

**Symptoms:**
- `matrix_snapshot_failures_total > 0`
- MinIO unavailable or full

**Response:**
1. Check MinIO: `kubectl get pods -n matrix -l app=matrix-minio`
2. Check MinIO PVC: `kubectl get pvc -n matrix -l v1.min.io/tenant=matrix-minio`
3. Verify snapshot path: `/data/snapshots` mounted and writable
4. Check disk space: `kubectl exec -n matrix deployment/matrix-core -- df -h /data/snapshots`

**Recovery:** Restore latest snapshot from MinIO backup bucket `neuron-snapshots`.

---

## RB06: EthicalFilterViolation

**Alert:** `EthicalViolation` (PrometheusRule)
**Severity:** Critical
**SLO Impact:** Zero-tolerance — immediate response required

**Symptoms:**
- `matrix_ethical_violations_total > 0`
- Audit log contains VETO events

**Response:**
1. Check audit log: Kafka topic `audit-log` or Loki: `{app="matrix-core"} |= "VETO"`
2. Review violating neuron: `kubectl exec -n matrix deployment/matrix-core -- curl -s localhost:9091/api/audit/latest`
3. Freeze violating neuron cluster: HADES self-healing protocol
4. Notify Ethics Committee (per CODE_OF_CONDUCT)
5. Post-incident review within 24h

---

## RB07: OOMKilled

**Alert:** `HighMemoryUsage` (PrometheusRule)
**Severity:** Warning → Critical
**SLO Impact:** Pod restart → availability dip

**Symptoms:**
- `jvm_memory_used_bytes / jvm_memory_max_bytes > 0.9`
- Container restarts visible in `kubectl get pods`

**Response:**
1. Check heap: `jvm_memory_used_bytes{area="heap"}` vs `jvm_memory_max_bytes{area="heap"}`
2. Check neuron count: high neuron count → memory pressure
3. VPA should auto-adjust; if not, manually bump limits
4. If GC thrashing: add `-XX:+UseZGC` (already default)

**Long-term:** Review snapshot frequency; consider neuron offloading to disk.

---

## RB08: NetworkPartition

**Alert:** `ClusterUnstable` (PrometheusRule)
**Severity:** Critical

**Symptoms:**
- Pekko cluster members unreachable
- Split-brain detected by ConsensusEngine

**Response:**
1. Check network: `kubectl get endpoints -n matrix`
2. Check cluster membership: `matrix_cluster_members` metric
3. Identify partitioned nodes: compare member lists across pods
4. Consensus engine should auto-resolve via majority; if stuck, restart minority partition pods

---

## Escalation Matrix

| Level | Who | When |
|-------|-----|------|
| L1 | On-call developer | Any alert firing |
| L2 | SRE lead | Alert > 15 min unresolved |
| L3 | Project lead | Alert > 30 min or Critical severity |
| L4 | Ethics Committee | RB06 (EthicalFilterViolation) |

## Post-Incident Protocol

1. Create GitHub issue with `incident` label
2. Document timeline, root cause, resolution in issue
3. Update relevant runbook if gaps found
4. Add regression test to `SafetyPropertiesTest`
5. Review at next weekly SRE sync
