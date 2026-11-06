# W49 — ONBOARDING SURFACE: THE AUDIT AND THE CHANGE LOG

**Status:** `ACTIVE` · **Date:** 2026-11-06 · **Wave:** W49 (task W49-07, owner directive
OD-048 <!-- no-id: the owner's directive identifier, not a document in this repository -->,
"Public Research Presence")

**What it owns:** the seven files a newcomer or an external researcher meets first —
`CONTRIBUTING.md`, `CODE_OF_CONDUCT`, the four issue templates and the pull-request template —
and the record of what was wrong in them, what replaced it, and how every claim in the new text
was checked.

**What it is not:** a guide. The guide is `CONTRIBUTING.md`; this document is the audit behind
it and the change log, so that a later correction can be traced to the defect it fixed.

---

## 0. Why this wave touched these files

The project's documentation had drifted in one direction: it kept naming **documents and
channels that no longer exist**, and it promised **services the project does not run**. Both
are the failure mode the constitution calls out — a claim that is not backed by anything that
exists (`CONSTITUTION.md`, Article VI) — applied to the project's own front door.

The three kinds of defect found:

1. **Dead paths.** The v1 documentation tree (`docs/` <!-- no-link: the v1 tree was removed by
   the migration to docs-v2 -->) was removed by that migration, and the front-door files kept
   pointing into it.
2. **Invented channels.** A maintainer mailbox, a chat, an office-hours call and a response
   time — none of them is configured anywhere in this repository.
3. **Unsourced claims.** A "Three Prohibitions" where the constitution has **four**, a
   "Checkstyle enforced" where no Checkstyle plugin exists, an "Axiom 1" that survives only in
   the raw archive, and a monotonic-functional requirement attributed to the wrong article.

---

## 1. The method — how every claim below was checked

| the claim | how it was checked |
|---|---|
| a path exists | `test -e` / `ls` on the path, from the repository root |
| the versions | `gradle/wrapper/gradle-wrapper.properties` (Gradle **9.6.0**), `build.gradle` and `matrix-core/build.gradle` (`sourceCompatibility = VERSION_25`), and `./gradlew --version` (Gradle 9.6.0, launcher JVM 25.0.4.1) |
| the serving paths | `docs-v2/architecture/RUNTIME-TOPOLOGY.md`, the routes in `matrix-core/src/main/java/io/matrix/api/` and `io/matrix/cognitive/`, and the chain-isolation gate |
| the verification scopes | `docs-v2/engineering/TEST-CATALOGUE.yml` — the scope ids, their filters and their last measured results |
| the research lifecycle | `docs-v2/research/RESEARCH-INDEX.md`, `docs-v2/research/PROTOCOL.md`, `docs-v2/research/HYPOTHESES-NEW.md`, `docs-v2/research/NEGATIVE-RESULTS-INDEX.md`, and the 68 files in `docs-v2/research/reports/` |
| the CI checks | `.github/workflows/ci.yml` — the commands the pipeline actually runs |
| "there is no Checkstyle" | `grep -rn "checkstyle"` over the build files, the workflows and the scripts — no match |
| "Axiom 1 is not normative" | `grep -rn "Axiom 1"` — the only hits are `docs-v2/_raw/2026-08-pre-v2/` (the raw archive) and one code comment |
| "the monotonic functional Φ is not in the constitution" | `grep -rn "Φ\|монотонн" CONSTITUTION.md` — no match |
| the repository's own links | `scripts/docs-link-check.py` (strict mode) |
| the issue tracker is a real channel | `git remote -v` — the `origin` remote is the GitHub repository the issue tracker belongs to |

**Every path named in the seven rewritten files was verified to exist.** A sweep of the
backticked path tokens in the seven files found **43 distinct tokens, all of them resolving**.
The link checker in strict mode reports **0 defects** for `CONTRIBUTING.md` and `CODE_OF_CONDUCT`.

---

## 2. The audit, file by file

### 2.1 `CONTRIBUTING.md`

| the defect | the evidence | the replacement |
|---|---|---|
| pointed at the removed v1 tree: `docs/spec/` <!-- no-link: removed by the migration to docs-v2 --> | the path does not exist; the file's own HTML comment said so | `docs-v2/specifications/`, `docs-v2/designs/`, `docs-v2/governance/ROADMAP.md` |
| pointed at `docs/research/HYPOTHESES.md` <!-- no-link: the v1 tree was removed --> | the path does not exist | `docs-v2/research/HYPOTHESES-NEW.md` — the current register, with the rule that a third register must not be created |
| told the contributor to add a line to `WAL.md` <!-- no-link: no such file; the session journal is wal/SESSION_WAL.md and it is git-ignored --> | no such file | the change log is the pull request; the wave report is `docs-v2/engineering/W<N>-STATUS.md` |
| attributed the monotonic functional Φ to "Article III" | the Constitution's Article III is the frozen zones; Φ appears nowhere in it | the rule that is actually normative: Article I (stratified stochasticity — no wall-clock and no LLM in a decision path) |
| said "coverage gate ≥ 82 %" without the axis | the gate is **≥ 82 % METHOD** on `matrix-core` | the same figure, named exactly |
| never said how to verify anything | — | §5 of the new file: the targeted scopes from the catalogue, the JUnit XML as the evidence, the three gates, the JMH command |
| never distinguished an engineering contribution from a research one | — | §1: the two paths, side by side, with the evidence each one produces |

### 2.2 `CODE_OF_CONDUCT`

| the defect | the evidence | the replacement |
|---|---|---|
| "report to `conduct@matrix-ai.org` (will be established)" | the mailbox does not exist, and the text itself says it will be | the issue tracker is named as the only channel, and the absence of a private one is stated plainly |
| "maintainers will respond within 48 hours" | there is no rota, no moderation team and no service to respond | "no response time is promised", with the reason |
| "all reports are confidential" | a public issue is public | the report is public, and the document says so before the reader writes it |
| "Three Prohibitions" | `CONSTITUTION.md`, Article IV states **four** | the four, verbatim, including the fourth (no reproduction without the operator's explicit consent) |
| cited `docs/L0_manifesto.md` <!-- no-link: the v1 tree was removed --> and `docs/L11_Mangement.md` <!-- no-link: the v1 tree was removed --> | neither exists | `CONSTITUTION.md`, Article IV, which does |
| named "chat, forums, video calls, conferences" as project spaces | none of them is configured in this repository | the issue tracker, the pull requests and the documents in this repository |
| said nothing about the licence, the constitution or the operational safety restrictions | — | §1: the table of the four binding documents, and the three enforcement mechanisms that are not documents at all — the ethics layer, `scripts/security-boundary-check.sh` and the frozen-zone check in `scripts/quality-gate.sh` |

### 2.3 The four issue templates and the pull-request template

| the defect | the evidence | the replacement |
|---|---|---|
| `.github/ISSUE_TEMPLATE/bug_report.md` offered "Gradle version: 9.1.0" as an example | the wrapper pins **9.6.0** | the versions the build actually uses, and the two commands that read them |
| `.github/ISSUE_TEMPLATE/bug_report.md` asked for a description of the failure | the project's evidence is the JUnit XML | the exact command, the failing scope, and the XML path |
| `.github/ISSUE_TEMPLATE/feature_request.md` asked "which L-documents would this affect? (L1, L5, L7)" | the L-documents are the removed v1 tree | the canonical directories that exist: `docs-v2/specifications/`, `docs-v2/designs/`, `docs-v2/governance/`, `docs-v2/architecture/`, `formal/` |
| `.github/ISSUE_TEMPLATE/feature_request.md` had no notion of "served" | the registry's four axes are the project's own distinction between implemented and running | the four axes as a table, and the serving path if there is one |
| `.github/ISSUE_TEMPLATE/pilot_proposal.md` described the pilot as "a new pilot project (L13)" | L13 does not exist | a domain pilot: the real problem, the stakeholders, the data and its licence, the baseline, the acceptance criteria, the failure criteria, the budget |
| `.github/ISSUE_TEMPLATE/pilot_proposal.md` had a checklist of "properties demonstrated" | it was a marketing list, and it asked nothing about whether the capability under evaluation is served | the mandatory question, in §3 of the template, with both answers spelled out |
| `.github/ISSUE_TEMPLATE/pilot_proposal.md` assumed a working system | the capability registry records that most capabilities are implemented but **not served** | the header note: a pilot proposal must not assume that MATRIX is production-ready |
| `.github/ISSUE_TEMPLATE/rfc.md` listed "L0…L8" and cited "L11 §4.2" for the discussion periods | neither the documents nor the periods exist | the canonical directories, and the real decision record — `docs-v2/governance/DECISION-REGISTER.md` |
| `.github/PULL_REQUEST_TEMPLATE.md` asked for "Checkstyle passes" | no Checkstyle plugin exists in the tree | the checks the pipeline really runs: the compile, the tests, the three gates, the secret scan, SpotBugs |
| `.github/PULL_REQUEST_TEMPLATE.md` asked for "`./gradlew test` passes" | the full `matrix-core` suite needs the owner's approval and a resource estimate | the targeted scope, with the catalogue as the source of the scope list |
| `.github/PULL_REQUEST_TEMPLATE.md` cited "Axiom 1" and "Three Prohibitions" | Axiom 1 survives only in the raw archive; the prohibitions are four | the four prohibitions, and the frozen-zone list from Article III |

---

## 3. What was deliberately removed, and why

| removed | why |
|---|---|
| `conduct@matrix-ai.org` | the mailbox does not exist; the document said it "will be established", which is a promise the project cannot keep |
| "maintainers will respond within 48 hours" | a response SLA the project has no mechanism to meet |
| "All reports are confidential" | false for the only channel that exists |
| the chat, the Discord, the office hours, "good first issue" | none is configured in this repository |
| the L-document references (`L1`, `L5`, `L7`, `L11`, `L12`, `L13`, `L0…L8`) | the v1 tree was removed; the canonical set is under `docs-v2/` |
| "Checkstyle enforced" | no Checkstyle plugin exists; the pipeline runs SpotBugs, the secret scan and the dependency check instead |
| "Axiom 1 — no float/double in the MPDT core" | it survives only in `docs-v2/_raw/`; the normative set does not state it |
| "Three Prohibitions" | the constitution has four |
| "coverage target ≥ 80 % for new code" | the gate that exists is ≥ 82 % METHOD on `matrix-core` |
| the promise that a review happens within a business day | the project is maintained by its owner together with AI agents; the acceptance criteria are the objective checks, not a queue |
| "AGPLv3 **with ethical restrictions**" as a second licence | AGPL-3.0 is the only licence. The additional conditions on the permitted use are **in the same `LICENSE` file**, and the new text says exactly that |

---

## 4. What could not be sourced

- **A maintainer contact.** None exists in the repository — no mailbox, no chat, no private
  reporting route. The issue tracker is the only channel, and it is public. The CoC says so
  instead of inventing a private one.
- **A response time.** There is no rota and no service. No time is promised.
- **A moderation team.** There is one owner. The consequences are stated; the enforcement is
  the owner's.
- **The clone URL.** Two remotes are configured (`gitverse` and `origin` on GitHub). The new
  `CONTRIBUTING.md` therefore says "the repository URL you were given" rather than naming one
  that could change.
- **The number of the wave's own task list.** The `docs-v2/engineering/PLAN.md` file exists but
  `docs-v2/governance/ROADMAP.md` supersedes it; the guide cites the roadmap.

---

## 5. The verification

| the check | the result |
|---|---|
| `python3 scripts/docs-link-check.py --scope CONTRIBUTING.md --strict` | 0 defects, 0 strict failures |
| `python3 scripts/docs-link-check.py --scope CODE_OF_CONDUCT` | 0 defects; 1 external link, reported as UNVERIFIED because the tool performs no network check |
| the path sweep over the seven files (backticked tokens that name a repository path) | 43 distinct tokens, all resolve |
| the link checker over the four issue templates and the pull-request template | the tool scans `docs-v2/` and the root documents; it reports 0 documents for the `.github/` scope, so those five files were verified by the path sweep instead |

---

## 6. The boundary of this wave

**Not touched, and why:**

- **`.github/workflows/**`** — frozen (`CONSTITUTION.md`, Article III) and explicitly excluded
  by the directive. The workflows are the source of the checks the new pull-request template
  asks for; nothing in them was changed.
- **`.github/ISSUE_TEMPLATE/security.md`** — outside this task's scope. **It is broken**: it
  points the reader at `SECURITY.md` <!-- no-link: the file does not exist in this repository -->,
  which does not exist in this repository, so a critical vulnerability report is sent to a
  missing document. It needs either a `SECURITY.md` or a rewrite that names the channel that
  exists. Reported to the lead.
- **`CONTRIBUTING`** (the file without the extension) — outside this task's scope, and it is a
  second, English contributing guide that contradicts the rewritten `CONTRIBUTING.md`: it
  promises a 48-hour review, a chat, office hours and a `CODE_OF_CONDUCT.md` <!-- no-link: the
  file has no extension; the real one is CODE_OF_CONDUCT --> that does not exist, and it cites
  the same removed L-documents. Two contributing guides that disagree is
  the defect the W46 audit found in the registries; this is the same shape. It should be
  deleted, or reduced to a pointer to `CONTRIBUTING.md`. Reported to the lead.
- **`SECURITY.md`** <!-- no-link: the file does not exist in this repository --> — does not exist. See above.
