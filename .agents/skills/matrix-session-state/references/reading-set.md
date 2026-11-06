# The reading set

The order matters. Each file answers a question the next one assumes.

## The five ordinary files

| # | the file | the question it answers |
|---|---|---|
| 1 | `docs-v2/governance/PROJECT-STATE.md` | what is MATRIX today, and what is blocked |
| 2 | `docs-v2/governance/ROADMAP.md` | what is the next task, and what does it need |
| 3 | `docs-v2/governance/AGENT-OPERATING-PROTOCOL.md` | how do I work — the autonomy levels, the protection rules, the resource rules |
| 4 | the subsystem's own `build.gradle` and `src/main/java/**` | what does the code actually do |
| 5 | `docs-v2/governance/DECISION-REGISTER.md` | was this already decided, and by whom |

**Add a sixth only when the question demands it:** the wave reports under
`docs-v2/engineering/`, the historical waves under `docs-v2/waves/`, or the local journal
`wal/SESSION_WAL.md`.

## The eleven owner entries — protected, never modified

At the snapshot commit the working tree carries these, and no agent may touch, discard,
stage or commit them (the source is `docs-v2/governance/PROJECT-STATE.md` §8; the rule is in
`docs-v2/governance/AGENT-OPERATING-PROTOCOL.md` §1.3):

```
.gitignore   .opencode/todo.md   .opencode/w37-plan.md   AGENTS.md   opencode.json
the deletion of opencode.json.bak   .codex/   .opencode/goals/
.opencode/opencode-swarm.json   .opencode/tmp/   .veai/
```

**Ten are the owner's protected work. The eleventh, `wal/SESSION_WAL.md`, is the local
journal.** A different set at the next session is not a failure — the owner worked in
between. **Record the set; do not restore it.**

`data/` is not on that list and is not on yours: it is ignored and holds the live databases.
**Never modify `data/`.** An audit that needs a database makes its own in a temporary
directory.

## The local journal

`wal/SESSION_WAL.md` is the running log of the machine; `wal/GLOBAL_WAL.md` is the project's
committed log. The journal is **not** a wave record: the wave record is what the next
session reads, and it lives under `docs-v2/engineering/`.

## What is *not* in the reading set

- **The owner entries.** Read them; never write them.
- **`docs-v2/_raw/`.** The pre-v2 archive. It is read-only history and it is never a source
  for a current claim.
- **A chat answer.** A decision that was made in a chat is not a decision until it is in
  `docs-v2/governance/DECISION-REGISTER.md`.
