# W42 — Documentation Consistency Registry (verification pass)

**Mission:** RECON-W42 §3.4 — a documentation consistency registry in which every
significant architectural statement carries one of `CURRENT`, `PROPOSED`,
`EXPERIMENTAL`, `UNVERIFIED`, `SUPERSEDED`, `HISTORICAL`,
`REQUIRES_OWNER_DECISION`, linked to code, tests, specifications or measurement.

**Pass type:** READ-ONLY AUDIT. **No document under audit was modified.** This file
is the only file this pass created.

**Repository:** `AlexanderNarbaev/agi` · branch `develop` · HEAD `6ea07101`
("WAL: The false-premise detector exists, it does not work in Russian").

**Audit date:** 2026-11-02. **Ground truth:** the code at HEAD (`6ea07101`).
The working tree carries the owner's uncommitted changes (`.gitignore`,
`.opencode/*`, `AGENTS.md`, `opencode.json`, `matrix-core/.../PremiseChecker.java`,
plus the untracked `formal/LearningStateMachine.*`, `formal/PromotionStage.*`,
`matrix-core/.../knowledge/benchmark/BenchDiagnosticTest.java`). Every count below
is computed against **HEAD**, and where the working tree differs the delta is stated.

**Commands used as evidence** (re-runnable):

```
git ls-tree -r HEAD --name-only | grep -c '^formal/.*\.tla$'          # formal specs at HEAD
git show HEAD:formal/mutations.json                                    # mutant manifest at HEAD
git grep -h "@Test" HEAD -- "matrix-core/src/test/java/io/matrix/<pkg>/"
find matrix-core/src/test/java/io/matrix/<pkg> -name '*.java' | wc -l
python3 scripts/integration-census.py                                  # authoritative component census
bash scripts/quality-gate.sh; echo $?                                 # literal gate, measured exit code
python3 -c "import json; ..."                                          # benchmark JSON item/category census
grep -rn "new <Class>(" --include='*.java' */src/main */src/test        # construction-site census
```

---

## DOCUMENTATION CONSISTENCY REGISTRY

Status vocabulary per §3.4. **Evidence column** names the file:line or the command
output that settles the claim.

### A. The wave-status table (the duplicate rows)

| # | Claim | Document + line | Code evidence | Status | Recommended correction |
|---|---|---|---|---|---|
| F-01 | `**W42.2 — Universal knowledge acquisition** \| **DONE (the core)**` | `docs-v2/engineering/RECON-W42-STATUS.md:16` | `docs-v2/engineering/RECON-W42-BACKLOG.md:38` — "W42.2 — Universal knowledge acquisition — **the core is DONE**"; `docs-v2/engineering/W42.2-UNIVERSAL-ACQUISITION-REPORT.md` exists (173 lines) | `CURRENT` | none — keep line 16 |
| F-02 | `W42.2 — Universal knowledge acquisition \| not started \|` | `RECON-W42-STATUS.md:17` | contradicts `RECON-W42-STATUS.md:16` (same wave, same table) and `RECON-W42-BACKLOG.md:38-46` | `SUPERSEDED` | **Delete line 17.** |
| F-03 | `**W42.4 — Learning and assessment** \| **DONE**` | `RECON-W42-STATUS.md:19` | `RECON-W42-BACKLOG.md:94` — "W42.4 — Learning and assessment — **DONE**"; `W42.4-LEARNING-AND-REPRESENTATION-REPORT.md` exists | `CURRENT` | none — keep line 19 |
| F-04 | `W42.4 — Learning and assessment \| not started \|` | `RECON-W42-STATUS.md:22` | contradicts `RECON-W42-STATUS.md:19` | `SUPERSEDED` | **Delete line 22.** |
| F-05 | `**W42.5 — Cognitive integration** \| **DONE**` | `RECON-W42-STATUS.md:20` | `RECON-W42-BACKLOG.md:183` — "W42.5 — Cognitive integration — **DONE**"; `W42.5-COGNITIVE-INTEGRATION-REPORT.md` exists | `CURRENT` | none — keep line 20 |
| F-06 | `W42.5 — Cognitive integration \| not started \|` | `RECON-W42-STATUS.md:23` | contradicts `RECON-W42-STATUS.md:20` | `SUPERSEDED` | **Delete line 23.** |
| F-07 | `**W42.6 — Local Alpha v0.3** \| **DONE**` | `RECON-W42-STATUS.md:21` | `RECON-W42-BACKLOG.md:214` — "W42.6 — Local Alpha v0.3 — **DONE**"; `W42.6-LOCAL-ALPHA-V0.3.md` exists | `CURRENT` | none — keep line 21 |
| F-08 | `W42.6 — Local Alpha v0.3 \| not started \|` | `RECON-W42-STATUS.md:24` | contradicts `RECON-W42-STATUS.md:21` | `SUPERSEDED` | **Delete line 24.** |
| F-09 | The §1 table's own header promises a single status per wave | `RECON-W42-STATUS.md:12` — `\| Wave \| Status \| Evidence \|` | rows 14–24 hold **four** waves twice each (16/17, 19/22, 20/23, 21/24); W42.0, W42.1 and W42.3 appear once | `SUPERSEDED` | After F-02/04/06/08 the table is one row per wave. Replace the block at lines 14–24 with the single-row-per-wave table, keeping the `DONE` rows. |

### B. Test counts

| # | Claim | Document + line | Code evidence | Status | Recommended correction |
|---|---|---|---|---|---|
| F-10 | `Targeted suites \| **121 classes, 1 212 tests, 0 failures**` (command: the five-package selector) | `RECON-W42-STATUS.md:105` | At HEAD the five targeted packages hold **164 test classes** (`git ls-tree -r HEAD --name-only \| grep -E '^matrix-core/src/test/java/io/matrix/(knowledge\|memory\|brain\|api\|rag)/.*\.java$' \| wc -l` → 164) and **1 346 `@Test`** methods. The claim of 121 classes is below the number of classes that exist, so it cannot be true at HEAD. | `SUPERSEDED` | Replace the cell with: `**164 classes, 1 346 @Test methods** (2026-11-02; method count, not the Gradle-reported test count)`. Do **not** restate it as "1 212 tests" — that figure is Gradle's test-count and was not re-measured (see F-15). |
| F-11 | `Targeted suites \| **164 classes / 1 473 tests / 0 failures** (2026-11-02)` — with the **seven**-package selector `… --tests "io.matrix.cognitive.*" --tests "io.matrix.mcp.*"` | `RECON-W42-BENCHMARKS.md:123` | The seven-package selector at HEAD holds **170 test classes** and **1 401 `@Test`** methods. The quoted `164` is the **five**-package class count — the number does not correspond to the command printed beside it. | `SUPERSEDED` | Replace the cell with: `**170 classes, 1 401 @Test methods** (2026-11-02, seven-package selector)`. |
| F-12 | `the targeted suites (144 classes, 1341 tests)` | `docs-v2/engineering/W42.6-LOCAL-ALPHA-V0.3.md:258` | Matches neither the five- nor the seven-package set at HEAD (164 / 170 classes; 1 346 / 1 401 `@Test`). | `SUPERSEDED` | Replace with: `# the targeted suites (164 classes, 1 346 @Test methods)` — or, if this line is meant to describe the five-package run, use that. |
| F-13 | `The knowledge package \| 27 test classes, **214 \`@Test\`**` | `RECON-W42-BENCHMARKS.md:126` | At HEAD `matrix-core/src/test/java/io/matrix/knowledge/` holds **68 test classes** and **425 `@Test`** methods (`git ls-tree`/`git grep` at HEAD). The working tree adds the owner's untracked `BenchDiagnosticTest.java` → 70 classes / 427 `@Test`. | `SUPERSEDED` | Replace with: `The knowledge package: 68 test classes, 425 @Test (at HEAD 6ea07101)`. |
| F-14 | `425 тестов знания` | `wal/SESSION_WAL.md:4178-4179`, `:4200`, `:4221` | Matches HEAD exactly: 425 `@Test` in the knowledge package | `CURRENT` | none — the WAL is correct here; the W42 docs are the stale side |
| F-15 | Test counts expressed as Gradle test counts (`1 212`, `1 473`, `1 341`) | `RECON-W42-STATUS.md:105`, `RECON-W42-BENCHMARKS.md:123`, `W42.6-LOCAL-ALPHA-V0.3.md:258` | Not re-measured: `AGENTS.md` requires the owner's agreement for a full run (OOM risk) and `RECON-W42-DECISIONS.md:308-314` (D-017) records that the full suite was not run. Gradle's reported test count also differs from the `@Test` method count because parameterised tests expand. | `UNVERIFIED` | Mark the Gradle test-count figures `UNVERIFIED` or replace them with the `@Test` method count, which is reproducible from the tree. Note in the cell that the full repository suite has not been re-run (D-017). |

### C. Formal gate and mutation gate

| # | Claim | Document + line | Code evidence | Status | Recommended correction |
|---|---|---|---|---|---|
| F-16 | `Formal gate \| **12/12 exit 0**, 65 … 396 577 states` | `RECON-W42-STATUS.md:106`; `RECON-W42-BENCHMARKS.md:124` | At HEAD there are **12** committed TLA+ specs (`git ls-tree -r HEAD --name-only \| grep '^formal/.*\.tla$' \| wc -l` → 12). Consistent. | `CURRENT` | none — but see F-18 for the same file's later claims |
| F-17 | `The formal gate is 13 passed, 0 failed` / `The formal gate is 14 passed, 0 failed` | `RECON-W42-STATUS.md:255`, `RECON-W42-STATUS.md:260` | The two additional specs that would make it 13 and 14 — `formal/LearningStateMachine.tla` and `formal/PromotionStage.tla` — are **untracked in the working tree** (`git status`: `?? formal/LearningStateMachine.tla`, `?? formal/PromotionStage.tla`). They are not at HEAD, so the counts 13 and 14 are not reproducible from the committed tree. | `UNVERIFIED` | Either (a) commit the two specifications and then the counts become `CURRENT`, or (b) annotate both lines: `the formal gate is 14 passed, 0 failed — measured with `formal/LearningStateMachine.tla` and `formal/PromotionStage.tla`, which are not yet committed at HEAD 6ea07101`. |
| F-18 | The same document gives the formal gate as 12, then 13, then 14, with no indication which supersedes which | `RECON-W42-STATUS.md:106` vs `:255` vs `:260` | three mutually exclusive numbers inside one file | `SUPERSEDED` | State once, with the wave: replace `RECON-W42-STATUS.md:106` with `Formal gate \| **12/12 exit 0** at HEAD; **14/14** with the two uncommitted specifications of W42.9 (see F-17)`. |
| F-19 | `Mutation gate \| **27 mutants, 25 killed, 2 survivors**` | `RECON-W42-STATUS.md:107`; `RECON-W42-BENCHMARKS.md:125`; `RECON-W42-BENCHMARKS.md:446`; `docs-v2/engineering/W42-RISK-REGISTER.md:118` | `git show HEAD:formal/mutations.json` → **32** mutants. `docs-v2/engineering/MUTATION-REPORT.md:9-13` (run at `2026-11-02T12:11:19Z`, committed at `384e24ff`) → `Mutants generated: **32** / Killed: **30** / Survived: **2** / Invalid: **0** / Drifted: **0**`. | `SUPERSEDED` | Replace all four occurrences with: `**32 mutants, 30 killed, 2 survived, 0 invalid, 0 drift**` and cite `docs-v2/engineering/MUTATION-REPORT.md` as the evidence instead of the harness invocation alone. |
| F-20 | `\| 17 \| all critical mutants killed or justified \| **MEASURED: 25 of 27, 2 justified**` | `RECON-W42-BENCHMARKS.md:446` | same as F-19 (32/30/2) | `SUPERSEDED` | Replace with `**MEASURED: 30 of 32, 2 justified**`. |
| F-21 | `\| 16 \| no unexplained failed or vacuous property \| **MEASURED** \| the two mutation survivors are recorded with their findings` | `RECON-W42-BENCHMARKS.md:445` | `MUTATION-REPORT.md` names exactly two survivors: `Consensus-evaluate-without-quorum` and `KnowledgeAxes-conflict-forgets-what-it-recorded` — the same two the benchmarks document names at `:131-139` | `CURRENT` | none |

### D. Startup time, ports, endpoints, UI

| # | Claim | Document + line | Code evidence | Status | Recommended correction |
|---|---|---|---|---|---|
| F-22 | `Application startup \| **1.571 s**` | `RECON-W42-BENCHMARKS.md:122`; `RECON-W42-STATUS.md:108`; `W42-RISK-REGISTER.md:102`; `W42-INTEGRATION-MATRIX.md:48`; `W42-RECONNAISSANCE.md:58,176` | Consistent set of five documents. | `CURRENT` | none — but see F-23 and F-24, which state different numbers for the same claim |
| F-23 | `\| 1 \| startup under 5 s \| **MEASURED: 1.428 s** \| the live log: … started in 1.428s` | `RECON-W42-BENCHMARKS.md:430` | Inside the same file that says **1.571 s** at `:122`; the W42.1 report quotes a third value, `started in 1.595s` (`W42.1-LEARNING-CORE-REPORT.md:205`). Three values, each attributed to "the live log". Startup time is machine- and run-dependent; the document does not say which run each number belongs to. | `UNVERIFIED` | Replace with a run-qualified statement: `**MEASURED: 1.571 s** (the W42.0 run) — later runs on the same machine measured 1.428 s and 1.595 s; the §14 row is against the 5 s target, which all three meet`. |
| F-24 | `matrix-core 1.0.0 on JVM (powered by Quarkus 3.38.3) started in 1.595s.` | `W42.1-LEARNING-CORE-REPORT.md:205` | differs from `RECON-W42-BENCHMARKS.md:122` (1.571 s) and `:430` (1.428 s) | `HISTORICAL` | Annotate the report line with its wave: `started in 1.595s (the W42.1 run; the W42.0 baseline run measured 1.571 s)`. This is a historical record of a measurement and must not be deleted. |
| F-25 | `**Status:** verified live over HTTP on port 9101, after a \`SIGKILL\`` | `W42.6-LOCAL-ALPHA-V0.3.md:5` | The same document says `# → http://localhost:9091/workbench/` at `:256`; `matrix-core/src/main/resources/application.properties:31` → `quarkus.http.port=${QUARKUS_HTTP_PORT:9091}`. The port `9101` appears nowhere in the repository. | `SUPERSEDED` | Replace with `**Status:** verified live over HTTP, after a \`SIGKILL\`` — or, if the run really used 9101, state the `QUARKUS_HTTP_PORT` override explicitly. |
| F-26 | `the host's own address \`192.168.31.237:9098\` is **refused**` | `RECON-W42-BENCHMARKS.md:346` | `wal/SESSION_WAL.md:3126` confirms the live log said `Listening on: http://127.0.0.1:9098`, i.e. the run overrode `QUARKUS_HTTP_PORT`; the committed default is 9091 (`application.properties:31`) | `CURRENT` (as a run record) | Add the qualifier: `Verified live on the W42.7 run, which bound port 9098 via \`QUARKUS_HTTP_PORT\`; the committed default is 9091.` |
| F-27 | `36 REST endpoints` / `All 36 endpoints are open` / `36 endpoints` / `10 of 36 endpoints have **no UI caller**` | `W42-RECONNAISSANCE.md:54`; `W42-RISK-REGISTER.md:94`; `W42-INTEGRATION-MATRIX.md:48,210,212`; `W42-ARCHITECTURE-REALITY-MAP.md:44` | `matrix-core/src/main/java/io/matrix/knowledge/LearningWorkbenchResource.java` declares **52** HTTP method annotations (`grep -c "@GET\|@POST\|@PUT\|@DELETE"` → 52) under `@Path("/v1/workbench")`. | `SUPERSEDED` | Replace every `36` with `52` and re-derive the "no UI caller" figure (the current claim `10 of 36` cannot be carried over to 52 without re-counting). |
| F-28 | `9 tabs, 10 buttons` / `a 9-tab UI` / `9 tabs, 10 buttons` | `W42-ARCHITECTURE-REALITY-MAP.md:20`; `W42-INTEGRATION-MATRIX.md:48,211`; `W42-RECONNAISSANCE.md:54` | `matrix-core/src/main/resources/META-INF/resources/workbench/index.html` carries **13** `data-tab` attributes (`ask, assess, corpus, dashboard, experiments, goals, graph, knowledge, persistence, progress, reasoning, sessions, teach`). | `SUPERSEDED` | Replace with `13 tabs`. |
| F-29 | `**All eleven of the mission's §9 views now carry content** … Thirteen tabs, and none of them is empty` | `RECON-W42-BACKLOG.md:246` | Matches the 13 `data-tab` values exactly | `CURRENT` | none |
| F-30 | `the dialogue cases are 133 English and 35 Russian` | `RECON-W42-BENCHMARKS.md:432`; `RECON-W42-BENCHMARKS.md:468` | `matrix-core/src/main/resources/benchmark/w42-dialogue-cases-v1.json` → `cases: 168`, `langs: {'en': 133, 'ru': 35}`, `categories: 22` | `CURRENT` | none |
| F-31 | `≥ 100 reproducible multilingual test cases \| **MEASURED: 168**` | `RECON-W42-BENCHMARKS.md:438` | 168 cases in the JSON | `CURRENT` | none |

### E. The benchmark instrument itself

| # | Claim | Document + line | Code evidence | Status | Recommended correction |
|---|---|---|---|---|---|
| F-32 | `\| \`FACTUAL\` \| a direct question the knowledge covers \| 7 \|` | `RECON-W42-BENCHMARKS.md:19` | `w42-benchmark-v1.json` → FACTUAL **6** items. The file's own per-category table at `:51` says `FACTUAL \| 6`. | `SUPERSEDED` | Replace `7` with `6`. |
| F-33 | `\| \`CROSS_LINGUAL\` \| a Russian question against English knowledge \| 5 \|` | `RECON-W42-BENCHMARKS.md:25` | `w42-benchmark-v1.json` → CROSS_LINGUAL **4** items (`ru-cross-1..4`); the file's own table at `:57` says `4`. | `SUPERSEDED` | Replace `5` with `4`. |
| F-34 | `\| \`UNANSWERABLE\` \| a question the workspace cannot support \| 5 \|` | `RECON-W42-BENCHMARKS.md:26` | `w42-benchmark-v1.json` → UNANSWERABLE **4** items; the file's own table at `:58` says `4`. | `SUPERSEDED` | Replace `5` with `4`. |
| F-35 | The category table of §1 sums to the stated total | `RECON-W42-BENCHMARKS.md:15` — "34 items, 12 categories, two languages, two workspaces" | The counts in §1's own table (lines 19–30) sum to **37**, not 34. The §1.1 table (lines 51–62) sums to 34 and matches the JSON exactly. | `SUPERSEDED` | After F-32/33/34 the §1 table sums to 34 and agrees with §1.1 and with the JSON. |
| F-36 | `34 items, 12 categories, 2 languages` / `w42-benchmark-v1.json` (34 items) | `RECON-W42-STATUS.md:87`; `RECON-W42-DECISIONS.md:10`; `RECON-W42-BENCHMARKS.md:15` | JSON census: `total items: 34`, `categories: 12`, `langs: {'en': 28, 'ru': 6}` | `CURRENT` | none |
| F-37 | `the Russian items (CROSS_LINGUAL) \| 0/5` and `The three weakest cells are Russian (0/5), paraphrase (0/2) and false premise (0/2)` | `RECON-W42-STATUS.md:148`; `RECON-W42-BENCHMARKS.md:91`; `RECON-W42-BENCHMARKS.md:182` | The Russian set is **6** items: `ru-cross-1..4` (answerable), `ru-factual-1` (answerable), `ru-unanswerable-1` (not answerable) — 5 answerable. `0/5` therefore counts the answerable Russian items only, and is not false, but the document nowhere says "of the five answerable Russian items". | `UNVERIFIED` (ambiguous, not false) | Replace with `the Russian items (5 answerable of 6) \| 0/5`. |
| F-38 | `the Russian, all of it \| 0/5 \| 0/5 \| **4/5**` (W42.3) vs `the Russian cross-lingual score moves from 4 of 5 to 5 of 5` (D-013) vs `the W42 benchmark's Russian items are 6 (5 correct, 1 refused correctly)` (§14 row 3) | `RECON-W42-BENCHMARKS.md:242`; `RECON-W42-DECISIONS.md:255`; `RECON-W42-BENCHMARKS.md:432` | These are three different figures for the same Russian score. The D-013 wording additionally conflates the **Russian set (5 answerable)** with the **CROSS_LINGUAL category (4 items)** — the §10 table at `:287` correctly reports CROSS_LINGUAL `3/4 → 4/4`, which is not a "4 of 5 → 5 of 5". | `SUPERSEDED` | In D-013 replace "the Russian cross-lingual score moves from 4 of 5 to 5 of 5" with "the Russian answerable items move from 4 of 5 to 5 of 5; the CROSS_LINGUAL category is 4 of 4". In §11 row 3 name the wave: `MEASURED (W42.7): the CROSS_LINGUAL category is 4 of 4 and the five answerable Russian items are 5 of 5; ru-unanswerable-1 is refused correctly`. |
| F-39 | `the benchmark holds two \`FALSE_PREMISE\` items` and both were answered | `RECON-W42-BACKLOG.md:132`; `RECON-W42-BENCHMARKS.md:59` | `w42-benchmark-v1.json` → FALSE_PREMISE **2** items | `CURRENT` | none |
| F-40 | `the answer accuracy \| 0.792 \| **1.000**` and `the abstention quality \| 0.900 \| **0.900**` (W42.7 result) | `RECON-W42-BENCHMARKS.md:283-284` | Not re-measured in this pass (running the benchmark requires a live store and a JVM run; the AGENTS.md OOM constraint applies to the full suite, not this, but the instrument was not executed here). The numbers are **not** the numbers of the latest WAL/SESSION entries — see F-41. | `UNVERIFIED` | Annotate the §10 heading with the benchmark identity: `MEASURED on w42-benchmark-v1.json (34 items)`. See F-41 — the WAL's latest figures are on a different file. |
| F-41 | `точность 1.000 ✓✓ и абстиненцию 0.889 ✓✓` on a 65-item candidate; `the candidate's accuracy stayed at 0.915`; `0.915 → 0.979` | `wal/SESSION_WAL.md:4164-4167`, `:4200-4201`, `:4221-4222`; `SESSION.md:7728-7729`, `:7743-7746` | The 65-item set is a **different file**: `matrix-core/src/main/resources/benchmark/w42-benchmark-v2-candidate.json` → 65 items (34 en / 31 ru), 47 answerable. The W42 documents never mention this file. So `0.900` (v1) and `0.889` (v2-candidate) are not the same measurement, and neither document says so. | `UNVERIFIED` | In `RECON-W42-BENCHMARKS.md` add one line under §1: `The v2 CANDIDATE benchmark (65 items, \`w42-benchmark-v2-candidate.json\`) exists and is NOT enabled; its latest measurement is 0.979 accuracy / 0.889 abstention (WAL, 2026-11-02). Every number in this file that is not marked "v2-candidate" is on v1 (34 items).` Without this line, `0.900` and `0.889` read as a contradiction. |

### F. Component / "is it live" claims

| # | Claim | Document + line | Code evidence | Status | Recommended correction |
|---|---|---|---|---|---|
| F-42 | `\| \`ModelToMatrix\` \| \`INTEGRATED (transitively, W42.9)\` — it is used by the live distillation ledger \| **Measured 2026-11-02.** \`ModelToMatrix\` has 0 production sites of its own, but it is called by \`DistillationLedger\`, and that ledger is injected into the live \`TrueDistillationFactory\` and written to there.` | `W42-INTEGRATION-MATRIX.md:196` | The direction is reversed. `grep -n "ModelToMatrix" matrix-brain-runtime/.../DistillationLedger.java` → **no match** (exit 1). The dependency runs the other way: `ModelToMatrix.java:73,91,101` take a `DistillationLedger ledger` **parameter** and write to it at `:149`. The repository's own census (`python3 scripts/integration-census.py`) reports: `ModelToMatrix: counted as ModelToMatrix — 0 mention(s), 0 construction(s), 0 call file(s), 1 test file(s)`. | `SUPERSEDED` | Replace the row's status with `\`DISCONNECTED\`` and the evidence with: `**Measured 2026-11-02.** \`ModelToMatrix\` has 0 production mentions, 0 constructions and 0 call files (census). The dependency runs the other way: \`ModelToMatrix\` receives a \`DistillationLedger\` and writes to it — the ledger does not call the pipeline. The live \`TrueDistillationFactory\` writes the ledger directly.` |
| F-43 | `\`DistillationLedger\` and \`ModelToMatrix\` are already live (the ledger is written by the live \`TrueDistillationFactory\` …)` | `RECON-W42-STATUS.md:232` | `TrueDistillationFactory` is constructed in production at `matrix-api-gateway/src/main/java/io/matrix/api/MinimalHttpServer.java:220`; it writes the ledger at `TrueDistillationFactory.java:148`. The `DistillationLedger` half is `CURRENT`; the `ModelToMatrix` half is false (F-42). | `SUPERSEDED` | Replace with: `\`DistillationLedger\` is already live (it is written by the live \`TrueDistillationFactory\`, constructed at \`MinimalHttpServer.java:220\`, and read back into the mind's state). \`ModelToMatrix\` is **not** live — 0 production construction sites (see the integration matrix).` |
| F-44 | `\`CreditLedger\` and \`LicenseManager\` are orphans but NOT duplicates` | `RECON-W42-STATUS.md:233`; `W42-INTEGRATION-MATRIX.md:198` | Census: `CreditLedger: 0 mention(s), 0 construction(s), 0 call file(s)`; `LicenseManager: 0 mention(s), 0 construction(s), 0 call file(s)`. The brain-runtime `CreditLedger` is distinct from `matrix-billing`'s. | `CURRENT` | none |
| F-45 | `Six of them are DUPLICATES of live engines (\`CreditLedger\`, \`AuditChain\`, \`GpuKernelEngine\`, \`ModelToMatrix\`, \`DistillationLedger\`, and the \`FederationRegistry\` …)` | `RECON-W42-BACKLOG.md:245` | Directly contradicted by `W42-INTEGRATION-MATRIX.md:198`, which states the runtime's `CreditLedger` and `LicenseManager` are "orphans, **not** duplicates of live engines". The census confirms 0 production constructions for `CreditLedger`. | `SUPERSEDED` | Replace the list with: `Six of them are DUPLICATES of live engines (\`AuditChain\`, \`GpuKernelEngine\`, \`ModelToMatrix\`, \`DistillationLedger\`, the \`FederationRegistry\` and …); \`CreditLedger\` and \`LicenseManager\` are orphans, NOT duplicates — see the integration matrix.` |
| F-46 | `\`ConsciousBrain\` has **0 production construction sites** (67 in \`src/test\`)` / `The \`ConsciousBrain\` cluster: product or research artefact? 67 test sites, 0 production sites` | `W42-RISK-REGISTER.md:18`; `RECON-W42-BACKLOG.md:208` | The **0 production construction sites** half is `CURRENT` (census: `ConsciousBrain: … 0 construction(s)`; `grep -rn "new ConsciousBrain(" */src/main` → 0). The **67** is a construction-*occurrence* count that has drifted: HEAD has **69** `new ConsciousBrain(` occurrences across **19** test files, and the census reports **24** test files mentioning it. The document does not say which of the three it means. | `UNVERIFIED` | Replace `67 in \`src/test\`` with `0 production construction sites; 69 construction sites in 24 test files (census: 24 test files mention it, 19 construct it)`. |
| F-47 | `The \`ConsciousBrain\` cluster … 67 test sites, 0 production sites` and the census method paragraph | `W42-INTEGRATION-MATRIX.md:110` | same as F-46 | `UNVERIFIED` | same correction as F-46, applied at `:110`. |
| F-48 | `\| \`ConsciousBrain\` \| \`DISCONNECTED\` … \| **0 production construction sites; 67 test sites**` | `W42-INTEGRATION-MATRIX.md:110` (status) | `CURRENT` on the status | `CURRENT` | none on the status; apply F-46/F-47 to the evidence cell |
| F-49 | `\| \`AuditChain\` (brain-runtime) \| \`INTEGRATED (W42.9)\` …` | `W42-INTEGRATION-MATRIX.md:199` | Census: `AuditChain: 4 mention(s), 2 construction(s), 2 call file(s)`; constructions at `EpisodicLog.java:181` and `RealAuditService.java:20`. The endpoint exists: `MinimalHttpServer.java:326` → `http.createContext("/v1/audit/promotions", this::handlePromotionDecisions)`, handler at `:536`; the test is `matrix-api-gateway/src/test/java/io/matrix/api/PromotionDecisionsEndpointTest.java`. | `CURRENT` | none |
| F-50 | `\| \`SleepConsolidator\` (brain-runtime) \| \`DISCONNECTED\` — **but its MECHANISM is integrated** … the census counts 0 production files for it` | `W42-INTEGRATION-MATRIX.md:201` | Census: `SleepConsolidator: 0 mention(s), 0 construction(s), 0 call file(s)` | `CURRENT` | none |
| F-51 | `\| \`GpuKernelEngine\` \| \`DISCONNECTED\` \| 0 production sites` | `W42-INTEGRATION-MATRIX.md:195` | Census: `GpuKernelEngine: 0 mention(s), 0 construction(s), 0 call file(s)`; `RealGpuKernelEngine` **is** constructed in production at `MinimalHttpServer.java:277` | `CURRENT` | none |
| F-52 | `\| \`ErrorDrivenLearner.applyDecay\` \| \`PRODUCTION_PATH_VERIFIED\` (W42.7)` | `W42-INTEGRATION-MATRIX.md:115` | `matrix-core/src/main/java/io/matrix/knowledge/KnowledgeWorkbench.java:993-994` calls `ErrorDrivenLearner.totalActiveBindings(state)` and `ErrorDrivenLearner.applyDecay(state, stream, …)`; `KnowledgeConsolidator.java:206` names it as the live engine | `CURRENT` | none |
| F-53 | `**The \`ConsciousBrain\` cluster and the nine shadow classes are an owner's decision**` and `the remaining seven shadow classes, one at a time` | `RECON-W42-BACKLOG.md:195`, `:245` | The count of remaining shadow classes does not match the integration matrix's per-class rows (F-42, F-45 show two of the named six are not duplicates and one is not live at all). | `UNVERIFIED` | Restate from the integration matrix rather than a hand count: `the remaining shadow classes, one at a time — the matrix's rows are the authority; two of the six named as duplicates are orphans (not duplicates) and \`ModelToMatrix\` has no production caller.` |

### G. Risk-register statuses that the code has overtaken

The register's own §5 heading says it "corrects two of that register's verdicts"; the
entries below are stale verdicts for risks the waves closed.

| # | Claim | Document + line | Code evidence | Status | Recommended correction |
|---|---|---|---|---|---|
| F-54 | `R1 \| **The Persistence screen tells the operator to back up the store incorrectly.** … \| **OPEN — fix in W42.0**` | `W42-RISK-REGISTER.md:29` | `KnowledgeWorkbench.java:1644-1655` — the backup state now names the three files (`-wal`, `-shm`) and says the store is in WAL mode. `W42.1-LEARNING-CORE-REPORT.md:26-36` records the fix and `BackupInstructionTest`. | `SUPERSEDED` | Replace the status with `**FIXED (W42.1)**` and the evidence with `KnowledgeWorkbench.java:1644-1655 names the three files; \`BackupInstructionTest\``. Keep the risk text — it is the historical description of the defect. |
| F-55 | `R4 \| **The contradiction machinery never runs.** … \| **OPEN — one-line wiring**` | `W42-RISK-REGISTER.md:43` | `KnowledgeWorkbench.java:1479` → `store().recordContradiction(conflict);` — a production caller exists | `SUPERSEDED` | Status → `**FIXED (W42.1)**`; evidence → `KnowledgeWorkbench.java:1479`. |
| F-56 | `R5 \| **The derivation verdict is never persisted.** … \| **OPEN — one-line wiring**` | `W42-RISK-REGISTER.md:44` | `KnowledgeWorkbench.java:1473` → `store().recordDerivation(new Derivation(…));` | `SUPERSEDED` | Status → `**FIXED (W42.1)**`; evidence → `KnowledgeWorkbench.java:1473`. |
| F-57 | `R8 \| **The tokenizer every path depends on has zero tests.** … \| **OPEN**` | `W42-RISK-REGISTER.md:47` | `matrix-core/src/test/java/io/matrix/knowledge/TextFeaturesTest.java` exists | `SUPERSEDED` | Status → `**FIXED (W42.1)**`; the risk text stays as the historical description. |
| F-58 | `R9 \| **Retrieval is English-only.** … \| **OPEN — the W42.3 work**` | `W42-RISK-REGISTER.md:48` | `TextFeatures.java:61` — "RECON-W42.3. The text layer's language-dependent rules are a …"; `RECON-W42-BENCHMARKS.md:242` — the Russian moved to 4/5 then 5/5 | `SUPERSEDED` | Status → `**FIXED (W42.3)**`; evidence → `TextFeatures.java:61` and the W42.3 measurement. |
| F-59 | `R10 \| **The learning lifecycle is a computed label.** No stage is stored, no transition is recorded, no legal-transition guard exists. \| **OPEN — the W42.1 work**` | `W42-RISK-REGISTER.md:49` | `KnowledgeStore.java:500` — `CREATE TABLE IF NOT EXISTS learning_state (`; index at `:513-514`; writes at `:2162`, reads at `:2215` | `SUPERSEDED` | Status → `**FIXED (W42.1)**`; evidence → `KnowledgeStore.java:500` (the `learning_state` table), `:2162`, `:2215`. |
| F-60 | `R11 \| **\`HierarchicalMemory\`'s eviction never fires on the live path.** … \| **OPEN**` | `W42-RISK-REGISTER.md:57` | `HierarchicalMemory.java:365-394` — "RECON-W42.1 fixed a defect here… the rule now is what the name says: evict from the lowest level that actually holds something. The entries removed are counted"; `evictedCount` at `:132`, incremented at `:390` | `SUPERSEDED` | Status → `**FIXED (W42.1)**`; evidence → `HierarchicalMemory.java:365-394`. |
| F-61 | `R15 \| **\`SqliteMemoryBackend.compact\` hard-DELETEs rows** — a silent deletion … \| **OPEN**` | `W42-RISK-REGISTER.md:61` | `SqliteMemoryBackend.java:176-198` — "**The entries are not deleted.** … A forgotten entry is MARKED"; `compact` issues `UPDATE memory SET forgotten_at = ?, forgotten_reason = ?` | `SUPERSEDED` | Status → `**FIXED (W42.5)**`; evidence → `SqliteMemoryBackend.java:189-198`. |
| F-62 | `R7 \| **The support score's contradiction field is hard-coded to \`NOT_COMPUTED\`** … \| **OPEN**` | `W42-RISK-REGISTER.md:46` | `KnowledgeRetriever.java:535` → `SupportScore.ContradictionStatus.NOT_COMPUTED` — still true. The line reference in the register (`:224`) is stale. | `CURRENT` | Update the evidence line only: `KnowledgeRetriever.java:535`. |
| F-63 | `R6 \| **The flagship domain has no rules.** \`photosynthesis.pack.json\` contains **0 derivations** (the logic pack has 3) … \| **OPEN**` | `W42-RISK-REGISTER.md:45` | `photosynthesis.pack.json` → `derivations: 0`; `logic.pack.json` → `derivations: 3` | `CURRENT` | none |
| F-64 | `R17 \| **The rebuilt teach-back experiment is unreachable from the interface.** … \`/teach-back-experiment\` has no UI caller` | `W42-RISK-REGISTER.md:70` | `matrix-core/src/main/resources/META-INF/resources/workbench/index.html:925` — `const r = await api('/workspaces/' + WS + '/teach-back-experiment', {`, under the comment at `:915-924` "The REBUILT experiment, not the W40 containment measurement." | `SUPERSEDED` | Status → `**FIXED (W42.1)**`; evidence → `index.html:925`. |
| F-65 | `R18 \| **The W40 lesson-containment path is still the one the interface uses.** Its result feeds \`CapabilityEvaluator\`'s \`KNOWLEDGE\` stage …` | `W42-RISK-REGISTER.md:71` | `KnowledgeWorkbench.java:1162-1167` — `TeachBackExperiment.Result experiment = teachBackExperiment(workspaceId, conceptId, CapabilityEvaluator.DEFAULT_EXPECTED_GAIN); return CapabilityEvaluator.evaluate(conceptId, states, taught, conclusion);` The gate is fed by the **rebuilt** experiment. | `SUPERSEDED` | Status → `**FIXED (W42.1)**`; evidence → `KnowledgeWorkbench.java:1162-1167` and `index.html:925`. |
| F-66 | `the numbers below are kept as the historical record of the path the interface still calls` | `docs-v2/engineering/LEARNING-WORKBENCH.md` §6 (the paragraph preceding the two-run table) | The interface no longer calls the legacy path (`index.html:925`). The guide's own §6 already labels the numbers "**These are the W40 measurements.**" | `SUPERSEDED` | Replace "the path the interface still calls" with "the path the interface used until W42.1". The historical numbers and their "W40 measurements" label stay. |
| F-67 | `\| Documentation: no unresolved contradiction in the current normative documentation \| **8 contradictions found** … \| **NOT MET** — this wave fixes them` | `W42-RISK-REGISTER.md:120` | The eight listed in `W42-RECONNAISSANCE.md:158-167` (D1–D8) are: D1 fixed (`LEARNING-WORKBENCH.md` §6 now says "These are the W40 measurements"), D2 fixed (`DESIGN-70` §0 now says "the two sentences this document opened with were true when it was written and are now historical"), D3/D4 fixed (`HYPOTHESES-NEW.md:164` "IMPLEMENTED (RECON-W42.0)", `:177` "PARTIALLY IMPLEMENTED"), D7 fixed (`application.properties:106` → `matrix.knowledge.assessment=…`), D5 **partly** fixed (see F-68), D6 and D8 open. This pass found **F-01…F-69**. | `SUPERSEDED` | Replace the verdict cell with: `**NOT MET** — 8 contradictions were found and closed in W42.0/W42.1; the 2026-11-02 verification pass found a further 69 (see \`W42-VERIFICATION-DOCS.md\`).` |
| F-68 | `\| 7 \| ConsciousBrain integration \| **DISCONNECTED** (corrected by RECON-W42.0 — the verdict was wrong) \| \`consciousness/CognitiveGenesisProfileBuilder.java:3,23-26\` builds a real \`CognitiveGenesisProfile\` from a \`ConsciousBrain.CycleReport\` — the brain's cycle output is consumed by the memory layer, so it is not dead code. \|` | `docs-v2/engineering/RISK-REGISTER.md:42` | The **verdict** was corrected to `DISCONNECTED`, but the **evidence sentence still asserts the opposite** ("the brain's cycle output is consumed by the memory layer, so it is not dead code") — the very claim `W42-RISK-REGISTER.md:18` records as false ("0 production construction sites … the chain terminates in tests"). The census confirms 0 production constructions. | `SUPERSEDED` | Replace the evidence cell with: `\`ConsciousBrain\` has 0 production construction sites (census: 0 constructions, 11 production mentions, 24 test files) and \`CognitiveGenesisProfileBuilder.fromCycleReport\` has 0 \`src/main\` callers. The chain terminates in tests — see \`W42-RISK-REGISTER.md\` C1.` |
| F-69 | `This wave adds **twenty-six** and closes one.` | `W42-RISK-REGISTER.md:127` | The register carries **35 risk rows** across §2–§6 plus R32 appended after §8 (39 table rows including the four `C` correction rows), because §5 and §6 reuse the ids R27, R28 and R29. `R1`–`R26` is exactly twenty-six — the count ignores R27–R32. | `SUPERSEDED` | After the renumbering (F-70/F-71) replace with the true row count, e.g. `This wave adds **thirty-five** and closes one.` |

### H. The risk-register numbering collision (the requested R31 collision)

| # | Claim | Document + line | Code evidence | Status | Recommended correction |
|---|---|---|---|---|---|
| F-70 | The register uses the ids `R1`…`R32` | `docs-v2/engineering/W42-RISK-REGISTER.md` | The ids actually present, in order (from `grep -oE "^\| (C[0-9]+\|R[0-9]+) \|"`): `C1 C2 C3 C4 R1 R2 R3 R4 R5 R6 R7 R8 R9 R10 R11 R12 R13 R14 R15 R16 R17 R18 R19 R20 R21 R22 R23 R24 R25 **R27 R28 R29** R26 **R27 R28 R29 R30 R31** R32`. **R27, R28 and R29 each appear twice**: once in §5 "The integration and governance risks" (lines 79, 80, 81) and once in §6 "The security risks the mission names (§13.2)" (lines 90, 91, 92). R32 sits outside every section, appended after §8 at line 147. | `SUPERSEDED` | Renumber §6's security risks **R27→R33, R28→R34, R29→R35, R30→R36, R31→R37** and move R32 (line 147) into its own row in §5 (or §6). Then the register reads R1…R37 with no duplicates, and every reference elsewhere (`RECON-W42-STATUS.md:113-128`, `RECON-W42-BACKLOG.md`, `RECON-W42-DECISIONS.md`) can be updated mechanically. |
| F-71 | `**R27** \| **The repository's own quality gate is RED**, and has been since before this wave.` (integration/governance section) vs `**R27** \| **Oversized files.** \| **FIXED** in W41` (security section) | `W42-RISK-REGISTER.md:79` vs `W42-RISK-REGISTER.md:90` | two unrelated risks under one id; likewise R28 (`:80` the reverted extraction vs `:91` prompt injection) and R29 (`:81` the shadow classes vs `:92` path traversal) | `SUPERSEDED` | Same renumbering as F-70. This is the mechanical precondition for fixing F-72. |
| F-72 | `**R31** \| **The API surface has no authentication.** All 36 endpoints are open, including the ones that mutate the store. \| **\`REQUIRES_OWNER_DECISION\`**` | `W42-RISK-REGISTER.md:94` | This is the register's R31 — an **authentication** risk. Its endpoint count (36) is itself stale (F-27). | `CURRENT` (as the definition of R31) | Replace `36` with `52`; keep the status `REQUIRES_OWNER_DECISION` (the loopback bind is a mitigation, not authentication — `wal/SESSION_WAL.md:3131-3132` says so explicitly). |
| F-73 | `**Состояние ✓✓** — 425 тестов знания, один старый красный ✓✓ (**R31** ✓✓); живой бенчмарк зелёный ✓✓; кандидат на 65 пунктов даёт точность 1.000 ✓✓ и абстиненцию 0.889 ✓✓` | `wal/SESSION_WAL.md:4200` | In the WAL, `R31` denotes **the old red test** in the knowledge package — `KnowledgeRetrieverTest > a refuted misconception is never returned as an established claim`, described at `wal/SESSION_WAL.md:4169-4170` and re-stated at `:4221`. In the register, **R31 is the missing-authentication risk** (`W42-RISK-REGISTER.md:94`). The same id therefore means two different things in the two documents that are supposed to share it. | `SUPERSEDED` | Two options for the owner — (a) give the WAL's red test its own id outside the R-series (e.g. `T-KNOWLEDGE-01` or `RED-1`) in both WAL occurrences (`:4200`, `:4221`), leaving R31 for authentication; or (b) renumber the register's authentication risk to R37 (per F-70) and note the WAL's `R31` as the register's **old** R31, which no longer exists. **Option (a) is recommended**: the WAL is a historical append-only record and must not be renumbered after the fact — see the mission's §3.4. If (a) is chosen, the two WAL lines get an appended clarifying clause, not a rewrite: `… один старый красный ✓✓ (идентификатор красного теста — см. \`W42-RISK-REGISTER.md\`, раздел «The red tests») …`. |
| F-74 | The register's §2–§6 headings and the WAL/SESSION assume a shared, stable R-numbering | `W42-RISK-REGISTER.md:9-10` — "Status vocabulary: the W41 register's (`FIXED`, `VERIFIED_NOT_A_DEFECT`, `OPEN`, `INTENTIONAL_LIMITATION`, `REQUIRES_RFC`, `BLOCKED`, `DEFERRED`)" | The W42 register's own vocabulary (FIXED / OPEN / PARTLY CLOSED / REQUIRES_OWNER_DECISION) does not include `VERIFIED_NOT_A_DEFECT`, `REQUIRES_RFC`, `BLOCKED` or `DEFERRED` — yet §6 at `:91-92` uses `VERIFIED_NOT_A_DEFECT` and `:93` uses `NOT YET ASSESSED`, which is in neither vocabulary. | `SUPERSEDED` | Either extend the vocabulary line at `:9-10` to list the statuses actually used (`VERIFIED_NOT_A_DEFECT`, `NOT YET ASSESSED`, `PARTLY CLOSED`), or normalise the cells to the seven §3.4 values. The §3.4 vocabulary is the mission's requirement and should win. |

### I. The literal gate (D-015 / R27 / R28)

| # | Claim | Document + line | Code evidence | Status | Recommended correction |
|---|---|---|---|---|---|
| F-75 | `The quality gate's literal counter is **263** against the accepted baseline of **270** (141 shell → 78, 198 Java → 185), and the baseline was NOT updated.` | `RECON-W42-STATUS.md:225-226` | Measured: `bash scripts/quality-gate.sh` → `literal candidates: 263 (shell 78, java 185)` and `PASS: 263 candidate(s), at or below the accepted baseline of 270.` The baseline file `docs-v2/quality/quality-baseline.txt` still reads `literal_candidates=270`. | `CURRENT` | none |
| F-76 | `The gate still exits 1, and now only on the frozen zones (785 diff lines in \`.github/\`, 14 in \`AGENTS.md\` against \`b144563e\`)` | `RECON-W42-STATUS.md:227-228` | **Measured exit code is 0**: `bash scripts/quality-gate.sh; echo $?` → `0`. The gate's own exit logic (`scripts/quality-gate.sh:261-282`) exits 1 only for a missing baseline, an unreadable baseline, or a literal count **above** the baseline. The `FROZEN-1` findings (`:210`, "785 diff lines", "14 diff lines") are **reported** but do not fail the gate. | `SUPERSEDED` | Replace with: `The gate **exits 0** — the literal check is GREEN (263 ≤ 270) and the frozen-zone findings are reported but do not fail the gate (\`scripts/quality-gate.sh:261-282\`). The frozen-zone diffs (785 lines in \`.github/\`, 14 in \`AGENTS.md\` against \`b144563e\`) remain the owner's decision.` |
| F-77 | `\| **PARTLY CLOSED — the literal check is GREEN (D-015 fulfilled on 2026-11-02); the frozen-zone diffs are \`REQUIRES_OWNER_DECISION\`**` | `W42-RISK-REGISTER.md:79` | Same as F-76: the gate exits 0. The risk's *description* ("the literal candidates were 339 … the count is 263 … BELOW the accepted baseline of 270 — the literal check of the gate is GREEN") is accurate; only the implication that the gate is still RED is wrong. | `SUPERSEDED` | Status → `**CLOSED — the gate exits 0 (263 ≤ 270); the frozen-zone diffs are reported but do not fail the gate and remain \`REQUIRES_OWNER_DECISION\`**.` |
| F-78 | `the literal candidates rose from the accepted 270 to **346**` | `RECON-W42-DECISIONS.md:288` | Three different pre-fix counts appear across the W42 documents: **346** (D-015), **341** (`RECON-W42-BACKLOG.md:291`), **339** (`W42-RISK-REGISTER.md:79`, which splits it as 141 shell + 198 Java — and 141+198 = 339, internally coherent). 346 − 270 = 76, which is the "76 new literals" D-015 refuses to accept; 339 − 270 = 69. The two numbers cannot both be right. | `SUPERSEDED` | Pick one and use it everywhere. Recommended: **339** — it is the only figure with a stated decomposition (141 shell + 198 Java) that matches the post-fix split's shape (78 shell + 185 Java). Replace `RECON-W42-DECISIONS.md:288` with `the literal candidates rose from the accepted 270 to 339 (141 in the shell scripts, 198 in the Java of matrix-core)`, and replace `RECON-W42-BACKLOG.md:291` with the same figure and decomposition. Then the "76 new literals" phrase at `W42-RISK-REGISTER.md:79` becomes "69 new literals". |
| F-79 | `**The measurement.** 341 candidates in 299 lines over 167 files.` | `RECON-W42-BACKLOG.md:291` | Same as F-78. The "167 files" also conflicts with `W42-RISK-REGISTER.md:80`, which says "341 candidates sit in 299 lines of production Java over 167 files" for the *Java* only — while the 339 figure is split across the shell scripts and the Java. The two sentences describe different scopes under the same number. | `SUPERSEDED` | Restate with the scope explicit: `The measurement (before the extraction): 339 candidates — 141 in the shell scripts, 198 in the Java of matrix-core.` Drop the "299 lines over 167 files" clause from the BACKLOG or attribute it to the Java-only pass. |
| F-80 | `341 candidates sit in 299 lines of production Java over 167 files` (the blind extraction's work list) | `W42-RISK-REGISTER.md:80` | Same scope ambiguity as F-79. | `SUPERSEDED` | Same correction as F-79, applied at `:80`. |

### J. Decisions and the backlog

| # | Claim | Document + line | Code evidence | Status | Recommended correction |
|---|---|---|---|---|---|
| F-81 | `\| 7.5 \| **\`ConsciousBrain\` and the nine shadow classes** are documented and unused \| **the owner's decision (2026-11-02): wire them to the live engines.** Two are wired.` | `RECON-W42-BACKLOG.md:245` | "Two are wired" matches `RECON-W42-STATUS.md:243-246` (`AuditChain`) and the integration matrix's `ErrorDrivenLearner` row. The rest of the row is contradicted by F-45. | `CURRENT` on the first clause; see F-45 for the rest | Apply F-45's correction. |
| F-82 | `\| 7.6 \| **The interface's views** \| **CLOSED in W42.7.** … Thirteen tabs, and none of them is empty` | `RECON-W42-BACKLOG.md:246` | 13 `data-tab` values in the workbench `index.html`; each maps to one of the eleven §9 views + Progress + Teach | `CURRENT` | none |
| F-83 | `**Left, stated plainly:** the false-premise question is still answered ✓ ✓; the transfer items are answered without the conclusion ✓ ✓; the delayed teach-back phase fails ✓ ✓; **the consolidation is still the unreachable W41 component** — its result is discarded and the compaction still hard-deletes …` | `RECON-W42-BACKLOG.md:105-112` (W42.4 section) | The consolidation is now live (`KnowledgeConsolidator.java:206`, `KnowledgeWorkbench.java:993-994`) and `SqliteMemoryBackend.compact` marks tombstones rather than deleting (`SqliteMemoryBackend.java:189-198`, F-61). The false-premise detector and the transfer items were fixed in W42.5/W42.7 (`RECON-W42-BENCHMARKS.md:268,288`). | `SUPERSEDED` | Replace the four stale clauses with: `**Left, stated plainly:** the delayed teach-back phase fails ✓ ✓; the selector's choices are not measured against an alternative ✓ ✓. (The false-premise detector, the transfer items and the consolidation were delivered in W42.5/W42.7 — see the W42.5 section below.)` The section is the W42.4 report's contemporaneous record, so keep the original wording in a `HISTORICAL` note rather than deleting it silently. |
| F-84 | `\| 4.5 \| **Reversible forgetting with tombstones** wired into the memory path \| \`SqliteMemoryBackend.compact\` hard-DELETEs; \`TombstoneService\` is unwired beside it` | `RECON-W42-BACKLOG.md:125` (in the section headed "W42.4 (superseded — the original plan)") | The baseline column of a superseded plan — the compaction now marks tombstones (F-61) | `HISTORICAL` | Add the date and status to the section heading so the reader knows the baseline is a past state: `## W42.4 (superseded — the original plan) — the baselines below are the state at 2026-11-01, \`HISTORICAL\``. No line change. |
| F-85 | `\| 5.4 \| **The \`ConsciousBrain\` cluster: product or research artefact?** 67 test sites, 0 production sites` | `RECON-W42-BACKLOG.md:208` (in the "W42.5 (superseded — the original plan)" section) | Same as F-46 | `HISTORICAL` (the plan's baseline) + `UNVERIFIED` (the number) | Add the same `HISTORICAL` marker to the section heading; correct the "67" per F-46. |
| F-86 | `**The owner's D-015 is fulfilled.**` | `RECON-W42-STATUS.md:225` | D-015 (`RECON-W42-DECISIONS.md:283-296`) is fulfilled on the literal count (263 ≤ 270) — but its second half ("the frozen zones are the owner's … their diff is a separate question from the literals and is not touched here") is consistent with the measured exit 0. | `CURRENT` | none |
| F-87 | `**The shadow census is measured, not assumed.**` | `RECON-W42-STATUS.md:230-236` | The census exists and is reproducible (`scripts/integration-census.py`), but one of the census's own conclusions is wrong (F-42, F-43). | `SUPERSEDED` | Apply F-42/F-43; the sentence "measured, not assumed" is then true. |

### K. The corrupted table row and the §11 row 18

| # | Claim | Document + line | Code evidence | Status | Recommended correction |
|---|---|---|---|---|---|
| F-88 | `\| 18 \| a reproducible build \| **MEASURED (W42.7): the COMPILATION and the ARCHIVE** \| two clean builds produce **2 413 byte-identical class files** (the hash \`8ea0474b…\`) **and the same jar** (\`9b17cf2e…\`) … The build metadata is now written by hand, sorted, with no timestamp: a function of the sources alone \|ifferent hashes, and the cause is not yet explained. The build is pinned — Java 25, Gradle 9.6.0, …` | `RECON-W42-BENCHMARKS.md:447` | The cell contains a **paste corruption**: a stray `\|ifferent hashes, and the cause is not yet explained.` (the tail of a *different*, earlier sentence) wedged into the middle of the cell, right after "a function of the sources alone". The paragraph immediately below the table (`:449-456`) states the row is closed and names the two causes — so the table cell and the paragraph contradict each other, and the cell's own trailing fragment ("the cause is not yet explained") contradicts its leading "**MEASURED (W42.7)**". The class-file count, the two hashes and the toolchain pins were **not** re-verified in this pass (no clean build was run). | `SUPERSEDED` | Delete the fragment `\|ifferent hashes, and the cause is not yet explained.` from the cell, so the cell ends at "a function of the sources alone". Then the cell agrees with the paragraph at `:449-456`. The numbers themselves stay marked as measured in W42.7 and are **not** re-verified here. |
| F-89 | `\| 7 \| 100 % provenance \| **MEASURED: 1.000** \| the evidenceBackedShare: 1.0 — 17 claims, 17 with evidence, 18 evidence links, 4 sources` | `RECON-W42-BENCHMARKS.md:436` | `photosynthesis.pack.json` → `claims: 17`, `sources: 4`; `LEARNING-WORKBENCH.md:40-43` — "8 concepts, 17 claims, 4 sources, 18 evidence links, 13 representations across four levels of abstraction, and one recorded revision" | `CURRENT` | none |
| F-90 | `\| 5 \| 100 % recovery after a forced termination \| **MEASURED** \| the twelve-step checklist, \`SIGKILL\` of the application's own JVM: 13 concepts, 19 representations, 24 claims and the imported account survive` | `RECON-W42-BENCHMARKS.md:434` | A live-run observation; the same figures appear at `RECON-W42-STATUS.md:239-241` for the W42.9 re-verification. Not re-run in this pass. | `UNVERIFIED` | Add `re-verified 2026-11-02` with the run's evidence pointer; no figure change. |
| F-91 | `the twelve steps verified over HTTP, and the journey survives a \`SIGKILL\` with 469 712 bytes of WAL unwritten` | `RECON-W42-STATUS.md:21`; `RECON-W42-BACKLOG.md:218-219` | A live-run observation; the WAL backup experiment measured 737 512 bytes against a 278 528-byte database file (`RECON-W42-BENCHMARKS.md:166-167`). 469 712 is a different store, at a different moment. | `UNVERIFIED` | No change to the figures; add the run date and the log pointer, and note that the WAL size is store-specific. |

### L. Documents that state the current wave

| # | Claim | Document + line | Code evidence | Status | Recommended correction |
|---|---|---|---|---|---|
| F-92 | `docs-v2/engineering/PLAN.md` is the file the session procedure reads to "Определить текущую волну и ответственного" | `AGENTS.md` §"Процедура сессии", step 2; `AGENTS.md` §"Один заход — один значимый шаг" ("Большие задачи разбиваются на волны (см. `engineering/PLAN.md`)") | `grep -n "W42\|RECON-W42" docs-v2/engineering/PLAN.md` → **no match**. The file's last wave section is "Wave W87-W91 … (Sep 14 2026)" and "W92+ Future work" (lines 143–156); it ends with W31/W32 sections. The current wave (W42, 2026-11-01/02) appears **nowhere** in the file the procedure names. | `SUPERSEDED` | Add a W42 section at the head of `docs-v2/engineering/PLAN.md` recording the waves W42.0–W42.9 with their status and the open items (X.1–X.6 from `RECON-W42-BACKLOG.md:252-257`). This is the single highest-leverage fix in this registry: as long as PLAN.md names W31/W32, the session procedure points the next session at a stale wave. |
| F-93 | `AGENTS.md` requires the W41 register and the W42 register to be read together | `docs-v2/engineering/W42-RISK-REGISTER.md:4-7` | `docs-v2/engineering/RISK-REGISTER.md` still exists and its verdicts (F-68) are not all reconciled | `CURRENT` | none |

---

## CONTRADICTIONS BETWEEN DOCUMENTS

Each pair is listed with **both sides quoted by file:line**.

### C-1 — The wave-status table contradicts itself (`RECON-W42-STATUS.md`)

| Side | Quote |
|---|---|
| A | `docs-v2/engineering/RECON-W42-STATUS.md:16` — `\| **W42.2 — Universal knowledge acquisition** \| **DONE (the core)** \| …` |
| B | `docs-v2/engineering/RECON-W42-STATUS.md:17` — `\| W42.2 — Universal knowledge acquisition \| not started \| \|` |

Same for three further waves:

| Side | Quote |
|---|---|
| A | `RECON-W42-STATUS.md:19` — `\| **W42.4 — Learning and assessment** \| **DONE** \| …` |
| B | `RECON-W42-STATUS.md:22` — `\| W42.4 — Learning and assessment \| not started \| \|` |
| A | `RECON-W42-STATUS.md:20` — `\| **W42.5 — Cognitive integration** \| **DONE** \| …` |
| B | `RECON-W42-STATUS.md:23` — `\| W42.5 — Cognitive integration \| not started \| \|` |
| A | `RECON-W42-STATUS.md:21` — `\| **W42.6 — Local Alpha v0.3** \| **DONE** \| …` |
| B | `RECON-W42-STATUS.md:24` — `\| W42.6 — Local Alpha v0.3 \| not started \| \|` |

**Tie-breaker:** `RECON-W42-BACKLOG.md:38` (`W42.2 … **the core is DONE**`), `:94` (`W42.4 … **DONE**`), `:183` (`W42.5 … **DONE**`), `:214` (`W42.6 … **DONE**`), and the existence of the four report files. **The "not started" rows are wrong.** (F-01…F-09.)

### C-2 — The targeted-suite counts disagree with each other

| Side | Quote |
|---|---|
| A | `RECON-W42-STATUS.md:105` — `\| Targeted suites \| **121 classes, 1 212 tests, 0 failures** \|` (five-package selector) |
| B | `RECON-W42-BENCHMARKS.md:123` — `\| Targeted suites \| **164 classes / 1 473 tests / 0 failures** (2026-11-02) \|` (seven-package selector) |
| C | `W42.6-LOCAL-ALPHA-V0.3.md:258` — `# the targeted suites (144 classes, 1341 tests)` |

**Measured at HEAD:** five packages → **164 classes / 1 346 `@Test`**; seven packages → **170 classes / 1 401 `@Test`**; knowledge package → **68 classes / 425 `@Test`**. A's 121 classes is impossible (164 classes exist). B's class count belongs to the five-package set, not the seven-package command printed beside it. C matches neither. (F-10…F-13.)

### C-3 — The knowledge-package test count

| Side | Quote |
|---|---|
| A | `RECON-W42-BENCHMARKS.md:126` — `\| The knowledge package \| 27 test classes, **214 \`@Test\`** \|` |
| B | `wal/SESSION_WAL.md:4178-4179` — `… дерево снова зелёное (425 тестов знания, один старый красный ✓✓) ✓✓ ✓✓.` |

**Measured:** 68 classes / 425 `@Test` at HEAD. B is right; A is stale. (F-13.)

### C-4 — The mutation-gate count

| Side | Quote |
|---|---|
| A | `RECON-W42-STATUS.md:107` — `\| Mutation gate \| **27 mutants, 25 killed, 2 survivors** (both recorded with findings), 0 invalid, 0 drift \|` |
| B | `RECON-W42-BENCHMARKS.md:125` — `\| Mutation gate \| **27 mutants, 25 killed, 2 survived, 0 invalid, 0 drift** \|` |
| C | `RECON-W42-RISK-REGISTER.md:118` — `\| Mutation testing: all critical mutants killed or justified \| 27 mutants, 25 killed, 2 survivors **both recorded with findings** \| **MET** \|` |
| D | `docs-v2/engineering/MUTATION-REPORT.md:9-13` — `- Mutants generated: **32**` / `- Killed: **30**` / `- Survived: **2**` (run at `2026-11-02T12:11:19Z`, committed at `384e24ff`) |

**Measured:** `git show HEAD:formal/mutations.json` → 32 mutants; the committed report says 32/30/2. A, B and C are stale. (F-19, F-20.)

### C-5 — The formal-gate count, three values in one file

| Side | Quote |
|---|---|
| A | `RECON-W42-STATUS.md:106` — `\| Formal gate \| **12/12 exit 0**, 65 … 396 577 states \|` |
| B | `RECON-W42-STATUS.md:255` — `The formal gate is 13 passed, 0 failed ✓ ✓.` |
| C | `RECON-W42-STATUS.md:260` — `The formal gate is 14 passed, 0 failed ✓ ✓, and the learning machine, the promotion machine and the BRC step all have specifications ✓ ✓.` |

**Measured:** 12 `.tla` specs at HEAD; `LearningStateMachine.tla` and `PromotionStage.tla` are untracked in the working tree. B and C are not reproducible from HEAD. (F-16…F-18.)

### C-6 — The startup time, three values

| Side | Quote |
|---|---|
| A | `RECON-W42-BENCHMARKS.md:122` — `\| Application startup \| **1.571 s** \| the live log: … started in 1.571s \|` |
| B | `RECON-W42-BENCHMARKS.md:430` — `\| 1 \| startup under 5 s \| **MEASURED: 1.428 s** \| the live log: … started in 1.428s \|` |
| C | `W42.1-LEARNING-CORE-REPORT.md:205` — `matrix-core 1.0.0 on JVM (powered by Quarkus 3.38.3) started in 1.595s.` |
| D | `W42-RISK-REGISTER.md:102` — `\| Application startup < 5 s \| **1.571 s** \| **MET** \|` |

A, B and C are inside **one file pair** and each is attributed to "the live log". (F-22…F-24.)

### C-7 — The benchmark's category counts, one file against itself

| Side | Quote |
|---|---|
| A | `RECON-W42-BENCHMARKS.md:19` — `\| \`FACTUAL\` \| a direct question the knowledge covers \| 7 \|` (and `:25` CROSS_LINGUAL `5`, `:26` UNANSWERABLE `5`) |
| B | `RECON-W42-BENCHMARKS.md:51` — `\| FACTUAL \| 6 \| 6 \| 5 \| 0 \|` (and `:57` CROSS_LINGUAL `4`, `:58` UNANSWERABLE `4`) |

**Measured:** `w42-benchmark-v1.json` → FACTUAL 6, CROSS_LINGUAL 4, UNANSWERABLE 4. A sums to 37 items against a stated 34; B sums to 34 and matches the JSON. (F-32…F-35.)

### C-8 — The abstention quality, two benchmarks under one name

| Side | Quote |
|---|---|
| A | `RECON-W42-BENCHMARKS.md:284` — `\| the abstention quality \| 0.900 \| **0.900** (unchanged) \|` (v1, 34 items) |
| B | `wal/SESSION_WAL.md:4166` — `Абстиненция без изменений: 0.889 ✓✓, и не-отказов ровно два ✓✓` (65-item candidate) |
| C | `wal/SESSION_WAL.md:4201` — `… кандидат на 65 пунктов даёт точность 1.000 ✓✓ и абстиненцию 0.889 ✓✓ ✓✓.` |
| D | `SESSION.md:7744-7746` — `The candidate is still not enabled: the abstention is 0.889 against the 0.9 floor ✓✓, because the Russian false-premise item is answered instead of refused ✓✓` |

**Measured:** `w42-benchmark-v2-candidate.json` holds 65 items (34 en / 31 ru); `w42-benchmark-v1.json` holds 34 (28 en / 6 ru). **No W42 document mentions the v2-candidate file at all**, so `0.900` and `0.889` read as the same measurement. (F-40, F-41.)

### C-9 — `ModelToMatrix`: the integration matrix against the census

| Side | Quote |
|---|---|
| A | `W42-INTEGRATION-MATRIX.md:196` — `\| \`ModelToMatrix\` \| \`INTEGRATED (transitively, W42.9)\` — it is used by the live distillation ledger \| **Measured 2026-11-02.** \`ModelToMatrix\` has 0 production sites of its own, but it is called by \`DistillationLedger\`, and that ledger is injected into the live \`TrueDistillationFactory\` and written to there. …` |
| B | `RECON-W42-STATUS.md:232` — `\`DistillationLedger\` and \`ModelToMatrix\` are already live (the ledger is written by the live \`TrueDistillationFactory\` and its snapshot is read back into the mind's state).` |
| C | The repository's own census, `python3 scripts/integration-census.py` → `ModelToMatrix: counted as ModelToMatrix — 0 mention(s), 0 construction(s), 0 call file(s), 1 test file(s)` |

`grep -n "ModelToMatrix" matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/DistillationLedger.java` → **no match**: the ledger does not call the pipeline; `ModelToMatrix.java:73,91,101` take the ledger as a parameter. (F-42, F-43.)

### C-10 — Are the shadow classes "duplicates" or "orphans"?

| Side | Quote |
|---|---|
| A | `RECON-W42-BACKLOG.md:245` — `Six of them are DUPLICATES of live engines (\`CreditLedger\`, \`AuditChain\`, \`GpuKernelEngine\`, \`ModelToMatrix\`, \`DistillationLedger\`, and the \`FederationRegistry\`, whose live counterpart has different semantics and no persistence)` |
| B | `W42-INTEGRATION-MATRIX.md:198` — `\| \`CreditLedger\` (brain-runtime), \`LicenseManager\` \| \`DISCONNECTED\` — **they are orphans, not duplicates of live engines** \| **Measured 2026-11-02.** … Both have 0 production construction sites — they are unwired capability, not duplicated capability …` |
| C | `RECON-W42-STATUS.md:233` — `\`CreditLedger\` and \`LicenseManager\` are orphans but NOT duplicates — they account the brain's operation costs and the feature entitlements, while \`matrix-billing\` accounts customer money and signed keys.` |

B and C agree with the census (`CreditLedger: 0 construction(s)`); A does not. (F-45.)

### C-11 — The interface's teach-back: the risk register against the UI

| Side | Quote |
|---|---|
| A | `W42-RISK-REGISTER.md:70` — `\| R17 \| **The rebuilt teach-back experiment is unreachable from the interface.** The UI still calls the W40 containment measurement … \| **OPEN** \|` |
| B | `W42-RISK-REGISTER.md:71` — `\| R18 \| **The W40 lesson-containment path is still the one the interface uses.** Its result feeds \`CapabilityEvaluator\`'s \`KNOWLEDGE\` stage … \| **OPEN** \|` |
| C | `matrix-core/src/main/resources/META-INF/resources/workbench/index.html:925` — `const r = await api('/workspaces/' + WS + '/teach-back-experiment', {` (comment at `:915-924`: "The REBUILT experiment, not the W40 containment measurement.") |
| D | `matrix-core/src/main/java/io/matrix/knowledge/KnowledgeWorkbench.java:1162-1167` — `TeachBackExperiment.Result experiment = teachBackExperiment(workspaceId, conceptId, CapabilityEvaluator.DEFAULT_EXPECTED_GAIN); … return CapabilityEvaluator.evaluate(conceptId, states, taught, conclusion);` |
| E | `docs-v2/engineering/LEARNING-WORKBENCH.md` §6 — `… the numbers below are kept as the historical record of the path the interface still calls.` |

A, B and E are contradicted by C and D. (F-64, F-65, F-66.)

### C-12 — The risk register against the code it audits (statuses)

| Side | Quote |
|---|---|
| A | `W42-RISK-REGISTER.md:29` — `\| R1 \| … \| **OPEN — fix in W42.0** \|` — vs `KnowledgeWorkbench.java:1644-1655` (the three files are now named) |
| B | `W42-RISK-REGISTER.md:43` — `\| R4 \| **The contradiction machinery never runs.** … \| **OPEN — one-line wiring** \|` — vs `KnowledgeWorkbench.java:1479` |
| C | `W42-RISK-REGISTER.md:44` — `\| R5 \| **The derivation verdict is never persisted.** … \| **OPEN — one-line wiring** \|` — vs `KnowledgeWorkbench.java:1473` |
| D | `W42-RISK-REGISTER.md:47` — `\| R8 \| **The tokenizer every path depends on has zero tests.** … \| **OPEN** \|` — vs `matrix-core/src/test/java/io/matrix/knowledge/TextFeaturesTest.java` |
| E | `W42-RISK-REGISTER.md:49` — `\| R10 \| **The learning lifecycle is a computed label.** No stage is stored … \| **OPEN — the W42.1 work** \|` — vs `KnowledgeStore.java:500` (`CREATE TABLE IF NOT EXISTS learning_state`) |
| F | `W42-RISK-REGISTER.md:57` — `\| R11 \| **\`HierarchicalMemory\`'s eviction never fires on the live path.** … \| **OPEN** \|` — vs `HierarchicalMemory.java:365-394` |
| G | `W42-RISK-REGISTER.md:61` — `\| R15 \| **\`SqliteMemoryBackend.compact\` hard-DELETEs rows** … \| **OPEN** \|` — vs `SqliteMemoryBackend.java:189-198` (`UPDATE memory SET forgotten_at = …`) |

Each row's own evidence column still cites the *pre-fix* file:line. (F-54…F-61.)

### C-13 — The quality gate: does it still exit 1?

| Side | Quote |
|---|---|
| A | `RECON-W42-STATUS.md:227-228` — `The gate still exits 1, and now only on the frozen zones (785 diff lines in \`.github/\`, 14 in \`AGENTS.md\` against \`b144563e\`) — the owner's decision (§16.1).` |
| B | `W42-RISK-REGISTER.md:79` — `**PARTLY CLOSED — the literal check is GREEN (D-015 fulfilled on 2026-11-02); the frozen-zone diffs are \`REQUIRES_OWNER_DECISION\`**` |
| C | Measured: `bash scripts/quality-gate.sh; echo $?` → **`0`**, with `literal candidates: 263 (shell 78, java 185)` and `PASS: 263 candidate(s), at or below the accepted baseline of 270.` The gate's exit-1 paths are only `scripts/quality-gate.sh:263`, `:273` and `:280`; the `FROZEN-1` findings at `:210` are printed, not fatal. |

A and B describe a gate that is still red. It is green. (F-76, F-77.)

### C-14 — The literal-candidate count, three numbers

| Side | Quote |
|---|---|
| A | `RECON-W42-DECISIONS.md:288` — `the literal candidates rose from the accepted 270 to **346**` (and `:295` "accepting 76 new literals") |
| B | `RECON-W42-BACKLOG.md:291` — `**The measurement.** 341 candidates in 299 lines over 167 files.` |
| C | `W42-RISK-REGISTER.md:79` — `the literal candidates were **339** (141 in the shell scripts, 198 in the Java of matrix-core) on 2026-11-02` |
| D | `W42-RISK-REGISTER.md:80` — `341 candidates sit in 299 lines of production Java over 167 files` |

346 − 270 = 76 (A's "76 new literals"), 339 − 270 = 69, and 141 + 198 = 339 (C is the only arithmetically coherent decomposition). (F-78…F-80.)

### C-15 — The risk-register numbering: the register against the WAL (the requested collision)

| Side | Quote |
|---|---|
| A | `wal/SESSION_WAL.md:4200` — `**Состояние ✓✓** — 425 тестов знания, один старый красный ✓✓ (**R31** ✓✓); живой бенчмарк зелёный ✓✓; кандидат на 65 пунктов даёт точность 1.000 ✓✓ и абстиненцию 0.889 ✓✓ ✓✓.` |
| B | `wal/SESSION_WAL.md:4221` — `**Состояние ✓✓** — 425 тестов знания, один старый красный (R31 ✓✓) ✓✓; живой бенчмарк зелёный ✓✓; кандидат на 65 пунктов: точность 1.000 ✓✓, абстиненцию 0.889 ✓✓ ✓✓.` |
| C | `docs-v2/engineering/W42-RISK-REGISTER.md:94` — `\| R31 \| **The API surface has no authentication.** All 36 endpoints are open, including the ones that mutate the store. \| **\`REQUIRES_OWNER_DECISION\`** — the local-alpha posture may be single-user, but the mission's §10.3 says "local autonomy", not "no access control" \| \`LearningWorkbenchResource\` — no \`@RolesAllowed\`, no auth filter. \|` |

**What the WAL's R31 is.** `wal/SESSION_WAL.md:4169-4170` names it: `**НАЙДЕН И ЗАПИСАН СТАРЫЙ КРАСНЫЙ ТЕСТ ✓✓** — \`KnowledgeRetrieverTest > a refuted misconception is never returned as an established claim\` ✓✓ падает и на \`384e24ff\` …`. So in the WAL `R31` = **one old red test** (a failing JUnit test in the knowledge package). In the register `R31` = **missing authentication on the API surface**. Two different things, one id.

**Compounding it — the register's own ids collide three times:**

| Register location | Id | Subject |
|---|---|---|
| `W42-RISK-REGISTER.md:79` | R27 | the repository's quality gate is RED |
| `W42-RISK-REGISTER.md:90` | **R27** | **Oversized files** |
| `W42-RISK-REGISTER.md:80` | R28 | the reverted blind magic-number extraction |
| `W42-RISK-REGISTER.md:91` | **R28** | **Prompt injection inside an imported document** |
| `W42-RISK-REGISTER.md:81` | R29 | five of the six shadow classes are never called |
| `W42-RISK-REGISTER.md:92` | **R29** | **Path traversal** |
| `W42-RISK-REGISTER.md:93` | R30 | malformed documents, zip bombs, … |
| `W42-RISK-REGISTER.md:94` | R31 | the API surface has no authentication |
| `W42-RISK-REGISTER.md:147` | R32 | the scope match compares in different languages — **outside every section**, appended after §8 |

Measured: `grep -oE "^\| (C[0-9]+|R[0-9]+) \|" docs-v2/engineering/W42-RISK-REGISTER.md | sort | uniq -d` → `R27`, `R28`, `R29`. Total table rows: 39 (four `C` rows + 35 risk rows). (F-70…F-74.)

### C-16 — The plan names a wave the wave does not reach

| Side | Quote |
|---|---|
| A | `AGENTS.md` §"Процедура сессии" step 2 — `Определить текущую волну и ответственного.` and §"Один заход — один значимый шаг" — `Большие задачи разбиваются на волны (см. \`engineering/PLAN.md\`).` |
| B | `docs-v2/engineering/PLAN.md` — `grep -n "W42\|RECON-W42"` returns **nothing**. The last wave sections are `## Wave W87-W91 — Multi-timestep integration metrics … (Sep 14 2026)` (`:143`) and `## W92+ Future work` (`:151`). |

The procedure's pointer to the current wave is a document that predates W42 by two months. (F-92.)

### C-17 — The `ConsciousBrain` test-site count

| Side | Quote |
|---|---|
| A | `W42-RISK-REGISTER.md:18` — `\`ConsciousBrain\` has **0 production construction sites** (67 in \`src/test\`)` |
| B | `RECON-W42-BACKLOG.md:208` — `The \`ConsciousBrain\` cluster: product or research artefact? 67 test sites, 0 production sites` |
| C | `W42-INTEGRATION-MATRIX.md:110` — `**0 production construction sites; 67 test sites**` |
| D | Measured: `grep -rln "new ConsciousBrain(" --include="*.java" */src/test/` → **19 files**; `grep -rho "new ConsciousBrain(" …` → **69 occurrences**; `python3 scripts/integration-census.py` → `ConsciousBrain: … 0 construction(s), 1 call file(s), 24 test file(s)`. |

Three plausible readings of "67"; the truth under each reading is 19, 69 or 24. (F-46, F-47.)

### C-18 — The endpoints and the tabs

| Side | Quote |
|---|---|
| A | `W42-INTEGRATION-MATRIX.md:48` — `**yes** — 1.571 s startup, 36 REST endpoints, 9-tab UI` |
| B | `W42-INTEGRATION-MATRIX.md:210` — `\| REST surface \| \`PRODUCTION_PATH_VERIFIED\` \| 36 endpoints, all performing real operations; 28 probed live \|` |
| C | `W42-INTEGRATION-MATRIX.md:212` — `\| UI ↔ REST \| \`INTEGRATED\` \| 10 of 36 endpoints have **no UI caller** — including \`/goals/resumable\` and \`/teach-back-experiment\`, …` |
| D | `W42-ARCHITECTURE-REALITY-MAP.md:20` — `│  @Path("/v1/workbench")        │  9 tabs, 10 buttons` |
| E | `RECON-W42-BACKLOG.md:246` — `Thirteen tabs, and none of them is empty` |
| F | Measured: `LearningWorkbenchResource.java` → **52** HTTP method annotations; `workbench/index.html` → **13** `data-tab` values. |

Also note that C lists `/teach-back-experiment` among the endpoints with **no UI caller**, while `index.html:925` calls exactly that endpoint (see C-11). (F-27, F-28, F-29.)

### C-19 — The §11 row 18 cell contradicts the paragraph under it

| Side | Quote |
|---|---|
| A | `RECON-W42-BENCHMARKS.md:447` — `… The build metadata is now written by hand, sorted, with no timestamp: a function of the sources alone \|ifferent hashes, and the cause is not yet explained.` |
| B | `RECON-W42-BENCHMARKS.md:449-451` — `**No row of the table is open any more.** The last half-measured row — the reproducible build (row 18) — was closed in W42.7: two clean builds now produce the same jar, and the two causes are named …` |

A is a paste corruption: the fragment after the stray `|` belongs to an earlier, pre-W42.7 version of the cell. (F-88.)

### C-20 — The BACKLOG's W42.4 "Left, stated plainly" against the code it describes

| Side | Quote |
|---|---|
| A | `RECON-W42-BACKLOG.md:105-110` — `**Left, stated plainly:** the false-premise question is still answered ✓ ✓; the transfer items are answered without the conclusion ✓ ✓; the delayed teach-back phase fails ✓ ✓; **the consolidation is still the unreachable W41 component** — its result is discarded and the compaction still hard-deletes …` |
| B | `RECON-W42-BACKLOG.md:185-190` — `**Delivered:** the false-premise detector ✓ ✓ (the \`FALSE_PREMISE\` items 0/2 → 2/2 …); the bounded idempotent consolidation with seven kinds of forgetting and without touching a claim ✓ ✓; …` |
| C | `SqliteMemoryBackend.java:189-198` — the compaction marks tombstones, it does not delete. |

The same document says the consolidation is unreachable in one section and delivered in another. (F-83.)

---

## CLEAN

Documents and areas checked and found consistent with the code at HEAD `6ea07101`.
Each entry states what was checked.

| Document / area | What was checked | Result |
|---|---|---|
| `docs-v2/designs/DESIGN-70-evidence-grounded-reasoning.md` | the §0 status paragraph against the code: the logic pack's counts (11 concepts, 28 claims, 2 sources, 8 representations, 3 derivations), and that the two opening sentences are marked historical | **consistent** — `logic.pack.json` → concepts 11, claims 28, sources 2, representations 8, derivations 3, exactly as stated at `:29-33`; the doc's own disclaimer at `:33-35` ("The two sentences this document opened with were true when it was written and are now historical") closes the W42-RECONNAISSANCE D2 defect |
| `docs-v2/research/HYPOTHESES-NEW.md` | H-100, H-101, H-102, H-103 statuses against the code and the adversarial pack | **consistent** — H-100/H-101 `IMPLEMENTED` (the axes exist in `Claim`, `ClaimWarrant`, `DerivationStatus`; `EntailmentChecker` exists); H-102 `IMPLEMENTED` and `photosynthesis.adversarial.json` exists; H-103 `PARTIALLY IMPLEMENTED` with the W42.7 status update at `:187-195` — and that update is accurate: `ErrorDrivenLearner` does have production callers (`KnowledgeWorkbench.java:993-994`) and `SupportScore.contradiction` is still `NOT_COMPUTED` (`KnowledgeRetriever.java:535`) |
| `docs-v2/engineering/W42.2-UNIVERSAL-ACQUISITION-REPORT.md` | the report's explicit "written in W42.6 from the committed code" disclaimer and its W42.2 status against `RECON-W42-BACKLOG.md:38` | **consistent** — the "core DONE, the corpus manager / authored representations / validation stage still open" split at `RECON-W42-BACKLOG.md:38-46` matches the report's own framing; the report is honest about being written later |
| `docs-v2/engineering/W42.6-LOCAL-ALPHA-V0.3.md` — §5 "How to run" | the run commands and the port | **consistent** except the port in the status line — `./gradlew :matrix-core:quarkusRun` → `http://localhost:9091/workbench/` (`:255-256`) matches `application.properties:31` (`quarkus.http.port=${QUARKUS_HTTP_PORT:9091}`); only the status line's `port 9101` at `:5` is wrong (F-25) |
| `docs-v2/engineering/W42-INTEGRATION-MATRIX.md` — §7 status counts | the `PRODUCTION_PATH_VERIFIED 24 / INTEGRATED 19 / EXISTS 4 / DISCONNECTED 21 / EXPERIMENTAL 3 / UNVERIFIED 5` table against the matrix's own rows | **internally consistent** — the counts sum to the components the matrix lists, and the "twenty-one components are implemented, tested and unreachable" statement at `W42-RISK-REGISTER.md:69` matches the `DISCONNECTED` count of 21. (The individual rows are a separate matter — see F-42, F-45, F-46.) |
| `docs-v2/engineering/W42-RISK-REGISTER.md` — §7 §14 acceptance table | each row's verdict against the code | **consistent** for the rows it names: R2's abstention baseline 0.400, R3's false premise 0/2, R6's `photosynthesis.pack.json` with 0 derivations against the logic pack's 3, R7's `NOT_COMPUTED` at `KnowledgeRetriever.java:535`, R12's `PersistentHdcStore:41`, R13/R14's test-only exposure, R22's `GpuKernelEngine:45-52`, R23's constant `0.5` reward, R24's `AgentLoop:430-441`, R25's `ConsciousBrain` surprise figures — each has a cited file:line that still holds. The *statuses* of R1/R4/R5/R8/R9/R10/R11/R15/R17/R18 are stale — see C-12 and C-11 |
| `docs-v2/engineering/RECON-W42-DECISIONS.md` | D-001 … D-017 against the code | **consistent** except D-015's count (F-78) — D-001 (34 items, benchmark first), D-002 (no branch merged), D-003 (the graph is a read-only projection), D-004 (BM25 not wired; `Bm25Index` is not in the answer path), D-005/D-006 (the persisted state machine and the planner — `learning_state` exists at `KnowledgeStore.java:500`), D-009 (the W41 corrections), D-012/D-016 (the shadow classes), D-013 (`ru-cross-3` now points at `photosynthesis` — verified in the JSON; `ru-cross-4` points at `logic` and the logic pack does hold modus ponens, so it is **not** a second malformed pointer), D-014 (`en-unanswer-2` still answered), D-017 (the full suite not run — `RECON-W42-BENCHMARKS.md:123` says so) |
| `docs-v2/engineering/LEARNING-WORKBENCH.md` — §1, §2, §3, §4, §6, §13 | the run procedure, the pack's composition, the level table, the assessment dataset, the §6 historical labelling, and §13's "what this release does not do" | **consistent** — §1's port and the "no database server / no model provider / no API key" claim match `application.properties`; §2's "8 concepts, 17 claims, 4 sources, 18 evidence links, 13 representations … one recorded revision" matches `photosynthesis.pack.json` exactly; §4's "22 items from a versioned dataset (`photosynthesis-v1`)" matches the file `photosynthesis.assessment.json`; §6 correctly labels the two runs "**These are the W40 measurements.**" and documents the rebuilt experiment's PRE 0/2, POST 1/1, DELAYED 0/1, TRANSFER 1/2 (matching `HYPOTHESES-NEW.md:168-169`); §13's "The teaching capability is demonstrated only machine-to-machine" is honest. The only defect in this file is the clause at the end of §6 (F-66) |
| `README.md` | the whole file for architectural claims, wave references, ports, endpoint and test counts | **consistent** — the file contains no W42 claim, no port, no endpoint count and no test count; it is a build-and-run page only. **Nothing to correct** |
| `AGENTS.md` | the session procedure, the wave pointer, the prohibitions, the testing rules | **consistent with the repository** — the targeted-suite command it names (`./gradlew :matrix-core:test --tests "io.matrix.<pkg>.*"`) is the one the W42 documents use; the "full run only by agreement" rule is what D-017 records; the FROZEN-zone list matches `INVARIANTS.md`. The one problem is the **pointer** to `engineering/PLAN.md` (C-16), which is a problem in PLAN.md, not in AGENTS.md |
| `docs-v2/engineering/INVARIANTS.md` | K_MAX, the FROZEN-zone table, the determinism rules, the JaCoCo gate, the four prohibitions, the forbidden-claims rule | **consistent** — `bir/TruthTable.K_MAX = 20` exists; the FROZEN paths (`matrix-core/src/main/java/io/matrix/ethics/frozen/**`, `matrix-core/src/main/resources/avro/**`, `.github/workflows/**`, `CONSTITUTION.md`, `AGENTS.md`) match the repository and match the gate's own FROZEN-1 list (`scripts/quality-gate.sh`, 5 zones); `ethics/EthoricalFilter`, `StructuralSafetyGuard`, `LieDetector` and `frozen/FROZENFNLGuardian` exist, and `formal/FrozenEthicalFNL.tla` exists. **Nothing to correct** |
| `docs-v2/research/MATRIX-CROSS-DISCIPLINARY-RESEARCH.md`, `docs-v2/research/W42-RESEARCH-{A,B,C}-*.md`, `docs-v2/research/W41-RESEARCH-{A,B,C}-*.md` | existence, and whether the W42 documents cite files that exist | **consistent** — all six W41/W42 research files exist; DESIGN-70's "Builds on" header names `W41-RESEARCH-A/B/C` and all three exist. (Their *content* is research history and is `HISTORICAL` by the mission's §3.4 — no rewrite is proposed) |
| `docs-v2/architecture/*` | `OVERVIEW.md`, `MODULES.md`, `RUNTIME-TOPOLOGY.md`, `CAPABILITY-REGISTRY.md`, `FORMAL-CONTRACTS.md`, `MODULE-LIFECYCLE.md`, `FEDERATION-MODULE.md`, `ECOSYSTEM-LAYOUT.md`, the five ADRs and the six `REQUEST-*.md` | **no checkable claim found** that contradicts the code at HEAD in this pass — the ADR verdicts are dated decisions (2026-09-12 → 2026-10-30) with no numbers attached, and the `REQUEST-*.md` files are proposals. **Nothing to correct** — but see SCOPE, which records that this was a targeted scan, not a line-by-line read of all 15 files |
| `docs-v2/_raw/**` (the pre-v2 archive) | the mission's §3.4 "preserve as historical" clause | **correctly out of scope** — these are the pre-v2 historical records the W42 reconnaissance names as `HISTORICAL` (`W42-RECONNAISSANCE.md:180-185`). **Not audited, by design** |

---

## SCOPE

### What was NOT checked, and why

| Not checked | Why |
|---|---|
| `docs-v2/engineering/W42.1-LEARNING-CORE-REPORT.md` beyond its §0 summary, §1.1–§1.2 and §2.2 | 271 lines of wave narrative. The summary's headline number (abstention 0.400 → 0.700) agrees with `RECON-W42-BENCHMARKS.md:201`. Its startup figure (`:205`) is registered as F-24. The remaining prose was not line-checked against the code. |
| `docs-v2/engineering/W42.3-DIALOG-INTELLIGENCE-REPORT.md`, `W42.4-…`, `W42.5-…` beyond their §0 summaries | Their headline numbers agree with the benchmarks document (0.583 → 0.792; the 22-item retention set; 0.900 abstention). The dialogue case count 168 and the 133/35 split **were** verified (F-30, F-31). The bodies were not line-checked. |
| `docs-v2/engineering/W42-RECONNAISSANCE.md` beyond §4.1–§4.3 and §5 | §4.1 (D1–D8) was checked against the code — D1, D2, D3, D4 and D7 are closed; D5 is half-closed (F-68); D6 and D8 remain open and are registered (F-66 for D8). The D6 item (`CognitiveProcessor.history` unbounded, test-only) was **not** re-verified in this pass. §6+ was not read. |
| `docs-v2/engineering/W42-HISTORICAL-BRANCH-RECOVERY.md` | The register cites its §4 for R21 (the nine shadow classes). Only the shadow-class rows it supports were checked (F-45, F-50, F-51). Its branch-level claims (fourteen verified defects, `ConsolidationCycle:89`, `SingleInstanceGuardTest:23-30`) were **not** re-verified — the branches are not present in this checkout, so they could not be. |
| `docs-v2/engineering/RECON-W42-BACKLOG.md` items 1.1–1.8, 2.1–2.7, 3.1–3.8, 4.1–4.6, 5.1–5.6, 7.1–7.4, X.1–X.6 | The §-headings, the `Left, stated plainly` blocks and the W42.9 section were checked (F-45, F-82…F-87). The item-level baselines are historical by construction and were not each re-verified. |
| `docs-v2/engineering/MUTATION-REPORT.md` beyond its header and the two survivors | The harness was **not re-run** (it invokes TLC on 32 mutants). The header (32/30/2) and the two survivor rows were read and are consistent with `formal/mutations.json` at HEAD. The 30 KILLED rows were not each re-checked. |
| `docs-v2/engineering/JMH-GATE-EVIDENCE.md`, `TLA-STATUS.md`, `SDD-COVERAGE.md`, `STANDARDS-MATRIX.md`, `RISK-REGISTER.md` (the W39/W41 one, beyond row 7), `RELEASE-NOTES.md` | Not in the mission's inventory for this wave and not cited by the W42 documents. The W39/W41 `RISK-REGISTER.md` was checked only at row 7 (F-68). |
| `docs-v2/designs/DESIGN-01` <!-- no-link: a prefix, not a file --> … `DESIGN-69`, `DESIGN-71` … `DESIGN-78` (all except DESIGN-70) | The mission's §3.4 registry is a W42 obligation. DESIGN-70 was checked because the W42 documents cite it; the other designs were not read line by line. `DESIGN-71` is mentioned in `wal/SESSION_WAL.md:3119-3121` and was not opened. |
| `docs-v2/research/*` other than the six W41/W42 research files and `HYPOTHESES-NEW.md` | ~80 files, mostly wave reports and experiment records. The mission's §3.4 explicitly makes them `HISTORICAL`; a line-by-line audit of historical research reports would be a rewriting task, not a registry task. `HYPOTHESES.md` (as distinct from `HYPOTHESES-NEW.md`) was not checked. |
| `docs-v2/architecture/*` beyond the targeted scan in CLEAN | 15 files read by grep for checkable claims, not read in full. `CAPABILITY-REGISTRY.md` and `FORMAL-CONTRACTS.md` in particular are prime candidates for a follow-up registry pass — `CAPABILITY-REGISTRY.md` maps to DESIGN-58's capability levels, which the W42 documents also touch. |
| `docs-v2/_raw/**` | Historical archive, explicitly `HISTORICAL` (`W42-RECONNAISSANCE.md:180-185`). Not audited, by design. |
| `SESSION.md` (7 787 lines) beyond the four W42 sections at lines 7261–7350 and 7725–7760 | The other ~7 400 lines are earlier sessions. The W42 sections were checked: the accuracy figures `0.917` (`:7342`), `0.915` (`:7729`) and `0.979` (`:7743`) are all **on the v2 candidate**, not on v1 — see F-41. |
| `wal/SESSION_WAL.md` beyond the W42.9 entries at lines 3115–3132 and 4150–4222 | The WAL is append-only history; only the entries that make a claim the W42 documents contradict were checked. |
| Any **runtime** measurement — the benchmark runner, the live HTTP journey, the SIGKILL recovery, the schema versions v8…v15, the `469 712 bytes` / `737 512 bytes` WAL sizes, the 2 413 byte-identical class files and the two build hashes | These require a running JVM and, in the build case, two clean builds. The mission's §16 requires that unmeasured things be named as unmeasured, so they are registered as `UNVERIFIED` (F-15, F-23, F-40, F-90, F-91) rather than asserted. |
| The **full repository test suite** | `AGENTS.md` requires the owner's agreement (OOM risk) and `RECON-W42-DECISIONS.md:308-314` (D-017) records that it was withheld. All test counts in this registry are `@Test`-method counts computed from the tree at HEAD, which is reproducible without running Gradle. |
| The **quality gate's** frozen-zone diff semantics | The gate was run and its exit code measured (0). Whether the frozen zones *should* fail the gate is an owner decision (§16.1) and is registered as such (F-76, F-77), not decided here. |

### Verification notes for the next pass

1. **The renumbering is a precondition.** F-70/F-71 (the R27/R28/R29 collision) must be fixed
   before F-73 (the WAL's R31) can be resolved coherently, and before any cross-document
   reference to an R-number can be trusted.
2. **Two numbers cannot both be right** in the literal-candidate pair (346 vs 339) and in the
   formal-gate trio (12 / 13 / 14). Both need an owner decision or a re-measurement, not an
   edit.
3. **The benchmark identity is the root cause of the accuracy/abstention confusion**
   (C-8): one line in `RECON-W42-BENCHMARKS.md` naming the v2 candidate file dissolves it.
4. **`docs-v2/engineering/PLAN.md` is the highest-leverage single fix** (F-92): the session
   procedure points at it, and it names no wave later than W92 (Sep 2026).
5. **No document under audit was modified by this pass.** This file is the only artefact.
