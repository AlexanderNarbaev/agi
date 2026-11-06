# The protected paths

## The frozen zones

`CONSTITUTION.md` Article III names the frozen zones. The rules:

- **No agent edits a frozen zone without an explicit RFC mandate from the owner, in that
  session.** The prohibition is in `AGENTS.md` §Prohibitions and in
  `docs-v2/governance/AGENT-OPERATING-PROTOCOL.md`.
- The frozen tree holds the ethical core — `matrix-core/src/main/java/io/matrix/ethics/frozen/**`
  — and the AVRO schemas.
- The implemented carriers are `ethics/EthicalFilter`, `StructuralSafetyGuard`,
  `LieDetector` and the FROZEN-FNL guardian. A change that touches one of them is a change
  to the prohibition, not to a detail.

## The eleven owner entries

The list is in `docs-v2/governance/AGENT-OPERATING-PROTOCOL.md` §1.3 and the source is
`docs-v2/governance/PROJECT-STATE.md` §8. They are the owner's protected work: no agent
touches, discards, stages or commits them. `wal/SESSION_WAL.md` is the eleventh entry and is
the local journal rather than protected content.

**`data/` is not on the list and is not on yours** — it holds the live databases and is
ignored. Never modify it.

## The security boundary

The boundary check is `scripts/security-boundary-check.sh`. It is a gate, not a document: it
verifies that the boundary the constitution names is still where it was. A change that moves
a route across the boundary fails it.

## The writer lock

One writer process per database file: `matrix-core/src/main/java/io/matrix/cognitive/WriterLock.java`.
A second process that opens the same file is refused, and a writer that dies leaves a
record that classifies the owner as dead so the next writer may take over. The registry
entry is `W45-WRITERLOCK` in `docs-v2/architecture/CAPABILITY-REGISTRY.yml`.

**A change that writes a second database from a new process is a writer-lock change**, and
it is an owner decision, not an implementation detail.
