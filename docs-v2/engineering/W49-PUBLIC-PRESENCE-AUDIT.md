# W49 — the public presence audit

**Status:** ACTIVE · **Date:** 2026-11-06 · **Task:** TASK-W49-03 (the audit) and
TASK-W49-05 (the pages themselves) · **Directive:** OD-048, "W49 DIRECTIVE ADDENDUM —
Public Research Presence"

**What this document is.** Two things in one file, because they are one thing: **the audit**
of every high-visibility public document against the repository, and **the change log** of
what was rewritten as a result. The audit lists the differences *before* anything was
rewritten; the change log says what each page says now.

**The scope of the audit.** The Pages landing page and the high-visibility pages:
`docs-v2/index.md`, `docs-v2/ecosystem/**`, `docs-v2/guide/getting-started.md`,
`docs-v2/story/overview.md`, `docs-v2/_config.yml` and their Russian equivalents under
`docs-v2/ru/**`.

**The method.** Every factual statement was checked against one of four things: the code
in this repository; a canonical document (`docs-v2/architecture/CAPABILITY-REGISTRY.yml`,
`docs-v2/architecture/RUNTIME-TOPOLOGY.md`, `docs-v2/governance/PROJECT-STATE.md`,
`docs-v2/engineering/W48-STATUS.md`, `docs-v2/engineering/W48-CI-RECONCILIATION.md`,
`docs-v2/engineering/W48-GPU-BENCHMARK.md`, `docs-v2/engineering/TEST-CATALOGUE.yml`,
`docs-v2/research/*`); the wave reports; or the GitHub API. **A statement that could not
be sourced against one of those was cut, not softened.**

---

## 1. The audit — what was wrong, page by page

### 1.1 The landing page (`docs-v2/index.md`)

| what it said | what the repository says | the disposition |
|---|---|---|
| a hero with an animated neural-network SVG, "🧠 MATRIX", "The Hybrid Intelligence Encyclopedia", a "city of minds" | the directive forbids the animated metaphor and the anthropomorphic language | **removed**; the page is now plain text with a stated description |
| "9,432 tests passing, 14 modules" | the test count is a stale whole; the baseline is a set of scopes, each verified on a named commit (`PROJECT-STATE.md` §3) | **replaced** by the scope table, with the total explicitly called unknown |
| "9 TLA+ specs, model-checked in CI" | there are **fifteen** specifications, and the registry row `C-01c` records the published number as wrong | **corrected** to fifteen |
| "0 LLM calls in the decision path" | **true** — registry row `C-01b`: `IMPLEMENTED` / `INTEGRATION_TESTED` / `MEASURED` / `ACTIVE` | **kept**, with its evidence |
| "75% on logic, 89.9% AUC, 92% Minecraft, 100% federation, 100,000× speed-up" | each is a row of the registry with a scope; the 100,000× is against a **synthetic** model and the 33,000× variant is `REJECTED` | **kept with their scope**, or **cut** where the scope was the point |
| "The AI That Thinks Like a Brain", "MATRIX Hybrid Intelligence" (in `_config.yml`) | the constitution's Article VI forbids "AGI" and absolute claims | **replaced** |
| a `url:` of `https://alexandr-narbaev.github.io` | the GitHub API answers `html_url: https://alexandernarbaev.github.io/agi/` — the configured host has **no "n"** | **fixed**; every canonical link the site generated was wrong |
| a "city of minds" audience toggle with three personas | — | **removed**; the page is written for one reader |

### 1.2 The ecosystem pages

| the page | what was wrong | the disposition |
|---|---|---|
| `ecosystem/index.md` | "the production-ready interface to the MATRIX Hybrid Neuro-Symbolic Civilization (W1500 research core)"; "1,119+ tests passing"; a status list of waves T-01…T-10 | **rewritten** against the two chains and the registry |
| `ecosystem/architecture/index.md` | "a monorepo with 9 modules"; a C4 diagram with an API gateway that does JWT+OAuth2+RBAC+Redis rate limiting, a Stripe billing module and a Prometheus/Grafana stack; "1,000+ requests/sec, 50ms p99"; "1,000+ nodes via consistent hashing (T-23)" | **rewritten** against the measured module graph (14 modules in `settings.gradle`), the real storage ownership and the real chains. **The scaling figures were cut** — nothing measures them |
| `ecosystem/quickstart/index.md` | a hosted API at `api.matrix.ai`, a free API key, `pip install matrix-ai`, `npm install @matrix/sdk`, three pricing plans and a rate-limit table | **rewritten** to the real commands. The second half of the old page (the stub/production modes) was accurate and was kept |
| `ecosystem/api/index.md` | the same hosted API, RBAC roles, `429` rate limits, a fabricated explain JSON | **rewritten** against the registered routes of both chains |
| `ecosystem/sdks/index.md` | a Maven dependency on `io.matrix:matrix-sdk-java:0.1.0`, a pip package and an npm package | **rewritten**: the Java SDK exists at `0.1.0-T08-SNAPSHOT`, the other two are in the tree but outside Gradle, and the SDK's **default base URL points at a service that does not exist** — the page now says so |
| `ecosystem/concept/index.md` | the "civilization" framing; a comparison table of absolute claims ("no hallucination", "fully explainable", "free local", "BIR is O(1)"); HDC as "10,000-bit vectors" | **rewritten**: the metaphor cut, the absolute claims cut, the dimension corrected to 1 024 |
| `ecosystem/constitution/index.md` | **eight invented articles** that do not match `CONSTITUTION.md`; the core licensed "Apache-2.0"; a `GdprPruner` in `matrix-audit`; "hallucination is structurally impossible"; commercial offerings | **rewritten** against the eight real articles. The licence is **AGPL-3.0** |
| `ecosystem/xai/index.md` | a five-step trace with stage names that **the runtime does not emit** | **rewritten** against `BrcStep` and the real stage names; the fact that the explanation lives in the gateway's memory was added |
| `ecosystem/architecture/mindcycle.md` | the class called `MindCycle` as the orchestrator; the contradiction check as two cosine thresholds | **corrected**: the gateway imports and instantiates `TrueMindCycle`; since W36.2 the **logical verdict decides the level** and the similarity is only reported |
| `ecosystem/algorithms/*` | the algorithms "power every `/v1/analyze` call"; classes that do not exist (`RuleBase`, `AtomStore`, `ForwardChainer`, `ExplainTracer`, `HyperVector`, `HdcMemory`, `Codebook`, `Modulator`, `ModulatorRegistry`, `PheromoneTable`, `StigmergicRouter`, `EvaporationDaemon`, `TreeNode`, `RolloutPolicy`); HDC as 10 000 bits; BIR as forward chaining over Horn clauses; timings with no measurement ("~3 ms per cycle", "~500 ms") | **corrected** against the code; the timings were cut and replaced with the JMH method |

### 1.3 The guide and the story

| the page | what was wrong | the disposition |
|---|---|---|
| `guide/getting-started.md` | `curl http://localhost:8080/health` — **no such port and no such route**; `POST /brain/query` — **no such route anywhere**; a `docker-compose up` answer of `{"status":"UP","nodes":3,"version":"5.0.0"}`; "GraalVM CE 25.0.2" as the prerequisite; "579 tests"; four configuration properties of which **three do not exist** (`matrix.federation.min-capability`, `matrix.sleep.decay-rate`, `matrix.homeostat.cpu-threshold`); `MATRIX_PORT=8080` | **rewritten**: the real ports (9091 and 8765), the real routes, the real health path (`/q/health`), the real configuration, and the note that the native build is blocked |
| `story/overview.md` | "a city of minds"; a "Four Laws" list that is **not** the constitution; "accuracy improved by 19% after sleeping, memory dropped 22%"; "100% accuracy on TerminalBench", "100% on ARC-AGI"; "100 adversarial attacks, 0 safety violations"; "Licensed under Apache 2.0" | **rewritten**: the metaphor cut, the laws replaced by the constitution's real ones, the unsourced numbers replaced by the registry's audited rows, and the licence corrected. **This page was also the origin of the refuted "33,000×" claim** — see §4 |

### 1.4 The Russian pages

The same audit, applied to `docs-v2/ru/**`. The Russian landing page carried "**724 теста
проходят. 33,000× быстрее, чем LLM. 100% соответствия Конституции**", the "город разумов"
and "ИИ, который думает как мозг"; the Russian ecosystem pages carried the same W1500,
1 119+ and the hosted API. All were rewritten to mirror the English pages, including the
`ru/ecosystem/algorithms/*` stubs, whose false claims (the 10 000-bit vectors, the Prolog
model, the "every `/v1/analyze` call" line, the biochemical page's claim that MATRIX uses
the biochemical network for its four modulators — it does not, the subsystem is an
`ISLAND`/`DORMANT` row) were corrected in place.

---

## 2. What the pages now say, and where each claim comes from

| the claim on the pages | the artefact |
|---|---|
| two serving applications that do not meet; 210 and 28 routes; the ports 9091 and 8765 | `docs-v2/architecture/RUNTIME-TOPOLOGY.md` §1; the census in `docs-v2/engineering/W46-ARCHITECTURE-TRUTH-MAP.md` §1 |
| the gateway's default mode is `stub`; the real brain is `MATRIX_MODE=production` | `RUNTIME-TOPOLOGY.md` §1 (the owner's open question F-01) |
| the four axes, and the three rules that govern them | `docs-v2/architecture/CAPABILITY-REGISTRY.yml`, its header and `status_vocabularies` |
| the test baseline as a set of scopes | `docs-v2/governance/PROJECT-STATE.md` §3 and `docs-v2/engineering/TEST-CATALOGUE.yml` |
| the five mandatory CI jobs green on run `42206946030` at `f6e2be9f`; the release path red for want of credentials | `docs-v2/engineering/W48-STATUS.md` §8 and §12; `docs-v2/engineering/W48-CI-RECONCILIATION.md` §4.11 |
| fifteen TLA+ specifications, the tool pinned by digest | `W48-STATUS.md` §3 (W48-04) and §12 |
| the GPU: 12.06× / 11.68× / 11.47×, end-to-end 12.69×, the point call 0.24× | `docs-v2/engineering/W48-GPU-BENCHMARK.md` §3.6 and `W48-STATUS.md` §3.6; the registry row `W46-GPU` |
| the GPU is an **optional** accelerator | OD-047, `W48-STATUS.md` §5 |
| the native-image capability is `BLOCKED` | `W48-STATUS.md` §8 item 2; `docs-v2/engineering/W48-TEST-RECONCILIATION.md` §5 |
| the known blockers, each with its owner | `PROJECT-STATE.md` §4 |
| the audited claims: BIR 75.0 % on 100 puzzles against a random 30.0 %; HDC AUC 0.899 after 50 samples against 0.253; 92 % in a headless simulator; 100 % consensus under node kills in a model | the registry rows `C-04`, `C-05`, `C-06`, `C-07`, each naming its report |
| the refuted claims: 33,000× (`C-02`), 90 % distillation retention (`C-08`), "100 % Constitution compliant" (`C-09`), the test counts (`C-01`) | the registry rows and `docs-v2/research/NEGATIVE-RESULTS-INDEX.md` |
| the eight articles of the constitution | `CONSTITUTION.md` |
| the module graph, with each module's real status | `docs-v2/engineering/W46-REPOSITORY-TOPOLOGY.md` §1 |
| the storage ownership | `docs-v2/architecture/DATA-AND-MEMORY-ARCHITECTURE.md` |
| AGPL-3.0 | `LICENSE`; `docs-v2/levels/L12-Legal.md` |
| the collaboration terms, the open-access floor, the no-dual-use rule | `docs-v2/levels/L22.md` |
| the research body: two registers, 20 protocols, 62 reports, 6 syntheses; 37 negative results | `docs-v2/research/RESEARCH-INDEX.md` §0 and `docs-v2/research/NEGATIVE-RESULTS-INDEX.md` |

---

## 3. The claims that were deliberately NOT made

| the claim not made | why |
|---|---|
| **a single test count** | the full `matrix-core` suite is not run in this repository's practice; the baseline is a set of scopes, and an older 9 816-execution figure is a floor from a past wave, not a total |
| **the native binary works** | the GraalVM toolchain is absent on the development host; the scope refuses to run rather than report a pass |
| **the GPU makes MATRIX fast** | the GPU is an optional accelerator; it is **4× slower** on a single-point call, and the serving-path factor is 12.06× on one machine and one model |
| **the gateway serves the brain** | by default it serves an 87-line stub; the pages say so on every surface that touches it |
| **MATRIX is hosted, or has an API key, or has rate limits** | none of that exists in the repository |
| **"no hallucination", "fully explainable", "100 % compliant"** | the constitution's Article VI forbids them, and the registry marks the percentage claim as a non-measurable quantity |
| **the 33,000×, the 100,000×, the 19 % sleep improvement, the 22 % memory drop, the 100 % TerminalBench, the 100 % ARC-AGI, the 0 violations in 100 attacks** | none is traceable to a measurement in this repository. Where the underlying registry row exists, the row's own scoped number was used instead |
| **the 1,000 requests/sec and the 50 ms p99, the 1,000-node federation pool** | nothing measures them |
| **"a city of minds", "the AI that thinks like a brain", the neuron/hive analogies** | the directive forbids them on the landing page, and they obscured the distance between *implemented* and *served* |
| **that the older, metaphorical pages were wrong about the systems they describe** | `story/brain-analogy.md`, `story/federation.md` and `story/ethics.md` are outside this task's write scope. They are linked from the rewritten story page **as the record of how the project was once described**, explicitly marked as not the source of any claim |

---

## 4. What could not be sourced

1. **Whether the Helm chart's port matches the application.** The chart declares
   `containerPort: 8080` and probes `/q/health/live`, while the application serves on
   `QUARKUS_HTTP_PORT` (default 9091) — the wave records this as an owner-level decision
   (`W48-STATUS.md` §8 item 11). The pages therefore describe the **application's** port and
   say nothing about the chart.
2. **The count of the modules outside Gradle.** `W46-REPOSITORY-TOPOLOGY.md` §1 says "two
   are outside Gradle entirely", while its own table lists three directories that Gradle
   does not include — the web UI, and the Python and JavaScript SDKs. The pages list the
   three **by name** and do not repeat the number.
3. **The number of vectors the persistent HDC store loads at startup.** The old mind-cycle
   page said "47 vectors loaded from disk on startup" — a figure from one run with no
   artefact. It was cut.
4. **The fate of the `CAPABILITY-REGISTRY.md` companion.** Its line 126 says that
   "`docs-v2/story/overview.md` still contains the 33,000× comparison" and that "rewriting a
   story page is a different task". **This task was that rewrite**, so the sentence is now
   stale. The file is a superseded companion and is **outside this task's write scope**;
   the coordinator owns the correction.

---

## 5. The change log

**36 files.** 35 modified, 1 created.

| the group | the files | what changed |
|---|---|---|
| the landing page and the configuration | `docs-v2/index.md`, `docs-v2/_config.yml` | rewritten; `url`, `title`, `description` and `footer_content` corrected |
| the ecosystem pages | `docs-v2/ecosystem/index.md`, `architecture/index.md`, `architecture/mindcycle.md`, `concept/index.md`, `constitution/index.md`, `quickstart/index.md`, `api/index.md`, `sdks/index.md`, `xai/index.md` | rewritten or corrected, as §1.2 lists |
| the algorithm pages | `docs-v2/ecosystem/algorithms/{index,bir,hdc,mcts,fft,sobel,biochemical,stigmergy}.md` | the class names corrected against the code, the HDC dimension corrected, the unsourced timings cut |
| the guide and the story | `docs-v2/guide/getting-started.md`, `docs-v2/story/overview.md` | rewritten, as §1.3 lists |
| the Russian pages | `docs-v2/ru/index.md`, `ru/overview.md`, `ru/ecosystem/index.md`, `ru/ecosystem/architecture/index.md`, `ru/ecosystem/{concept,constitution,quickstart,api,sdks,xai}/index.md`, `ru/ecosystem/algorithms/{index,bir,hdc,biochemical}.md` | rewritten or corrected to mirror the English pages |
| this document | `docs-v2/engineering/W49-PUBLIC-PRESENCE-AUDIT.md` | created |

**What was NOT touched:** `docs-v2/_raw/**`, `docs-v2/archive/**` and `docs-v2/waves/**`
(the historical archive); `docs-v2/architecture/CAPABILITY-REGISTRY.md` (the superseded
companion — see §4.4); any register, any index, the workbench resource, and any file
outside the scope of TASK-W49-05.

---

## 6. The verification

| the check | the command | the result |
|---|---|---|
| the documentation links | `python3 scripts/docs-link-check.py --root . --strict` | **0 defects in the 36 files of this task.** The six strict failures that remain in the tree are **not** in them: `W49-BASELINE.md:121` and `:155`, `DECISION-REGISTER.md:88` and `:497`, `ROADMAP.md:180` and `:188` — all of them references to the in-flight files of the other tasks of this wave (`W49-NATIVE-BUDGET.md`, `W49-STATUS.md`), which do not exist yet |
| the quality gate | `bash scripts/quality-gate.sh` | the literal baseline is still **271** (shell 78, java 193). Two violations are reported, and **neither is in this task's scope**: **DOCS-1**, for the missing wave files named above, and **FROZEN-1**, because the `.github/` tree now has 1 619 diff lines against a recorded exception of 1 056 — the issue templates, the pull-request template and the Pages workflow were changed by another task of this wave |
| the deployed URL | `gh api repos/AlexanderNarbaev/agi/pages` | `html_url: https://alexandernarbaev.github.io/agi/` — the configured `url` had a typo, and the configuration now matches |
| the Pages configuration | `bash scripts/validate-pages-config.sh` | **exit 0** — "the Pages configuration matches the deployment: https://alexandernarbaev.github.io/agi" and "the title is MATRIX" |

**Two things a reader of this document should know.** First, the pages of this task are
**`UNREACHABLE_FROM_INDEX`** in the link checker's classification — the index
(`docs-v2/INDEX.md`) is the coordinator's, and the entry for the landing page has to be
added there. Second, the Pages build itself is **not** verified here: the deployment path
is TASK-W49-06, and the Jekyll build was not run in this task.
