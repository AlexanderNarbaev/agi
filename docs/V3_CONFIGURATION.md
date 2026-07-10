# v3.0 Configuration Reference — MATRIX

**Версия:** v3.0
**Дата:** 2026-07-10

---

## Обзор

MATRIX v3.0 introduces 5 new cognitive components, each with environment variable configuration. This document describes all v3.0 configuration options, their defaults, valid ranges, and performance tuning guidance.

---

## Environment Variables

### Boolean Reasoning Chain (BRC)

| Variable | Default | Range | Description |
|----------|---------|-------|-------------|
| `BRC_MAX_STEPS` | `5` | 1–20 | Maximum reasoning steps in a BRC chain. Higher values allow deeper reasoning but increase latency. |
| `BRC_CONVERGENCE_THRESHOLD` | `2` | 1–10 | Number of consecutive identical outputs before early stopping. Lower = more aggressive convergence. |

**Tuning guide:**
- **Low latency:** Set `BRC_MAX_STEPS=3`, `BRC_CONVERGENCE_THRESHOLD=1`
- **Deep reasoning:** Set `BRC_MAX_STEPS=10`, `BRC_CONVERGENCE_THRESHOLD=3`
- **Default (balanced):** `BRC_MAX_STEPS=5`, `BRC_CONVERGENCE_THRESHOLD=2`

**Source:** `io.matrix.reasoning.BrcChain`

---

### Boolean RAG (Retrieval-Augmented Generation)

| Variable | Default | Range | Description |
|----------|---------|-------|-------------|
| `RAG_TOP_K` | `5` | 1–50 | Number of knowledge vectors retrieved per query. Higher = more context but more memory. |

**Tuning guide:**
- **Minimal context:** `RAG_TOP_K=3` — fast, low memory
- **Rich context:** `RAG_TOP_K=10` — comprehensive but slower
- **Default (balanced):** `RAG_TOP_K=5`

**Source:** `io.matrix.rag.BooleanRag`

---

### VQ-VAE Proxy

| Variable | Default | Range | Description |
|----------|---------|-------|-------------|
| `VQVAE_CODEBOOK_SIZE` | `256` | 64–4096 | Number of codebook entries. Larger = finer quantization but more memory. |

**Tuning guide:**
- **Low memory:** `VQVAE_CODEBOOK_SIZE=64` — coarse quantization
- **High precision:** `VQVAE_CODEBOOK_SIZE=1024` — fine-grained encoding
- **Default (balanced):** `VQVAE_CODEBOOK_SIZE=256`

**Memory impact:** Each codebook entry stores a `dimension`-sized double vector. With default dimension=8:
- 64 entries → ~4 KB
- 256 entries → ~16 KB
- 1024 entries → ~64 KB

**Source:** `io.matrix.vqvae.VqVaeProxy`, `io.matrix.vqvae.CodeBook`

---

### MCTS-Guided Evolution

| Variable | Default | Range | Description |
|----------|---------|-------|-------------|
| `MCTS_ITERATIONS` | `100` | 10–10000 | Number of MCTS search iterations per evolution step. Higher = better mutations but slower. |

**Tuning guide:**
- **Fast evolution:** `MCTS_ITERATIONS=50` — quick exploration
- **Thorough search:** `MCTS_ITERATIONS=500` — exhaustive mutation search
- **Default (balanced):** `MCTS_ITERATIONS=100`

**Performance:** Each iteration runs Selection → Expansion → Simulation → Backpropagation. Simulation depth is configurable in code (default: 5 random mutations).

**Source:** `io.matrix.mcts.MctsTree`

---

### Agent Loop

| Variable | Default | Range | Description |
|----------|---------|-------|-------------|
| `AGENT_MAX_ITERATIONS` | `1000` | 10–100000 | Maximum Observe→Think→Act cycles before forced convergence. |

**Tuning guide:**
- **Quick tasks:** `AGENT_MAX_ITERATIONS=100` — fast convergence
- **Long-running agents:** `AGENT_MAX_ITERATIONS=10000` — extended exploration
- **Default (balanced):** `AGENT_MAX_ITERATIONS=1000`

**Convergence detection (code-level):**
- `DEFAULT_CONVERGENCE_THRESHOLD = 5` — consecutive identical actions before declaring stuck
- Convergence reasons: `MAX_ITERATIONS`, `REPEATING_ACTION`, `TASK_COMPLETED`, `TASK_FAILED`, `MANUAL_STOP`

**Source:** `io.matrix.agent.AgentLoop`

---

## Configuration Examples

### Minimal (development)

```bash
export BRC_MAX_STEPS=3
export BRC_CONVERGENCE_THRESHOLD=1
export RAG_TOP_K=3
export VQVAE_CODEBOOK_SIZE=64
export MCTS_ITERATIONS=50
export AGENT_MAX_ITERATIONS=100
```

### Balanced (default production)

```bash
export BRC_MAX_STEPS=5
export BRC_CONVERGENCE_THRESHOLD=2
export RAG_TOP_K=5
export VQVAE_CODEBOOK_SIZE=256
export MCTS_ITERATIONS=100
export AGENT_MAX_ITERATIONS=1000
```

### Maximum quality (research)

```bash
export BRC_MAX_STEPS=10
export BRC_CONVERGENCE_THRESHOLD=3
export RAG_TOP_K=10
export VQVAE_CODEBOOK_SIZE=1024
export MCTS_ITERATIONS=500
export AGENT_MAX_ITERATIONS=10000
```

---

## Kubernetes Configuration

### Environment variables in Deployment

```yaml
env:
  - name: BRC_MAX_STEPS
    value: "5"
  - name: BRC_CONVERGENCE_THRESHOLD
    value: "2"
  - name: RAG_TOP_K
    value: "5"
  - name: VQVAE_CODEBOOK_SIZE
    value: "256"
  - name: MCTS_ITERATIONS
    value: "100"
  - name: AGENT_MAX_ITERATIONS
    value: "1000"
```

### Resource limits

```yaml
resources:
  limits:
    cpu: "2"
    memory: 2Gi
  requests:
    cpu: 500m
    memory: 512Mi
```

### Pretrained weights volume

```yaml
volumeMounts:
  - name: pretrained-weights
    mountPath: /app/models/pretrained
    readOnly: true
volumes:
  - name: pretrained-weights
    persistentVolumeClaim:
      claimName: pretrained-weights-pvc
```

---

## Performance Tuning

### Latency vs Quality Tradeoffs

| Scenario | BRC_MAX_STEPS | RAG_TOP_K | MCTS_ITERATIONS | AGENT_MAX_ITERATIONS |
|----------|---------------|-----------|-----------------|---------------------|
| Real-time (gaming) | 2 | 2 | 20 | 50 |
| Interactive (chat) | 5 | 5 | 100 | 1000 |
| Batch processing | 10 | 10 | 500 | 10000 |
| Research/analysis | 20 | 20 | 1000 | 100000 |

### Memory Estimation

| Component | Formula | Default Memory |
|-----------|---------|----------------|
| BRC | steps × vector_width × 8 bytes | ~400 bytes |
| RAG | topK × vector_width × 8 bytes + index | ~400 bytes + index |
| VQ-VAE | codebook_size × dimension × 8 bytes | ~16 KB |
| MCTS | iterations × tree_depth × node_size | ~500 KB |
| Agent Loop | max_iterations × state_size × 8 bytes | ~80 KB |

### JVM Tuning for v3.0

```bash
# Recommended JVM flags for v3.0
-Xmx2g -Xms512m
-XX:+UseZGC
-XX:+ZGenerational
--enable-preview
--add-exports java.base/sun.nio.ch=ALL-UNNAMED
```

---

## Monitoring

### v3.0 Metrics (Prometheus)

| Metric | Type | Description |
|--------|------|-------------|
| `matrix_brc_steps_total` | Counter | Total BRC steps executed |
| `matrix_brc_converged_total` | Counter | BRC convergence events |
| `matrix_rag_queries_total` | Counter | Total RAG queries |
| `matrix_rag_hits_total` | Counter | Total knowledge hits |
| `matrix_mcts_iterations_total` | Counter | Total MCTS iterations |
| `matrix_mcts_best_reward` | Gauge | Best MCTS reward found |
| `matrix_agent_ticks_total` | Counter | Total agent loop ticks |
| `matrix_agent_converged_total` | Counter | Agent convergence events |

### Grafana Dashboard

Import the v3.0 dashboard from `infra/grafana/dashboards/matrix-v3.json` for:
- BRC reasoning latency histogram
- RAG hit rate gauge
- MCTS convergence speed
- Agent loop tick rate

---

## Troubleshooting

### BRC not converging

- Increase `BRC_CONVERGENCE_THRESHOLD` (try 3–5)
- Check that input vectors are non-trivial (not all zeros)
- Verify NeuronLayer configuration

### RAG returning empty results

- Verify BooleanIndex has been populated
- Check `RAG_TOP_K` is ≥ 1
- Ensure query vector dimensions match index dimensions

### VQ-VAE encoding quality low

- Increase `VQVAE_CODEBOOK_SIZE` (try 512 or 1024)
- Check that training data is representative
- Verify dimension matches expected input size

### MCTS slow convergence

- Decrease `MCTS_ITERATIONS` for faster but less optimal results
- Check simulation depth (default 5)
- Verify reward function returns values in [0.0, 1.0]

### Agent Loop stuck

- Check `AGENT_MAX_ITERATIONS` is sufficient
- Verify sensor function returns valid data
- Check driver states are being updated
- Look for `ConvergenceReason.REPEATING_ACTION` in logs

---

## See Also

- [README.md](../README.md) — Quick start and overview
- [docs/INDEX.md](INDEX.md) — Knowledge base map
- [docs/MASTER_PLAN.md](MASTER_PLAN.md) — Development roadmap
- [infra/k8s/minikube/matrix-core.yaml](../infra/k8s/minikube/matrix-core.yaml) — K8s manifest

---

*End of V3_CONFIGURATION.md — v3.0, 2026-07-10*
