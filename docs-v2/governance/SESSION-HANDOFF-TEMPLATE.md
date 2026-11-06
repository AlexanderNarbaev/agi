# MATRIX — SESSION HANDOFF

**Status:** `CANONICAL` · **Date:** 2026-11-04
**What it is:** the template every session ends with. **Copy it, fill it in, put it where the
next session will find it** — and the next session does not need the previous one's memory.

**The rule:** *a session that cannot be resumed from its own record did not finish.* The next
agent must be able to pick up the work from this document alone.

**Where it goes:** the wave record is `docs-v2/engineering/W<N>-STATUS.md`; this template is
the **shape** of the end-of-session section of that document. The local journal is
`wal/SESSION_WAL.md` — it **is** tracked (the `/wal/` ignore rule applies only to NEW files,
and both journals have been committed since the early waves), so a handoff there survives. **It
is still not enough**: the journal is a running log, not a wave record, and the wave record is
what the next session reads.

---

## 1. The template

```markdown
## The handoff

**The wave:** W<N> · **the date:** <date> · **the task id:** <TASK-…>

### The identity
| | |
|---|---|
| the starting commit | `<sha>` |
| the ending commit | `<sha>` — **uncommitted** if the wave forbids a commit |
| the branch | `develop` |
| the working tree | <the count of the modified, the untracked and the ignored owner files> |

### The exact scope
<one paragraph: what was in scope, and — just as important — what was NOT>

### The files changed
| the file | the change | the reason |
|---|---|---|
| `<path>` | created / modified / deleted | <the why> |

### The decisions
| the decision | the kind (owner / coordinator) | the record |
|---|---|---|
| <what was decided> | <who decided it> | `OD-…` / `CD-…` / `ND-…` |

### The tests — the exact commands, the exact counts
| the command | the result | the evidence |
|---|---|---|
| `<the exact command>` | <the classes, the tests, the failures, the skips> | <the path of the XML or the report> |

**The tests that were NOT run, and why:**
- `<the scope>` — <the reason: the owner's approval, the missing toolchain, the resource limit>

### The benchmarks — the methodology and the environment
<the command, the workload, the environment, the numbers, the comparison. If no benchmark was
run, say so: "no benchmark was run in this session".>

### The known limitations
<what this session's work does NOT establish>

### The unresolved questions
| the id | the question | what would resolve it |
|---|---|---|

### The owner approvals required
| the decision | why it needs the owner | where it is recorded |
|---|---|---|

### The next task
**The id:** `<TASK-…>` · **the objective:** <one line>

### The exact files the next agent should read
1. `<path>` — <why>
2. `<path>` — <why>

### The exact first command
```bash
<the one command that puts the next agent exactly where this session stopped>
```
```

---

## 2. The rules the template enforces

### The four never-say statements

**A handoff that contains any of these is wrong, and the wrongness is worse than the gap it
hides:**

| never | because |
|---|---|
| **"the tests pass"** without the counts | a test count without its denominator is a claim, not evidence. The counts are: the classes discovered, the classes executed, the methods executed, the successful, the failed, the errored, the skipped, the aborted, the filtered, the not-executed. |
| **"the benchmark shows X× faster"** without the methodology | a benchmark is evidence only for **its** workload, **its** environment and **its** comparison. Say which. |
| **"the capability is integrated"** without a serving path | a capability is integrated when a production caller reaches it. The registry records the path. |
| **"the suite is green"** without saying what was not run | a targeted scope is not the suite. Name what was left out and why. |

### The evidence rule

**Every number in the handoff carries its source.** The three acceptable sources:

1. **the JUnit XML** — `<module>/build/test-results/test/*.xml` is the primary evidence, and
   the counts are read out of it;
2. **the command and its output** — for a script, the command and its exit code;
3. **the document that holds it** — for a number that was measured in an earlier wave, the
   document and the commit that produced it.

**A number without a source is a guess, and a guess in a handoff becomes a fact in the next
session.**

### The honesty rule about what was not done

> **"Never report a test as green if it was not executed. Never report a benchmark as
> measured if it was estimated."**

The handoff must carry the **not-run** list explicitly. A reader who sees only the green
results will assume the scope was the whole suite; the not-run list is what stops that.

### The protection rule

**The handoff names the owner's files it did not touch.** The owner's working tree holds
eleven entries — the eleven are listed in `docs-v2/governance/PROJECT-STATE.md` §9 — and the
next agent must know they are there before it runs `git status` and wonders.

### The cleanup rule

> **Clean up what you created, and report what remains.**

The handoff lists the temporary directories, the background jobs and the scratch files that
are still on the machine. A session that leaves a 583 MB orphan in `/tmp` — as two of them
did — has moved the problem, not solved it.

---

## 3. The compact form — for a session that changed nothing structural

Not every session deserves a full handoff. A session that read, measured and reported can
close with four lines:

```markdown
## The handoff
**the task:** <the id> · **the commit:** <the sha> · **the tree:** <the count of the changes>
**the commands run:** `<cmd>` → <the result with the counts>
**not run:** <the scope> and why
**the next task:** <TASK-…> — read `<path>`, then run `<cmd>`
```

**What still requires the full template:** any session that changed a file, any session that
ran a test, and any session that discovered something the next session must not repeat.

---

## 4. Where the handoff goes, and what the next session does with it

| the artefact | the path | in git? |
|---|---|---|
| the wave record | `docs-v2/engineering/W<N>-STATUS.md` | yes |
| the work log row | `docs-v2/waves/INDEX.md` | yes |
| the decision | `docs-v2/governance/DECISION-REGISTER.md` | yes |
| the risk or the question | `docs-v2/governance/RISK-REGISTER.md` / `OPEN-QUESTIONS.md` | yes |
| the state | `docs-v2/governance/PROJECT-STATE.md` — **replaced, not appended** | yes |
| the local journal | `wal/SESSION_WAL.md` | **no** — the directory is in `.gitignore` |

**The next session's first act** is the protocol's step 1–3: establish the root, the branch
and the HEAD, and read the working-tree status — then it reads **five files**: the project
state, the roadmap, the decision register, the open questions, and the subsystem it is about
to change. **It does not read the history** unless the task changes an architectural
assumption.

**The bootstrap block** — the compact form the next session can consume directly — is at the
end of `docs-v2/engineering/W46-FINAL-REPORT.md`.
