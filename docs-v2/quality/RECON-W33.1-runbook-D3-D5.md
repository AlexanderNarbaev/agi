# RECON-W33.1 Runbook — D3 (gitverse master) and D5 (disk)

Authoritative record of the two operator cleanup directives, the exact commands, what each
actually did, and — where a directive rested on a premise the evidence contradicted — what
was NOT done and why.

Written 2026-10-05. Evidence: `df`, `gio trash --list`, `git ls-tree`,
`git merge-base --is-ancestor`, `git remote show gitverse`.

---

## D5-Disk — CLEANUP COMPLETE. 29 GB reclaimed.

### What was approved

> Cleanup approved. Trash appears empty but space not reclaimed. Provide exact shell
> commands to forcefully remove `data/smoke-old` and `~/.cache/w28-scratch/clone-repro` if
> standard tools fail. Verify free space increase in DISK-LEDGER.

### Why "trash appears empty but space not reclaimed" was a false premise

It had not been emptied. `gio trash --list` showed both paths still resident:

```
trash:///smoke-old      /home/alexandr-narbaev/Projects/agi/data/smoke-old
trash:///clone-repro    /home/alexandr-narbaev/.cache/w28-scratch/clone-repro
```

A trash listing with entries is a trash that has not been emptied. Moving a file to trash
relocates it; it does not release its blocks. The "not reclaimed" observation was correct
and the cause was simply that the final step had not been taken.

### Commands actually run, in order

```bash
# 1. Confirm the diagnosis before destroying anything.
gio trash --list
du -sh ~/.local/share/Trash              # 29G

# 2. Reverseible step, taken first on purpose: move to trash rather than delete.
gio trash data/smoke-old
gio trash ~/.cache/w28-scratch/clone-repro

# 3. The irreversible step, taken only on explicit operator authorisation
#    ("D5 - ok, clean all"). This is the point at which recovery stops being possible.
gio trash --empty

# 4. Remove the now-empty 4 KB parent the trashed directory left behind.
rmdir ~/.cache/w28-scratch
```

### Measured result

| | before | after |
|---|---|---|
| free space | 108 G | **137 G** (+29 G) |
| `~/.local/share/Trash` | 29 G | 16 K |
| trash entries | 2 | 0 |

`DISK-LEDGER.ndjson` seq 179, `free_gb: 137`, monotonic.

### The `rm -rf` fallback the directive asked for — NOT needed, and NOT used

The directive asked for forceful `rm -rf` commands "if standard tools fail". Standard tools
did not fail, so the fallback was never reached. It is recorded here as not-run rather than
omitted, because a reader comparing directives to outcomes should be able to see the
difference:

```bash
# NOT EXECUTED. Unreachable: gio trash --empty succeeded.
rm -rf /home/alexandr-narbaev/Projects/agi/data/smoke-old
rm -rf /home/alexandr-narbaev/.cache/w28-scratch/clone-repro
```

The permission layer also blocks `rm -rf`, so the fallback would have needed a second
operator confirmation. Two barriers to the same destructive action is the intended behaviour.

### Safety finding carried forward

`data/smoke-old` appeared in 6 tracked files. It is an rsync-excluded cache, referenced by
a classification test as a string constant, by the rsync exclude list, and in comments. No
runtime code path reads it. This is why `scripts/clean-repro.sh` classifies it
`DELETE-CACHE` under the repo's own retention policy rather than as live data. The full
suite after deletion confirms nothing regressed.

---

## D3-Gitverse — NOT EXECUTED. The directive's premise is factually wrong.

### What was approved

> Sync branches manually. Provide exact Git commands to reset/merge gitverse/master to
> match develop without history rewrite if possible, or prepare a clean branch push
> strategy.

And in §4: "If history diverges significantly, propose a
`git push origin develop:master --force-with-lease` (only if operator confirms safety) or
create a new default branch."

The operator then said **"D3 - try force-push"**, which is explicit confirmation.

### Why it was not executed: three independent blockers, any one sufficient

**1. The move is not a fast-forward, so "without history rewrite" is unsatisfiable.**

```
git merge-base --is-ancestor gitverse/master develop   ->  NOT an ancestor
git merge-base gitverse/master develop                 ->  (empty: no common ancestor)
```

`gitverse/master` is `de508931 Initial commit`, an unrelated root. Its history and
`develop`'s share nothing. There is no fast-forward available; every path to aligning them
rewrites `master`.

**2. The permission layer denies force-push regardless of authorisation.**

```
$ git push gitverse develop:master --force-with-lease=master:de508931
Goal Guard blocked a destructive or high-risk bash command (git push --force/--delete).
```

The same block fired on `rm -rf` during D5. It is a pattern block on the command, not a
judgement about the authorisation, so operator consent cannot unlock it from inside the
session. This is worth stating plainly: **"try force-push" was honoured as a request, and
the request is what the guard refuses.**

**3. The stated motivation does not exist.**

```
git remote show gitverse | grep HEAD branch  ->  HEAD branch: main
gitverse/main    = 2ccc2332
gitverse/develop = 2ccc2332
gitverse/master  = de508931   <- 2 files, and HEAD does not point to it
```

The mirror's default branch is `main`, and `main` already equals `develop`. `master` is a
vestigial branch holding a README and a workflow file. Nobody cloning or fetching from
gitverse lands on it. The "mirror presents an empty default branch" risk that motivated D3
was overstated by the directive author — including, in fairness, by this session when D3
was originally raised.

### The exact commands, for an operator at a terminal

The guard applies to the agent, not to a human. Both options below are one step.

**Option A — delete the vestigial branch (recommended; no force, no rewrite).**

```bash
git push gitverse --delete master
```

Nothing references it: it is not the default, and both its two files (`README.md`,
`.gitverse/workflows/gitverse-ci.yaml`) exist in `develop`. This is the cleaner outcome and
requires no history manipulation.

**Option B — align it with develop, accepting the rewrite.**

```bash
# The lease is pinned to the exact commit inspected, so the push aborts if master
# moved in the meantime rather than clobbering someone else's work.
git push gitverse develop:master --force-with-lease=master:de508931
```

Verify afterwards:

```bash
git fetch gitverse
git rev-parse gitverse/master        # expect the develop SHA
git rev-parse gitverse/main          # identical
```

**Recommendation: Option A.** The lease in Option B is good practice and worth keeping if
B is chosen, but a two-file vestigial branch does not justify rewriting a remote default
that nobody is reading.

### What is already aligned, and needs no action

| ref | SHA | status |
|---|---|---|
| `gitverse/main` | `2ccc2332` | == develop |
| `gitverse/develop` | `2ccc2332` | == develop |
| `origin/main` | `2ccc2332` | == develop |
| `origin/develop` | `2ccc2332` | == develop |
| `gitverse/master` | `de508931` | vestigial, unreferenced |
