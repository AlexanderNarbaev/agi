# MATRIX — WORK LOG

**Status:** `CANONICAL` · **Date:** 2026-11-04
**This is the one authoritative chronological work log.** Every wave of the project is listed
here with its record; the record itself lives in the document this index points at.

**The rules of the log:**

- **Historical records are immutable.** A wave's record is never rewritten to make the
  history look cleaner. A correction is **appended as a correction**, and it names the
  original.
- **The log distinguishes the events.** *Planned · started · implemented · tested · verified ·
  committed · pushed · released* are **different events**, and a wave is not "done" until the
  last one that applies to it has happened. A wave that is committed but not pushed says so.
- **A wave with no release is not a release.** There is **no release process** in this
  repository; the tags `v1.0.0` and `v16.0.0-mind` predate three waves of work and no
  version is current.

---

## 1. The recent waves — the ones with a full record

| the wave | the date | the commits | the objective | the record |
|---|---|---|---|---|
| **RECON-W44.0** | 2026-11-02 → 2026-11-04 | `ae6c017b` | the bytecode-level archaeology: what MATRIX actually is, module by module, class by class | `docs-v2/engineering/RECON-W44-ARCHAEOLOGY.md` and the eleven W44 maps beside it; the status is `docs-v2/engineering/RECON-W44-STATUS.md` |
| **W45.0** | 2026-11-04 | `eae6003f` | the cognitive kernel's integrity: a durable contradiction disposition, an immutable episode history, an enforced cycle budget, a durable regulatory state | `docs-v2/engineering/W45-STATUS.md`; the design is `W45-INTEGRITY-DESIGN.md`; the decisions are `W45-DECISION-LOG.md` and `W45-OWNER-DECISIONS.md` |
| **W45.1** | 2026-11-04 | `509d23ba` | crash consistency: a request survives the death of its process, and an adjudication survives a repeated observation | `docs-v2/engineering/W45.1-STATUS.md`; the design is `W45.1-CRASH-CONSISTENCY-DESIGN.md`; the matrix is `W45.1-RECOVERY-MATRIX.csv` |
| **W45.2** | 2026-11-04 | `a6ded233` | the owner's four architecture decisions: a named entry point per application, an isolated chain boundary, a fail-closed reload, one writer per database | `docs-v2/engineering/W45.2-STATUS.md`; the decisions are `W45.2-OWNER-DECISIONS.md`; the boundary is `W45.2-CHAIN-BOUNDARY.md`; the ADR is `docs-v2/engineering/ADR-W45.2-01-canonical-entry-point.md` |
| **W46** | 2026-11-04 → | **uncommitted — see below** | the consolidation: make the project explain its own state, its history, its constraints, its decisions and its next steps | `docs-v2/engineering/W46.0-FINAL-REPORT.md` (phase 0), `W46.1-STATUS.md` (phase 1), `W46.2-STATUS.md` (phase 2), `W46-FINAL-REPORT.md` (the final report and the bootstrap block) |

### The state of each event, for the recent waves

| the wave | implemented | tested | verified | committed | pushed | released |
|---|---|---|---|---|---|---|
| RECON-W44.0 | yes | yes | yes | yes (`ae6c017b`) | **yes** | no |
| W45.0 | yes | yes | yes | yes (`eae6003f`) | **yes** | no |
| W45.1 | yes | yes | yes | yes (`509d23ba`) | **yes** | no |
| W45.2 | yes | yes | yes | yes (`a6ded233`) | **yes** | no |
| W46 | phases 0–3 yes | yes | yes | **no** | **no** | no |

**W46 is deliberately uncommitted.** The W46 directive forbids a commit or a push until the
owner reviews the final report and authorises it. The wave's work therefore lives in the
working tree, and this index says so rather than implying a commit that did not happen.

**The four phases of W46 and what each one produced:**

| the phase | the objective | the report |
|---|---|---|
| **W46.0** | the read-only audit: the actual architecture, the topology, the persistence, the capability portfolio, the hardware, the risks, the open questions | `docs-v2/engineering/W46.0-FINAL-REPORT.md` and the ten audit artefacts beside it |
| **W46.1** | the canonical governance structure: eleven documents, one subject each, plus eleven compatibility pointers | `docs-v2/engineering/W46.1-STATUS.md` |
| **W46.2** | the reconciliation: the links, the index, the README, the work log, the history, the `AGENTS.md` proposal, and the tooling that keeps it true | `docs-v2/engineering/W46.2-STATUS.md` |
| **W46.3** | the autonomous engineering infrastructure: the skill inventory, the handoff template, the hardware profile, and the gates that hold it all | `docs-v2/engineering/W46-FINAL-REPORT.md` |

---

## 2. The historical waves — W352 … W469

**These are recorded as a range, not as 320 rows.** Each wave has its own document in this
directory, and the directory is the record.

| the range | the period | the documents | what they are |
|---|---|---|---|
| the build-out phase | 2026-09-11 → 2026-09-18 | the `WAL-0*.md` files | the CLI tools, the conversation store, the container work, the monitoring |
| the federation phase | 2026-09-18 | `SESSION-W347-W351-summary.md` and `wave-347-protobuf-schema-dynamic-modulators.md` | the summary of the federation phase |
| the last phase | 2026-09-19 | the `WAL-1*.md` … `WAL-6*.md` files | the ModelDownloader, the streaming server, the Docker helper, the conversation CLI family, the per-wave verification notes |

**The three rows are a phase guide, not a partition** — the `WAL-<n>` files carry a wave
number, and the numbers run from **1 to 650** with gaps. **318** of the files are named
`WAL-<n>.md` and two are summaries; the file list is the census.

**321 documents in this directory: 320 wave records and this index.** The records are named
`WAL-<number>` (the numbers run from the low hundreds to 650) plus two summaries; a wave is
found by its number. Their status is `HISTORICAL` — they are the record of what was done at
the time, and they are **not** rewritten.

**The commits.** The history holds **1573 `WAL:` commits** — the wave records are also in
the git history, which is where a reader looks for "what changed and when". The commit
message carries the one-line summary; the document carries the detail.

**Two things a reader must know about this range:**

1. **The wave numbers are not the wave identifiers of the recent waves.** W45.0 is not
   "wave 450" — the numbering restarted. The recent waves are identified by their own
   `W45.x` / `W46` names, and their records are in `docs-v2/engineering/`, not here.
2. **The test counts in these documents are from their own moment.** They are **not**
   baselines. The baseline lives in `docs-v2/engineering/TEST-CATALOGUE.yml`, and it is
   **commit-specific**.

---

## 3. The record structure

Every wave record carries the same fields, so a reader can compare two waves:

| the field | the question it answers |
|---|---|
| the wave id and the date | when |
| the starting and the ending commit | what it changed |
| the objective | why |
| the decisions made | what was settled, and by whom |
| the source changes | what code moved |
| the documentation changes | what documentation moved |
| the tests run | the exact commands and the exact denominators |
| the tests **not** run | the honest boundary |
| the benchmarks | the methodology and the environment |
| the failures | what went wrong, including the ones that were fixed |
| the negative results | what was refuted, and at what cost to repeat |
| the migrations | the schema versions, and whether they are re-runnable |
| the risks | what it made riskier |
| the unresolved questions | what it did not settle |
| the next recommended step | where the next session starts |

**The last row is the one that matters most for the next agent.** It is what makes the
project resumable without the previous agent's memory.

---

## 4. Where the wave records live

| the family | the directory | the count | the status |
|---|---|---|---|
| the recent wave records (W44, W45.x, W46) | `docs-v2/engineering/` | the `W4*.md` files | `ACTIVE` |
| the historical wave records | `docs-v2/waves/` (this directory) | **320** | `HISTORICAL` |
| the pre-v2 archive | `docs-v2/_raw/` | 282 | `ARCHIVED` — **never modified** |
| the archaeology maps (W44) | `docs-v2/engineering/` | 13 documents + 5 CSV/JSON | `HISTORICAL` — the evidence of the W44 audit |
| the local journal | `wal/SESSION_WAL.md` | — | **local only** — the directory is in `.gitignore`, so it is not part of the project's history |

**The complete document registry** — every document, with its category, its status, its
currency and its successor — is `docs-v2/engineering/W46-DOCUMENT-AUDIT.csv` (880 rows, one
per document outside the pre-v2 archive).

---

## 5. How a wave is added to this log

1. The wave produces its **record document** — `docs-v2/engineering/W<N>-STATUS.md` — with
   every field of §3.
2. The **decision register** is updated if the wave decided anything
   (`docs-v2/governance/DECISION-REGISTER.md`).
3. The **roadmap** is updated: the wave's task moves to *done*, and its successor is named
   (`docs-v2/governance/ROADMAP.md`).
4. The **project state** is **replaced**, not appended to — it is a snapshot
   (`docs-v2/governance/PROJECT-STATE.md`).
5. The **risk and open-question registers** are updated
   (`docs-v2/governance/RISK-REGISTER.md`, `docs-v2/governance/OPEN-QUESTIONS.md`).
6. **This log** gets one row, and the row says whether the wave is committed, pushed and
   released — because those are three different facts.

**The correction rule.** If a wave's record turns out to be wrong, the record is **not**
edited: the correction is appended to it, dated, and it references the original statement.
This log's rows may be corrected the same way.
