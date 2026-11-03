# W44 — Algorithm portfolio map

**Audit:** RECON-W44.0 · 2026-11-02
**Evidence:** `W44-CLASS-INVENTORY.csv`, `W44-CALL-GRAPH.csv` (bytecode), the portfolio
sources, and the W44 benchmark measurements.

---

## 1. The two things called "the portfolio"

| | the W44 portfolio | `io.matrix.neuron` |
|---|---|---|
| size | 8 methods, 4 families | **181 types** |
| selection | declared preconditions + measured cost | none — the caller picks |
| reachability | served (`POST /v1/cognitive/solve`, the kernel's SELECT) | 8 types have production callers |
| measurement | the W44 benchmark | none with a denominator |

**The repository has no portfolio; it has a library.** The W44 portfolio is the first
component in the repository that *chooses* between algorithms on measured evidence, and
the audit's opinion is that this is the right thing to have built — with the caveats in
§5.

---

## 2. The four families, as they exist

| family | methods | exactness | precondition | reachable how |
|---|---|---|---|---|
| `arithmetic.exact-integer` | `IntegerArithmeticMethod` over `ExactIntegerArithmetic` (`BigInteger`) | exact | `WELL_FORMED_EXPRESSION` | portfolio only |
| `reasoning.deductive-entailment` | `EntailmentMethod` over the existing `io.matrix.knowledge.EntailmentChecker` | exact | `THEORY_WITHIN_ATOM_BUDGET` | portfolio only |
| `graph.shortest-path` | `DijkstraShortestPathMethod`, `BellmanFordShortestPathMethod`, `BoundedUniformCostSearchMethod` | exact / exact / bounded-incomplete | `NON_NEGATIVE_WEIGHTS`, `BOUNDED_RESULT_ACCEPTED` | portfolio **and** the W44 solver endpoint |
| `collection.membership` | `LinearScanMembershipMethod`, `TrieMembershipMethod`, `BloomFilterMembershipMethod` | exact / exact / probabilistic | `INDEX_PREBUILT` | portfolio only |
| `procedure.reuse` | `ProcedureMethod` over a promoted `Procedure` | as validated | the procedure's own `requiredFacts` | the W44 kernel's fast path |

---

## 3. The precondition problem, and how the portfolio solved it

`io.matrix.neuron.Dijkstra`'s javadoc says "non-negative weights" and **the code never
checks `e.weight()`**. The portfolio wrapper checks it and **refuses even when the
caller asserts the fact** — the selector's precondition check and the method's own check
are separate, and the method's wins.

This is the single most valuable thing the portfolio does, and the audit measured its
consequence: `Dijkstra` has exactly **one** production caller in the whole repository —
`DijkstraShortestPathMethod`. The precondition is therefore enforced everywhere it is
used.

**What is still unenforced:** `BellmanFord`'s negative-cycle detection surfaces as an
`AlgorithmRefusalException`, and the W44 `CognitiveSolver` converts that refusal into an
answer that says *"No shortest path exists"* rather than a finite distance.

---

## 4. The measured trade-offs (from the W44 benchmark)

| comparison | measurement | selection |
|---|---|---|
| linear scan vs a trie over 64 queries | scan 761.8 ns/query, trie 341.1 ns/query after a 121138.5 ns build → the index repays itself after **288** queries; the corpus asks 64, so end-to-end the scan is **2.9× cheaper** | the selector picks the **trie**, because `INDEX_PREBUILT` is the caller's assertion |
| Dijkstra vs Bellman-Ford (120 vertices, 360 edges, seed 42) | p50 158299 ns vs 245283 ns → **1.83×** | Dijkstra, at equal quality (1.0 over 12 cases) |
| exact search vs a bounded search | the bounded search that *finishes* is slower (581630 ns mean); the one that *gives up* under a node budget is **5.5× faster** and returns `complete=false` | the bounded method only when the request carries `BOUNDED_RESULT_ACCEPTED` |

**The scan-versus-index result is the one to remember.** The folklore says an index wins;
at this scale it loses end to end by 2.9×, and the selector — which sees only the
per-call cost — still picks the index. The benchmark prints both numbers side by side.

---

## 5. The portfolio's own limitations, as measured

1. **No amortisation of a preprocessing cost.** The selector compares per-call costs;
   the build cost is the caller's precondition. Honest, but it moves the decision to
   whoever writes the caller.
2. **The accuracy evidence is not shrunk by its denominator.** A method with 1/1 evidence
   scores the same as one with 500/500. This is hypothesis **H-114** in the research
   registry.
3. **The declared-cost fallback is a convention.** `DeclaredCost`'s growth table
   (`O(1)` → 1 ns … `O(2^n)` → 1 ms) orders methods nobody measured; the decision names
   the basis per candidate (`MEASURED` vs `DECLARED_COMPLEXITY`).
4. **Only 4 of the 181 `io.matrix.neuron` types are in the portfolio.** The rest of the
   library is untouched, and the audit confirms the library is not reachable as a
   portfolio — it is a set of building blocks.
5. **Two of the four families are reachable only through the portfolio API**, not through
   a user request: the arithmetic and entailment families. The shortest-path family is
   reachable from `POST /v1/cognitive/solve`; the membership family is not reachable from
   any request at all.

---

## 6. What the audit recommends

1. **Keep the precondition discipline and apply it to the rest of the library.**
   `io.matrix.neuron.Dijkstra` is used through the wrapper; nothing stops the next caller
   from using it directly.
2. **Shrink the accuracy estimate by its denominator** (H-114) before any learned policy
   is considered — otherwise the learned policy will learn the same artefact.
3. **Measure the library, not just the portfolio.** 181 types with no measured cost and
   no declared preconditions is the largest unexploited asset in the repository.
4. **Do not connect the portfolio to a path that has not been measured.** The W44 wave
   deliberately wired the shortest-path family to a request and left the others behind;
   that is the right order.

---

## 7. The algorithms that are exact, approximate and heuristic — the audit's list

| kind | types |
|---|---|
| exact | `BooleanRuntime`, `TruthTableAdapter`, `ClauseSetForm`, `BddForm`, `Dijkstra`, `BellmanFord`, `ExactIntegerArithmetic`, `EntailmentChecker`, `Trie` |
| probabilistic | `BloomFilter` (declared floor 0.95) |
| approximate | HDC (`HdcBrain`, `SparseHDC`), `ContentSimilarity` token overlap, `AnalogyStage`'s seed table |
| learned | `BirClassifier`, `Tsetlin*`, `MpdtGaProducer`, `BitLinearTrainer`, `BitNetAutoregressive` |
| heuristic | `StubBrainCycle` (keyword matching), `GpuTaskExecutor` (echoes its input), `SignalStage` (FNV-1a) |
