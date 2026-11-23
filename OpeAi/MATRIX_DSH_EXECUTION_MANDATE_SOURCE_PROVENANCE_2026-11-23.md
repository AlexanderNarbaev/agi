# M.A.T.R.I.X. — DSH Execution Mandate: Revision-Accurate Evidence and Claim Provenance

**Status:** OWNER-AUTHORIZED EXECUTION COMMISSION. This document is a bounded implementation task, not a request for another strategy report.
**Execution:** DeepSeek Harness (DSH), primarily MiniMax on the already verified subscription-backed route. DeepSeek may be used only on the already approved provider path and with the same source-disclosure protections. Codex acts as the scarce upstream commissioner, not the loop controller or routine reviewer.
**Repository:** `AlexanderNarbaev/agi`, default working branch `develop`. The externally verified reference on 2026-11-23 was `f4ad32d8bb35a6229122f97195c6af2e0817400b`; verify actual local and remote HEAD before making changes. Stable `main` is outside this commission.
**Parent rules:** `CONSTITUTION.md`, `AGENTS.md`, current approved owner decisions, `COGNITIVE-DEVELOPMENT-PROGRAM.md`, CRJ-01 protocol and the single-owner AI-assisted evaluation amendment. All new tasks, comments, commits and documentation in **English**.

## 1. Mission: deliver a correctness gain, not another partial experiment

**Required outcome:** make MATRIX reliably preserve immutable historical sources while serving **the actually applicable source revision and its own claim/evidence identity**. A successful answer must never attach a claim ID from an inactive revision to a different active observer/source merely because the proposition text or canonical concept looks equivalent.

The most recent local SQLite schema v21→v22 candidate passed **6/7 existing independent control groups** but failed the provenance/corroboration group. It was correctly withheld from `develop`. Its observed failure is:

- a response cites an **active observer source** while carrying a **canonical claim identifier belonging to an inactive revision**;
- a separate diagnostic found the importer's own extracted claim for the observer had been marked `SUPERSEDED`;
- workspace isolation, explicit historical V1 access, rollback/restart, learned-procedure exclusion and cache freshness passed in that local run;
- 26,003/30,000 reported MiniMax tokens were consumed and **no remote code change was published**.

Treat this as a reproducible counterexample requiring a revised hypothesis. Do not discard the useful local candidate or pretend its other passing controls are newly proved. Reproduce the failure first, then minimize its cause.

## 2. Root architectural question (must be explicitly answered)

Distinguish these identities **without conflating them**:

1. **Logical document** — a named or explicitly declared revision stream. Same filename alone must not imply same logical document.
2. **Immutable source revision** — concrete content digest, source ID, imported bytes, spans and import identity.
3. **Canonical proposition / semantic claim** — a normalized assertion that different source revisions or independent observers may express.
4. **Source-specific claim occurrence / evidence attestation** — the exact actual claim and source occurrence that warrants a citation, with its own status and lineage.
5. **Applicability projection** — which source revision and attestations are eligible for a current versus explicit historical query, under an approved policy.

Inspect the actual present implementation of `DocumentImporter`, `KnowledgeStore`, `KnowledgeSnapshot`, `KnowledgeWorkbench`, `KnowledgeRetriever`, `PremiseAssessment`, `KnowledgeRevision`, `Claim`, `Evidence`, the revision projection, and any procedure/cache validity code. If the schema already expresses this distinction, fix the defective mapping rather than adding another storage layer. If a new field/table is genuinely needed, state why the existing v22 candidate cannot express it, preserve migration compatibility and the owner's previously approved v21→v22 boundary, and apply only through the accepted migration protocol.

**Forbidden shortcuts:** globally marking a canonical proposition false because one revision was superseded; changing source or claim IDs merely to pass the test; erasing history; selecting latest by wall clock; using filename equality as proof of logical identity; treating source hash as an applicability decision; replacing SQLite with PostgreSQL; introducing a second knowledge store or cognitive conductor; hardcoding the particular fixture or topic.

## 3. Minimal real failing control — before modifying production

Create fresh, isolated A and B workspaces. Use an explicitly identified document V1 with a traceable contradictory claim, a V2 that changes its applicability, and an independent **observer source** whose own extracted claim is warranted and remains active.

Generate and retain the following audit fields for every emitted claim/evidence: workspace ID, logical document ID, selected source revision ID and content digest, actual evidence source ID, actual claim occurrence ID, canonical proposition ID (if different), claim status, exact literal evidence span, span locator/hash, selection policy/version, and query context (`current` or explicit historical revision).

**Primary RED counterexample:** in workspace A, switch the explicit document revision from V1 to V2. When an active observer's claim supports the result, the answer must cite **the observer's own active claim occurrence and active source**, not pair the observer's source with the canonical claim ID from V1. Verify the observer's original stored status remains unchanged unless an independently valid, explicitly audited transition authorizes changing it. No silent `SUPERSEDED` mutation of an independent observation.

Require one test that **fails under the prior 6/7 candidate**. Preserve original RED JUnit XML, minimal fixture and exact commit SHA. If the test is green before the fix, it is not the required counterexample; strengthen it rather than claiming success.

## 4. Regression and anti-regression matrix

All previous seven control groups remain necessary (their definitions and frozen artifacts take precedence over this summary), plus explicit evidence identity checks:

- **A V1→V2**: A's ordinary current answer excludes inapplicable V1-only refutations and cites active evidence correctly; historical V1 query reconstructs exact original claim, source and span.
- **B untouched**: B still answers `REFUTED` using its own V1 claim, source and literal span.
- **Observer corroboration**: A valid independent observation retains its own claim/status; valid answer cites its occurrence and source, not a foreign inactive claim ID.
- **Wrong claim/source pairing**: correct topic and source name with a foreign claim ID must be rejected, not scored as correct.
- **No accidental supersession** on same filename without explicit logical-document revision relation; identical content reimport remains idempotent.
- **Partial vs full correction**: unrelated claims and independently corroborated evidence remain usable; affected claims are subject to applicability, not deletion.
- **Persistence**: migration restart, failed/partial update rollback, revision lineage, snapshot/export, historical query and crash consistency do not lose source/claim/evidence history.
- **Learning**: procedures whose support depends on inapplicable revisions are revalidated or excluded; independent active observer evidence must not be discarded through shared canonical IDs.
- **Cache**: pre-revision report or action replay does not silently return a stale successful answer when revision applicability changes.
- **Epistemic safety**: missing refuter does not imply the opposite proposition is proved; unresolved scope may require `AMBIGUOUS`/`UNSUPPORTED` rather than invented confidence.
- **Cross-workspace isolation**: no claim/source IDs leak between A and B, even for identical content.

At least one **new blind adversarial fixture** must be authored by a reviewer operating in a fresh context, frozen before final evaluation and not provided to the implementation agent. This is internal single-owner AI-assisted evidence, not an independent human study. Previous exposed control groups are regression tests, not new held-out validation.

## 5. DSH-only autonomous execution loop

**The outer Codex session must not supervise each step.** DSH owns the complete task DAG and is permitted to repeat local implementation/test/review cycles within this commission and standing owner authorization.

1. **RECON / TARGETED:** verify HEAD, worktrees, local v22 candidate, preserved 6/7 evidence and root failure. Do not repeat broad project archaeology.
2. **RED:** reproduce the cross-revision observer/claim mismatch on a disposable v22 SQLite fixture and the actual served application path. Save the failing assertion and XML.
3. **MODEL:** inspect both conceptual and source-specific claim identities. Decide the smallest compliant fix. If necessary, run a second independent architecture critique, but do not initiate a second general research wave.
4. **IMPLEMENT:** separate writer and reviewer contexts. Change only what is required. Avoid a broad refactor or a new subsystem.
5. **VERIFY:** targeted Gradle/JUnit, mutation/negative control, full previous seven groups, new blind control, actual packaged HTTP or approved real service endpoint, database reopen/restart and affected module regression. Track exact tested SHA.
6. **ADVERSARIAL REVIEW:** a fresh DSH reviewer, unable to see hidden evaluation answers while writing implementation, must challenge wrong claim IDs, source/claim mismatches, status corruption, stale caches, cross-workspace leakage and temporal-query behavior. Apply valid criticism and re-run.
7. **INTEGRATE:** preserve commit history, add concise English evidence and capacity/status updates to canonical records; push compliant `fix/*` or `feature/*` branches; open PR into `develop`; require all applicable technical checks, then merge under already granted standing authorization. Do not touch `main`.
8. **CLOSE:** only after the integration and gates, provide a short outcome report, resource checkpoint and next recommended cognitive capability. If the hypothesis is falsified after serious attempts, preserve objective evidence and propose a different mechanism rather than a superficial tweak.

Keep at most four deep agents concurrently and **one heavy `matrix-core` Gradle process** across all agents. Separate writable worktrees and data directories; one source of truth for test results. Permit cheap read-only parallel analysis. Do not have a verifier read the implementer's private expectations if claiming blind checks. Protect secret credentials, hidden evaluation data and live databases.

### Role assignment (activate only when needed)

- **Coordinator** (MiniMax): dependency DAG, scoped authorizations, release checkpoints.
- **Implementer** (MiniMax): source-specific revision/attestation correction.
- **Adversarial verifier** (fresh MiniMax or already approved DeepSeek provider): negative controls and hidden-case authoring; no implementation write access.
- **Integration engineer** (MiniMax): packaged test, migration, restart, GitHub checks; no self-approval of scientific claims.

The coordinator may hand off between subagents, preserve the written state, and resume the saved DSH session. **Headless is one-shot and its process exits:** persistence of a Session is not the same as ongoing background execution. If Codex must exit, launch the DSH worker under a verified user-owned process supervisor independent of the Codex tool invocation, or give the owner an exact standalone launch command. Verify live execution rather than promising that a detached shell will survive. For a reliable persistent multi-turn coordinator, prefer the installed DSH ACP surface when its actual route and permission semantics are confirmed. If headless exits after one task, use its saved session / explicitly persisted continuation: **do not use a fresh Codex interaction for each DSH continuation**. Ensure all required child work has completed; do not interpret a successful headless process exit as proof of passing tests or completed GitHub actions.

## 6. Budget and continuation policy — owner-approved 512K-token maximum

Use the existing paid-for MiniMax subscription where permitted. The previous 30,000-token hard cap ended before a correct implementation. For this milestone:

- **Owner-approved MiniMax execution budget: 512K = 512,000 reported tokens TOTAL for this milestone**, summed across all DSH/MiniMax coordinator, implementer, reviewer and retry calls. This is the authorized maximum, **not** a target, per-agent allowance or model context-window setting. Report `NOT_INSTRUMENTED` if usage is not exposed, and do not invent cumulative counts.
- **Autonomous continuation:** DSH may spend the available budget up to this 512,000-token total without seeking renewed Codex approval at intermediate thresholds, provided work stays within approved scope and the existing subscription/usage quota permits it. Stop before exceeding the budget or on actual quota exhaustion; never silently purchase credits, modify billing or switch providers.
- **Local checkpoints:** write concise durable checkpoints at major acceptance boundaries and approximately every 64,000 reported tokens; include cumulative use, remaining allowance, concrete RED/GREEN evidence, unclosed defects and next action. Review convergence particularly around 256K and 384K without treating either as a stop threshold or consuming Codex context.
- The 512K allowance does **not** override the stop/pivot conditions, security restrictions or any per-request/model context limits. If progress has clearly stopped, stop early rather than burning the remaining allocation.
- Do not terminate merely because 6/7 passed, a subagent's context became full, a transient tool execution failed, or an initial negative result appeared. Save state, resume or swap a DSH child context, and continue until the accepted criterion or genuine stop condition is met.
- **Stop and pivot** after two materially distinct approaches fail without new information, when the architecture would violate a protected invariant, or when evidence demonstrates the acceptance condition cannot be satisfied under the approved scope. An unsupported confidence claim is never an alternative to proof.
- Codex may be called for a material new owner-only architecture decision, a serious safety boundary change, or one final milestone review. **No ongoing Codex review loop**.

### Durable session handoff

Create and update a short local `EXECUTION_STATE.md` (outside the tracked repository unless an approved document path exists) containing: objective, source HEAD, current worktree, DSH session ID, provider/model, active child scopes, completed/failed checks, evidence paths, actual token use, pending work, exact next command and permission state. Retain artifacts outside ephemeral `/tmp`; do not store credentials or hidden gold in this handoff. Never paste multi-megabyte tool logs back into Codex.

## 7. Security and Git standing authorization

Existing owner consent permits routine source files, the scoped MiniMax endpoint, isolated code/test execution, commits, pushes to nonprotected branches, PR publication and technically verified merges into `develop`. Do not ask for permission again for these actions. Existing project Constitution and protected boundaries still win.

Do **not** expose secrets, personal/untracked data, raw live DBs or blind evaluator answers; do not modify `main`, bypass failed technical checks, rewrite Git history, erase historical claims, deploy externally, change paid-plan billing, or perform destructive production migration. A migration affecting a live DB or protected contract outside the already approved isolated SQLite v22 experiment requires explicit owner approval.

If headless sandbox prevents a necessary action, use the existing authorized DSH permission/profile route with a verifiable non-secret workspace policy; do not silently disable sandboxing or convert failures into PASS. Report any genuinely unavailable privileged operation with a one-line operator instruction, and keep permitted independent work moving.

## 8. Hard acceptance criteria

The milestone is **PRODUCT_GAIN** only if all are true:

1. The previously failing observer/canonical-claim mismatch is reproduced RED on the earlier implementation and GREEN after the fix.
2. **All seven previous independent controls pass**, with no changed historical expected answers and no scored fixture contamination, plus the new exact source/claim/span/status control and a separately frozen blind adversarial test.
3. The served answer contains an **internally consistent, exact and active** source revision/content digest, actual supporting claim occurrence ID, expected status and literal evidence span. No incorrect canonical-ID/source pairing.
4. Historical V1, current V2, untouched B and independent observer evidence are all simultaneously correct after restart. Snapshot/migration/rollback safety is unchanged.
5. Learned procedures and caches follow revision applicability; unchanged valid independent evidence remains available.
6. Real served-path and relevant Gradle/CI evidence is green on the exact final SHA; limitations of skipped native-image tests are disclosed, not misrepresented.
7. The accepted fix is committed and PR-integrated into `develop`, with traceable source changes and a concise VALUE_CHECKPOINT.

If a test cannot be executed, status is `INCOMPLETE`, not PASS. If only 6/7 succeed again, report a falsified or incomplete candidate, not a new product capability.

Final report must be **compact**: baseline SHA → final SHA, actual old failure mechanism, minimal design change, RED→GREEN, 7/7+new control, packaged/CI result, cost, known limitation, next capability, and `CONTINUE/PIVOT/STOP`. Do not output extended agent transcripts or day-by-day diaries. Intermediate work and detailed logs stay in files.

**Execute this commission. Preserve data. Stop returning planning-only artifacts. Deliver a tested, reviewed and integrated cognitive capability or a correctly falsified hypothesis supported by executable evidence.**
