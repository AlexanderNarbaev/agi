---
name: matrix-document-integrity
description: Check that the MATRIX document set is internally consistent — that every cross-document reference resolves, that every path a document cites exists, that the governance registers agree with each other and with the tree, and that a document's stated status matches its content. Use after a documentation change and before a wave closes. It reports defects; it does not rewrite documents.
license: project
metadata:
  version: "1.0.0"
  owner: coordinator
  authority: docs-v2/governance/DOCUMENT-STATUS-POLICY.md
---

# matrix-document-integrity

A **checker, not a writer**. The writers already exist: the governance registers are the
writers. What the audits actually found broken was never a missing document — it was a broken
reference, a stale status, a duplicated identifier, a prose path that does not resolve. A
writer would create a second source of truth; a checker cannot.

## When to use

- after a documentation change;
- before a wave closes;
- when a register and the tree are suspected of disagreeing.

## The procedure

1. **Run the checkers.** `references/checklist.md` names the seven defect classes and the
   command that finds each. The link and reference checker is `docs-link-check.py`; the
   registry validator is `validate-project-registry.py`; the project-state validator is
   `validate-project-state.py`; the test-catalogue validator is `validate-test-catalogue.py`
   — all four live in the repository's scripts/ directory, and each is run from the
   repository root.
2. **Check the statuses.** The vocabulary is in
   `docs-v2/governance/DOCUMENT-STATUS-POLICY.md`, and the rule is in
   `references/status-vocabulary.md`: a status is an explicit line, never a title.
3. **Check the registers against the tree.** The canonical set is listed in
   `docs-v2/governance/DOCUMENT-STATUS-POLICY.md`; the current state is
   `docs-v2/governance/PROJECT-STATE.md`; the decisions are
   `docs-v2/governance/DECISION-REGISTER.md`; the open questions are
   `docs-v2/governance/OPEN-QUESTIONS.md`.
4. **Check the navigation.** The section index is `docs-v2/INDEX.md`; the complete
   document registry is `docs-v2/engineering/W46-DOCUMENT-AUDIT.csv`.
5. **Report each defect with `path:line`, its class and its severity.** A defect without a
   line cannot be fixed by the person who reads the report.

**Output — the defect list:** broken relative link · broken anchor · missing
prose-referenced file · duplicate stable identifier · a current document citing a superseded
one · a status that does not match the content · a count that does not match the tree.

## The safety rules

1. **It reports; it does not fix.** A rewrite is a separate task with its own write scope.
2. **`docs-v2/_raw/` is never read for content and never written.**
3. **The eleven owner entries are never modified** — the list in
   `docs-v2/governance/AGENT-OPERATING-PROTOCOL.md` §1.3.
4. **An external link is reported UNVERIFIED** — never fetched, because fetching is an
   outbound call the owner has not authorised for a checker.
5. **A file's absence is a finding only when a document claims it exists**, not when it
   simply does not.
6. **It does not change a status line**; it reports that the status and the content
   disagree.

## The references

- `references/checklist.md` — the seven defect classes and the command that finds each.
- `references/status-vocabulary.md` — the status vocabulary and the rule that a status is an
  explicit line, not a title.
