# W44 — Perception and representation map

**Audit:** RECON-W44.0 · 2026-11-02
**Evidence:** the encoder sources themselves, `W44-CLASS-INVENTORY.csv`,
`W44-CALL-GRAPH.csv`, and the test-run logs of this audit.

---

## 1. The pipeline as it is

```
request text
   │
   ├─ SignalStage.encode ──────────► per-token FNV-1a 32-bit hash, mod DIM (256)
   │                                   ("Pure hashing of tokens. No LLM. No wall-clock.")
   │
   ├─ TextEncoder.encode ───────────► SHA-256 digest → 256 BOOLEAN bits
   │
   ├─ TextEmbedder.embed ──────────► Qwen2.5-0.5B ONNX hidden states, mean-pooled, 896 floats
   │                                   ⚠ requires models/hf_cache/qwen05b — ABSENT in this checkout
   │
   ├─ HdcRetrievalStage.retrieve ──► token-overlap similarity (ContentSimilarity) + a floor
   │
   └─ CrossModalAligner.align ─────► float vector → float vector, via FeatureExtractor
```

---

## 2. What each representation actually is

| representation | what it is | invertible? | collisions |
|---|---|---|---|
| `SignalStage`'s observation | **FNV-1a 32-bit hash per token**, reduced mod 256 | no | 32-bit hash into a 256-slot space: collisions by construction |
| `TextEncoder`'s vector | the **bits of a SHA-256 digest** | no | cryptographic (none in practice) |
| `TextEmbedder`'s vector | mean-pooled ONNX hidden states | no | the only **semantic** representation in the repository |
| `HdcRetrievalStage`'s score | **token-overlap** similarity with a retrieval floor | n/a | lexical, not semantic |
| `CrossModalAligner`'s output | a float vector aligned into another modality's space | no | depends on the extractor |

**The finding that matters.** `TextEncoder` is documented as
*"text → bytes → SHA-256 hash → fixed-size boolean vector"*. A SHA-256 digest is a
**fingerprint, not a representation**: two different sentences have unrelated vectors, and
two spellings of the same sentence have unrelated vectors. Any "similarity" computed over
these bits is similarity of *hashes*, which is to say, equality.

**And the semantic path is not available in this checkout.** `TextEmbedder` reaches into
`OnnxRuntimeAdapter`'s private `session` field by reflection and runs the Qwen ONNX
model; the test logs of this audit show
`QwenOnnxBridge: tokenizer load failed: vocab.json and merges.txt required at
models/hf_cache/qwen05b`, and the CUDA provider fails with
`undefined symbol: cudaLibraryGetKernel, version libcudart.so.12`.

**Therefore the serving path's notion of "similar" is lexical token overlap**, and the
architecture documents that describe "multimodal understanding" describe a capability
whose semantic half is not present in the repository.

---

## 3. The HDC memory is not a hypervector retrieval on the serving path

`io.matrix.neuron.HdcBrain` is a real hypervector machine: `learn(float[] features,
label)`, `forward(float[] features)`, `encodeFeatures(float[] features)` with a random
projection into hypervector space. It has 8 production callers and is used where a float
feature vector exists.

`HdcRetrievalStage` — the stage the brain runtime actually calls — does **not** feed it
float features. It calls `ContentSimilarity` over the persisted fact table, tokenising
the query against each fact, with a similarity floor and an explicit "ignorance" outcome
when nothing clears the floor. Its own comments record the optimisation history
(RECON-W32.17: scoring 2016 facts re-tokenised the query 2016 times).

**Consequence:** the repository contains a hypervector memory and a lexical retrieval,
and the serving path uses the second one. The word "HDC" in the stage name is a name, not
a description.

---

## 4. Multimodal alignment

`CrossModalAligner` takes a CDI `Instance<FeatureExtractor>` and aligns a float vector
from one modality into another's space. `UnifiedRepresentation` is a container of the
aligned vectors.

**What is preserved:** the geometry of the embedding space, for the extractors that
exist. **What is lost:** everything an extractor did not encode, and — for the text
modality — the semantic content itself, unless the ONNX embedder is present.

**The irreversible step is at the extractor boundary**, not at the aligner. The aligner
cannot recover information a feature extractor did not produce, and there is no decoder
anywhere in the repository that maps a unified representation back to a claim.

---

## 5. The signal-module family — what it really is

The directive names `SignalModule`, `SignalModuleRegistry`, `SignalRegistry`,
`TextSignalModule`, `ImageSignalModule`, `AudioSignalModule`. **They all exist**, in
`io.matrix.signals`, and the audit's earlier reading that they were absent was wrong.
The verified picture:

| type | state | what it is |
|---|---|---|
| `io.matrix.signals.SignalModule` | served (2 callers) | the interface: `modality()`, `version()`, `encode(Object)` |
| `io.matrix.signals.SignalModuleRegistry` | served (2 callers) | registers modules by modality and version; `modalities()`, `listModules()`, `unregister` |
| `io.matrix.signals.SignalRegistry` | island (1 caller: `DefaultSignals`) | a second registry — **two registries coexist** |
| `io.matrix.signals.TextSignalModule` | served (1 caller: `TrueMindCycle`) | "encodes text into a 64-bit signal vector… character-level hashing with positional mixing… **not a cryptographic hash**" |
| `io.matrix.signals.ImageSignalModule` | **inactive** (0 callers) | — |
| `io.matrix.signals.AudioSignalModule` | **inactive** (0 callers) | — |
| `io.matrix.signals.SensorPacket` | island (1 caller) | — |
| `io.matrix.perception.TextEncoder` | served (7 callers) | SHA-256 → 256 boolean bits |
| `io.matrix.api.TextEmbedder` | served (1 caller) | the Qwen ONNX mean-pooled embedding — **model files absent** |
| `io.matrix.multimodal.FeatureExtractor` | served (1 caller) | the extractor's interface |

**Two registries and three text representations.** `SignalRegistry` and
`SignalModuleRegistry` both exist and both are reachable; `TextSignalModule` (FNV-1a with
positional mixing into 64 bits), `TextEncoder` (SHA-256 into 256 bits) and `TextEmbedder`
(the only semantic one) are three different encodings of the same input.

**And the served `TextSignalModule` is constructed and registered but its encoding is
deliberately not used.** `TrueMindCycle` instantiates it, registers it in the registry,
and then writes in a comment:

> *"Use SignalStage.encode (NOT textSignal) — TextSignalModule returns [bit positions].
> SignalStage does per-token FNV-1a matching."*

So the served path registers a signal module and then routes around its output. That is a
fourth representation in play — the one `SignalStage` computes.

**`SignalProcessor` and `MultimodalExtractor` do not exist** under those names.

## 6. What the map forces

1. **Never write "semantic similarity" for the current pipeline.** The measurable
   statement is "token-overlap similarity with a floor".
2. **Decide whether the ONNX embedder is a dependency.** If the model files are not
   shipped, the semantic path is unavailable and every document that assumes it is stale.
3. **Either wire `BrainVision` or remove it.** It is the only class that could preserve
   pixel content and it has no caller.
4. **Keep the fingerprints labelled as fingerprints.** `TextEncoder` is a hash; calling it
   an encoder invites a reader to expect semantics it cannot deliver.
