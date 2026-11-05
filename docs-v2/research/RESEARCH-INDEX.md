# RESEARCH-INDEX

**Status:** `CANONICAL` · **Date:** 2026-11-04 · **Wave:** W46.2 (task T3)
**Scope:** the index of the research body — the hypothesis registers, the experiment reports,
the corpus families, and the classification of the historical research.

---

## 0. What the research body is, and the separation rule

`docs-v2/research/` holds **190 files** (178 Markdown, 12 data files) in **four** families:
two hypothesis registers, 20 preregistration protocols, 62 experiment reports (+6 JSON
result files), and 6 syntheses — plus 90 top-level documents that are the wave summaries,
the deep-research syntheses and the topical reports (BitNet, edge AI, GPU/native-image,
federation scale, distillation, the cross-disciplinary doctrine).

The research body exists to do one thing: **make a claim measurable and then measure it.**
Everything else in it — the wave summaries, the syntheses, the topical reports — exists to
carry a measurement's result to the next reader.

> **The separation rule: a research proposal must never silently become a normative
> specification.**
>
> A hypothesis is a question with a preregistered gate. A specification is a contract with
> an owner. Moving from the first to the second requires a decision, a document, and a
> date — never the passage of time and the absence of objection.

> **The inverse rule: a research finding does not authorise a production integration.**
>
> A measured result on a synthetic corpus (the `refuted-toy` scope, `synthetic-scope`
> acceptance) says what happened on that corpus. It does not license a production path, and
> the W44 audit's "Wired vs. tested-only" table exists precisely because that line was
> crossed in the past.

**The companion index is `NEGATIVE-RESULTS-INDEX.md`** — the discoverable index of the
refuted and abandoned material. This document indexes the body; that one indexes the
negative evidence inside it.

---

## 1. The hypothesis registers

There are **two** registers. They are not versions of each other; they are two corpora with
two formats, and a third parallel register must not be created.

| the register | the file | the rows | the ids | the format | the status vocabulary |
|---|---|---|---|---|---|
| the older register | `docs-v2/research/HYPOTHESES.md` | **24** table rows, **21** of them carrying a status token | H-001…H-038: **23** ids mentioned, **19** of them declared as a row | free-text claim in the first column, a status word in the second, a pointer to the evidence in the third | `accepted (synthetic-scope)`, `refuted-toy`, `running`, `proposed` |
| the newer register | `docs-v2/research/HYPOTHESES-NEW.md` | **60** table rows, **20** of them carrying a status token | **90** ids mentioned, **53** declared as a row (H-039…H-091) | a five-column card: `ID` \| `One-line summary` \| `Methodology sketch` \| `Gate criterion` \| `Prereq`, with the verdict appended to the prereq cell | `accepted (synthetic-scope, RUN n)`, `running`, `proposed`, plus the Russian set `ПОДТВЕРЖДЕНА / ЧАСТИЧНО / ОТКРЫТА / НЕ ПОДТВЕРЖДЕНА` in the W31 sections |

**The status distribution, as the documents state it** (row-level count of an explicit
status token; the W44 audit's own note is that *"the status vocabulary is not uniform"* and
that a machine-readable status column *"would be the single highest-value addition to the
registry"* — `docs-v2/engineering/W44-HYPOTHESIS-AND-FAILURE-MAP.md` §1):

| status | `HYPOTHESES.md` | `HYPOTHESES-NEW.md` |
|---|---|---|
| `refuted-toy` | **3** | 0 |
| `accepted` | 1 | 5 |
| `running` | 8 | 13 |
| `proposed` | 9 | — |
| `partial` | — | 2 |
| **rows carrying a status** | **21 of 24** | **20 of 60** |

**The three refuted entries, and they are the only ones:** H-002, H-003, H-035 — all
`refuted-toy`, all in the older register. They are indexed with their measurements in
`NEGATIVE-RESULTS-INDEX.md` §2.1.

> **The rule on parallel registers.**
>
> `HYPOTHESES.md` and `HYPOTHESES-NEW.md` already coexist. **A third register must not be
> created.** A new hypothesis goes into `HYPOTHESES-NEW.md` as a card in the existing
> five-column format, in the section for its wave, with a preregistered gate and a prereq —
> and its measurement, when it is run, becomes an `EXP-*.md` report in
> `docs-v2/research/reports/`. The protocol that governs the lifecycle is
> `docs-v2/research/PROTOCOL.md`.
>
> Two rules from the same document matter when a card is added:
> *the target metrics are `proposed` until the preregistration, and may be tightened or
> loosened at preregistration*; and *no numerical characterisation of the system's behaviour
> that has not been measured* (`docs-v2/research/HYPOTHESES-NEW.md` §"Чего здесь НЕ
> утверждается").

**The two capability registries are a different thing and are not a third hypothesis
register.** `docs-v2/architecture/CAPABILITY-REGISTRY.md` and
`docs-v2/engineering/W44-CAPABILITY-REGISTRY.md` both use a *capability* vocabulary
(`VERIFIED / UNSOURCED / CORRECTED / REWORDED / REMOVED` and
`IMPLEMENTED / TESTED / SERVED / ISLAND / INACTIVE / …`); both are marked `SUPERSEDED`
(2026-11-04) in favour of `docs-v2/architecture/CAPABILITY-REGISTRY.yml`, and both are kept
as the evidence of what was claimed on 2026-10-26 and 2026-11-02 respectively.

---

## 2. The preregistration protocols

`docs-v2/research/protocols/` holds **20** `H-*.md` cards. Each fixes, before the
measurement, the metric, the accept gate, the refute gate, and the level at which the gate
applies.

| the card | the hypothesis it fixes | the verdict the card records |
|---|---|---|
| `H-002-clauseset-vs-ga.md` | CLAUSESET vs MPDT-GA | `refuted-toy` |
| `H-003-living-learner.md` | the GA producer vs Tsetlin | `refuted-toy` |
| `H-005-developmental-loop.md` | the developmental loop's solve-time | running |
| `H-006-guardrail.md` | the BIR-composition guardrail | running |
| `H-007-memory-stack.md` | Knowledge Stack Recall@5 | running |
| `H-009-distillation.md` | BIR/LLM parity, latency and energy | running (preliminary EXP-009B/C) |
| `H-010-wisard-vs-tsetlin.md` | WiSARD vs Tsetlin | `accepted (synthetic-scope)` |
| `H-011-sdm-reader.md` | the SDM reader vs a flat top-K baseline | running |
| `H-015-intes-network.md` | intESN vs float-ESN | running |
| `H-039.md` … `H-050.md` | the W31 subconscious/consent/federation/calibration cluster | mostly `proposed`; H-043, H-044, H-045, H-046, H-050 carry `accepted (synthetic-scope)` in the register |

**Why the protocols are the most valuable documents in the corpus.** The register says
*H-002 is refuted*; the protocol says *which three gates must be re-measured before it may
be re-opened, and on what corpus*. A refutation without its gates is an anecdote; a
refutation with its gates is reusable.

---

## 3. The experiment reports

`docs-v2/research/reports/` holds **68** `EXP-*` files: **62** Markdown reports and **6**
JSON result files (`EXP-MATRIX.8-baseline.json`, `EXP-MATRIX.8-boolean.json`,
`EXP-MATRIX.9-training.json`, `EXP-MATRIX.12-bitlinear-training.json`,
`EXP-MATRIX.13-full-bench.json`, `EXP-MATRIX.13-full-bench-10k.json`). These 68 are the
**only** `EXP-*` documents outside `docs-v2/_raw/` — the family is complete and lives in one
place.

The two naming families are distinct:

- **`EXP-0NN-report.md`** — the reports attached to a preregistered protocol (EXP-002,
  EXP-003, EXP-009, EXP-010, EXP-039…EXP-050). Their head carries a **status line**
  (`**Статус: refuted-toy**`, `**Статус: running → accepted (synthetic-scope)**`).
- **`EXP-MATRIX.NN-*.md`** — the wave reports (RUN-numbered, e.g. `EXP-MATRIX.27 — H-044
  calibration verification with real ECE (RUN 35)`). They do **not** carry a status line;
  their structure is `Hypothesis / Method / …`, and the verdict is carried by the register
  row that cites them.

**The honest count of a machine-readable status: 5 of 62** reports put a `Статус:` token in
their head (`EXP-002-report.md` `refuted-toy`, `EXP-003-report.md` `refuted-toy`,
`EXP-009-report.md` `running`, `EXP-010-report.md` `running → accepted`, and one more);
the other **57** do not. The status of a wave report is therefore **only** discoverable by
reading it or by following the register row that cites it. This is the same gap the W44
audit named for the registers, one level down.

### 3.1 The classification of the important reports

| class | the reports | what it means for a reader |
|---|---|---|
| **a reproducible result** | `EXP-009-report.md` (its GPU leg), `EXP-002-report.md`, `EXP-003-report.md`, `EXP-010-report.md`, and the `EXP-MATRIX.14/15/27/28/29` cards that the register records as `accepted (synthetic-scope)` with a named measurement | the number can be re-measured and will come out the same. **EXP-009C was re-measured on 2026-11-04** and came out at `gpuBatchMs=0.0208` against the retained `0.0200` and `gpuSingleUs=17.49` against the retained `17.25` — **within 1.4 %**, with torch 2.14.0+cu130 instead of the recorded 2.12.1+cu130. The retained numbers are in `docs-v2/research/reports/EXP-009-report.md` §EXP-009C and in `docs-v2/research/reports/EXP-MATRIX.2-report.md` |
| **a superseded result** | `docs-v2/architecture/CAPABILITY-REGISTRY.md` (the whole card set, superseded by `docs-v2/architecture/CAPABILITY-REGISTRY.yml`); the GPU-path reports `EXP-MATRIX.37-gpu-onnx.md` and `EXP-MATRIX.38-gpu-vs-cpu.md`; the "GPU unavailable" reading of EXP-009's CPU leg | the number was real and the interpretation has since been refuted. **The EXP-009 GPU numbers must not be generalised to the ONNX path**: EXP-009C was measured on **torch**, and the ONNX path in the Java stack could not load the CUDA provider at all (`docs-v2/research/reports/EXP-009-report.md`: *"CUDA-EP: «Failed to find CUDA shared provider» в Java-стеке → GPU-нога через Java недоступна"*). The torch result and the ONNX result are **two different experiments** and the report itself keeps them apart |
| **a result that cannot be reproduced on this host** | the native-image reports: `EXP-MATRIX.11-native-build-status.md`, `EXP-MATRIX.13-native-final.md`, `EXP-MATRIX.13-native-run18.md`, `EXP-MATRIX.34-native-status.md`, `EXP-MATRIX.35-hf-onnx-graalvm.md`, `EXP-MATRIX.36-native-attempt.md`; the `EXP-MATRIX.37` / `.38` GPU-path reports | GraalVM and Mandrel are absent on this host and the mandrel builder image is a multi-GB pull. `EXP-MATRIX.36-native-attempt.md` states the outcome plainly: *"None succeeded. JVM mode (Quarkus uber-jar, 155 MB) remains the production target."* The eight native-image test classes cannot run here |
| **a result about an external experiment** | `docs-v2/research/EXTERNAL-BENCHMARK-REPORT-W760.md`; the "real LLM slice" limb of `EXP-009`; `docs-v2/research/BENCHMARK-REPORT-W620.md`'s synthetic LLM ops/sec model | the subject is another system's published or modelled number, not a MATRIX measurement. `BENCHMARK-REPORT-W620.md` §4 compares against a **synthetic** LLM ops/sec model — which is exactly how the "33,000×" figure entered the corpus and why it was refuted (`NEGATIVE-RESULTS-INDEX.md` NR-015) |

**The one number to carry forward from this section:** EXP-009C reproduces, on the torch
path, to within 1.4 %. Everything else in the reports corpus is either scoped to a synthetic
corpus, superseded, or not reproducible on this host.

---

## 4. The research corpus, family by family

| the family | the size | its purpose | its status | where it is reachable from |
|---|---|---|---|---|
| `docs-v2/research/HYPOTHESES.md` + `HYPOTHESES-NEW.md` | 2 files | the two hypothesis registers | canonical | `docs-v2/INDEX.md` (rows naming `research/HYPOTHESES.md` and `research/HYPOTHESES-NEW.md`) |
| `docs-v2/research/protocols/` | 20 files | the preregistration cards — the gate criteria of each hypothesis | canonical | **not reachable** from the index; named only inside `docs-v2/vision/FINALSUMMARY.md` and `docs-v2/research/HYPOTHESES.md` |
| `docs-v2/research/reports/` | 68 files | the measurements — the evidence behind every accepted or refuted verdict | canonical | **not reachable**; `docs-v2/INDEX.md` names `research/reports/` as a directory, not its contents |
| `docs-v2/research/summaries/` | 6 files | the cross-wave syntheses (`W31-CROSS-DISCIPLINARY-SYNTHESIS.md`, `synthesis-W25.md`, `synthesis-W28.md`, `WAVE15-summary.md`, `INDEX-W20.md`, `GLOSSARY.md`) | mixed | only `W31-CROSS-DISCIPLINARY-SYNTHESIS.md` is named by `docs-v2/INDEX.md`; the other five are named by 1–3 documents elsewhere in `docs-v2/`, none of them the index |
| `docs-v2/research/` top level | 90 `.md` + 6 data files | the wave summaries (`RECON-W0`…`RECON-W25`), the deep-research syntheses (`W60-W64`, `W69-W72`, `W76`, `W80`, `W87-W99`, `W95`, `W103`), the topical reports (BitNet ×5, edge AI, GPU/native-image, federation scale, grand unification, civilisation, mind reports V17/V17.2, routing table, the MIND validation checklist, the cross-disciplinary doctrine), and the two registers | mixed | partly; the doctrine `MATRIX-CROSS-DISCIPLINARY-RESEARCH.md` is named by `docs-v2/INDEX.md` |
| `docs-v2/research/MATRIX-CROSS-DISCIPLINARY-RESEARCH.md` | 1 file, 67 KB | the **W31 doctrine** (META-R1…META-R5): the cross-disciplinary wave rule, the persistence rule, the primary-source rule, the timeout rule, the capability-level rule | canonical (it is the rule, not a result) | `docs-v2/INDEX.md` §Доктрина |
| `docs-v2/research/PROTOCOL.md` | 1 file | the hypothesis lifecycle protocol the registers cite | canonical | named by both registers; not by the index |
| `docs-v2/science/` | 5 files | the substrate models, the foundations, the goals and requirements, the open problems, the algorithm atlas index | canonical | `docs-v2/INDEX.md` names the family as a **brace set** (`science/{SUBSTRATE-MODELS,FOUNDATIONS,GOALS-REQUIREMENTS,OPEN-PROBLEMS,ALGORITHM-ATLAS-INDEX}.md`) — a notation, not a link, so no single file is reachable from it |
| `docs-v2/science-v2/` | 4 files | the second-generation science notes, including a **hypothesis list of its own** | **divergent** — see §5.2 | not from the index |

### 4.1 The reachability measurement

The corpus is **178** Markdown documents. Measured on 2026-11-04 with a script that walks
every `.md` outside `docs-v2/_raw/` and checks whether the document's basename appears in
it:

| | count |
|---|---|
| research documents in `docs-v2/research/` | **178** |
| named by at least one document outside `docs-v2/_raw/` | **95** |
| **named by nothing outside `docs-v2/_raw/`** | **83** |

The W46.0 audit reported the same problem as *"580 of the 849 `docs-v2` documents
unreachable from the index"* (`docs-v2/engineering/W46.0-FINAL-REPORT.md` §B.4) and the W46.2
brief cites *"98 of the 178 research documents are unreachable"*. The two numbers differ
because the measures differ: **83** is "named by no other document at all", which is
stricter than "not reachable from `docs-v2/INDEX.md`" by graph. Both are the same finding
— the corpus is not navigable — and the repair is task `TASK-W46-3` in
`docs-v2/governance/ROADMAP.md` §4. **This index does not repair the navigation**; it
makes the corpus describable in one place.

---

## 5. The classification of the historical research

### 5.1 The wave summaries

`docs-v2/research/` holds **18** `RECON-W*-REPORT.md` wave summaries: `RECON-W0`, `W1`,
`W2` (plus `W2-COMPLETE` and `W2-PARTIAL`), `W3-PART-A`, `W3-PART-B`, `W4`, `W5`,
`W13`…`W18`, `W20`, `W21`, `W22`, `W25`. The gaps are real: W6–W12, W19, W23, W24 are
absent from this directory.

**Their status is historical.** They are the record of what a wave believed and measured at
the time. They are **not** the current truth: `docs-v2/engineering/PLAN.md` — the current
plan — recorded its last wave as **W44** while W45 and W46 were in flight, and the W46.0
audit found the plan *"three waves behind"*
(`docs-v2/engineering/W46-ARCHITECTURE-TRUTH-MAP.md` §5). A wave summary is read as
*what was true then*, never as *what is true now*; the current truth is
`docs-v2/engineering/W46-ARCHITECTURE-TRUTH-MAP.md`,
`docs-v2/engineering/W46-REPOSITORY-TOPOLOGY.md` and
`docs-v2/engineering/W46.0-FINAL-REPORT.md`.

The research syntheses follow the same rule: `W60-W64-DEEP-RESEARCH-SYNTHESIS.md`,
`W69-W72-INTEGRATION-METRICS-SYNTHESIS.md`, `W76-EMPIRICAL-VALIDATION-REPORT.md`,
`W80-FINAL-SYNTHESIS-REPORT.md`, `W87-W99-FINAL-SYNTHESIS-REPORT.md`,
`W95-CYBERNETIC-RESEARCH-REPORT.md`, `W103-SOVIET-ASIAN-RESEARCH-REPORT.md`,
`W41-RESEARCH-A-LEARNING-SCIENCE.md`, `W42-RESEARCH-A/B/C-…`. Note that the two
synthesis files the index names under `research/summaries/` actually live at the **top level**
of `docs-v2/research/` — `docs-v2/INDEX.md` names `research/summaries/W60-W64-DEEP-RESEARCH-SYNTHESIS.md`,
which **does not exist**; the file is `docs-v2/research/W60-W64-DEEP-RESEARCH-SYNTHESIS.md`.

### 5.2 The duplicate-identifier problem inside the research corpus

**This is the most consequential thing in the corpus, and it is a correctness problem, not
a tidiness problem.**

Measured on 2026-11-04 (a script that collects every `H-NNN` declared as a heading or as
the first cell of a table row, in every `.md` outside `docs-v2/_raw/`, excluding the two
files this task created): **106** distinct ids are declared somewhere; **76** of them are
declared in **more than one file** and 30 in exactly one. The W46.2 brief named five of the
multi-file ones — `H-002`, `H-003`, `H-006`, `H-010` (in `research/protocols/` and in
`docs-v2/vision/FINALSUMMARY.md`) and `H-007` (in two files). The real set is larger, and
for **12** of them it is **not a duplicate mention — it is a different claim under the same
number.**

| id | the research corpus (`HYPOTHESES.md` + `research/protocols/`) | `docs-v2/science-v2/hypotheses.md` | the collision |
|---|---|---|---|
| **H-001** | LLM→boolean distillation fidelity ≥0.9, k≤12 — `running` | *"BIR Achieves Competitive Accuracy on Structured Logic ✅"* | **different claim** |
| **H-002** | CLAUSESET (Tsetlin) beats MPDT-GA — **`refuted-toy`** | *"HDC Shows Superior Sample Efficiency ✅"* | **different claim — and one is a refutation, the other a confirmation** |
| **H-003** | no 'living' learner justified vs Tsetlin — **`refuted-toy`** | *"BIR is Massively More Energy Efficient ✅"* | **different claim — same danger** |
| **H-004** | a Ricci fingerprint detects degradation before a Recall@5 drop — `proposed` | *"Federation Survives 50% Node Failures ✅"* | **different claim** |
| **H-005** | developmental loop cuts solve-time ≥30 % — `running` | *"Sleep Improves Accuracy and Reduces Memory ✅"* | **different claim** |
| **H-006** | BIR-composition guardrail FPR≤5 %@TPR≥95 % — `running` | *"Non-Linear Modulator Interactions Create Realistic Behavior ✅"* | **different claim** |
| **H-007** | Knowledge Stack Recall@5 ≥0.85 — `running` | *"Stigmergy Enables Emergent Cluster Formation ✅"* | **different claim** |
| **H-008** | BIR proof-memory batch mode ≥1000 units/tick — `proposed` | *"Causal Planning Improves Decision Quality 🔄"* | **different claim** |
| **H-009** | BIR-classifier parity ±3 pp, ≥10⁴× less energy — `running` | *"Federated Sleep Accelerates Convergence 🔄"* | **different claim** |
| **H-010** | WiSARD WNN vs Tsetlin — `accepted (synthetic-scope)` | *"Stigmergy Scales to 1000+ Nodes 🔄"* | **different claim** |
| **H-011** | SDM M1 read beats flat top-K — `running` | *"BIR Generalizes to k≥8 Variables 🅿️"* | **different claim** |
| **H-035** | EBL speeds convergence ≥2× — **`refuted-toy`** | *"EBL Outperforms BIR on XOR Tasks ❌"* | **the same claim, and the same negative verdict** ✓ |

`docs-v2/vision/FINALSUMMARY.md` is a **consistent mirror** of the research corpus: its
H-002/H-003 rows quote the same `refuted-toy` verdicts and the same measurements, and its
H-010 row quotes the same `accepted (synthetic-scope)`. It is a duplicate **of the research
corpus**, not a competitor to it. `docs-v2/science-v2/hypotheses.md` is the dangerous one:
it is a **second, independent numbering** in which the same ids carry different claims.

**The rule for reading them:**

1. **The research corpus owns the id space.** `docs-v2/research/HYPOTHESES.md`,
   `HYPOTHESES-NEW.md` and `research/protocols/` are the registers; an `H-NNN` means what
   they say it means.
2. **`docs-v2/science-v2/hypotheses.md` is a parallel, unlinked numbering.** Read its
   claims as *its own* claims; **never** resolve `H-002` in it to the register's `H-002`.
3. **`docs-v2/vision/FINALSUMMARY.md` is a mirror.** It is safe to read for the verdicts it
   quotes, but the register is the authority and the summary is undated in its body.
4. **A collision is not a duplicate to be deduplicated by deleting.** The documents are
   evidence; the repair is to give the second numbering a distinct prefix or to state the
   mapping explicitly — an owner decision, recorded in the governance documents, not an
   edit made silently by an index.
5. **The other 66 multi-file declarations are duplicates of one kind**: the id is declared
   in the register *and* quoted in the wave synthesis that reported it
   (H-051…H-060 in `MATRIX-CROSS-DISCIPLINARY-RESEARCH.md` and in
   `docs-v2/waves/WAL-012-w31-cross-disciplinary-research-wave-2026-09-11.md`; H-069…H-088
   in `W80-FINAL-SYNTHESIS-REPORT.md` and `W87-W99-FINAL-SYNTHESIS-REPORT.md`; H-089…H-091
   in `W103-SOVIET-ASIAN-RESEARCH-REPORT.md`; H-113…H-128 in
   `docs-v2/engineering/W44-HYPOTHESIS-AND-FAILURE-MAP.md`). These are benign — the
   synthesis quotes the card it reports — and they are the **correct** pattern: the
   register states, the report cites.

---

## 6. What a session should read

**Usually none of the 178 documents.**

The working set for an ordinary session is four files:

| the file | what it answers |
|---|---|
| `docs-v2/research/HYPOTHESES.md` + `HYPOTHESES-NEW.md` | what is claimed, and with which gate |
| `docs-v2/research/NEGATIVE-RESULTS-INDEX.md` | what was refuted, what was rejected, and what repeating it costs |
| `docs-v2/research/RESEARCH-INDEX.md` (this file) | where the corpus is, and how to read it |

**An individual report is read only when its claim is in play** — when a decision depends on
the number, when the claim is being re-opened, or when the register and the protocol
disagree. The full corpus is a library, not a checklist. Reading it end to end is the
documented anti-pattern that produced 83 documents nobody can find.

**Three rules of navigation:**

1. **Start from the register, not from the report.** The register states the verdict; the
   protocol states the gate; the report holds the numbers. Read in that order and the
   corpus stays small.
2. **Check the date.** A wave summary is what was true then. The current truth is in
   `docs-v2/engineering/W46-*.md`.
3. **A claim without a gate is not a claim.** `proposed` is not `running` is not
   `accepted`, and `refuted-toy` on a synthetic corpus is not a refutation of the idea —
   only of that measurement.

---

## 7. How this index was built, and what it does not claim

Every count in this document comes from a command run on 2026-11-04 against
`HEAD = a6ded233`, branch `develop`:

| the number | the command |
|---|---|
| 190 files / 178 `.md` / 6 subdirectory sizes | `find docs-v2/research -type f \| wc -l` and the per-directory equivalents |
| the 68 `EXP-*` files, all in `reports/` | `find docs-v2 -name "EXP-*" -not -path "docs-v2/_raw/*" \| wc -l` and `ls docs-v2/research/reports/` |
| the register row counts and status distribution | a Python pass over the table rows of each register, counting rows whose first matching cell carries a status token |
| 76 multi-file id declarations (106 distinct ids declared; 30 in exactly one file) | a Python pass over every `.md` outside `docs-v2/_raw/`, excluding the two files this task created, collecting the ids declared as a heading or as the first cell of a table row |
| 95 named / 83 named by nothing | a Python pass comparing each research document's basename against the contents of every `.md` outside `docs-v2/_raw/` |
| 15 `.tla` files, 14 gated | `find . -name "*.tla" -not -path "*/build/*" -not -path "./docs-v2/_raw/*"` and the id list of `formal/spec-implementation-map.yml` |
| the EXP-009C re-measurement | `.venv/bin/python scripts/bench_gpu_vs_bir.py models/teacher/teacher_ffn16.onnx` → `gpuBatchMs=0.0208 gpuSingleUs=17.49` against the retained `0.0200` / `17.25` |

**What this index does not claim.** It does not verify the measurements it indexes — it
indexes them. It does not normalise the status vocabulary, because the W44 audit was right
that a historical report is not to be rewritten. It does not repair the navigation, does not
resolve the duplicate identifiers, and does not change any status. It makes the corpus
**describable in one place** so that a future agent can find the evidence instead of
repeating the experiment.
