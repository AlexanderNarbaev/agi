# CRJ-01 — Source revision applicability

**Status:** `CANONICAL`

**Measurement date:** 2026-11-23

**Classification:** PRODUCT_GAIN · **Decision:** CONTINUE

## Measured result

The final [implementation PR](https://github.com/AlexanderNarbaev/agi/pull/78)
head `f68ec498a6a6a50a34eecf0653a64385ed9d6c78` passed all seven JUnit groups
in the unchanged frozen v2 evaluator. The earlier `2ffa2cb82a14869f5598bc6c0c98f085707633d2`
candidate also passed all seven.
The evaluator covered the primary two-workspace case and all ten reliability
controls. It invoked the production `ArtifactReportResource` in process using
synthetic workspaces.

Workspace A switched explicitly from V1 to V2 while B retained its own V1
refutation. A retained both historical views and reconstructed the refutation
from its V1 view. The checks covered import idempotency, same-filename
coexistence, independent corroboration, workspace-local identity rejection,
competing successor selection, rollback after insertion, restart, snapshot
exchange, invalidation of a learned trace, and refusal to replay a stale
report while retaining its historical artifact.

The evaluator audited 12 refuted and seven unsupported responses using frozen
source bytes and importer records made before the query. It verified the
canonical claim, source observation claim, evidence identity, source hash and
literal excerpt. One refuted response carried distinct canonical and
observation identities. The typed coordinates identify the enclosing source
segment. The unique literal excerpt within that segment reconstructs the
independently frozen exact sentence coordinates. Unsupported responses carried
zero refuting observations. The retained trace contains 65 import, selection,
served-response and diagnostic records.

The applicability policy is explicit. The workbench appends a workspace-local
selection event naming the logical document key, selected source ID and content
hash, and predecessor event. The current view uses the terminal event in each
document chain. A report request with `sourceRevisionEventId` substitutes one
named historical event for its document and leaves other document selections
current. This is document-specific historical reconstruction. Historical
requests are excluded from both report learner routes.

The final head also passed **43 developer tests across nine suites** and
received independent AI source review. That targeted scope exercised the v21
to v22 upgrade in synthetic SQLite, malformed observation rejection and
historical exclusion from both learner routes.

## Prior outcomes and limits

The pre-implementation run on `3fcbfdc10ed32d6c7a5bc8856f309a2b55f4babe`
observed both workspaces still relying on V1 after an ordinary V2 import in A
and a restart. Import preserved both sources and established no explicit
supersession. The first implementation run on `03ca971c9db69eda078a53d811df9059a4ff6965`
passed 4/7 groups and exposed evaluator setup defects. The corrected runner
preserved frozen source and gold bytes. The next candidate,
`916a4719987f6034f3e039297ea66c1402c5752c`, passed 6/7 and failed the exact
independent source-claim identity check. The earlier text-matching
experiment also remains recorded. [NR-038](../research/CRJ-01-PREMISE-TEXT-MATCHING-NEGATIVE-RESULT.md)

Projecting sources and evidence edges alone was insufficient. When an archived
canonical introducer had an applicable independent support edge, the
canonical identity and the supporting observation could refer to different
claims. The evaluated response carries both identities so consumers can
verify which observation supplied the literal span.

This is an independent AI-assisted internal measurement of a synthetic
Chain A report path. The experimental results concern applicability of
explicit source revisions and exact refuting provenance in the tested view.
The scope is synthetic source revision applicability and exact observation
provenance. General premise reasoning, learning transfer, cross-chain behavior,
live database migration and the full CRJ-01 freeze remain outside the measured
scope. Exact-head [GitHub CI](https://github.com/AlexanderNarbaev/agi/actions/runs/45200890619)
passed. PR #78 merged normally at `b226a5e45cd5e8fecb6a7bad0c3980ec8122e3f4`,
whose tree matches the independently evaluated head.

## Evidence and expenditure

**The retained evidence now resides IN THE REPOSITORY**, at
`docs-v2/engineering/evidence/crj01-source-revision/` — so a reader on another machine
does not need the session's temporary directory, and this record cannot point at a path
that only ever existed on one machine. The per-run records, the JUnit XMLs, the audit and
the traces are all there, and the accepted run's `CHECKSUMS.sha256` verifies (**9 of 9
files OK**).

**What is deliberately NOT retained.** The evaluator's frozen cases and **gold** remain
under evaluator custody — the mandate requires it ("do not expose ... hidden gold"), and a
blind evaluation that publishes its answers is not blind. The evaluator's **working
copies** (seven runs, about 130 MB each) are machine state and are not carried either.
What the repository publishes is the **verifiable record**: the per-run commands, the
JUnit XMLs, the audits and the digests of the runner and the fixture, so that the owner
can check the retained evidence against the gold they hold, and a reader can check the
result without the repository publishing the answers.

The v2 runner manifest SHA-256 is
`75a578d76b0a59f515f969f48f5a402b94f197133174cf4cdda32c2eeca9a55b`.

| Evidence under the retained directory (`evidence/`) | SHA-256 |
|---|---|
| `green-f68ec49/CHECKSUMS.sha256` | `f9d2a3d051e2594f12d32ff417d5e481ae5ccde8a12e449b12f2683caa44ee56` |
| `green-f68ec49/RESULT.md` | `9c0dbca8a841c82b41350df5cc9998574c48e8f269e79a17647c516efdf45123` |
| `green-f68ec49/TEST-io.matrix.crj.runner.FrozenSourceRevisionTest.xml` | `e926501d4890356e8c90ecd21b5668b7245350aef4d5e2940235721c2e21c74a` |
| `green-f68ec49/refuting-observation-audit.json` | `7e22d029c05b780a3ad1cc5ee18e5331bb79ea4d1da575b57eab6f17984733d1` |
| `green-f68ec49/command.txt` | `9ba40b789ded0db403c034cc013a6a332e327ec07298efe6e3ab305d28d87552` |
| `green-2ffa2cb/RESULT.md` | `79b14e2f578469cc07068c48b230a45020c7076a02e93342269d9c5c0f22f6b1` |
| `green-2ffa2cb/TEST-io.matrix.crj.runner.FrozenSourceRevisionTest.xml` | `a9ae74c8cd1fb0c02f3da3f38081385f754a9ca75f9ee6306ecbe8188b2e37b1` |
| `green-2ffa2cb/refuting-observation-audit.json` | `7e22d029c05b780a3ad1cc5ee18e5331bb79ea4d1da575b57eab6f17984733d1` |
| `green-2ffa2cb/command.txt` | `76a5d3960daf953644f9ba605f06c033e2bf3235fa260c3be187b575c7e08d2c` |

The evaluator used Gradle 9.6.0 with
`--offline --no-daemon --no-build-cache --max-workers=2 :matrix-core:test --tests io.matrix.crj.runner.FrozenSourceRevisionTest`.
The commands, JUnit XML, resource measurements and request traces for the
earlier runs are in the retained `evidence/baseline`, `evidence/green-03ca971`,
`evidence/green-916a471` and `evidence/green-2ffa2cb` subdirectories. The initial architecture and measured failures are
in the retained `docs-v2/engineering/evidence/crj01-source-revision/pr78-review/`
directory — the implementation PR's review evidence, the continuation's checkpoints and
the served-path test XMLs.

| Task | Observed expenditure | Measured outcome |
|---|---|---|
| Architecture, storage and projection dispatches | 26,003 reported MiniMax tokens in three calls, 163.131 seconds provider-turn wall time | Architecture mapping and candidate implementation input |
| First sealed implementation run | 28.73 seconds wall, 91.79 seconds CPU | 4/7 |
| Corrected sealed run before exact observation identity | 23.40 seconds wall, 93.69 seconds CPU | 6/7 |
| Sealed run on `2ffa2cb` | 24.51 seconds wall, 93.56 seconds CPU, 2,000,360 KiB peak RSS, zero provider tokens | 7/7, zero failures |
| Sealed run on final PR head `f68ec49` | 23.41 seconds wall, 93.27 seconds CPU, 2,053,860 KiB peak RSS, zero provider tokens | 7/7, zero failures |

The developer-count source is `pr78-review/served-intermediate-test-xml` in the
retained directory. The independent source review is retained in the implementation PR
and in the review note beside the checkpoint.

The resource times include compilation and fixture work. Provider monetary
cost and allocation per case were `NOT_INSTRUMENTED`.
