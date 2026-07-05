📍 Status: v2.3.2 — Pretrain from large open-source models. pretrain_neurons.py updated with --quantize flag, architecture auto-detection. New pretrain_large.py for >10GB models with mmap/disk swapping. Qwen2.5-0.5B pretrained (720 neurons, 24 layers).
🚀 Active: Try larger models (Qwen2.5-1.5B/3B/7B), MoE extraction (Mixtral, DeepSeek-R1), integrate with Minecraft bot
🛑 Protected: Pekko 1.6.0, K_MAX=20, FROZEN-neurons, Quarkus 3.36.1, Java 25, AGPLv3+ethics, Three Prohibitions, 82% floor

### 2026-07-05: OpenAI Chat API (L13 Pilot #2)
- Created OpenAI-compatible `/v1/chat/completions` + `/v1/models` endpoints
- Text2VecService: text↔20-bit binary vector for MPDT I/O
- EthicalFilter integration: blocks L7-violating content with `content_filter` reason
- HADES circuit breaker: resets brain on 10+ consecutive identical responses
- Models: mpdt-smollm2 (pretrained), mpdt-qwen (random)
- 31 tests (17 resource + 14 text2vec), all passing
- Commit: b8b19ad

### 2026-07-06: K8s Operator & Deployment enhancement (L9)
- Enhanced MatrixClusterReconciler: PVC, Service, health probes, env vars, resource limits, status conditions
- Added 3 new tests (Service, PVC creation, conditions) — 18/18 pass
- Created flat K8s manifests: `infra/k8s/namespace.yaml`, `-deployment.yaml`, `-service.yaml`, `-hpa.yaml`, `kustomization.yaml`
- Updated Dockerfile: added `--start-period=60s` to HEALTHCHECK
- Appended Quick Start with minikube section to `docs/L9_Deployment.md`
- Commit: d59e60a
