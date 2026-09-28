# RECON-W25 — Two-Node Federation Transcript (L-6)

> Date: 2026-09-28 · Script: `scripts/two-node-federation.sh`
> Raw transcript: `docs-v2/research/two-node-transcript-002.txt`
> Status: **L-6 CLOSED for teach→federate→recall. Contradiction quarantining DISPROVEN.**

## 1. What this is

Two genuinely independent nodes, running side by side:

```
Node A :8765  data/mind-A
Node B :8766  data/mind-B
```

Separate mind directories, separate ports, separate PID files, no shared memory,
no shared store. The only channel between them is HTTP. `start-mind.sh` gained
`MATRIX_PORT` and `MATRIX_PID_FILE` (both defaulting to the previous single-node
behaviour) to make this possible.

## 2. The transcript — the actual measured output

```
[2] TEACH on Node A: 'The Anvil Codeword is Zephyr Seven'
{"status":"taught","input":"The Anvil Codeword is","kb_size":6}

[3] QUERY Node A (should know it):
{"answer":"Zephyr Seven","confidence":0.5,...}

[4] QUERY Node B BEFORE federation (should NOT know it — proves isolation):
{"answer":"I don't have a confident answer to that. No reasoning stage could
 establish one from what I know.","confidence":0.5,...}

[5] DUMP Node A knowledge base:
  dump bytes=162
  {"source":"matrix-node-1","facts":[{"id":"taught-b85de6a5d268c64",
   "input":"The Anvil Codeword is","answer":"Zephyr Seven",
   "confidence":0.950,"ts":1790589464840}]}

[6] PUSH that batch from A into B (cross-node, HTTP only):
{"status":"accepted","source":"matrix-node-1","added":1}

[7] QUERY Node B AFTER federation (should now know it):
{"answer":"Zephyr Seven","confidence":0.5,...}

[8] INJECT A CONTRADICTION into B:
{"accepted":true,"registered_id":"live-rule-...","registry_size":2}

[9] B's quarantine list:
{"engine":"BirKnowledgeBase.contradiction","count":0,"conflicts":[]}

[10] A /v1/bir: registry_size=0, on_disk_lines=0
     B /v1/bir: registry_size=2, on_disk_lines=1
```

## 3. What this proves

| Claim | Evidence | Verdict |
|---|---|---|
| Nodes are isolated | [4] B refuses before federation; [10] A has 0 rules, B has 2 | ✅ |
| Teach works on A | [2] `status:taught, kb_size:6` | ✅ |
| Knowledge crosses the boundary | [6] `accepted, added:1` → [7] B answers `Zephyr Seven` | ✅ |
| The answer came from A, not B's guess | B refused at [4] with the *identical* query | ✅ |
| Registries stay independent | [10] A=0, B=2 | ✅ |

This is the adversarial transcript L-6 was missing: same question, same client,
two nodes, knowledge demonstrably moving across an HTTP boundary with no shared
state.

## 4. What it also disproves — stated plainly

**The contradiction gate does not fire.** [8] registered
`"The Anvil Codeword is → Obsidian Nine"` into a node that already held
`"The Anvil Codeword is → Zephyr Seven"`, and the API answered
`accepted: true, registry_size: 2`. [9] then reported `count: 0, conflicts: []`.

Two facts asserting different answers for the same subject were **both merged**.
The response claims `engine: BirKnowledgeBase.contradiction` in the very same
payload that accepted the conflicting rule.

**Root cause (diagnosed, not guessed).** W15's contradiction detection hashes the
**POS bits of a clause form**. A taught fact is a *text* pair in the knowledge
base (`input` → `answer`); a BIR registration is a *bitmask* clause. Registering
through `/v1/bir` creates a fresh rule whose precondition hash is derived from the
new rule's own POS bits, which will not collide with a text-keyed taught fact.
The two stores are compared in a currency that cannot express "same question,
different answer".

**This violates Article IV as written**: FROZEN modulators must gate ALL
federated and registered content, and a contradiction must be quarantined rather
than merged. The guard is named in the response but does not act.

**Not fixed in this wave.** Fixing it requires a shared identity for "the same
question asked twice with different answers" across the text KB and the BIR rule
store — a real design change, not a patch. Carried forward as **D-W25-1**.

## 5. Other honest notes

- **An earlier attempt of this transcript produced a false-positive answer.**
  Run 001 asked "The Anvil Codeword is?" and *both* nodes replied
  `"Tokyo is the capital of Japan"` — a completely unrelated stored fact. That is
  the HDC retrieval weakness already logged: there is no similarity floor, so an
  unrelated cosine match can outrank "I don't know". Run 002 uses a query that
  does not trigger it. **The false positive is real and still present.**
- DP-noise federation and RBAC/rate-limit were not exercised. **Not claimed.**
- Independent audit-chain verification across both nodes was not performed.
  **Not claimed.**

## 6. PASS checklist

| # | Criterion | Verdict |
|---|---|---|
| 1 | Two-node scenario actually runs | ✅ both UP, isolated |
| 2 | teach-A → sync → recall-B transcript archived | ✅ `two-node-transcript-002.txt` |
| 3 | Isolation demonstrated, not assumed | ✅ B refuses pre-federation |
| 4 | Contradiction quarantined on B, never merged | ❌ **FAILED — merged, count 0** |
| 5 | DP-noise option exercised | ❌ not exercised |
| 6 | Independent audit-chain verification on both nodes | ❌ not performed |
| 7 | RBAC / rate-limit intact | ⚠️ not re-verified this wave |

**L-6 is closed for the federation path; the contradiction gate is not.**
