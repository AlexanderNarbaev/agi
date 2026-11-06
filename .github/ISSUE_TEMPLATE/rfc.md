---
name: RFC (Request for Comments)
about: Propose an architectural, specification or normative change
title: "[RFC] "
labels: rfc
assignees: ""
---

> Use this template when the change touches an architecture, a specification, a contract or a
> frozen boundary. A bug or a contained improvement is a **bug report** or a **feature
> request** instead.

## 1. Motivation

<!-- The problem, not the solution. What breaks, what is missing, what cost is being paid?
     If the answer is "it would be nicer", say what is wrong with the current state. -->

## 2. The proposal

<!-- What changes, concretely: the files, the contracts, the data, the interfaces. -->

## 3. Scope of the change

- **Modules affected:**
- **Serving paths affected** (chain A, chain B, or none — see
  `docs-v2/architecture/RUNTIME-TOPOLOGY.md`):
- **Canonical documents affected:**
  - [ ] `docs-v2/specifications/` — a SPEC
  - [ ] `docs-v2/designs/` — a DESIGN
  - [ ] `docs-v2/governance/` — a governance document
  - [ ] `docs-v2/architecture/` — an ADR or the capability registry
  - [ ] `formal/` — a TLA+ specification
  - [ ] none of these

## 4. Safety and ethics

- **Does the change touch the four prohibitions** (`CONSTITUTION.md`, Article IV)? How is that
  checked?
- **Does it touch a frozen zone** (`CONSTITUTION.md`, Article III)? If yes, this RFC needs an
  explicit owner mandate — say so here.
- **Does it change what is SERVED** rather than what is implemented? Name the serving path.

## 5. Breaking changes

<!-- What breaks for a caller, a stored record, a route, a CLI, or a downstream integration.
     What is the migration, and is it reversible? -->

## 6. Alternatives considered

<!-- What else was on the table, and why it was not chosen. An RFC with one option is a
     proposal, not a request for comments. -->

## 7. Verification plan

<!-- How the change will be shown to be correct. The project's evidence is the JUnit XML and
     the gate exit codes, not a description of intent. -->

- **Targeted scope(s)** (from `docs-v2/engineering/TEST-CATALOGUE.yml`):
- **The commands:**
- **The gates that must stay green:**
  - [ ] `scripts/quality-gate.sh`
  - [ ] `scripts/formal-gate.sh` (if a `formal/` specification changed)
  - [ ] `scripts/security-boundary-check.sh` (if an authorisation boundary changed)
- **The measurement**, if the change is a capability claim: the metric, the baseline, the
  threshold, and the corpus scope (`docs-v2/research/PROTOCOL.md`).

## 8. Documentation

<!-- Which document carries the decision once it is taken? The decision itself is recorded in
     docs-v2/governance/DECISION-REGISTER.md. -->

## 9. Open questions

<!-- What is still undecided, and who must answer it. -->
