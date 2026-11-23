# Source revision applicability checkpoint

2026-11-23. Baseline develop `3fcbfdc10ed32d6c7a5bc8856f309a2b55f4babe`.
Class 2 research, class 5 capability instrument. Proposed implementation
would also be class 10 migration. All checks used synthetic data.

| Capability or task | Actual resource expenditure | Measured result | Remaining defects | Evidence | Decision |
|---|---|---|---|---|---|
| DSH code and persistence mapping | 6,439 reported tokens in 14.895 s, one call | Content identity and archival ledger do not encode named, restart-stable source selection | Durable relation and serving view absent | architecture-scope.json, architecture-result.json, architecture-review.md | CONTINUE |
| Independent two-workspace baseline | 21.51 s wall, 91.00 s user and 3.31 s system CPU, peak RSS 1,766,012 KiB, zero provider tokens | Both workspaces initially REFUTED with different local V1 IDs. A retained both revisions. With V2 imported in A only, both still use V1 through restart | No operation to explicitly change applicability, replay history through a named selection, or bind learning and cache behavior to that view | Separate evaluator baseline JUnit XML, command and telemetry below | CONTINUE pending RFC authorization |
| Additive migration RFC | No additional provider call | Names a workspace-local append-only event and gives compatibility and backup rollback behavior | Schema authorization, GREEN, full reliability controls and CI pending | SOURCE-REVISION-RFC.md | CONTINUE pending authorization |

The baseline observes the gap without treating same-filename imports as
authorization. Existing idempotency and workspace isolation passed their
preliminary checks. Revision selection, as-of reconstruction, interruption
recovery, procedure invalidation and cache freshness are unverified. Billing
and allocated bytes were NOT_INSTRUMENTED. Classification
RESEARCH_INFORMATION_GAIN. Cost ceiling remains 30,000 MiniMax reported
tokens for the first experiment, with 23,561 remaining before expansion.

Evaluator fixture manifest SHA-256
`1addffa3d4f864aa53a6dee1fd19fa63e74fd04ce49fd5aa81f4b41db8732e00`.
The evaluator did not share gold or fixture details with DSH. Its durable
evidence directory is
`/home/alexandr-narbaev/Projects/agi/matrix-revision-evaluator.teFBiFtH/evidence/baseline/`.
The exact Gradle invocation and reference SHA are retained in command.txt.

The prior local candidate remains excluded from this work. Its independent
2/7 result and resource record are retained in the preceding milestone
checkpoint. No current capability promotion is supported.

## Source-grounded boundaries

All references are under matrix-core/src/main/java/io/matrix at the baseline
SHA. This work belongs to the served Chain A Workbench, its epistemic memory
and report learning path.

| Layer | Existing contract | Gap |
|---|---|---|
| Identity and relationships | knowledge/acquisition/DocumentImporter.java:462 and :482 derive content identities. knowledge/KnowledgeRevision.java:15 and knowledge/RevisionKind.java:9 model semantic change to one target | No logical document identity or explicit document revision link |
| Persistence and archival export | knowledge/KnowledgeStore.java:2869 and :5166 persist generic revisions and export full state via knowledge/KnowledgeSnapshot.java:42 | No durable event selecting applicable source and no historical selector |
| Query behavior | knowledge/KnowledgeWorkbench.java:432 and :510 filter explicit source IDs only. knowledge/KnowledgeRetriever.java:395 and :443 evaluate snapshot claims against epistemic policy. knowledge/KnowledgeWorkbench.java:719 invokes the premise path, where knowledge/PremiseAssessment.java:1032 reads evidence for claims | No policy-selected applicability projection. Claim eligibility and evidence eligibility must be handled separately |
| Procedure and cache | knowledge/ArtifactReportResource.java:337 and :719 plan from the full snapshot and test source existence. cognitive/report/ReportJourneyPlanner.java:176 can select a forced method. cognitive/report/ReportProcedureLearner.java:179 consumes observed source IDs. cognitive/report/WorkspaceFingerprint.java:34 hashes claim fields | Stale known source passes presence guards. Forced execution, procedure registration, learned overlay and claim-only cache fingerprints must consume the same selected view |

## Prior-result integration

PR #77 merged the NR-038 negative-result record into develop at
`f4ad32d8bb35a6229122f97195c6af2e0817400b`. All six required checks passed on
head `84b165ec3827af70f2af892fd72ad2552a71dc66` in run 45142791246. Pages
passed in run 45142744289. The merge used the normal exact-head squash path.
A Git diff confirmed the new develop commit changes only the negative-result
report and its index. The baseline matrix-core sources are unchanged. The
source revision implementation still requires the specific SQLite migration
approval in the active objective. No additional MiniMax tokens were used.

## Approved SQLite implementation and independent result

The owner authorized the v21→v22 additive migration for synthetic tests and
integration after review and CI. The worktree is based on develop
`f4ad32d8bb35a6229122f97195c6af2e0817400b`. No live database was opened.
PostgreSQL was considered separately. The served path uses the SQLite writer
lease and migration chain. PostgreSQL appears in DESIGN-67 as a possible
global federation layer. The experiment measured neither a SQLite
contention threshold nor a cross-node consistency requirement.

| Task | Expenditure | Result | Decision |
|---|---|---|---|
| DSH architecture, storage, and projection calls | 26,003 reported MiniMax tokens across three calls, 163.131 s total provider-turn wall time | Subscription-backed MiniMax-M3.1-Flash, native DSH, three completed calls. 3,997 tokens remain under 30,000 | Keep exact payload manifests |
| SQLite storage and migration tests | 20.65 s wall, 84.22 CPU s, peak RSS 1,553,028 KiB | 23/23 passing on storage implementation; additive v21 upgrade and restart were exercised | Retain |
| Intermediate report regressions | 128.74 s wall, 132.91 CPU s | 51/51 on an intermediate source tree before final policy/provenance edits. Not exact-head verification | Retain as intermediate |
| First sealed served run, `03ca971c9db69eda078a53d811df9059a4ff6965` | 28.73 s wall including lock, 91.79 CPU s | 4/7. Claim identity failure, learner setup below existing minimum and cache test stopped at attempted method metadata on a 409 | Correct evaluator setup, keep all original evidence |
| Corrected sealed served run, `916a4719987f6034f3e039297ea66c1402c5752c` | 23.40 s wall, 93.69 CPU s, peak RSS 1,879,864 KiB | 6/7. Primary current/historical, isolation, restart and snapshot exchange passed. Foreign/ambiguous refusal, competing CAS, rollback, real learner and cache controls passed. Independent source claim identity failed | PIVOT. Candidate stays local |

All MiniMax requests used pinned scope, `MiniMax-M3.1-Flash` and the
tool-free Responses profile. The hashes, exact source excerpts by range and
observed usage are in architecture-result.json, storage-result.json and
projection-result.json. Codex reviewed and adapted the DSH storage proposal.
Its projection proposal did not select current revisions at all and used
reflective field lookup. Codex replaced that portion with typed projection
and wired report, planner, learner and cache consumers. No sealed fixture or
gold was sent to MiniMax. Monetary cost and allocation were NOT_INSTRUMENTED.

The evaluator author corrected learner setup to meet the production minimum
of five held-out questions and checked the actual cache refusal and artifact
bytes. The frozen main fixture and expected source and claim identities did
not change. Group counts from the two evaluator versions are not a
performance comparison. On the primary stable fixture, baseline A retained
the old refutation after restart, while the candidate selected V2 in A,
kept B on its own V1, and reconstructed V1 in A when explicitly requested.

The failed test falsifies the assumption that projecting sources and evidence
links alone is enough to preserve exact served claim identity. The importer
can canonicalize equal propositions under a claim originating in a now-inactive
revision when it attaches an independent support edge. The candidate emits
the active observer source with that inactive claim identity. Trying to prefer
an equivalent directly introduced usable claim did not change the outcome.
The existing acquisition validator may archive that equivalent claim as
SUPERSEDED and point evidence at the canonical claim. Revision selection must
distinguish canonical proposition identity from the exact source observation
and its literal span without promoting superseded content.

**RESEARCH_INFORMATION_GAIN · PIVOT.** No production candidate was pushed,
no code PR CI was claimed and no capability registry promotion was made.
The next falsifiable task is to make that canonical-vs-observation distinction
explicit in served evidence. After a canonical introducer becomes inactive,
the route must resolve the exact active independent observation, its digest
and literal span while keeping the archived canonical claim and its status.
A canonical ID paired with a span from another observation, an invented
truth promotion, or disappearance of independent evidence fails the task.

Evaluator-only XML, commands and telemetry are under
`/home/alexandr-narbaev/Projects/agi/matrix-revision-evaluator.teFBiFtH/evidence/`
in green-03ca971 and green-916a471. Do not send evaluator material to
implementation providers. The v2 runner manifest SHA-256 is
`75a578d76b0a59f515f969f48f5a402b94f197133174cf4cdda32c2eeca9a55b`.

The evaluator confirmed the mechanism in a separate diagnostic using this
candidate's compiled classes and a synthetic database. The canonical claim
was VERIFIED and retained an active observer SUPPORTS edge. The observer's
own extracted claim for the same proposition and scope was SUPERSEDED and
still retained its own SUPPORTS edge. The stored rank and provenance records
therefore cannot be substituted for one another. Diagnostic:
evidence/green-916a471/claim-canonicalization-diagnostic.txt in the evaluator
directory. This diagnostic explains the failure without changing the oracle.

## Observation identity continuation

The continuation preserved the prior 6/7 candidate and changed the
provenance contract. It now binds the canonical reasoning ID to the active
local observation ID, direct evidence edge, source digest, literal excerpt
and bounded extracted-text segment. The withdrawn duplicate's stored
epistemic status remains untouched. Missing or malformed exact spans do not
produce typed observations. Both learned claim and procedure readers reject
retained historical report traces. Archive reconstruction refuses source,
claim and evidence ID collisions rather than replacing another workspace's
provenance rows. Composed representations are filtered by their source
binding, and historical requests use reference reasoning or same-view replay.

The exact PR head is `f68ec498a6a6a50a34eecf0653a64385ed9d6c78`. Its nine
targeted JUnit suites passed 43/43. Timing and exact command are recorded in
continuation-verified.time and the XMLs in integration-head-test-xml.
Initial developer fixture setup failures are retained in the two
continuation failure XML directories. The final developer control uses
explicitly constructed canonical and observation records. Independently
real import and canonicalization were exercised in the frozen evaluator.

The unchanged sealed evaluator passed 7/7 groups and all ten reliability
controls. The separate typed provenance audit confirmed 12 REFUTED and seven
UNSUPPORTED responses, and one canonical-to-observation distinction in 65
records. The frozen evaluator took 23.41 s wall, 89.77 s user and 3.50 s
system CPU, peak RSS 2,053,860 KiB. No provider call was made in this
continuation. Total prior milestone MiniMax consumption remained 26,003
reported tokens under the 30,000 ceiling. Exact evaluator manifest:
`f9d2a3d051e2594f12d32ff417d5e481ae5ccde8a12e449b12f2683caa44ee56`.
Evaluator path:
`/home/alexandr-narbaev/Projects/agi/matrix-revision-evaluator.teFBiFtH/evidence/green-f68ec49`.

PR #78 is reviewed by a separate AI reviewer and awaiting exact-head
GitHub CI run 45200890619 before normal integration. The product claim is
bounded to explicit source selection in a synthetic Chain A served
report, and does not assert workspace-wide historical reconstruction or
a general recalculation of stored warrants.

### Integration verification

PR #78 passed all six required checks and the matrix-core compile/targeted
job in exact-head fast-pr run 45200890619. It merged normally, with no
administrative bypass, into develop at
`b226a5e45cd5e8fecb6a7bad0c3980ec8122e3f4`. The command
`git diff --exit-code f68ec498a6a6a50a34eecf0653a64385ed9d6c78 b226a5e45cd5e8fecb6a7bad0c3980ec8122e3f4`
confirmed the verified and merged trees match. Classification:
**PRODUCT_GAIN** in scoped synthetic Chain A source applicability.

Next falsifiable capability: revision-sensitive warrant revalidation. Start
with a claim verified by two independent sources. Retire one in workspace A
while B retains both. Require A's served decision to report the surviving
applicable support without using the retired source to justify verification.
Require B to keep the two-source verified warrant and A's historical query
to reconstruct its former warrant. Fail on an inactive evidence ID,
incorrect promotion, any archived claim mutation, or a changed result in B.

The exact-head GitHub matrix-core job used 203 job-seconds. The final local
developer test run used 12.42 seconds wall, 43.37 CPU seconds and 960,832 KiB
peak RSS. No per-case latency, allocation or monetary cost was instrumented.
