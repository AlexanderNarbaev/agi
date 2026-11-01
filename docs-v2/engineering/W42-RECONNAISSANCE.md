# W42 — Reconnaissance

**RECON-W42.0** · 2026-11-01 · owner directive Part II
**Base:** `0296e83e` (W41 final verification) · branch `develop`
**Method:** four read-only specialist agents + the coordinator, all reading
the same tree; every claim below carries a file:line or a command output.

---

## 1. The repository state, as found

**The remote HEAD is `0296e83e`** — the W41 baseline the mission named. It was
verified, not assumed:

```
$ GIT_SSH_COMMAND="ssh -i ~/.ssh/id_ed25519 -o IdentitiesOnly=yes" git fetch origin --prune
$ git rev-parse origin/develop   →  0296e83e88b9caecba828c992f9634c21d51a1c8
$ git rev-parse HEAD              →  0296e83e88b9caecba828c992f9634c21d51a1c8
```

The fetch also pruned three stale dependabot branches and added two. The
local `develop` and `origin/develop` agree; the tree is not ahead of the
remote.

**The working tree carries only the owner's pre-existing modifications.**
They are untouched by this wave and will stay untouched:

| Path | State | Owner |
|---|---|---|
| `.gitignore` | modified | owner |
| `.opencode/todo.md`, `.opencode/w37-plan.md` | modified | owner |
| `AGENTS.md` | modified | owner |
| `opencode.json` | modified | owner |
| `opencode.json.bak` | deleted | owner |
| `.codex/`, `.opencode/goals/`, `.opencode/opencode-swash.json`, `.opencode/tmp/`, `.veai/` | untracked | owner |

**Branches.** 20 local branches, 14 remote. The eight MIND branches are
detailed in `W42-HISTORICAL-BRANCH-RECOVERY.md`; the finding that changes
the plan is that **all of their work is already on `develop`**, and four of
their classes were deliberately deleted by a truthfulness pass.

**Worktrees.** `git worktree list` — none besides the main checkout.

**Tags and releases.** `origin/release/v1.0` and `origin/safe-main` exist
remotely; neither is a W41-era branch and neither was touched.

---

## 2. What the product actually is

Two products, not one:

**Product A — the Learning Workbench.** `matrix-core` as a Quarkus app on
`http://0.0.0.0:9091`. 36 REST endpoints under `/v1/workbench`, a 9-tab
static UI, one SQLite file. Verified live in this wave:

```
matrix-core 1.0.0 on JVM (powered by Quarkus 3.38.3) started in 1.571s.
Listening on: http://0.0.0.0:9091
Installed features: [agroal, cdi, hibernate-validator, jdbc-postgresql,
  micrometer, narayana-jta, opentelemetry, picocli, reactive-pg-client,
  rest, rest-jackson, smallrye-context-propagation, smallrye-health,
  vertx, websockets, websockets-client]
```

**No Kafka extension, no Redis extension** — the two that logged warnings
during the W41 wave were the raw client libraries, not Quarkus extensions.
**No external service is required and no API key is configured anywhere in
the repository.**

**Product B — the gateway brain.** `matrix-api-gateway` is a hand-written JDK
`HttpServer` (`MinimalHttpServer:1564`), not a Quarkus app — its
`build.gradle` applies no Quarkus plugin, so its JAX-RS resources
(`AnalyzeResource`, `ExplainResource`) are **never mounted**. Its real path
runs `TrueMindCycle`, the 10-stage BRC pipeline, over an HDC codebook.

**The two products share no state.** Product B's brain cannot see a single
claim of product A's knowledge store, and vice versa.

---

## 3. The measurement instrument this wave built, and what it says

The mission's §14.1 asks for a stable benchmark; §14 asks for retrieval to
be "measured against BM25 and lexical baselines". Both were built in this
wave so that the baseline exists **before** anything is improved.

- `matrix-core/src/main/resources/benchmark/w42-benchmark-v1.json` — 34
  items across 12 categories (FACTUAL, MULTI_HOP, CONTRADICTORY, AMBIGUOUS,
  MISSPELLED, PARAPHRASE, CROSS_LINGUAL, UNANSWERABLE, FALSE_PREMISE,
  CAUSAL, TRANSFER, ADVERSARIAL), in English and Russian, each stating
  whether the system should answer or refuse.
- `io.matrix.knowledge.benchmark.Bm25Index` — the BM25 baseline, with
  Robertson & Zaragoza's probabilistic IDF and the standard `k1 = 1.2`,
  `b = 0.75`.
- `io.matrix.knowledge.benchmark.BenchmarkRunner` — runs the items and
  reports answer accuracy, abstention quality, the BM25 overlap and the
  per-category / per-language breakdown.

**The W42 baseline, measured:**

```
the benchmark items: 34
  answerable: 24 | answered correctly: 14 | accuracy: 0.583
  unanswerable: 10 | refused correctly: 4 | abstention quality: 0.400
  the BM25 overlap on the answered items: 0.593

by category              by language
  FACTUAL 5/6              en  14/19 correct, 3/5 refused
  MULTI_HOP 3/3             ru   0/5  correct, 1/1 refused
  CONTRADICTORY 1/1
  AMBIGUOUS 1/2 refused
  MISSPELLED 3/4
  PARAPHRASE 0/2        ← a paraphrase is refused
  CROSS_LINGUAL 0/4     ← Russian is refused
  UNANSWERABLE 2/4      ← two unanswerable questions are answered
  FALSE_PREMISE 0/2     ← the system AGREES with both false premises
  CAUSAL 2/2
  TRANSFER 0/2
  ADVERSARIAL 1/2
```

**The five numbers that define W42's work:**

1. **Abstention quality 0.400.** The system answers six of the ten questions
   it cannot support. This is an *epistemic* defect, not a missing feature:
   the W41 work built the machinery to refuse, and the refusal threshold is
   too permissive.
2. **False premises: 0/2.** "Since photosynthesis produces oxygen from carbon
   dioxide, how much oxygen…" gets an answer that repeats the false premise.
   The system does not yet check the premise of a question it is asked.
3. **Russian: 0/5.** The retrieval is English-only by construction — an
   80-word English stopword list, an English-only suffix fold, an English
   negation word list, English task-classification keywords.
4. **Paraphrase: 0/2.** A lexical retriever cannot answer "Explain how green
   plants make food using sunlight" against a claim that says "photosynthesis
   is the process by which…". The BM25 overlap of 0.593 says the retrieval
   agrees with the standard baseline about 60% of the time — so the gap is in
   the *matching*, not in the ranking.
5. **TextFeatures has zero tests in the entire repository**, and every
   retrieval, grading, contradiction and reasoning path depends on it.

**The sample size is 34 items and the report says so.** These are the
numbers a W42 improvement will be measured against, not a claim about the
system's competence.

---

## 4. Documentation consistency registry

The mission's §3.4 requires a registry where every significant architectural
statement carries one of `CURRENT`, `PROPOSED`, `EXPERIMENTAL`, `UNVERIFIED`,
`SUPERSEDED`, `HISTORICAL`, `REQUIRES_OWNER_DECISION`, linked to code,
tests, specifications or measurement.

### 4.1 The statements that are wrong today

| # | Document and claim | Status | Evidence |
|---|---|---|---|
| D1 | `docs-v2/engineering/LEARNING-WORKBENCH.md` §6 presents the **legacy** teach-back (0.00 → 0.81) as the current measurement | `SUPERSEDED` — the rebuilt experiment exists but the guide still describes the old one | the guide's own §6 "What this measurement is" is honest about the containment limitation, but it does not say the measurement was replaced |
| D2 | `docs-v2/designs/DESIGN-70-evidence-grounded-reasoning.md` §0: *"the second domain does not exist yet, and the teach-back is still the containment measurement RECON-W40 built"* | `SUPERSEDED` | the logic domain exists (11/28/2/8 + 3 derivations) and `TeachBackExperiment` exists |
| D3 | `docs-v2/research/HYPOTHESES-NEW.md` H-102 — *"Status: PROPOSED — the item pack is not yet written"* | `CURRENT` in substance, stale in wording | `photosynthesis.adversarial.json` exists: 13 items, keyed alternatives, misconception bindings |
| D4 | `docs-v2/research/HYPOTHESES-NEW.md` H-103 — *"Status: PROPOSED"* | `PARTIALLY IMPLEMENTED` | `SupportScore`/`Derivation`/`Answer` are implemented and in production; the error-gated eligibility-weighted component update is **not** |
| D5 | `docs-v2/engineering/RISK-REGISTER.md` risk 7 — *"the brain's cycle output is consumed by the memory layer"* | `VERIFIED_NOT_A_DEFECT` is **false** | `ConsciousBrain` has 0 production construction sites; `CognitiveGenesisProfileBuilder.fromCycleReport` has 0 `src/main` callers |
| D6 | `docs-v2/engineering/RISK-REGISTER.md` risk 4 (unbounded `CognitiveProcessor.history`) | `OPEN`, but **understated** | the growth is O(n²) memory and O(n³) cumulative time, and it is currently **test-only** because `ConsciousBrain` is unreachable |
| D7 | `matrix-core/src/main/resources/application.properties:93` — `atrix.knowledge.assessment` | **FIXED** in `b724c79d` | the key was dead; the value happened to equal the code default, so nothing broke and nothing looked broken |
| D8 | `docs-v2/engineering/LEARNING-WORKBENCH.md` §13's last bullet describes only the legacy teach-back's limitation | `INCOMPLETE` | the rebuilt experiment measures four phases, a leakage audit and calibration — the bullet omits that there are now two experiments and that the UI runs the old one |

### 4.2 The statements that are CURRENT

| Document | Claim | Evidence |
|---|---|---|
| `W41-REALITY-AUDIT.md` | the W40 "confidence 0.84" was the mean of the authored confidence × IDF coverage | `KnowledgeRetriever:206-214` computes the support score the same way, and `SupportScore.UNCALIBRATED` says so permanently |
| `W41-REALITY-AUDIT.md` | the W40 teach-back measured lesson containment | `SyntheticLearner.answer` returns the taught statements; the containment path is still live at `/teach-back` |
| `W41-LOCAL-ALPHA-V0.2.md` | 12 specifications, all exit 0; 27 mutants, 25 killed, 2 survivors | re-verified this wave: `OK` for all twelve; the mutation report regenerated |
| `W41-LOCAL-ALPHA-V0.2.md` | the app starts in 1.5–1.7 s with no external services | 1.571 s observed live |
| `formal/spec-implementation-map.yml` | each specification's abstraction gap | the `KnowledgeAxes` entry carries its `VACUOUS_PROPERTY` gap |
| `DESIGN-58 §6a` | the capability registry with what each measurement does **not** establish | consistent with the code |

### 4.3 The documentation that is HISTORICAL and must stay that way

`docs-v2/_raw/**` (the pre-v2 archive), `docs-v2/research/reports/EXP-0*`
(the experiment records), and the branch reports of the MIND waves. The
mission's §12.4 forbids discarding them. They are **not** to be rewritten;
they are the record of what was measured when.

---

## 5. The five findings that decide the plan

Each is stated with the evidence that decided it.

### F1 — The learning lifecycle is a label, not a state machine

`LearningStateEvaluator.stageFor` and `CapabilityEvaluator.evaluate` are two
threshold functions over an append-only outcome log. `KnowledgeWorkbench:202-219`
recomputes every state on every request. **No table stores a stage; no
transition is recorded.** A regression is representable — a failing outcome
lowers the mean below 0.8 — but it is visible only as a recomputed label plus
one sentence of note, and the note fires **only when the last outcome**
contradicts an earlier success (`LearningStateEvaluator:149-150`).

The mission's §7 requires a real state machine with observable, explained
regression and preserved earlier mastery records. **None of that exists.**

### F2 — Nothing decides what to learn next

`GoalManager`'s methods are `start`, `completeSubtask`, `failAttempt`,
`checkpoint`, `goalsWithSubtasks`, `resumable`, `recomputeStatus` — all
recorders and readers. The subtask list arrives in the HTTP request body.
There is no `nextAction`, `plan`, `select`, `recommend` or `due` method
anywhere in `io.matrix.knowledge`.

The raw material exists and is unused: `LearningGoal.remainingSubtasks()`,
`LearningGoal.blockedSubtasks()`, `LearningGoal.hasDependencyCycle()`,
`KnowledgeGapDetector.missingPrerequisites()`, the `prerequisite` table with
its acyclicity check, and `CurriculumOrderer` in `io.matrix.ktopo` (which
the knowledge subsystem never references).

### F3 — Nothing makes a learner practise

No exercise generator, no item selector, no "next item" endpoint, no
spaced-repetition scheduler, no `dueAt`/`reviewAt` column. The items are
hand-authored static JSON; the **caller** supplies the item id
(`LearningWorkbenchResource:370` rejects a request without one).
`TeachingComposer`'s "exercise" and "check" are constant English strings.

### F4 — There is no document ingestion

The only ingest is a curated package JSON. `KnowledgeImporter.importPack(Path)`
and `importPack(String)` have **no production caller** — the runtime can only
load the two bundled packs. `KnowledgeRefiner` cannot create a concept
(`KnowledgeRefiner:74`), so a genuinely new topic has no runtime entry
point even through the correction path.

`io.matrix.imports` is about **model weights** (BitNet, safetensors), not
documents. The only reader of `.md`/`.txt` files is `SimpleKnowledgeBase`
— a different, in-memory system the W41 store never reads.

### F5 — Three knowledge systems, and the retrieval is English-only

`KnowledgeStore` (SQLite, provenance-carrying, product A),
`SimpleKnowledgeBase` (in-memory, the brain's), `KnowledgeGraphStore`
(in-memory graph, MCP's). None reads another.

The retrieval's English assumptions are enumerated in the W42 integration
matrix's companion audit: the stopword list (`TextFeatures:30-38`), the
suffix fold (`:141-156`), the duplicated fold in the reasoning engine's
classifier (`ReasoningEngine:316-331`), the English task-classification
keywords (`ReasoningEngine:290-301`), and the English negation list
(`ContradictionDetector:112,139`, `StructuredLearner:220`,
`TextFeatureVector:121`). Tokenisation itself is Unicode-safe
(`Character.isLetterOrDigit`, `toLowerCase(Locale.ROOT)`) — the breakage is
in the morphology, the stopwords, the negation list and the classifier.

---

## 6. What was NOT done in this wave, and why

- **No major implementation.** The mission's §17 says to establish the real
  state first. The only code written was the benchmark instrument and the
  benchmark data, plus the one-character configuration fix.
- **No branch was merged, restored or deleted.** The archaeology found that
  merging or restoring would be actively harmful — see
  `W42-HISTORICAL-BRANCH-RECOVERY.md`.
- **The full repository suite was not run.** The standing rule requires the
  owner's agreement because of the OOM risk. The targeted packages were run
  instead (121 classes, 1 212 tests, 0 failures at the last broad run).
- **No FROZEN zone was edited.** `.github/workflows/tla.yml` was changed in
  the W41 wave under an owner directive and recorded as incident D9; this
  wave did not touch it.
