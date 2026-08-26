# L1 — MPDT Neuron (formal model)

**Status:** normative · **Layer:** 1 (compute element) · **Date:** 2026-08-26
**Changelog:** 2026-08-26 — brain wave v2; text densified from archive
copy archive/2026-08-pre-v2/docs-root-flat/L1_MPDT_neuron.md.

## 1. Definition

The **MPDT neuron** (McCulloch–Pitts Decision Tree Neuron) is the
atomic compute element. It generalises the 1943 threshold neuron by
replacing the linear step with an arbitrary k-ary boolean function,
encoded as a truth table or a decision tree.

Tuple: N = (id, k, F, S, W, meta).

| Field | Type | Note |
|---|---|---|
| id | NeuronId | UUID + generation counter |
| k | int | 1 ≤ k ≤ K_MAX (K_MAX = 20) |
| F | BooleanFunction | truth table or decision tree |
| S | NeuronState | lifecycle stage |
| W | WeightVector | w_i ∈ {1,2,3} |
| meta | Metadata | lineage, accuracy stats |

NeuronId.uuid is immutable across mutations; generation is
monotonically increasing on accepted mutations.

## 2. Lifecycle States

STABLE — deterministic LUT inference. LEARNING — soft decision
tree, differentiable. MUTATING — deterministic, active trial
phase. FROZEN — immutable; mutation rejected without external
cryptographic authorisation.

## 3. Boolean Function Representations

Truth table. Bit array T of length 2^k. Inference is a single LUT
lookup: T[pack(X)]. Memory: 8 KiB at k=16, 128 KiB at k=20.

Decision tree. Binary tree; internal nodes test bit `inputIndex ∈
[0, k)`; leaves carry the output bit. Depth ≤ k; no repeated bit
test on any root-to-leaf path. The tree compiles uniquely into a
table; the inverse is a minimisation problem (Quine–McCluskey,
Espresso, or genetic programming; see DESIGN-04).

## 4. Operations

Evaluate(X) — state-dependent. STABLE / FROZEN return a
deterministic bit via the LUT. LEARNING may return a probabilistic
output (soft tree, see §5).

Mutate — stochastic tree edit. Operators: FlipLeaf, SplitLeaf,
PruneTree, ChangeInput, SwapChildren, GrowSubtree (depth 1–3),
Crossover, CompressBranch. After a successful mutation the LUT is
recomputed and generation += 1.

Merge — combine N ≥ 2 same-fan-in neurons by a chosen boolean
operator (AND, OR, XOR, ...) over output bits; produce a minimal
tree for the resulting table; record parentIds.

Simplify — minimise the tree while preserving the table; exact or
approximate as budget allows.

## 5. Off-line Training

Training never executes inside runtime inference. Cycle:
initialise small tree / LUT → optional pre-training in LEARNING
with soft tree → anneal to deterministic → enter MUTATING →
evolutionary loop (mutate, evaluate on held-out set, keep best) →
STABLE → optional FROZEN. The transition from LEARNING to STABLE
removes all continuous artefacts; inference thereafter is strictly
boolean.

## 6. Invariants (machine-checked)

1. k ≤ K_MAX. 2. Table / tree consistency on every STABLE entry.
3. uuid constant; generation non-decreasing. 4. FROZEN neurons
reject every mutation and merge request. 5. STABLE / FROZEN
Evaluate is a deterministic function of X. 6. No redundant input
bit. Violators are simplified.

## 7. Serialisation

Apache Avro. STABLE / FROZEN form: id, k, state enum, truthTable
bytes, weights, metadata {createdAt, mutationCount, parentIds,
accuracyHistory}. A distinct LEARNING schema carries soft-tree
parameters; on snapshot a neuron is always materialised into its
LUT form.

## 8. Verification

Per neuron the runtime asserts: table length = 2^k; compiled tree
matches stored table; no extraneous inputs; FROZEN rejects write;
determinism on equal inputs. Tests live under TruthTable /
NeuronVerifier in matrix-core. The neuron is minimal but
Turing-complete in composition; its discrete substrate underpins
verifiability (DESIGN-04), compresses losslessly (DESIGN-10), and
admits genetic operators that keep the core boolean at runtime.

Next: L2 cluster and routing — how neurons compose into actors.
