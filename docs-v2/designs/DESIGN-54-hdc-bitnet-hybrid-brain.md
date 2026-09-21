# DESIGN-54 — HDC × BitLinear × Boolean Brain Hybrid

**Status:** v1 design. Wave W31 (cross-disciplinary synthesis). Реализуется в RUN 437+.

## 1. Мотивация

Однотрубная HDC-memoria Kanerva (1988) и BitNet b1.58 Microsoft (2024) по отдельности
показали практичность в edge-AI (миллиард параметров на CPU, контраст с десятками GB FP16).
Что **никто** не сделал: объединить HDC-binding + Boolean-table substrate + BitLinear aggregation
в единый reproduce-on-CPU brain simulator.

То, что получается, не есть **AGI** — это **hybrid edge-AI brain** с:
- Sparse binary 1024-bit memory (HDC).
- 2-bit ternary aggregation (BitLinear).
- Tables-of-truth для логического inference (Boolean).
- Hebbian local learning (cybernetics школа Anokhin + современные NCA).

## 2. Архитектура одного такта

```
INPUT (binary or HDC-encoded concept)
    │
    ▼
[ENCODER: Table → HDC vector]   ← HdcEncoding.java (RUN 437)
    │
    ▼
[PREV STATE ⊕ INPUT via XOR]   ← HdcBinding.java (RUN 438)
    │
    ▼
[BUNDLING: majority over ⊕-ed roles]   ← ∈ HdcBinding.java
    │
    ▼
[CLEANUP MEMORY: k-NN against codebook]   ← CodebookMemory.java (RUN 440)
    │ uses HammingNative.ffm for ~21K pops/sec
    ▼
[BITLINEAR aggregate to ternary weights]   ← BitLinear.java (RUN 439)
    │
    ▼
[HEBBIAN WRITE: OR-store new prototype OR shift existing]   ← HebbianUpdater.java (RUN 441)
    │
    ▼
OUTPUT (binary or HDC-decoded)
```

## 3. API классов

```java
public final class HdcEncoding {
    public static long[] tableToSparsecod(int k, java.util.BitSet table, int N);
    public static long[] xor(long[] a, long[] b);
}

public final class HdcBinding {
    public static long[] xor_shift(long[] a, long[] b);  // circular shift before XOR
    public static long[] bundle(long[]... roles);  // majority
    public static long[] permute(long[] v, int shift);
}

public final class BitLinear {
    public static short[] absmean_quantize(float[] W);  // {-1, 0, +1}
    public static float[] subln(float[] x);  // normalise before quantise
    public static float[] forward(short[] Wq, float[] x);  // W·x with int8 MAC
}

public final class CodebookMemory {
    public static int lookup(long[] query, long[][] codebook);  // Hamming-NN
    public static void write(long[] prototype);
    public static double[] distances(long[] q);
}

public final class HebbianUpdater {
    public static void update(short[] weights, long[] activation, float reward);
}
```

## 4. Использование существующих 78 классов

| Класс | Как применяется |
|---|---|
| `HammingNative.hamming(long,long)` (RUN 416) | FFM-based popcount, ~10x faster than Java `Long.bitCount`, используется в `CodebookMemory` |
| `KdTree.nearest(double[], int)` | Альтернатива `CodebookMemory.lookup` для плотных (не двоичных) векторов |
| `BloomFilter` | Дедупликация прототипов в codebook |
| `HyperLogLog` | Cardinality estimation для codebook size |
| `TokenBucket` | Rate limiting для sequence-of-lookups |
| `BellmanFord` | Reasoning through state-space search (optional) |
| `HopfieldAssociator` | Один слой cleanup (energy-based) |
| `KauffmanNetwork` | Boolean dynamics для emergent computation |
| `HdcBrainLayer` (новый, RUN 442) | Интеграция предыдущих в brain component |

## 5. Capability Levels (Sokolov-Spilke-Anokhin-Bernstein)

Capability Levels 0-6 — когнитивно-релевантные milestones с ceiloso-mathematical proofs:

- **Level 0 — Fabric**: 78 classes + 24-block chain. (у нас есть)
- **Level 1 — Pavlov**: "stimulus A → response B" с trial-to-trial шумом. Тест: novel A → predicts B.  (1 неделя)
- **Level 2 — Spelke**: 4 core knowledge (object permanence, agent, numerosity, goal). (2-3 недели)
- **Level 3 — Cross-modal HDC**: audio → HDC bind → visual. Cleanup memory retrieves visual from audio query. (1 неделя)
- **Level 4 — Piaget 3rd substage**: action → outcome → feedback → Hebbian update. (3-4 недели)
- **Level 5 — Symbol grounding**: arbitrary names ↔ referents. Pinker-style. (4-6 недель)
- **Level 6 — Compositional reasoning**: 2-hop chains (X→Y, Y→Z, predict Z from X). (6-12 недель)

Capacity math (Kanerva): `M_max ≈ 2^N / N`. For N=1024 → ~10^308 patterns addressable. For our codebook of 100K patterns → ~17 MB.

## 6. HDC-as-LLM-Preprocessor

**Идея**: длинный контекст (100K messages) сжимается через HDC в 1024-bit vector. LLM получает top-K nearest compressed code as дополнительный контекст.
- Compression ratio: 100K × 1KB → 100KB text  →  1024-bit code  ≈ **800× compression**.
- Retrieval speed: `HammingNative.hamming` ~21K pops/sec → ~21K candidates/sec scanned.

Benchmarks target vs Mem0/MemGPT:
- Memory footprint: 100x уменьшение для 10M memories.
- Retrieval latency: O(N×Hamming-SIMD) vs O(N×float-cosine). 

## 7. BitNet b1.58 детали (формулы, которые наш `BitLinear.java` реализует)

```
absmean:
W̃ = RoundClip(W / (γ + ε), -1, 1)
γ  = (1/nm) · Σ_{ij} |W_{ij}|
RoundClip(x, a, b) = max(a, min(b, round(x)))

SubLN before quant:
LN: x' = (x - μ) / σ         (μ, σ per-row)
y  = W̃ · x'_act

Act quant (8-bit):
x_act = Clip(x · Q_b / ‖x‖_∞, -Q_b+ε, Q_b-ε),   Q_b = 2^(b-1)
```

Per BitNet b1.58 paper (arxiv 2402.17764):
- 3B BitNet b1.58 = LLaMA 3B at **3.55× less memory**, **2.71× faster inference**.
- **71.4× less arithmetic energy** на 7nm hardware.

## 8. Когда запускать эту реализацию

- У нас уже есть `HammingNative` (FFM-based popcount).
- У нас есть `KdTree` для cleanup.
- У нас есть 78 классов, которые этими компонентами интегрируются.

**Timeline:** 8-10 weeks → publication-grade demo with video.

## 9. Failure modes / risks

- **Capacity collapse**: codebook of >100K vectors hits KdTree O(log N) asymptote. Mitigate: cascaded codebooks by category.
- **Noise accumulation**: HDC not clean bit-error-correcting for >30% flips. Bound empirically.
- **Hebbian instability**: weights drift if reward signal noisy. Mitigate: decay >0, normalize periodically.

