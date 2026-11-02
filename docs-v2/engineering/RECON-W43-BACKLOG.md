# RECON-W43 — Backlog

**Wave:** W43 · 2026-11-02 · **The status of each item is in
`RECON-W43-STATUS.md`.**

Nothing here is a task with an unstated owner. Every item says what it is,
what it blocks, and — where it needs a decision — whose decision it is.

---

## 1. The security blockers (the release blockers)

Ordered by how much damage they can do.

| # | Item | What it is | State |
|---|---|---|---|
| 1.1 | **B1 — the tool route** | `POST /api/v1/tools/invoke` writes files at an arbitrary path, fetches arbitrary URLs, and runs a shell whose allow-list checks only the FIRST WORD: `cat /etc/shadow` passes. It is behind the boundary in the exposed mode, and a LOCAL caller can still reach it | **OWNER DECISION** — disable the route, or jail it, or give it its own authorization |
| 1.2 | **B2 — the unsigned JWT** | `JwtAuthFilter` parses `sub\|email\|exp\|plan\|role` and verifies no signature: `x\|y\|99999999999\|ENTERPRISE\|ADMIN` passes the DEVELOPER check | **OWNER DECISION** — an identity provider is a product decision |
| 1.3 | **B4 — the SSRF ingest** | `POST /api/v1/ingest/url` fetches any URL | **OWNER DECISION** — same shape as B1 |
| 1.4 | **B7 — the path-parameter tenant** | `TenantQaResource` takes the tenant from the PATH (`/v1/tenant/{tenantId}/learn`), so it is still client-chosen. The class's own javadoc admits "No auth" | OPEN — the same policy as B1 |
| 1.5 | **B8 — no role model** | one credential, one rule. There is no per-route policy beyond "read" and "write" | **OWNER DECISION** — §1.3 asks for a model, §1.5 for a policy per route |
| 1.6 | **B9 — the health endpoints** | they are public, and their information disclosure was not reviewed | OPEN — a review, not a change |
| 1.7 | The OpenAPI mismatch | the gateway declares a global `bearerAuth` it does not enforce; matrix-core publishes no OpenAPI at all | OPEN — §1.8 |
| 1.8 | The gateway container's wiring | `Dockerfile.api-gateway` exposes and health-checks **8765**; `deploy/docker-compose.yml` publishes **8080** and health-checks `/q/health`, which the gateway does not serve (it serves `/health/ready`) | OPEN — a deployment defect |
| 1.9 | The helm ingress | it publishes the gateway at `api.matrix.ai`. The chart now REQUIRES a credential, but the ingress itself is the owner's decision | **OWNER DECISION** |
| 1.10 | The `infra/k8s/**` manifests | they set no `QUARKUS_HTTP_HOST`, so the application binds the loopback INSIDE the pod and the Service is dead | OPEN — a deployment bug, not a security one |

## 2. The evaluation of the slice (mission §10)

The slice works; **it has not been measured**. This is the largest honest
gap of the wave, and it is the first thing the next wave should do.

| # | Item | What it must produce |
|---|---|---|
| 2.1 | The benchmark dataset | positive examples, counterexamples, exceptions, boundary cases, ambiguous cases, false premises, missing evidence, contradictory sources, temporal changes, negation scope, typo variants, **English and Russian separately**, cases where the answer is abstention, cases where clarification is required, and cases where a claim must be REFINED rather than deleted |
| 2.2 | The train/validation/test split | the test cases unavailable to the learning process — the discipline the v3 benchmark already follows |
| 2.3 | The metrics | claim-to-source traceability, evidence-relation classification, counterexample detection, false-premise rejection, scope and negation handling, unsupported-assertion rate, abstention precision AND recall, clarification quality, retrieval recall, revision correctness, delayed recall, transfer to unseen examples, persistence after a forced restart, idempotent import and consolidation — each with its definition and its limitation |
| 2.4 | The per-language reporting | the results are reported per language, never merged |
| 2.5 | The comparison | against the previous benchmark ONLY on identical overlapping cases; v3 reuses 24 of v1's questions, so the overlap must be computed, not assumed |

## 3. The corpus foundation (mission §7)

| # | Item | What it must produce |
|---|---|---|
| 3.1 | Verify the parsers | Markdown, TXT, HTML, PDF, EPUB, DOCX, CSV, JSON — what each actually preserves, and what each actually breaks on. A parser that exists is not a format that is supported |
| 3.2 | Choose ONE or TWO formats | and prove them end to end. The mission says not to import a large uncontrolled corpus to increase concept counts |
| 3.3 | The provenance | the document structure, the page/chapter/section location, the extraction warnings, the content hash, the idempotent deduplication, the licensing metadata, and a TRACEABLE path from every claim to the original source span |
| 3.4 | The import report | inspectable, and it must distinguish the source text from the system's interpretation |
| 3.5 | The negative rule | imported text is never treated as trusted knowledge, and no model weights are trained on imported books in this wave |

## 4. The epistemic model, continued (mission §3)

| # | Item | State |
|---|---|---|
| 4.1 | The `Case` object and the schema-16 migration | **OWNER DECISION** — ADR-W43-01 §9.1 |
| 4.2 | `Claim.modality`, `polarity`, `temporalScope`, `preconditions`, `exceptions` | same migration |
| 4.3 | `Derivation.goal`, `evidenceIds`, `contradictedClaimIds`, `assumptions`, `scopeLimitations`, `unresolvedAmbiguities`, `direct` | same migration |
| 4.4 | `Source.author`, `license`, `importMethod`, `parserVersion`, `documentPath`, `chapter`, `section`, `page`, `startOffset`, `endOffset`, `extractionWarnings`, `supersededBySourceId` | same migration |
| 4.5 | `KnowledgeRevision.supersededClaimId` | same migration — today the link is a convention (R45) |
| 4.6 | `Evidence.quality` | **OWNER DECISION** — remove it, or demote it to an explicitly uncalibrated hint (ADR §9.2) |
| 4.7 | Who decides the counterexample reading | **OWNER DECISION** (ADR §9.3) |
| 4.8 | The structured `Claim.scope` | **OWNER DECISION** (ADR §9.4) |
| 4.9 | The failure-pattern catalogue | the mission lists eleven patterns. Implement only the ones that can be operationally defined and tested, each with a minimal reproducible case and a regression test. Three are defined today: overgeneralization from one example (the promotion policy's refusal), affirming the consequent (the entailment check on a derivation), scope error with negation (`PremiseChecker.denies`) |
| 4.10 | The failed-prediction case | the mission's §4C: the original prediction, the evidence at decision time, the reasoning path, the actual outcome, the difference, the suspected failure mode, and whether the revision passed a NEW test |

## 5. The dialogue and language work (mission §6)

| # | Item | State |
|---|---|---|
| 5.1 | Inventory what is genuinely supported today | separate Unicode handling, tokenization, stemming, lemmatization, language identification and semantic understanding. The W42 audit measured the cross-lingual accuracy at **0/5** |
| 5.2 | The dialogue turn | preserve the original input, the detection status, the normalized text, the spelling corrections, the ambiguity candidates, the selected interpretation, the clarification, the retrieved claims, the reasoning result, the answer, the abstention reason, the user's correction and the resulting knowledge changes |
| 5.3 | Typo correction is not interpretation | correcting a misspelled word does not prove that the intended concept was identified |
| 5.4 | The per-language benchmark subsets | and the mixed-language and cross-language terminology cases |

## 6. The gates, continued

| # | Item | State |
|---|---|---|
| 6.1 | A gate that checks every listener binds through the boundary | R39 — the mitigation is a review, not a check |
| 6.2 | The frozen-zone baseline as a per-file hash | R40 — a diff count cannot see a same-sized change |
| 6.3 | The determinism baseline keyed by a content hash | R41 — a count cannot see a swap |
| 6.4 | The mutation harness records its survivors with a reason | the mission's §2.7; the harness does not record them yet |
| 6.5 | The startup auto-trainer | R42 — 143-158 seconds with the corpus present. The mission's §11 asks for an explicit, bounded, observable background operation with progress and failure reporting |
| 6.6 | A reproducible-build script | the mission's §14 requires one; no script produces the quoted hashes |
| 6.7 | Package signatures | `SnapshotSigner` exists in `io.matrix.security` and is not wired to the knowledge packages |

## 7. The integration census (mission §8)

No component joins the critical path without a benchmark and a fallback.

| # | Item | State |
|---|---|---|
| 7.1 | The 6 unconsumed modules and `matrix-sdk-java` | **OWNER DECISION** — wire them or retire them; they are not deleted without it |
| 7.2 | The 9-11 disconnected families | **OWNER DECISION** — mark them experimental or inactive, do not delete them |
| 7.3 | The MCTS reward function | it is constant, and the measured result loses to greedy (18.197 against 0.808 µs/op). It must not be used while that holds |
| 7.4 | `matrix-web-ui` | it is a decorative site: 0 tabs, 4 of 6 buttons dead, a hardcoded `https://api.matrix.ai`, and a LiveDemo that SIMULATES MATRIX in the browser. The real workbench is `matrix-core/src/main/resources/META-INF/resources/workbench/index.html` |
| 7.5 | The stale risk statuses | 26 of 36 rows in the W42 register were stale; this wave updated R37 and added R39-R47 |

## 8. The workbench UI

| # | Item | State |
|---|---|---|
| 8.1 | A panel for the counterexample cycle | the API is live (`/claims/{id}/examples`, `/assessment`, `/refine/accept`, `/refine/reject`); the HTML workbench does not show it |
| 8.2 | The panel must distinguish | source evidence, direct observation, derived conclusion, hypothesis, contradiction, unknown or insufficient information |
| 8.3 | The 16 uncalled endpoints of 52 | the W42 documentation said 15; the measured number is 16 |
