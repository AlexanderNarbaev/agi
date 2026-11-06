## What this pull request does

<!-- What changes and why, in a paragraph a reader can check against the diff. -->

## Type of change

- [ ] Bug fix (`fix`)
- [ ] New capability (`feat`)
- [ ] Documentation (`docs`)
- [ ] Tests only (`test`)
- [ ] Refactoring, no behaviour change (`refactor`)
- [ ] Infrastructure, CI, tooling (`chore`)
- [ ] Research artefact: a hypothesis card, an experiment report, a negative result
      (see `CONTRIBUTING.md` §7)

## Related issue

<!-- Closes #123 — or, for a research artefact, the hypothesis id (H-0xx) in
     docs-v2/research/HYPOTHESES-NEW.md -->

## Verification

<!-- The project's evidence is the JUnit XML and the gates' exit codes, not a description of
     intent (docs-v2/engineering/TEST-CATALOGUE.yml). -->

- **Targeted scope(s)** run, with the exact commands:

  ```
  ./gradlew :matrix-core:test --tests "io.matrix.cognitive.*"
  ```

- **JUnit XML** (the primary evidence):
  `<module>/build/test-results/test/*.xml`
- **Gates run** (with their exit codes):
  - [ ] `scripts/quality-gate.sh`
  - [ ] `scripts/formal-gate.sh` — only if a `formal/` specification changed
  - [ ] `scripts/security-boundary-check.sh` — only if an authorisation boundary changed
  - [ ] `scripts/w45-chain-isolation-check.sh` — only if a chain's reachability changed
- **A measurement**, if the change carries a capability claim: the metric, the baseline, the
  threshold, the corpus scope, and the report it is written up in
  (`docs-v2/research/PROTOCOL.md`).

## Checklist

- [ ] Every new class has a test (`AGENTS.md`); the targeted scope is green.
- [ ] No LLM call and no wall-clock in a decision path; any randomness is seeded by a pure
      function of the state (`CONSTITUTION.md`, Article I).
- [ ] The four prohibitions are untouched (`CONSTITUTION.md`, Article IV).
- [ ] The frozen zones are untouched: `CONSTITUTION.md`, `AGENTS.md`,
      `matrix-core/src/main/java/io/matrix/ethics/frozen/**`,
      `matrix-core/src/main/resources/avro/**`, `.github/workflows/**`
      (`CONSTITUTION.md`, Article III).
- [ ] The coverage gate stays at ≥82 % METHOD on `matrix-core` (`CONSTITUTION.md`, Article V).
- [ ] Every number in the description and in the diff is backed by a run that is named —
      no unmeasured claims (`CONSTITUTION.md`, Article VI).
- [ ] The commit messages follow `BRANCHING-STRATEGY.md`.
- [ ] No credential, token or personal data is included — CI scans every push for secrets.

## Notes for the reviewer

<!-- Anything the reviewer should look at first: the risky part, the part that is not covered
     by a test, the decision that needs a second opinion. -->
