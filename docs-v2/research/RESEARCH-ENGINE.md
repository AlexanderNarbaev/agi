# MATRIX Research Engine — TRUE-W11

> Per META-R doctrine (AGENTS.md R-A..R-F), the MATRIX mind never stops
> harvesting cross-disciplinary algorithms. Each iteration is a single
> unit: pick a domain → prototype → benchmark vs incumbent → promote or
> reject with numbers → archive.

## Iteration Protocol

For each research iteration:

1. **Pick a domain** (queue below — never pick two in the same iteration).
2. **Write DESIGN note** at `docs-v2/designs/DESIGN-NN-<slug>.md` with:
   - Algorithmic core (1 paragraph)
   - Why it might help MATRIX (link to W1..W10 gaps)
   - Implementation sketch (≤30 lines pseudo-code)
3. **Prototype** in `matrix-core/src/main/java/io/matrix/experimental/`
   behind `-Pexperimental=true` Gradle property.
4. **Benchmark** vs incumbent on the fixed eval battery
   (`docs-v2/research/MATRIX-EVAL-BATTERY.md`).
5. **Promote or reject** with measured numbers in the design note.
6. **Record failures** in `docs-v2/archive/failures.md` (do not delete).
7. **If promoted**: integrate into MindCycle behind capability levels
   (DESIGN-58 L0..L23).

## Research Queue (seed from META-R)

| # | Domain | Source | Status |
|---|--------|--------|--------|
| 1 | Free Energy Principle minimization | R-A SOTA ML | ⏳ |
| 2 | Neuromodulatory RL (dopamine gating) | R-D neuroscience | ⏳ |
| 3 | Analog computing primitives | R-E physics | ⏳ |
| 4 | DNA-storage compression schemes | R-E chemistry | ⏳ |
| 5 | Immunological negative-selection for anomaly detection | R-E biology | ⏳ |
| 6 | Category-theoretic memory mappings | R-F math | ⏳ |
| 7 | Mycelium/stigmergy routing | R-E biology | ⏳ |
| 8 | Chinese-school symbolic methods (Nyaya inference) | R-C | ⏳ |
| 9 | Soviet cybernetics (Anokhin acceptor action) | R-B | ⏳ |
| 10 | Sparse-HDC extensions (Million-bit codes) | R-F math | ⏳ |
| 11 | MPDT induction improvements | R-F math | ⏳ |
| 12 | Thermodynamic bounds on learning | R-E physics | ⏳ |
| 13 | Graph-neural memory indexing | R-A SOTA ML | ⏳ |

## First iteration (executed in this wave)

### DESIGN-NN: Sparse-HDC via winner-take-all hashing
- **Source**: META-R-F (math of creativity)
- **Why**: HDC bit-cosine can be sparse — only `k` of `D` bits need be set
  per vector. Reduces memory by 10x while preserving cosine similarity.
- **Implementation**: `WtaHash.hash(text, dim=10000, k=64)` returns a
  BitSet with only 64 bits set; same cosine math.
- **Benchmark vs incumbent**: PersistedHdcStore with full 10000-bit
  vector vs WTA sparse version. Target: <5% fidelity loss, ≥50% memory
  reduction.
- **Result (this wave)**: design drafted; implementation deferred to
  next iteration.

## Disk-budget enforcement

Per TRUE-W0 disk-guard utility:
- Refuse a research run if projected artifact size > free−10 GB.
- Archive / compress outputs when free < 25 GB.

## Failure archive

`docs-v2/archive/failures.md` accumulates rejected designs with
measured numbers. Do not delete; the failure log is how the mind
learns what NOT to try.
