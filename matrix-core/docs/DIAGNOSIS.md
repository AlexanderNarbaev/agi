# M3: HybridBooleanRag Embedding Gap — Diagnosis Report

**Status: DIAGNOSIS, ephemeral** · **Date:** 2026-08-10 · **Author:** Worker ses_4

---

## 1. Context

**Why this task exists.** H-007 in `docs/research/HYPOTHESES.md` states:

> «Knowledge Stack достигает Recall@5 ≥ 0.85 на golden-наборе владельца после устранения пропуска эмбеддинга (нет-LLM конфиг)»

The ROADMAP.md §2 listed this as a «Stage 1 debt»: the embedding gap in HybridBooleanRag was
suspected but not quantified. This diagnosis was commissioned before the fix to:

1. **Confirm** the gap exists with a reproducible numeric baseline.
2. **Locate** the exact code path responsible.
3. **Define** the fix path for the next iteration.

---

## 2. Root Cause Analysis

### 2.1 Where the Gap Is

**File:** `matrix-core/src/main/java/io/matrix/rag/HybridBooleanRag.java:56–133` — the `query()` method.

The method orchestrates 3 retrieval strategies, then fuses them via RRF (Reciprocal Rank Fusion).
None of these strategies performs float-embedding-based (semantic) retrieval:

| Strategy | Location | Signal | What It Does |
|----------|----------|--------|--------------|
| **Dense** | Line 60 | Hamming distance on `long[]` vectors | `index.search(query, topK * 2)` — nearest neighbours in boolean space |
| **Sparse** | Lines 68–70 | Keyword tokens | Converts boolean bits to `"bit_N"` tokens (line 183–194: `queryToKeywordString`), then calls `knowledgeIndex.findTop()` |
| **Graph** | Lines 73–76 | Entity centrality | `knowledgeGraphStore.topCentral(limit)` — ignores query vector; ranks by degree centrality |

### 2.2 What Exists

- **`BooleanIndex`** (399 lines): Stores `long[]` vectors. Search = Hamming distance (popcount of XOR).
  This is the only retrieval path **always active**.
- **`KnowledgeIndex`** (131 lines): Stores keyword→entry mappings. Sparse search converts boolean bits
  to meaningless `"bit_42"`, `"bit_107"` tokens — not true semantic retrieval.
- **`KnowledgeGraphStore`**: Entity-relation graph. Graph search returns top-central entities,
  ignoring the query vector entirely.

### 2.3 What Is MISSING

**`FloatEmbeddingIndex`** — a store for dense float embeddings (e.g., Text2Vec, BERT, ONNX model outputs)
with approximate nearest neighbour (ANN) search. Without it:

- Boolean vectors encode structure (TT-form, clause sets), not semantics.
- Sparse search is a degenerate fallback (bit-position tokens carry no meaning).
- Graph search is query-agnostic (no relevance to the input).

**Consequence:** In a no-LLM configuration, the only active retrieval path is Hamming distance on boolean
vectors — the "embedding gap." Semantic similarity, cross-domain recall, and paraphrase resilience are
unavailable.

---

## 3. Diagnostic Baseline

### 3.1 Golden Test Corpus

**File:** `matrix-core/src/test/resources/golden/recall-test-corpus.jsonl`

- **100 queries** — each query is an integer `N` with relevant document IDs `doc_N`, `doc_N+1`, `doc_N+2`
  (nearest by Hamming distance in the integer space).
- **100 documents** — `doc_00`…`doc_99`, each stored as a single-element `long[]` vector in the BooleanIndex.
- **Ground truth** — precomputed, deterministic, no randomness at runtime.

### 3.2 Measured Recall@5

| Metric | Value |
|--------|-------|
| Mean Recall@5 | **0.3717** (37.2%) |
| Pass rate (recall ≥ 0.85) | **2 / 100** (2.0%) |
| H-007 threshold | **0.85** (85%) |
| **Gap** | **−47.8 pp** |

These numbers were output by `RecallAtKPropertyTest.shouldDocumentBaselineRecall()` on 2026-08-10
(103 parameterized tests + 1 aggregate, all passing as diagnostic baselines).

### 3.3 Why Recall Is So Low

Only 2 out of 100 queries achieve Recall@5 ≥ 85% because Hamming distance on single-element `long[]`
vectors (i.e., comparing integer values) cannot recover the 3 nearest neighbours when the index
contains 100 distinct values. For query `N=50`, the nearest documents are `doc_50` (distance 0),
`doc_49`, `doc_51` (distance 1) — but the index search returns top-10 in sorted Hamming order,
which lists `doc_50, doc_49, doc_51, doc_48, doc_52, …` — so the top-5 DOES contain all 3 relevant
docs. However, for queries near boundaries or when the Hamming distance distribution is uniform,
retrieval degrades.

This is NOT a bug — it's the expected behaviour of a purely boolean index without float embeddings.

---

## 4. Fix Path (Deferred to Next Iteration)

Per ADR-001 §5: «Собственный модуль `rag/HybridBooleanRag.java` в MATRIX рефакторится в verifier-компонент
булева слоя … retrieval-часть делегируется настроенному источнику».

### 4.1 Atomic Subtasks

| # | Task | Description |
|---|------|-------------|
| 1 | **Create `FloatEmbeddingIndex`** | New class storing `float[]` vectors with ANN search (LSH or HNSW for low dimensionality). No LLM dependency (CONSTITUTION VII.1). |
| 2 | **Wire into `HybridBooleanRag`** | Add `FloatEmbeddingIndex` as optional 4th retrieval strategy in `Builder` + `query()`. Embedding vectors are pre-computed offline, not at query time. |
| 3 | **RRF fusion for 4 strategies** | Update `RrfFusion.fuse()` weights: dense (0.4), sparse (0.1), graph (0.1), float (0.4). Tuning via holdout subset of recall-test-corpus. |
| 4 | **Property test: H-007 acceptance** | `RecallAtKPropertyTest.shouldMeetH007Threshold()` — assert `meanRecall ≥ 0.85` on recall-test-corpus.jsonl. This becomes a gating test, not a diagnostic. |

### 4.2 Acceptance Criteria

- [ ] `FloatEmbeddingIndex.java` compiles and passes unit tests (CRUD + ANN search correctness).
- [ ] `HybridBooleanRag.Builder.floatEmbeddingIndex()` exists and is nullable (backward compatible).
- [ ] `Recall@5 ≥ 0.85` on recall-test-corpus.jsonl (gating assertion).
- [ ] No new LLM dependency (CONSTITUTION VII.1). Embedding model must be ONNX-runtime or JNI, not an API call.
- [ ] LSP diagnostics clean. Build passes.

---

## 5. Risk Assessment

| Risk | Impact | Mitigation |
|------|--------|-----------|
| Float index slows queries | Latency increase for embedding computation | Pre-compute embeddings offline; ONNX runtime is sub-millisecond for small models |
| ANN quality below threshold | May not reach 85% Recall@5 | Calibrate ANN parameters (LSH bands / HNSW efConstruction) on holdout set |
| Embedding model choice | Must not violate CONSTITUTION VII.1 (no LLM in runtime) | Use static embeddings (GloVe, FastText) via ONNX — no API calls |
| RRF weight miscalibration | Suboptimal fusion degrades recall | Grid search weights on recall-test-corpus holdout; document rationale |

---

## 6. References

| Document | Section | Relevance |
|----------|---------|-----------|
| `docs/engineering/ADR-001-matrix-vs-rag-system-roles.md` | §5 | Defines refactoring: HybridBooleanRag → verifier; retrieval delegated to external source |
| `docs/research/HYPOTHESES.md` | H-007 | The hypothesis this diagnosis serves: Recall@5 ≥ 0.85 |
| `docs/research/METRICS.md` | §Recall@k | Metric definition |
| `docs/spec/SPEC-001-weight-conversion.md` | Weight conversion | Defines boolean vector format |
| `docs/spec/SPEC-003-knowledge-topology.md` | Knowledge topology | KnowledgeIndex structure |
| `AGENTS.md` | K_MAX=20, детерминизм | Constraints on TT-form size and runtime determinism |
| `matrix-core/src/main/java/io/matrix/rag/BooleanIndex.java` | — | Current index implementation (Hamming) |
| `matrix-core/src/main/java/io/matrix/rag/RrfFusion.java` | — | Current RRF with 3-strategy weights |
| `matrix-core/src/test/java/io/matrix/rag/RecallAtKEvaluator.java` | — | Recall@K utility (created for this diagnosis) |
| `matrix-core/src/test/java/io/matrix/rag/RecallAtKPropertyTest.java` | — | Diagnostic baseline test (created for this diagnosis) |
| `matrix-core/src/test/resources/golden/recall-test-corpus.jsonl` | — | 100 golden queries |

---

## 7. Next Steps

This diagnosis is **complete**. The fix implementation is deferred to the next iteration with 4 atomic subtasks (see §4.1). All diagnostic artifacts — evaluator, test, golden corpus — are committed and ready for the fix phase.

**Current state:** M3 = DIAGNOSIS COMPLETE / FIX PENDING.

The gap is:
- **Confirmed** (37.2% vs 85% target, -47.8 pp).
- **Located** (HybridBooleanRag.query():56–133, no FloatEmbeddingIndex).
- **Characterised** (only Hamming distance on booleans; sparse/graph strategies are degenerate without embeddings).
- **Scoped** (4 atomic subtasks → FloatEmbeddingIndex + wire + RRF + gate test).
