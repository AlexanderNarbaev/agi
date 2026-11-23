# CRJ-01 and claim-level premise refutation checkpoint

Date 2026-11-22. MATRIX develop is at
`3fcbfdc10ed32d6c7a5bc8856f309a2b55f4babe`. The stable branch was observed at
`188d756e785250bf70b6e2f878d0ee8043be8a34`.

## Integrated engineering

Provenance merged through #72 at `5c7366612ace0ebaa430921e2bf2d055bdcc4f26`.
The owner merged #72 while the replacement ran. The replacement #75 passed
all technical gates on the identical tree and was closed as redundant.
Scoring replacement #74 merged at `abc2b2f9d7f539194f1b48ace78f43474d32179c`
after the changed base was retested and reviewed. #73 closed with a replacement
link. Original branches were retained. The governance amendment #76 integrated
OD-083 and OD-084 after exact-head CI and the Pages build. Internal AI-assisted
review is authorized with implementation and blind evaluation separated.
GitHub develop requires six technically identical checks, fresh base, squash
PR and resolved threads under ruleset 27854496 with no bypass actors.
The effective main protections were verified against before snapshots.
See github-integration/DECISION.md and governance-ci/README.md.
Classification **ENGINEERING_MAINTENANCE**. Decision **CONTINUE**.

## Reproduced defect and tested candidates

A fresh single-source synthetic served report returned UNSUPPORTED for an
explicitly contradicted causal-exclusivity premise. Its oracle selects the
refuting claim by literal imported text and canonical source before scoring the
response. The exact RED XML is premise-red-identity.xml, run against
`514519bd02bb64e5d25e9f3ed0b82fef1e9c12a3` production.

DSH authored the initial test and candidate. Codex adapted the test's import
call and premise rendering to the repository API, and tightened the oracle to
require the specific claim identity. The first code candidate compiled, passed
17 existing tests and failed the fresh positive regression. Separate AI review
also found unordered token matching can reverse subject direction or erase
a time-scope distinction. This candidate was rejected.

DSH supplied a second recognizer sketch after a bounded retry. Codex corrected
its malformed match handling and adapted its ordered clause matching and
evidence guard for the local experiment. Local-only candidate
`b69c9aa7d0cd652bf7b349c862277786ecd766f5` passed 18/18 targeted tests,
including the literal claim and workspace-local source control. A separate AI
review found that the candidate can treat a qualified backing passage as proof
of an unqualified claim because it uses substring containment. It also does
not establish contributor distinctness, claim applicability, or active source
revision. This reviewed candidate is for measurement only.

The independent evaluator froze nine source files and expected tuples for
12 trials in seven groups. Source and oracle bytes were withheld from DSH and
the production implementer. The manifest hash is
`a2072ba21519b431abf999bb52acf4006029edfa65e886397dca82d08be6dc21`.
The evaluator corrected two baseline harness setup defects before candidate
execution without changing the source or gold. Runner manifest hash is
`7a863083d63163563617ff468508047bcf3dedfcde176a48ed79d7f95f2e7563`.
The corrected baseline passed two groups, failed four on disposition, and
left one source revision group unverified. This freeze is a scoped capability
experiment under evaluator control. It does not freeze the full CRJ-01 split.

## Resource record

| Activity | Observed expenditure | Result |
|---|---|---|
| Replacement CI for scoring and provenance | 383 matrix-core job-seconds across three runs | Six required checks passed on each replacement head |
| MiniMax source and implementation turns | 98,089 provider-reported tokens over 6 calls and 254.139 network-turn seconds | Two completed proposal stages, a compact recognizer sketch, three capped turns |
| Isolated candidate two targeted Gradle | 23.21 s wall, 53.47 user + 2.88 system CPU s, peak RSS 937,528 KiB | 18/18, including positive scoped causal proof |
| Frozen evaluator corrected baseline | 10.40 s wall, 42.30 user + 2.44 system CPU s, peak RSS 991,488 KiB | 12 trials, 7 groups, 2 pass, 4 semantic failures, 1 revision unverified |

Plan billing, per-case allocations and provider-side CPU were NOT_INSTRUMENTED.
The prior 405-token synthetic subscription preflight is outside the six scoped
turns. Effective provider MiniMax, model MiniMax-M3.1-Flash, OpenAI Responses at
https://api.minimax.io/v1, native DSH 0.2.2-rc.5. Three exact tracked files
were provided in the six turns: PremiseAssessment.java, PremiseSemantics.java
and PremiseReportResponseTest.java. Scope and content digests are recorded in
each premise turn's scope or result JSON. The pivot also supplied the generated
first candidate patch. No hidden input, raw service logs or credentials were
part of the outbound prompt. DSH used isolated temporary directories, a
tool-free overlay and no DeepSeek fallback. Wire evidence for the low reasoning
setting is minimax-low-wire.json. No hosts or credentials were configured
globally.

Exact local developer test command is recorded in premise-candidate-two.time.
It uses `flock /tmp/gradle-matrix.lock ./gradlew :matrix-core:test` with the
three test class filters, offline mode, two workers and no daemon.

## Frozen candidate outcome and architecture decision

The original evaluator agent reached its service usage limit. Codex ran the
evaluator's unchanged runner in a separate, detached evaluation worktree
without giving its files to DSH. The initial evaluator was corrected before
candidate execution to render an explicit background premise and register
a truly empty workspace. Candidate and corrected baseline used identical
source, gold and runner digests. Both Gradle executions produced fresh JUnit
XML. This is independent AI-assisted internal evaluation. Codex coordinated
and applied local candidate changes, so operational execution by Codex is
distinct from independent evaluator authorship.

| Frozen control group | Baseline 0549d27e | Candidate b69c9aa7 |
|---|---|---|
| Explicit and scoped contradiction | Failed, UNSUPPORTED | Failed, UNSUPPORTED |
| Competing readings | Failed, UNSUPPORTED | Failed, UNSUPPORTED |
| Missing evidence | Pass | Pass |
| Topical evidence without contradiction | Pass | Pass |
| Workspace-local identity | Failed positive prerequisite, UNSUPPORTED | Failed positive prerequisite, UNSUPPORTED |
| Changed source revision | Unverified. Reimport retained prior active claims | Same |
| Correct source and wrong span | Failed positive prerequisite, UNSUPPORTED | Failed positive prerequisite, UNSUPPORTED |

There was **zero change in passing frozen groups (2/7 → 2/7)**, despite the
local positive regression turning GREEN. The source revision group provides
a distinct dependency finding. The import maps source identity to workspace
and content hash and derives claim identity from the same hash plus the
proposition. Reimporting changed bytes under the same filename creates
distinct observations but does not select an effective revision for
refutation. A revision-aware proof cannot derive that applicability decision
from a non-null hash or a filename match. This does not establish that all
scoped contradiction approaches will fail.

Candidate evaluator resource usage was 21.65 seconds elapsed, 79.73 seconds
user and 2.83 seconds system CPU, 1,956,000 KiB peak RSS. Fresh candidate
compilation and cache differences make these wall times unsuitable as
cognitive latency measurements. See evidence-candidate/command.txt,
resource.txt and JUnit XML under evaluator-evidence.KZ8jhJaC. The candidate
and all failed controls are retained locally. No candidate change was
published or merged.

**RESEARCH_INFORMATION_GAIN · PIVOT.** The tested text-matching approach
failed the independently authored acceptance controls. Stop adding patterns
to that recognizer. Establish an explicit versioned evidence applicability
contract in the existing acquisition and premise flow first. Use an explicit
revision operation or a typed caller-selected source revision. Do not infer
replacement from a filename or delete historical evidence. Then require the
served result to bind the premise, claim, scope and relation to a unique
literal span for the selected revision. Reassess general causal semantics
after this prerequisite passes.

Smallest next falsifiable task: through the existing served report path,
import a source that refutes a scoped premise in two isolated workspaces.
Explicitly supersede or select a nonrefuting revision in one workspace only.
Require that the old claim and source digest remain historically resolvable
there while neither appears as applicable refutation evidence. Require the
unchanged workspace to still return REFUTED with its original local claim,
source digest and exact span. An old refuting ID in the switched workspace,
loss of history, or any cross-workspace identity leak fails the experiment.
