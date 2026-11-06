---
name: Pilot Proposal
about: Propose a domain pilot — a real problem, a real dataset, a measurable outcome
title: "[PILOT] "
labels: pilot-proposal, rfc
assignees: ""
---

> **A pilot proposal must not assume that MATRIX is production-ready.** MATRIX is a research
> system: most of its capabilities are implemented, wired or measured but **not served**. State
> which of those the capability you evaluate is — the question below is mandatory, and a
> proposal that leaves it open cannot be reviewed.

## 1. The real-world problem

<!-- What is the actual problem, in the domain's own terms? Who has it, how often, and what
     does it cost today? Not "MATRIX could do X" — the problem, first. -->

## 2. The domain and the stakeholders

- **Domain:**
- **Who would use the outcome** (role, not a name):
- **Who would be affected by a wrong answer:**
- **The decision the output feeds** (a human decision, a process step, a monitoring rule):

## 3. The MATRIX capability under evaluation

- **Capability (name and path):**
- **Its four axes, as `docs-v2/architecture/CAPABILITY-REGISTRY.yml` states them:**

  | axis | value | where you read it |
  |---|---|---|
  | `implementation` | | |
  | `integration` | | |
  | `verification` | | |
  | `operational` | | |

- **Is it currently SERVED or RESEARCH-ONLY?** *(mandatory — pick one and name the evidence)*

  - [ ] **SERVED** — it has a serving path. Name it: `ClassName — path/to/File.java`, and the
        route or CLI a user would call.
  - [ ] **RESEARCH-ONLY** — it is implemented but not served, or measured but not integrated.
        Name the measurement that would have to happen first, and the experiment report that
        exists today (if any).

- **What the pilot would exercise that is not exercised today:**
- **If the capability is research-only: what would this pilot MEASURE, and what would it not
  be allowed to conclude?**

## 4. Test data

- **Dataset** (name, origin, size):
- **Licence / permission to use it**, and who granted it:
- **Personal or sensitive data:** yes/no — if yes, the lawful basis and the redaction plan:
- **How the data reaches the pilot** (and how it is removed afterwards):

## 5. The baseline

- **What is compared against** (the current method, a rule, a model, or "no system"):
- **Why that baseline is the right one:**
- **The metric that both are measured with:**

## 6. Acceptance criteria

<!-- Numeric thresholds, fixed BEFORE the run. A criterion without a number is not a criterion.
     If the scope is synthetic, say so: the project's own verdicts carry "synthetic-scope"
     until they are measured on a domain corpus. -->

| the criterion | the threshold | the measurement that decides it |
|---|---|---|
| | | |

## 7. Integration requirements

- **Where the output would be consumed** (a route, a CLI, a report, a human):
- **What must exist before it can be integrated** (a serving path, a safety contract, a
  deployment change):
- **What the pilot explicitly does NOT integrate:**
- **How a wrong answer is detected and who is told:**

## 8. Hardware and resource budget

- **CPU / GPU / RAM** the run needs (the machine's limits are in
  `docs-v2/operations/RESOURCE-BUDGETS.md`):
- **Storage** the run needs:
- **Wall-clock** the run is expected to take:

## 9. Expected duration

<!-- Calendar duration of the pilot, and how often the run repeats. -->

## 10. Failure criteria

<!-- When is this pilot a FAILURE, and what happens then? A pilot with no failure criterion
     cannot produce a negative result, and this project keeps negative results as evidence
     (docs-v2/research/NEGATIVE-RESULTS-INDEX.md). -->

- **The pilot fails if:**
- **What the failure means for the hypothesis** (refuted, inconclusive, not tested):
- **What happens to the artefacts of a failed pilot:**

## 11. Data security and safety

- **What the pilot may write** (data, artefacts, issues, reports):
- **What it must never write** (credentials, personal data, anything under the frozen zones):
- **The four prohibitions** (`CONSTITUTION.md`, Article IV) — does any step of this pilot touch
  them, and how is that checked?
- **Who may see the pilot's output, and for how long:**
- **What is discarded when the pilot ends:**

## 12. References

- The capability's row in `docs-v2/architecture/CAPABILITY-REGISTRY.yml`:
- The experiment reports behind it, if any (`docs-v2/research/reports/`):
- The method it would follow: `docs-v2/research/PROTOCOL.md`
