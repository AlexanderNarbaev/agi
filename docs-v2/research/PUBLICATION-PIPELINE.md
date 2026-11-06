# PUBLICATION PIPELINE — how a MATRIX result becomes a public document

**Status:** `CANONICAL` · **Date:** 2026-11-06 · **Wave:** W49 (tasks W49-08, W49-09) ·
**Branch:** `develop` · **HEAD:** `ed3725d5`
**Owner directive:** OD-048, "W49 DIRECTIVE ADDENDUM — Public Research Presence" (2026-11-06)

**What this document is:** the workflow that carries a measurement from an idea to a
published paper — and, just as importantly, the workflow that carries a measurement to the
place where most of them belong, which is **an engineering report in this repository**.

**What it is not:** a new register. It adds **no** hypothesis register, **no** experiment
register and **no** index. It rides on the ones that exist (§6).

**The rule this pipeline enforces:** *an external publication must never make a stronger
claim than the repository's evidence supports.* The repository already holds the cases
that break this rule — the "33,000× faster than LLMs" claim was **REJECTED** and
**REMOVED** (`docs-v2/architecture/CAPABILITY-REGISTRY.yml`, row `C-02`), and the
"production-grade hybrid AI framework" claim is **UNKNOWN** on all four axes (row `C-09`).
A pipeline that cannot say "no" to those two is not a pipeline.

---

## 1. The seven states

A document is in exactly one state. The state is a property of the **document**, not of
the idea: the same work can be an IDEA in the register and a DRAFT in a file.

| the state | what it means | the entry criterion | the artefact produced | who reviews it | the evidence it requires |
|---|---|---|---|---|---|
| **IDEA** | a question, with no measurement yet | the question is written where the hypotheses live: `docs-v2/research/HYPOTHESES.md` or `docs-v2/research/HYPOTHESES-NEW.md`, with an `H-*` id | a hypothesis card: the metric, the accept/refute threshold, the baseline, the dataset and split, the seed — **before** the run | the coordinator | nothing measured; the card itself is the requirement. A question with no preregistered threshold cannot enter the pipeline |
| **OUTLINE** | the argument of the paper, before the prose | the card has a threshold that a measurement could meet, and there is a `docs-v2/research/protocols/H-*.md` protocol for it | an outline: the claim, the evidence that would support it, the sections, the figures, the tables | the coordinator, plus one person who did not write the work | the outline names **every** number it will use and where that number comes from; a number with no source is not in the outline |
| **DRAFT** | the full text, with every section present | the outline is approved and the measurements exist | a draft in `docs-v2/research/`, with the mandatory content of §4 | the coordinator | the draft's numbers must already be traceable to a file; a draft with an unsourced number is returned, not discussed |
| **INTERNAL_REVIEW** | the draft is being attacked | the draft is complete | the review: a list of objections, each with the evidence that raises it | **two** independent readers: the coordinator and one other; plus the negative-evidence pass — every refuted hypothesis and every corrected claim that touches the draft's subject must be read against it (`docs-v2/research/NEGATIVE-RESULTS-INDEX.md`) | the reviewer checks the draft against the **artefacts**, not against the author's memory. A claim with no artefact is removed at this state |
| **REPRODUCED** | a second person ran the work and got the same answer | the internal review closed with no open objection | the reproduction record: the commands, the environment, the observed output, and the differences | a person who did not write the work | the reproduction must be **from the recorded commands**, in a **clean checkout**, at a **named commit**. A reproduction on the author's machine, with the author's state, is not a reproduction |
| **SUBMITTED** | the artefact is with a venue | the reproduction record exists and agrees | the submission package: the paper, the artefacts (code, data, figures), the reproduction instructions, the licence statement | the coordinator (the submission is an owner decision — §7) | the package carries the **mandatory content** of §4 in full, including the failures and the limitations, and the **claim-strength rule** of §5 has been applied sentence by sentence |
| **PUBLISHED** | it is out | the venue accepted it | the published artefact **plus** the repository record of what was published, where, when, and against which commit | the coordinator | the repository keeps the artefact and its commit; the publication does not replace the repository record, it points at it. **A publication that cannot be traced to a commit in this repository is not published by this pipeline** |

### 1.1 The two rules that are not negotiable

1. **The evidence is the artefact, not the summary.** A number enters the pipeline only
   with the file that holds it and the command that re-derives it. The repository's own
   rule: the JUnit XML is the evidence, not the Gradle console summary (`AGENTS.md`).
2. **The negative evidence is read at REVIEW, not at the end.** The refuted material is
   not a postscript; it is the review's first task. A paper that does not mention the
   refuted hypothesis in its subject area has not been reviewed.

---

## 2. How a report ends as an engineering report instead

**Most technical reports in this repository must not become papers, and the pipeline says
so explicitly rather than letting them drift.** A report ends as an **engineering report**
when any of these is true:

| the condition | where the report goes | why that is the right ending |
|---|---|---|
| the result is a **defect, a contract, a gate or an operating procedure** | `docs-v2/engineering/` (the wave's report) and, if it is a durable rule, `docs-v2/governance/` | an engineering report is read by the next agent that has to maintain the thing. A paper is read by a stranger. The evidence discipline serves the first reader best |
| the result is **one measure inside a larger system** | the wave report, in the measurement's own section | a single measurement is not a contribution; it is a data point |
| the measurement is **synthetic-scope** and the paper would need a domain to mean anything | the experiment report, marked `synthetic-scope` | `docs-v2/research/PROTOCOL.md` §"synthetic-scope": all the project's confirming measurements were obtained on synthetic datasets; the conclusion is valid only as far as the synthetic data reflects the domain, and that is not known. A paper would have to say that in its abstract; an engineering report says it in the measurement's own paragraph |
| the measurement is **preliminary** (single-run JVM level, per the protocol's four levels) | the experiment report, at the protocol's own level | a single-run `nanoTime` measurement does not give an accept verdict; publishing it as a result would overstate it |
| the work is **already published** in substance, elsewhere, and this document is the copy | the repository record, pointing at the publication | two copies of one claim drift apart; one authoritative copy and one pointer |
| the finding is **negative** and the repository already holds it | `docs-v2/research/NEGATIVE-RESULTS-INDEX.md` | the negative corpus is an asset, not a paper. It is what makes the positive claims checkable |

**The exit is not a demotion.** An engineering report that ends a question is a complete
deliverable; the pipeline records it as `PUBLISHED (engineering report)`. The state
vocabulary does not distinguish a published paper from a published report — the artefact
does.

---

## 3. The mandatory content of a public technical article

**Every article that leaves the repository carries all fourteen items.** An item that
cannot be filled is filled with an explicit statement of why it cannot be — never dropped.

| # | the item | what it must contain | what the repository already holds |
|---|---|---|---|
| 1 | **the research question** | the question as it was asked, with the `H-*` id | `docs-v2/research/HYPOTHESES.md`, `docs-v2/research/HYPOTHESES-NEW.md` |
| 2 | **the implementation commit** | the commit the code was in when the measurement was made | `git rev-parse HEAD` at the time; the registry rows carry `last_verified_commit` |
| 3 | **the algorithm / system version** | the version of the thing under test — the model digest, the spec version, the payload version | e.g. `KnowledgeStore.SNAPSHOT_PAYLOAD_VERSION = 2`; the model digests in the GPU benchmark's preflight |
| 4 | **the experimental setup** | what was run, how many times, with what warm-up, over what input | the benchmark harnesses; the protocol's four evidence levels |
| 5 | **the hardware and software environment** | the device, the driver, the CUDA version, the ONNX Runtime version, the JDK, the OS | the GPU benchmark's preflight table; `docs-v2/engineering/W46-HARDWARE-AND-RESOURCE-REPORT.md` |
| 6 | **the dataset and its licence** | the corpus, its size, its provenance and its licence | `docs-v2/research/PROTOCOL.md` (the preregistration names the dataset and the split); the synthetic corpora are seed-controlled and generated in-tree — their licence is the repository's |
| 7 | **the reference baseline** | what the result is compared against, and why that baseline | the preregistered baseline; the algorithm portfolio's measured costs; the BM25 baseline in W42 |
| 8 | **the measurement methodology** | how the number was produced, including the statistics | `docs-v2/research/PROTOCOL.md`; the benchmark's median / p95 / sd discipline |
| 9 | **the results** | the numbers, with their denominators | the experiment reports; `docs-v2/research/reports/` |
| 10 | **the uncertainty or the dispersion** | the spread, and the statistic that summarises it | the GPU benchmark's min / median / p95 / max / sd per series, and its note that one GC pause dominates the BIR row's sd |
| 11 | **the failures and the negative results** | what did not work, what was refuted, what the baseline lost to | `docs-v2/research/NEGATIVE-RESULTS-INDEX.md`; the registry's `REJECTED` rows |
| 12 | **the limitations** | what the result does not establish | the registry's `risks` and `next_verification_condition` per row; DESIGN-58 §6a's "What it does NOT establish" column |
| 13 | **the reproducible commands** | the commands, in order, that re-derive every number | `docs-v2/research/REPRODUCIBLE-OPPORTUNITIES.md` (the three opportunities, each executed) |
| 14 | **the supporting source documents** | the list of the repository documents a reader must open | this document, `docs-v2/research/RESEARCH-INDEX.md`, the registry |

### 3.1 The claim-strength rule, stated as an operational test

Before submission, **every sentence that contains a number or a superlative** is checked
against three questions:

1. **Is the number in a file in this repository?** If not, it does not go in.
2. **Is the claim's scope the scope of the measurement?** The measured scope is the
   synthetic corpus; the claim about the domain is not available. The GPU case is the
   worked example: the ≈12× holds for the forward pass *as MATRIX calls it*; the
   *bare* compute is ≈20–24×; and the **single-point call is 0.25× — the GPU is four
   times slower there**. A paper that says "the GPU makes MATRIX 12× faster" is false
   in the case that MATRIX actually cares about most, and a paper that says "the GPU is
   20× faster" is false in the case the serving path pays.
3. **Would the sentence survive the negative-evidence pass?** If a refuted hypothesis in
   the same subject contradicts it, the sentence is either removed or it is rewritten to
   the scope that the evidence supports.

---

## 4. The states, the artefacts, and what the repository already provides

**The pipeline adds a route, not a register.** Each row names what exists and what the
pipeline adds on top of it.

| the artefact that already exists | what it already provides | what the pipeline adds | what the pipeline does **not** add |
|---|---|---|---|
| `docs-v2/research/HYPOTHESES.md` (38 cards, H-001…H-038) | the metric, the threshold, the baseline and the verdict of every hypothesis, with the experiment that decided it | the IDEA state's entry criterion, and the rule that a card without a threshold cannot leave the register | a second register of ideas |
| `docs-v2/research/HYPOTHESES-NEW.md` (the post-H-038 waves) | the same, for the later waves — the H-039…H-050 block, the W31 cross-disciplinary block, the W41/W42/W44 blocks | the same, and the rule that a card in this file is not a paper until it has a report | a second register |
| `docs-v2/research/protocols/` (20 preregistration protocols) | the preregistered method for each hypothesis — what was to be measured, against what, with what threshold | the OUTLINE state's entry criterion: the protocol must exist and the outline must follow it | a parallel protocol set |
| `docs-v2/research/PROTOCOL.md` | the four evidence levels (single-run JVM / JMH-grade / multi-dataset × seeds / production-domain), the preregistration rule, the `synthetic-scope` definition, the JMH entry points | the rule that an article's evidence level is stated in the article, and that `synthetic-scope` is a **limitation**, never a footnote | a new methodology |
| `docs-v2/research/reports/` (68 files: the EXP-*.md reports and their JSON results) | the measured result of every experiment, with its command | the REPRODUCED state's entry criterion: the report must exist and must name its command | a report index of its own — `RESEARCH-INDEX.md` already indexes them |
| `docs-v2/research/RESEARCH-INDEX.md` | the index of the whole research body, its families, the classification of the historical material, and the separation rule (a proposal must never silently become a normative specification) | the pointer from the OUTLINE state to the existing report, and the rule that the pipeline's intermediate artefacts live in the research body, not elsewhere | a competing index |
| `docs-v2/research/NEGATIVE-RESULTS-INDEX.md` | the discoverable index of the refuted, the corrected and the abandoned material | the INTERNAL_REVIEW state's mandatory negative-evidence pass | a negative-evidence list of its own |
| `docs-v2/architecture/CAPABILITY-REGISTRY.yml` | the four axes of every capability — implementation, integration, verification, operational — plus the serving path, the benchmark evidence and the per-row risks | the claim-strength rule's source of truth: a capability's operational axis decides whether it may be described as available | a capability list |
| `docs-v2/governance/DECISION-REGISTER.md` | every architecture decision, with its owner, its date, its status and its successor | the rule that a paper's architectural claims are traced to a decision, and that a decision may never be inferred from a precedent | a decision list |

### 4.1 Where the pipeline's own artefacts live

| the artefact | the path | the note |
|---|---|---|
| the idea | the existing `H-*` card | no new file |
| the preregistration | the existing `docs-v2/research/protocols/H-*.md` | no new file |
| the outline | the draft file's own §0 | the outline is a section of the draft, not a separate document that can drift away from it |
| the draft | `docs-v2/research/<NAME>.md` | the file the article will become |
| the internal review | a section of the draft, `## The internal review` | a review that lives in a chat is a review that was not done |
| the reproduction record | a section of the draft, `## The reproduction` | it holds the commands, the environment and the observed output |
| the submission package | the draft plus the artefacts it names | — |
| the published record | the draft's own header, rewritten to the published state | the repository keeps the artefact; the publication points at it |

---

## 5. The CITATION.cff — an evaluation, not a file

**Decision: do not add a `CITATION.cff` yet. The repository has no formal release to
cite.** The evaluation, with the state that was measured:

| the question | the measured answer | the command |
|---|---|---|
| what is the licence? | **GNU AGPL v3** — `LICENSE` at the repository root, the full text | `head -3 LICENSE` |
| is there a CITATION.cff today? | **no** — and no `.zenodo.json`, no `codemeta.json` | `ls CITATION* .zenodo.json codemeta.json` |
| are there releases? | **no** — `gh release list` is empty on the GitHub remote | `gh release list --limit 10` |
| are there version tags? | **yes** — 38 tags, of which 16 are version tags: `v1.0.0`, `v16.0.0-mind` … `v18.0.0-mind`; the newest is `v18.0.0-mind` at commit `7a0ed0cc` (2026-10-12), and it **is** an ancestor of `develop` | `git tag`, `git describe --tags --abbrev=0`, `git merge-base --is-ancestor v18.0.0-mind HEAD` |
| what are the remotes? | two: `origin` → `https://github.com/AlexanderNarbaev/agi.git` and `gitverse` → `git@gitverse.ru:AlexandrNarbaev/agi.git` | `git remote -v` |

**What a `CITATION.cff` would have to contain, and what cannot be invented:**

| the field | the value that is *knowable* | what is **not** knowable and must not be invented |
|---|---|---|
| `title` | the repository's own name and its subject | — |
| `version` | a **tag**, not a release: `v18.0.0-mind` is the newest tag that exists | a release version — there is no release |
| `date-released` | the tag's commit date, `2026-10-12` | a publication date — the paper is not published |
| `license` | `AGPL-3.0-or-later`, from `LICENSE` | — |
| `repository-code` | `https://github.com/AlexanderNarbaev/agi.git` | — |
| `authors` | **the owner decides the author order.** An agent cannot infer it, and an alphabetical or "the first commit's author" rule would be an invention | the author list and its order |
| `doi` | **none** | a DOI — no DOI exists, and minting one is the owner's and the repository host's act |
| `journal` / `publisher` | **none** | a publisher — there is no venue yet |

**The rule that follows.** Until there is a release, the citable unit is **a versioned
commit**, and the citation instruction the repository should give is this:

> Cite the exact commit. `git rev-parse HEAD` at the moment of the measurement, and the
> tag it carries. The repository's newest tag is `v18.0.0-mind` (`7a0ed0cc`, 2026-10-12);
> `develop` is ahead of it. **A claim measured at a commit is citable; a claim measured
> at "the current state of `develop`" is not.**

**When the `CITATION.cff` becomes worth adding:** at the first formal release — that is,
when a tag exists that the repository's host treats as a release, with a date and a
DOI. At that moment the file is filled from the release's own metadata, and the author
list is the owner's. **Until then, the citation instruction above is the citable form,
and it costs nothing.**

---

## 6. What the pipeline does not do

1. It does not create a hypothesis register, an experiment register or a paper index.
2. It does not activate anything, and it does not change `opencode.json`.
3. It does not decide who the authors are, and it does not mint a DOI.
4. It does not turn a synthetic-scope measurement into a domain claim.
5. It does not replace the wave report: the wave report stays the record of the wave, and
   the paper is a separate artefact that cites it.
6. It does not weaken any gate. The quality gate, the formal gate and the link checker
   are what they are; the pipeline adds no exemption to any of them.

---

## 7. The vocabulary

| the term | the meaning |
|---|---|
| **the state** | one of the seven of §1, a property of a document |
| **the artefact** | the file that holds the result — a card, a protocol, a report, a draft |
| **the evidence** | the file and the command that re-derive the number |
| **the evidence level** | the protocol's four levels: single-run JVM, JMH-grade, multi-dataset × seeds, production-domain |
| **`synthetic-scope`** | the protocol's own term: the corpus is synthetic and seed-controlled, so the conclusion holds as far as the synthetic data reflects the domain |
| **the claim-strength rule** | §3.1: the external claim may not exceed the repository's evidence |
| **the negative-evidence pass** | the mandatory review step that reads the refuted material against the draft |
| **PUBLISHED (engineering report)** | the state of a report that answered its question in the repository and will not become a paper |

**The companion documents:** `docs-v2/research/PUBLICATION-CANDIDATES.md` (the three
candidates and their verdicts, and the disposition of the W31 arXiv draft),
`docs-v2/research/COLLABORATION-BRIEF.md` (what an outside collaborator may work on),
`docs-v2/research/REPRODUCIBLE-OPPORTUNITIES.md` (the three opportunities, each one
executed).
