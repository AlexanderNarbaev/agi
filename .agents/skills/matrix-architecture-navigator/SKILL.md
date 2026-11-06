---
name: matrix-architecture-navigator
description: Answer where a behaviour lives in MATRIX and whether it is served. Use when new work must be placed in the right module, when a task must decide which serving chain a change belongs to, when the six cognitive cycles must be told apart, or when a change approaches a frozen or protected path. Read-only, and every answer names the file and line it came from.
license: project
metadata:
  version: "1.0.0"
  owner: coordinator
  authority: docs-v2/architecture/ + docs-v2/governance/AGENT-OPERATING-PROTOCOL.md
---

# matrix-architecture-navigator

A placement question has one honest answer, and it has three parts: **the module**, **the
chain**, and **the `path:line`**. The skill exists because this repository is mostly
implemented and mostly unserved, and an answer that names a class without naming its
serving path is not an answer.

## When to use

- new work must be placed in the right module;
- a change must be assigned to a serving chain;
- two candidates are both "the brain" and the choice matters;
- a change approaches a frozen or a protected path.

## The procedure

1. **Find the code, not the description.** Start from the class or package name and read the
   module's own `build.gradle` and `src/main/java/**`. The tree is in
   `docs-v2/engineering/W46-REPOSITORY-TOPOLOGY.md`.
2. **Name the chain.** Two serving applications, and they do not meet: chain A is the
   Quarkus workbench and epistemic application, chain B is the JDK HTTP gateway and the
   brain runtime. `references/two-chains.md` has the ports, the routes and the databases.
3. **Name the cycle.** Six cycles are named in the codebase, and two more exist that are
   served by nobody. `references/six-cycles.md` tells them apart.
4. **Answer "served" from the registry, not from the code.** A class that exists is
   IMPLEMENTED; a class on a serving path is SERVED. The registry of record is
   `docs-v2/architecture/CAPABILITY-REGISTRY.yml`, and the reconciled evidence it was built
   from is `docs-v2/engineering/W46-CAPABILITY-RECONCILIATION.csv`. The rule the registry
   applies: SERVED requires a named `serving_path`, and a class with no production caller
   is IMPLEMENTED and UNREACHABLE — say it that way.
5. **Check the protected paths.** `references/protected-paths.md` has the frozen zones, the
   eleven owner entries, the security boundary and the writer lock.
6. **Cite.** Every claim carries `path:line`. A claim without one is a guess.

**Output — the placement answer:**

| | |
|---|---|
| the module and the package | |
| the serving chain (A or B), or "neither" | |
| the cognitive cycle, if one applies | |
| served, or implemented-and-unreachable — and the evidence | |
| the protected boundary it touches | |

## The safety rules

1. **Read-only.** This skill never writes.
2. **Never assert "served" without a citation to the serving path.** An implemented class
   with no production caller is not served, and saying so plainly is the point.
3. **Never read a document's title as its status.** The status is the explicit line, and a
   document can be HISTORICAL and still be the best available source.
4. **The two chains stay isolated.** A question about one is never answered with evidence
   from the other.
5. **`docs-v2/architecture/` is read-only to this skill.**
6. **Never invent a capability.** If the registry says UNKNOWN, the answer says UNKNOWN.

## The references

- `references/two-chains.md` — the two chains, the isolation rule, what each serves.
- `references/six-cycles.md` — the six cycles, the two that serve nobody, and how to tell
  them apart.
- `references/protected-paths.md` — the frozen zones, the eleven owner entries, the security
  boundary, and the writer lock.
