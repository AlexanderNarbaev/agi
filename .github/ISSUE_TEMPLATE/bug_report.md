---
name: Bug Report
about: Report a bug or unexpected behaviour
title: "[BUG] "
labels: bug
assignees: ""
-->

## What happens

<!-- The observed behaviour, in one or two sentences. -->

## What was expected

<!-- And what should have happened instead. -->

## How to reproduce

<!-- The exact commands, not a description of them. A reproduction that is a command can be
     re-run; a reproduction that is a story has to be re-derived. -->

```
./gradlew :matrix-core:test --tests "io.matrix.cognitive.*"
```

- **The failing test or route** (the class, the method, the route):
- **The scope it belongs to** (from `docs-v2/engineering/TEST-CATALOGUE.yml`):
- **The output** (the stack trace, or the failing assertion — in full, not summarised):

## Environment

- **Commit:** `git rev-parse --short HEAD`
- **JDK:** 25 (`java -version`)
- **Gradle:** 9.6.0 (`./gradlew --version`)
- **OS:**
- **How MATRIX was run** (`:matrix-core:quarkusDev`, `scripts/start-mind.sh`, a test, …):

## Evidence

<!-- The project's evidence is the JUnit XML, not the console. Attach or name the file. -->

- **JUnit XML:** `<module>/build/test-results/test/*.xml`
- **Gate exit code**, if a gate is involved (`scripts/quality-gate.sh`,
  `scripts/formal-gate.sh`, `scripts/security-boundary-check.sh`):

## Notes

<!-- Anything else that helps: a reproducer, a minimal input, a related issue. -->
