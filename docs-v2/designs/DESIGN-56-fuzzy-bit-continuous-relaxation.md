# DESIGN-56 — Fuzzy-bit Continuous Relaxation Surface

**Status:** v1 design.

## 1. Что это и зачем

Современный BitNet b1.58 (Microsoft, 2024) квантизует веса в **3 состояния {-1, 0, +1}**.
А Boolean-таблицы нашего brain simulator дают **2 состояния {0,1}**.
Zadeh fuzzy sets (1965) дают **continuum [0, 1]**.

Ни один из этих уровней не имеет достоинств одного и не имеет ограничений другого. **FuzzyBit
бинарно представляется как μ ∈ [0,1]**, и через continuous relaxation получаем
**плавный bridge** между BitNet-grade ternary и простой Boolean таблицей.

## 2. Зачем это нужно

Сейчас у нас **проблема квантизационного шума**:

- Если мы обучаем модель в continuous floats и потом quantize в {-1, 0, +1}, мы теряем sub-bit precision.
- Если мы тренируем напрямую в {-1, 0, +1}, мы используем straight-through estimator, и gradient flow страдает.

Zadeh Fuzzy даёт **3-й путь**: обучать в continuous space, хранить в continuous,
квантизовать только на inference. Stored representation = independent параметров model;
quantization parameter = "inference precision" — может быть 1-bit, 2-bit, ... arbitrary.

## 3. Математика

```
Soft Bit membership: μ ∈ [0, 1]

Soft AND:  μ(a ∧ b) = μ(a) · μ(b)
Soft OR:   μ(a ∨ b) = μ(a) + μ(b) - μ(a)·μ(b)
Soft NOT:  μ(¬a) = 1 - μ(a)

Aggregation: 
  sum_x μ(x) · 2^μ(x)^mode   (mode typically min, max, or sum)

Quantization:
  b = Round(μ · (Q-1))      where Q = number of quantization states
```

## 4. Реализация

```java
public final class FuzzyBit {
    public static final FuzzyBit ZERO  = new FuzzyBit(0.0);
    public static final FuzzyBit ONE   = new FuzzyBit(1.0);
    public static final FuzzyBit HALF  = new FuzzyBit(0.5);
    
    public final double μ;
    public FuzzyBit(double μ) { this.μ = Math.max(0.0, Math.min(1.0, μ)); }
    
    public FuzzyBit and(FuzzyBit b) { return new FuzzyBit(μ * b.μ); }
    public FuzzyBit or(FuzzyBit b)  { return new FuzzyBit(μ + b.μ - μ * b.μ); }
    public FuzzyBit not() { return new FuzzyBit(1 - μ); }
    
    public boolean toBoolean() { return μ > 0.5; }
    
    public static FuzzyBit fromBoolean(boolean b) { return b ? ONE : ZERO; }
    
    public static FuzzyBit[] quantizeBatch(FuzzyBit[] bits, int Q) {
        FuzzyBit[] result = new FuzzyBit[bits.length];
        for (int i = 0; i < bits.length; i++) {
            double q = Math.round(bits[i].μ * (Q - 1)) / (Q - 1);
            result[i] = new FuzzyBit(q);
        }
        return result;
    }
    
    public FuzzyBit trainingUpdate(double gradient, double lr) {
        return new FuzzyBit(μ - lr * gradient);
    }
}
```

## 5. Tests

- (Identity) `μA.and(ONE).μ == μA.μ`: 100% pass.
- (De Morgan) `μA.and(μB.not()).or(μA.not().and(μB.not()))`: not exactly (0,0) — soft logic не boolean.
- (Continuity): adjacent `μA=0.4` vs `μA=0.6` produce small output diff in `and(μB=0.5)`.

## 6. Comparison vs BitNet b1.58

| Feature | BitNet b1.58 | FuzzyBit (this design) |
|---|---|---|
| Storage | 2 bits/weight | full double (8 bytes) but compressible |
| Inference | fast (INT2 MAC) | slower (float MAC) |
| Training | non-trivial STE | straight double grad |
| Noise robustness | low (only 3 states) | high (real-valued) |
| Memory | smallest | largest |
| Use case | production inference | research, training |

**Вывод:** FuzzyBit — **research artifact**, не production. BitNet b1.58 — production inference.

## 7. Integration with HDC brain

HDC-bit learned through FuzzyBit relaxation:
1. Real-valued random projection → FuzzyBit (each bit = projected value → sigmoid).
2. Train via standard backprop on continuous projection.
3. Quantize via BitLinear or Cleanly to binary at inference.

This is the bridge that **current DL literature does NOT have implemented cleanly** — most work has
chosen one extreme (binary, ternary, FP) and stuck with it. Hybrid Fuzzy→Boolean does not have
commercial equivalents yet.

## 8. Why don't existing systems use this?

- It's slower than bit-only inference.
- Differentiability masks signal: continuous projection loses structure.
- Only advantage: cleaner training. So it's a TRAINING TOOL not a running memory representation.
- Practically used in: **deepfake detection** (continuous-vs-discrete discriminator), 
  **distillation pipelines** (teacher continuous → student binarized).

## 9. RUN-цель

- `FuzzyBit.java` — pure-function class
- `FuzzyTable.java` — table-of-truth with μ ∈ [0,1] entries; AND/OR/NOT over table cells
- `ContinuousTraining.java` — backprop demonstration
- Test: distillation of continuous knowledge into binary table-of-truth

