---
name: matrix-evidence
description: Reconcile a number, a status or a claim against the artefact that proves it, and say plainly what the artefact does not prove. Use before publishing any test result, capability number, or research finding — and before saying a claim is false. In test mode it reads the retained JUnit XML and the audit CSVs; in research mode it reads the hypothesis register and the EXP reports against primary sources. It never runs the test suite.
license: project
metadata:
  version: "1.0.0"
  owner: coordinator
  modes: [test, research]
  authority: AGENTS.md + docs-v2/engineering/W45-TEST-EVIDENCE-RECONCILIATION.md
---

# matrix-evidence

Two modes, one contract: **a claim is only as strong as the artefact that proves it, and an
artefact nobody can re-read is not evidence.** The modes differ in their artefacts, not in
their discipline.

## Mode: test

1. Find the artefact before the number. The evidence families are listed in
   `references/test-evidence.md`; the reconciled baseline is
   `docs-v2/engineering/W45-TEST-EVIDENCE-RECONCILIATION.md`, and the per-scope registry is
   `docs-v2/engineering/TEST-CATALOGUE.yml`.
2. **Count with the right denominator.** Classes, executions and files are not
   interchangeable, and the artefact names which is which.
3. **Separate the failure kinds** before reporting either: an ordinary JVM regression and a
   native-image infrastructure failure are different findings.
4. Name the command that re-derives the number, and the path of the file that holds it.
5. Say what the artefact does **not** prove.

**Output — the reconciliation:** the value the artefact supports · the exact command that
re-derives it · the denominator it belongs to · the residual gap · `unverified` where the
artefact cannot answer.

## Mode: research

1. Find the claim in the register: `docs-v2/research/HYPOTHESES.md` and
   `docs-v2/research/HYPOTHESES-NEW.md`. A claim with no register entry is not a project
   hypothesis yet.
2. Find its experiment report and its preregistration under `docs-v2/research/`.
3. **Tie every substantial claim to a primary source.** An aggregator is not a source; an
   unverifiable claim is marked UNVERIFIED rather than dropped. The negative results are
   indexed in `docs-v2/research/NEGATIVE-RESULTS-INDEX.md`, and the research body in
   `docs-v2/research/RESEARCH-INDEX.md`.
4. **Persist the finding** where the project's own rule requires it; a finding that exists
   only in the chat is lost.

**Output — the same shape as the test mode**, with the primary source beside each claim.

## The safety rules

1. **Never run the `matrix-core` test suite.** The full run is owner-held; a targeted suite
   runs only when the task says so.
2. **Never publish a number that no retained artefact supports**, and never restate a CSV
   column as a count without re-deriving it.
3. **Three denominators are three denominators.**
4. In research mode, every substantial claim is tied to a primary source; an aggregator is
   not a source.
5. **`docs-v2/_raw/` is never written.**
6. A test failure is separated from an environment failure before either is reported.

## The references

- `references/test-evidence.md` — the artefact families, the three denominators, the failure
  taxonomy, the retention rules.
- `references/research-evidence.md` — the hypothesis register, the experiment-report shape,
  the preregistration rule, the primary-source rule.
