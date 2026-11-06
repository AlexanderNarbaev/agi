# The handoff protocol

## Which template to fill

`docs-v2/governance/SESSION-HANDOFF-TEMPLATE.md`. It is `CANONICAL` and it is the shape of
the end-of-session section of the wave record under `docs-v2/engineering/`. **Do not invent
a shape.** The template's own rule is the one this file exists to serve: *a session that
cannot be resumed from its own record did not finish.*

## The rule for a claim that names its command

Every number in a handoff carries **the command that produced it** and **the path of the
artefact that holds it**.

| the claim | it must name |
|---|---|
| a test count | the command, the scope, and the JUnit XML path |
| a capability status | the `path:line` of the serving path, or the statement that there is none |
| a benchmark number | the command, the workload, the environment, and the comparison |
| a document count | the command that counted it, and the tree it counted |

A number without a re-derivable command is **not published** — it is written as
`unverified`, or it is left out. A stale number is not a small defect: it is a number that
will be believed later.

## The tests that were not run

The template asks for them, and they are not a confession — they are a fact about the
coverage of the session. For each: the scope, and the reason (the owner's approval, the
missing toolchain, the resource limit). **The full `matrix-core` suite is owner-held**; if
it was not run, say so and say why.

## What the handoff is not

- **Not a normative file.** The handoff is a note in the wave record. The registers are
  written by the wave that owns them.
- **Not a commit.** No git write command is part of this protocol.
- **Not a summary of the chat.** The next session must not need the previous one's memory.
