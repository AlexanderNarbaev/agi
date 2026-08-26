# L10 — Monitoring, Observability, SRE

**Status:** normative · **Layer:** 10 (operations) · **Date:** 2026-08-26
**Changelog:** 2026-08-26 — brain wave v2; densified from archive copy
archive/2026-08-pre-v2/docs-root-flat/L10_Monitoring.md.

## 1. Scope

L10 specifies how the running system is measured, traced, and
operated. Three pillars — metrics, logs, traces — are mandatory on
every component. Alerts are symptom-based, not cause-based.

## 2. SLIs and SLOs

Indicators per service: availability (successful / total requests),
latency (p50 / p95 / p99), throughput (signals per second),
accuracy (held-out probe), error rate (5xx share).

Targets: NeuronClusterActor (inference) — availability 99.9 %, p95
< 10 ms, accuracy > 99 %. Mediator API — 99.5 %, p95 < 100 ms.
Kafka brokers — 99.95 %, produce p95 < 5 ms. MinIO — 99.9 %, GET
p95 < 50 ms. Error budget = 1 − SLO; on exhaustion, new releases
block until stability is restored.

## 3. Collection Stack

Metrics — Quarkus endpoint `/q/metrics` in Prometheus exposition
format; Prometheus server retention 15 days; long-term in Thanos or
VictoriaMetrics (optional). A `ServiceMonitor` is created
automatically by the Operator for every component.

Logs — structured JSON: timestamp, level, logger, message, traceId,
spanId, instanceId, neuronId when applicable. Shipped via Fluent
Bit / Promtail to Loki with S3 (MinIO) backing. Retention 30 days
for debug, up to one year for security audit.

Traces — Quarkus + OpenTelemetry, OTLP export to Jaeger or Grafana
Tempo; trace context propagated through gRPC / Kafka headers.

## 4. Alerting

Symptoms. Examples: NeuronClusterDown — up == 0 for 1 m,
critical. HighErrorRate — 5xx > 1 % over 5 m, critical. LowAccuracy
— accuracy < 0.8 for 10 m, warning. HADESTriggered — count > 0,
critical. EthicalViolation — increase(violations[5m]) > 0,
critical. Warning band: p95 latency > 50 ms for 10 m; disk > 80 %;
consumer lag > 1000. Routing via Alertmanager to chat, PagerDuty /
Opsgenie for critical, and the issue tracker for auto-ticketing.

## 5. Incident Management

Three severities. SEV1: full outage of a critical service. SEV2:
partial degradation. SEV3: non-critical issue with measurable user
impact. Response: detect → acknowledge → escalate after 15 m on
SEV1 → mitigate via runbook or auto-reaction → blameless postmortem
within 48 h. Auto-reactions: re-run Cauldron on LowAccuracy (if
policy permits), pod restart on NeuronClusterDown with HADES
fallback, client-side circuit breaker on HighErrorRate.

## 6. Dashboards

Operational dashboard (Grafana): active neurons, clusters, FNLs,
system version, signals/s, batch-packets/s, latencies, error share,
HADES counts, ethical checks, per-pod resources. Accuracy / evolution
dashboard: FNL accuracy history, mutation accept / reject counts,
Cauldron runs. Dashboards ship as JSON and load via ConfigMap or
the Grafana Operator.

## 7. Operator Integration

The Matrix Operator (DESIGN-08) creates a ServiceMonitor, a default
PrometheusRule, a logging label, and an OpenTelemetry Collector
sidecar for each instance. New instances arrive under monitoring
without manual steps.

## 8. SRE Practices

Runbooks live under `docs/runbooks/` with one page per alert and a
deterministic procedure. Periodic chaos drills (Chaos Mesh or
Litmus): random pod kill, network partition, Kafka throttle —
validate HADES, circuit breaker, and buffering. Post-deploy,
ethical unit tests run in CI (L8); failure triggers automatic
rollback.

## 9. Cross-Reference

SLO numbers above are starting points; final values come from
DESIGN-08 and the JMH gates in DESIGN-04 / DESIGN-14.

Next: L11 governance — cell management, snapshots, decision rights.
