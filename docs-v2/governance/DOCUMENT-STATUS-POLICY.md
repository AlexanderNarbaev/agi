# MATRIX — DOCUMENT STATUS POLICY

**Status:** `CANONICAL` · **Date:** 2026-11-04
**What it decides:** what the status of a document means, who may change a status, and what
an agent must do when it finds a document whose status no longer matches its content.

---

## 1. Why this policy exists

The W46.0 audit found **1162 Markdown documents** (880 outside the pre-v2 archive) and **three
different status vocabularies** in use, none of them defined anywhere:

- the architecture registry used the **provenance of a claim** (`VERIFIED`, `UNSOURCED`,
  `CORRECTED`, `REWORDED`, `REMOVED`) — which says where a sentence came from, not whether
  the thing it describes exists;
- the W44 registry used a **different** vocabulary (`IMPLEMENTED`, `TESTED`, `SERVED`,
  `ISLAND`, `INACTIVE`, `DORMANT`, `MEASURED`, `DEMONSTRATED`, `EXPERIMENTAL`, `PLANNED`);
- the documents themselves mostly carry **no status at all** — only 170 of the 880 carry an
  explicit status line.

**The cost of the ambiguity is measurable**: a reader could not tell which of two capability
registries was authoritative, and the root `README.md` pointed at a `WAL.md` that had been
renamed two years earlier. A status that is not defined is not a status; it is a guess.

---

## 2. The vocabulary

**Exactly these eleven values.** A document's status is **one** of them — never two, never a
combination, never a phrase.

| the status | what it means | the test that decides it | who may set it |
|---|---|---|---|
| `NORMATIVE` | the document **binds** the work: violating it violates the project | it states a rule, a prohibition or an invariant, and the project fails without it | the **owner** only |
| `CANONICAL` | the document is **the** authority for its subject — the one place that subject is written down | there is exactly one canonical document per subject, and the others point at it | the coordinator, by the owner's directive |
| `ACTIVE` | the document describes **current** work and is **maintained** | it has a named owner and is kept in step with the subject it describes | the coordinator |
| `REFERENCE` | the document is **consulted**, not maintained — it describes a subject that another canonical document owns | a reader would consult it and then be sent elsewhere | the coordinator |
| `RESEARCH` | the document records **evidence and findings**, not a commitment | it reports what was measured, observed or proposed, and it does not bind the implementation | the coordinator |
| `EXPERIMENTAL` | the document describes something that is **being tried** and may be abandoned | the work is explicitly an experiment, with a stated falsification condition | the coordinator |
| `HISTORICAL` | the document was **true at the time it was written** and records a past state | it describes a state that no longer exists, and it is kept as the record of that state | the coordinator |
| `SUPERSEDED` | the document was replaced by **another** one, which is named | a successor exists and the successor says so | the coordinator |
| `DEPRECATED` | the document is **obsolete but still in use** — it is not replaced, it is on its way out | a successor may not exist yet, and the document says it is on its way out | the coordinator |
| `ARCHIVED` | the document is **kept for the record** and is not part of the current structure | it is in an archive area, or it is explicitly marked as a dead end | the coordinator |
| `UNVERIFIED` | the document makes claims that **nobody has checked**, or its own status is not established | the content asserts something that no executed evidence supports | the coordinator |

### The three distinctions that the vocabulary makes explicit

1. **`HISTORICAL` is not `SUPERSEDED`.** A historical document was *true* and simply describes
   a past state — the W44 cycle map names five cycles because five were found that day. A
   superseded document was *replaced* — the architecture capability registry was replaced by
   the machine-readable one. **Neither is deleted.** The difference is whether a successor
   exists.
2. **`RESEARCH` is not `NORMATIVE`.** A research proposal must never silently become a
   specification. When a research finding is adopted, a **specification or a decision** is
   written, and the research document stays research.
3. **`ACTIVE` is not `CANONICAL`.** A document may be actively maintained and still not be
   the authority for its subject — the W45.2 wave report is active, but the architecture
   truth map is canonical for the topology.

---

## 3. The rules

1. **One subject, one canonical document.** If a second document starts to claim the same
   subject, one of them becomes `REFERENCE` and points at the other.
2. **A status is written in the document**, in the first fourteen lines, in a form the
   registry can read: a line beginning `**Status:**` or `Статус:` with the value in
   backticks or in capitals.
3. **A status is never set by the passage of time.** It changes only when the document's
   role changes, and then by an explicit edit with the reason.
4. **A `SUPERSEDED` document names its successor, and the successor names its predecessor.**
   The link runs both ways, so neither document can be read in isolation and mislead.
5. **A historical document is not rewritten to look current.** If it is wrong about the
   present, it stays wrong about the present — that is what makes it evidence. The correction
   is appended as a correction that references the original.
6. **The registry is generated, not maintained by hand.** `docs-v2/engineering/W46-DOCUMENT-AUDIT.csv`
   is the census; the link checker (TASK-W46-3) and the registry validator (TASK-W46-2) keep
   it honest. **A hand-maintained inventory of documents is exactly the failure this wave
   exists to remove.**
7. **The `NORMATIVE` set is tiny and enumerated.** At the snapshot: `CONSTITUTION.md`,
   `AGENTS.md`, `docs-v2/engineering/INVARIANTS.md`, the `SPEC-*` series and the `DESIGN-01…15`
   series. **Everything else that claims `NORMATIVE` is wrong** — the audit found 73 documents
   claiming it by category rather by role.

---

## 4. What an agent must do when it finds a mismatch

| the finding | the action |
|---|---|
| a document claims `CANONICAL` and another claims the same subject | **report it**; do not demote either without the coordinator's decision |
| a document's content contradicts its status | **report it**; a `HISTORICAL` document whose content describes the present is not "wrong" — it is mislabelled, and the label is what changes |
| a document has **no** status | it is `ACTIVE` by default until classified, and the registry records it as such |
| a `NORMATIVE` document is found in a category that does not earn it | **report it** — the `NORMATIVE` set must be small enough to be meaningful |
| the registry and the tree disagree | **the tree wins**; the registry is regenerated |

**The agent never deletes a document to resolve a mismatch.** The options are: set the status,
name a successor, or archive — in that order of preference.

---

## 5. The status of the status policy

This policy is `CANONICAL` for the document statuses. It is one of the eleven canonical
governance documents:

| the document | the subject it owns |
|---|---|
| `docs-v2/governance/PROJECT-STATE.md` | the current state of the project |
| `docs-v2/governance/ROADMAP.md` | the engineering roadmap |
| `docs-v2/governance/DECISION-REGISTER.md` | the decisions |
| `docs-v2/governance/RISK-REGISTER.md` | the risks |
| `docs-v2/governance/OPEN-QUESTIONS.md` | the open questions |
| `docs-v2/governance/AGENT-OPERATING-PROTOCOL.md` | how an agent works |
| `docs-v2/governance/DOCUMENT-STATUS-POLICY.md` | **this document** — the statuses |
| `docs-v2/architecture/CAPABILITY-REGISTRY.yml` | the capabilities |
| `docs-v2/engineering/TEST-CATALOGUE.yml` | the test scopes |
| `docs-v2/operations/RESOURCE-BUDGETS.md` | the resource envelope |
| `docs-v2/operations/RUNBOOK.md` | the operational procedures |
