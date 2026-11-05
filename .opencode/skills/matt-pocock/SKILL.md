---
name: matt-pocock
description: The seventeen engineering sub-skills of this repository, in one place. Use when a task is one of the engineering disciplines they cover — test-driven development, code review, diagnosing bugs, research, planning, prototyping, or the design vocabularies — and the discipline's own file should be read before the work starts.
---

# The engineering sub-skills

This directory is a container: seventeen sub-skills, each in its own directory, each with its
own `SKILL.md`. This root file exists because the harness loads a package by its root
manifest — without it, the container is invisible to the harness and the seventeen
disciplines are reachable only by their bare names.

**The owner's decision (OD-040, 2026-11-05).** The wave recommended leaving the container
without a root manifest, on the ground that giving it one changes what the container means.
The owner decided that it should change: the container becomes one skill whose body points at
the seventeen disciplines inside it.

## The seventeen disciplines

| the sub-skill | what it is for |
|---|---|
| `tdd` | the red → green loop, and what makes a test worth keeping |
| `code-review` | a review along the standards axis and the spec axis, side by side |
| `codebase-review-swarm` | a rigorous quote-grounded review or audit of a large subsystem |
| `codebase-design` | the vocabulary of deep modules, and where a seam belongs |
| `diagnosing-bugs` | the diagnosis loop for hard bugs and performance regressions |
| `domain-modeling` | the domain's terms, the ubiquitous language, and a record of a decision |
| `grill-me` | a relentless interview that sharpens a plan |
| `grill-with-docs` | the same interview, writing the documents as it goes |
| `grilling` | the relentless interview under its other names |
| `implement` | carrying out a piece of work against a specification |
| `improve-codebase-architecture` | a scan for deepening opportunities, then the questions |
| `prototype` | a throwaway built to answer a design question |
| `research` | an investigation against high-trust sources, captured in the repository |
| `resolving-merge-conflicts` | an in-progress merge or rebase that needs resolving |
| `to-spec` | turning the current conversation into a specification |
| `to-tickets` | turning a plan into tracer-bullet tickets |
| `wayfinder` | planning a huge piece of work as a map of decision tickets |
| `wizard` | an interactive wizard for work a human must approve step by step |

## How to use it

1. Identify which discipline the task belongs to — the table above is the index.
2. **Read that sub-skill's `SKILL.md` before starting the work.** Each one is a procedure,
   not a summary; working without it is working from the summary.
3. Follow the procedure as it is written. If the procedure turns out to be wrong for the
   situation, say so and deviate deliberately — do not silently skip its steps.

## What this root file is not

It is not a replacement for the seventeen files, and it does not summarise them: the summary
would be the wrong thing to work from. It is an index, and the harness's entry point.

The sub-skills are addressed as `matt-pocock/<name>` — that is how the trigger table names
them, and how the harness's own catalogue lists them.
