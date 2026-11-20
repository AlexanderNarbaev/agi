# BRANCH-INVENTORY — every remote branch, its provenance and its recovery

**Status:** `ACTIVE` — the inventory is complete and the recovery is proven; **nothing was
deleted.**
**Date:** 2026-11-15 · **Branch:** `develop` · **HEAD at the measurement:** `3f6f53bb`
**Owner authorization:** the directive of 2026-11-15, §5 (the branch cleanup policy: the
pre-W46 branches and every ref whose provenance is ambiguous are preserved) and **OD-054**
(the owner's answer of 2026-11-15: no automatic deletion of merged branches; a deletion
requires a verified durable recovery reference and a proven restoration).
**The machine-readable form:** `docs-v2/engineering/BRANCH-INVENTORY.json`.

---

## 1. What was measured, and how

| the question | the command | the answer |
|---|---|---|
| which branches exist on the remote | `git ls-remote --heads origin` | **20** |
| which branches exist on the second remote | `git ls-remote --heads gitverse` | **2** |
| which remote-tracking refs are stale locally | `git branch -r` minus the live list | **5** |
| **the total covered by this inventory** | | **27** |
| are there open pull requests | `gh pr list --state open --json number` | **0** |
| are there worktrees | `git worktree list` | **1** (the main checkout, on `develop`) |
| is the default branch | `gh repo view --json defaultBranchRef` | `develop` |
| are merged branches auto-deleted | `gh repo view --json deleteBranchOnMerge` | `false` |
| what protects the branches | `gh api repos/AlexanderNarbaev/agi/rulesets` | one ruleset, `Protected main` (id `20469548`), whose condition is `~DEFAULT_BRANCH` — i.e. **`develop`** |

**The W46 boundary.** The consolidation commit of W46 is **`6c003e05`, 2026-11-05** ("W46.0–W46.3
the canonical project set and the tooling that holds it"). A branch is **post-W46** when its
work is later than that commit; the pre-W46 branches of this repository are the MIND-track
branches of 2026-09-25 and the `release/v1.0` of the same period.

**Why ancestry alone is not enough here.** The repository's ruleset enforces
`required_linear_history`, and every pull request in the recent history was merged by
**squash** (`git log --merges` shows only the merge-backs of the release branches). A squash
merge creates a **new** commit: the branch's own commits are **not** ancestors of
`develop`, so `git merge-base --is-ancestor` reports "not an ancestor" for a branch whose
content is fully present. The decisive test used here is therefore the **content** test:

```bash
git diff --name-only <the pull request's merge commit> <the branch tip>
# empty  ⇒  the branch tip's tree IS the merge commit's tree
#          ⇒  every line the branch wrote is already in develop's history
```

## 2. The complete inventory — 27 refs

| the ref | the remote | the tip | the last activity | the unique commits vs `develop` | the tree's files vs `develop` | the tip is an ancestor of `develop` | the pull request |
|---|---|---|---|---|---|---|---|
| `develop` | origin | `c5853ac3af` | 2026-11-15 | 0 | 0 | yes | #13 CLOSED, #14–#61 MERGED |
| `main` | origin | `26c549c4e4` | 2026-11-15 | 0 | 7 | yes | — (release branch) |
| `feature/mind-w1-cognitive-orchestration` | origin | `1cc0df9722` | 2026-09-25 | 3 | 1 752 | no | #16 MERGED, #17 CLOSED |
| `feature/mind-w2-persistent-mind` | origin | `7517840fac` | 2026-09-25 | 2 | 1 754 | no | #18 MERGED, #19 CLOSED |
| `feature/mind-w3-sleep-consolidation` | origin | `fd7c3137bf` | 2026-09-25 | 2 | 1 756 | no | #20 MERGED, #21 CLOSED |
| `feature/mind-w6-gpu-acceleration` | origin | `8eed771192` | 2026-09-25 | 3 | 1 754 | no | #24 CLOSED |
| `feature/mind-w6-gpu-final` | origin | `473ec8a15b` | 2026-09-25 | 2 | 1 752 | no | #25 MERGED, #26 CLOSED |
| `feature/mind-w7-complete` | origin | `e9f22d3af1` | 2026-09-25 | 2 | 1 750 | no | #28 CLOSED |
| `feature/mind-w7-credit-ledger` | origin | `e9f22d3af1` | 2026-09-25 | 2 | 1 750 | no | #27 MERGED |
| `safe-main` | origin | `6d7410fcab` | 2026-08-30 | 0 | 3 982 | yes | — |
| `release/v1.0` | origin | `c9ce58800a` | 2026-09-26 | 0 | 1 732 | yes | #15 MERGED |
| `dependabot/npm_and_yarn/matrix-web-ui/next-16.4.1` | origin | `5da72c360d` | 2026-11-06 | 0 | 422 | yes | #43 CLOSED |
| `dependabot/npm_and_yarn/matrix-web-ui/sharp-0.35.5` | origin | `c36367def6` | 2026-11-06 | 0 | 423 | yes | #46 CLOSED |
| `docs/evid-base-integrated-state` <!-- no-link: this is a branch name, not a path --> | origin | `f484f3bc5c` | 2026-11-14 | 1 | 54 | no | **#55 MERGED** |
| `feature/concept-to-action` | origin | `6ff0612bad` | 2026-11-15 | 17 | 7 | no | **#60 MERGED** |
| `feature/evid-base-evidence-to-action` | origin | `43fba12464` | 2026-11-14 | 19 | 54 | no | **#53 MERGED** |
| `feature/evid-base-release-record` | origin | `6d751ae7de` | 2026-11-14 | 6 | 49 | no | **#58 MERGED** |
| `fix/evid-base-shellcheck` | origin | `7125b337dd` | 2026-11-14 | 1 | 54 | no | **#57 MERGED** |
| `release-state` | origin | `f484f3bc5c` | 2026-11-14 | 1 | 54 | no | #54 **CLOSED, not merged** |
| `feature/w53-distributed-cognition` | origin | `8cfcf854b3` | 2026-11-13 | 0 | 119 | **yes** | — (no pull request was ever opened) |
| `dependabot/.../multi-d49ef80a9c` | origin *(local only)* | `bb8836bc95` | 2026-10-19 | 1 | 1 324 | no | #42 CLOSED |
| `dependabot/.../next-16.3.8` | origin *(local only)* | `1f2a5b3636` | 2026-10-09 | 1 | 1 467 | no | #40 CLOSED |
| `dependabot/.../postcss-selector-parser-6.1.5` | origin *(local only)* | `3626fe15bc` | 2026-11-06 | 0 | 423 | yes | #45 CLOSED |
| `dependabot/.../source-map-js-1.2.2` | origin *(local only)* | `c0447e108c` | 2026-10-18 | 0 | 1 324 | yes | #41 CLOSED |
| `_dependabot.rootowned-stale/.../source-map-js-1.2.2` | origin *(local only)* | `c0447e108c` | 2026-10-18 | 0 | 1 324 | yes | — |
| `develop` | gitverse | `0c47e51a19` | 2026-11-01 | 0 | 1 171 | yes | — |
| `main` | gitverse | `7a0ed0cc1f` | 2026-10-12 | 0 | 1 324 | yes | — |

**The "unique commits vs `develop`" column is the column that decides the question.** A
number of 0 means the branch holds nothing that `develop` does not already hold — either
because its tip is literally an ancestor, or because its content was squashed into
`develop` under a new commit id.

**The five "local only" rows are not remote branches at all.** They are stale
remote-tracking refs left in this clone by branches that GitHub no longer has. **There is
nothing on GitHub to delete for them.** Two of them (`multi-d49ef80a9c`, `next-16.3.8`)
still hold one commit each that `develop` does not have, so a local `git remote prune origin`
would leave that content reachable only through the reflog — they are kept, not pruned.

---

## 3. The classification

| the class | the count | the refs |
|---|---|---|
| **PRUNE-ELIGIBLE** | **5** | `docs/evid-base-integrated-state` <!-- no-link: this is a branch name, not a path -->, `feature/concept-to-action`, `feature/evid-base-evidence-to-action`, `feature/evid-base-release-record`, `fix/evid-base-shellcheck` |
| **OWNER-QUESTION** | **2** | `release-state`, `feature/w53-distributed-cognition` |
| **KEEP** | **20** | `main`, `develop`, the seven `feature/mind-w*` branches, `safe-main`, `release/v1.0`, the two live `dependabot/*`, the five stale local refs, the two `gitverse` refs |
| **BLOCKED** | **0** | — |
| **the total** | **27** | |

### 3.1 The five PRUNE-ELIGIBLE branches — every condition of OD-054, one by one

| the condition | how each of the five satisfies it |
|---|---|
| **post-W46** | all five were created on 2026-11-14/15, after the W46 consolidation of 2026-11-05 |
| **its pull request is closed AND merged** | #55, #60, #53, #58, #57 — all five are `MERGED` |
| **it holds no unique unrecovered content** | `git diff --name-only <merge commit> <branch tip>` is **empty for all five**: the branch tip's tree is byte-identical to the pull request's merge commit's tree. The commits themselves are unique to the branch (1, 17, 19, 6 and 1 of them) because the merge was a squash — and all of them are preserved by the recovery references of §4 |
| **no open pull request depends on it** | `gh pr list --state open` → **zero** open pull requests in the repository |
| **no worktree depends on it** | `git worktree list` → one worktree, on `develop` |
| **a verified durable recovery reference** | an annotated **archive tag pushed to `origin`** for each, plus a **git bundle with a manifest and its sha256**, whose restoration was **proven in a temporary independent clone** (§4) |

### 3.2 The two OWNER-QUESTION branches — the exact reason, and what is already in place

| the branch | why it is not PRUNE-ELIGIBLE | what is already true | what the owner's answer changes |
|---|---|---|---|
| `release-state` | **its own pull request #54 was CLOSED WITHOUT BEING MERGED.** OD-054's condition is *"its PR is closed **and merged**"*; a closed-but-unmerged pull request does not satisfy it literally | its tip `f484f3bc5c` is **byte-identical** to the tip of PR #55's branch, and PR #55 merged as `5ff4964a` — so **its content is already in `develop`**. Its archive tag and its bundle entry already exist | one answer promotes it to PRUNE-ELIGIBLE; the recovery recipe needs no new work |
| `feature/w53-distributed-cognition` | **no pull request was ever opened for it**, so the condition *"its PR is closed and merged"* has **no referent** | its tip `8cfcf854b3` is an **ancestor of `develop`** — every one of its commits is already inside `develop`'s history, so deleting the branch would lose **nothing at all**, not even a commit object. Its archive tag and its bundle entry already exist | one answer promotes it to PRUNE-ELIGIBLE; the recipe needs no new work |

**Neither of the two is BLOCKED.** They are not blocked by anything external: they are
waiting for a decision that only the owner can make, and the decision is one word.

### 3.3 The twenty KEEP refs — the reason for each

| the ref(s) | the reason |
|---|---|
| `main` | the stable release branch, named by the directive (§2) as the branch to keep. **A measured gap: the ruleset `Protected main` conditions on `~DEFAULT_BRANCH`, which is now `develop`, so `main` is currently unprotected** — `gh api repos/AlexanderNarbaev/agi/branches/main/protection` → *"Branch not protected"*. The directive (§11 Gate 1) says *"keep main protected"*, so the ruleset's include list must name `main`. **Owner-gated; this work did not change it.** |
| `develop` | the working branch and the GitHub default branch; protected by the ruleset |
| the seven `feature/mind-w*` branches | **pre-W46** (2026-09-25). The directive preserves every pre-W46 branch unconditionally. They also hold 1 750–1 756 files' worth of difference against `develop` — the MIND track, which the later work built on |
| `safe-main` | named by the directive as a branch to preserve; its provenance was never merged and never closed by a pull request, so it is **ambiguous by the standard the directive sets** |
| `release/v1.0` | **pre-W46** (PR #15, 2026-09-25); the directive preserves it unconditionally. It is 1 732 files away from `develop` |
| the two live `dependabot/*` branches | the directive preserves the dependabot branches unconditionally. Both are already ancestors of `develop` (0 unique commits), so they hold nothing — but they are preserved anyway, by instruction |
| the five stale local refs | **not remote branches**: GitHub has no such branches. Two of them hold one commit each that `develop` does not have. They are kept; a local `git remote prune origin` would remove only the tracking ref, and the commit would survive only in the reflog |
| the two `gitverse` refs | a **different remote** (gitverse.ru), not the GitHub repository the directive governs. Both are ancestors of `origin/develop`, so they hold nothing that `develop` lacks, but their deletion is outside the authorized scope |

---

## 4. The recovery material and the restoration proof

### 4.1 The two references

| the reference | where it is | its identity |
|---|---|---|
| the **archive tags** | **pushed to `origin`** on 2026-11-15 | `refs/tags/archive/branch/<slug>-20261115`, an annotated tag whose target is the branch's exact tip commit |
| the **bundle** | `/home/alexandr-narbaev/branch-recovery/matrix-prune-candidates-20261115.bundle` | **968 683 bytes**, sha256 **`9a2b54bf2c8996355d8bbdbb5f9ccb4aba76d6011feeddd300f82ee322c8a290`** |

The bundle declares **five prerequisites**, and all five are commits inside `develop`'s
history (`5ff4964a`, `01d4cdc0`, `343299b1`, `4257d6df`, `429eba27`), so any clone of the
repository can fetch it.

**The manifest:** `/home/alexandr-narbaev/branch-recovery/MANIFEST.md` — it names every
ref, its tip, its unique-commit count, its archive tag, both recovery recipes and the
proof.

### 4.2 The restoration proof — executed, not asserted

```bash
# 1. an independent clone: a bare, blob-filtered clone of GitHub that shares no object
#    store with the working repository
git clone --bare --filter=blob:none --single-branch --branch develop \
  https://github.com/AlexanderNarbaev/agi.git /tmp/prune-proof/independent.git
#    → 4.2 MB, 1.5 s

# 2. the bundle is fetched into it
git -C /tmp/prune-proof/independent.git fetch \
  /home/alexandr-narbaev/branch-recovery/matrix-prune-candidates-20261115.bundle \
  'refs/tags/archive/branch/*:refs/tags/archive/branch/*'
#    → all seven tags created

# 3. every archived tip is compared with the recorded SHA
```

| the archived tag | the tip after the restoration | the recorded tip | the match | the restored tree |
|---|---|---|---|---|
| `archive/branch/docs-evid-base-integrated-state-20261115` | `f484f3bc5c` | `f484f3bc5c` | ✅ | `39399ed955` |
| `archive/branch/feature-concept-to-action-20261115` | `6ff0612bad` | `6ff0612bad` | ✅ | `1e70749981` |
| `archive/branch/feature-evid-base-evidence-to-action-20261115` | `43fba12464` | `43fba12464` | ✅ | `759517458d` |
| `archive/branch/feature-evid-base-release-record-20261115` | `6d751ae7de` | `6d751ae7de` | ✅ | `6051348d65` |
| `archive/branch/feature-w53-distributed-cognition-20261115` | `8cfcf854b3` | `8cfcf854b3` | ✅ | `733cd21172` |
| `archive/branch/fix-evid-base-shellcheck-20261115` | `7125b337dd` | `7125b337dd` | ✅ | `7a703df9b6` |
| `archive/branch/release-state-20261115` | `f484f3bc5c` | `f484f3bc5c` | ✅ | `39399ed955` |

**Seven of seven restored and matched.** The bundle is verified with
`git bundle verify` ("is okay"), and its digest is recorded in
`matrix-prune-candidates-20261115.bundle.sha256` beside it.

---

## 5. The exact deletion list, with the recovery recipe of each ref

**Nothing below was executed.** This is the list the owner authorises or rejects.

### 5.1 The five PRUNE-ELIGIBLE refs

| the ref to delete | the pull request | the recipe to restore it |
|---|---|---|
| `origin/feature/concept-to-action` | #60 (squash → `dfd24b44`) | `git fetch origin refs/tags/archive/branch/feature-concept-to-action-20261115:refs/tags/archive/branch/feature-concept-to-action-20261115` · `git branch feature/concept-to-action refs/tags/archive/branch/feature-concept-to-action-20261115^{commit}` |
| `origin/feature/evid-base-evidence-to-action` | #53 (squash → `01d4cdc0`) | `git fetch origin refs/tags/archive/branch/feature-evid-base-evidence-to-action-20261115:refs/tags/archive/branch/feature-evid-base-evidence-to-action-20261115` · `git branch feature/evid-base-evidence-to-action refs/tags/archive/branch/feature-evid-base-evidence-to-action-20261115^{commit}` |
| `origin/feature/evid-base-release-record` | #58 (squash → `5d096762`) | `git fetch origin refs/tags/archive/branch/feature-evid-base-release-record-20261115:refs/tags/archive/branch/feature-evid-base-release-record-20261115` · `git branch feature/evid-base-release-record refs/tags/archive/branch/feature-evid-base-release-record-20261115^{commit}` |
| `origin/docs/evid-base-integrated-state` | #55 (squash → `5ff4964a`) | `git fetch origin refs/tags/archive/branch/docs-evid-base-integrated-state-20261115:refs/tags/archive/branch/docs-evid-base-integrated-state-20261115` · `git branch docs/evid-base-integrated-state refs/tags/archive/branch/docs-evid-base-integrated-state-20261115^{commit}` |
| `origin/fix/evid-base-shellcheck` | #57 (squash → `2b531dd0`) | `git fetch origin refs/tags/archive/branch/fix-evid-base-shellcheck-20261115:refs/tags/archive/branch/fix-evid-base-shellcheck-20261115` · `git branch fix/evid-base-shellcheck refs/tags/archive/branch/fix-evid-base-shellcheck-20261115^{commit}` |

**The offline variant of every recipe** (the bundle instead of the remote):

```bash
sha256sum -c matrix-prune-candidates-20261115.bundle.sha256
git fetch /home/alexandr-narbaev/branch-recovery/matrix-prune-candidates-20261115.bundle \
  'refs/tags/archive/branch/*:refs/tags/archive/branch/*'
git branch <the original branch name> refs/tags/archive/branch/<slug>-20261115^{commit}
```

**The deletion command, when the owner authorises it** (one line per ref, and **not before**):

```bash
git push origin --delete \
  feature/concept-to-action \
  feature/evid-base-evidence-to-action \
  feature/evid-base-release-record \
  docs/evid-base-integrated-state \
  fix/evid-base-shellcheck
```

### 5.2 The two OWNER-QUESTION refs — the same recipes, one word away

| the ref | the recipe, if the owner promotes it |
|---|---|
| `origin/release-state` | `git fetch origin refs/tags/archive/branch/release-state-20261115:refs/tags/archive/branch/release-state-20261115` · `git branch release-state refs/tags/archive/branch/release-state-20261115^{commit}` |
| `origin/feature/w53-distributed-cognition` | `git fetch origin refs/tags/archive/branch/feature-w53-distributed-cognition-20261115:refs/tags/archive/branch/feature-w53-distributed-cognition-20261115` · `git branch feature/w53-distributed-cognition refs/tags/archive/branch/feature-w53-distributed-cognition-20261115^{commit}` |

### 5.3 The deletion log — the form every deletion must take

Every deletion is logged with its exact recovery recipe, as OD-054 requires. The log entry
has this shape:

```
<date> · deleted <the exact ref> · the tip <sha> · the pull request #<n> (squash →
<the merge commit>) · the content proof: `git diff --name-only <merge commit> <tip>` is
empty · the recovery reference: the archive tag archive/branch/<slug>-20261115 (pushed to
origin) and the bundle matrix-prune-candidates-20261115.bundle
(sha256 9a2b54bf2c8996355d8bbdbb5f9ccb4aba76d6011feeddd300f82ee322c8a290) · the
restoration recipe: git fetch origin refs/tags/archive/branch/<slug>-20261115:refs/tags/
archive/branch/<slug>-20261115 && git branch <name> refs/tags/archive/branch/
<slug>-20261115^{commit} · the restoration was proven in a temporary independent clone on
2026-11-15 (7 of 7 tips matched)
```

---

## 6. The preserved list, with the reason — 20 refs

| the ref | the reason it is preserved |
|---|---|
| `main` | the release branch; **and it needs the ruleset re-pointed** (§3.3) |
| `develop` | the working branch and the default branch |
| `feature/mind-w1-cognitive-orchestration` | pre-W46 (2026-09-25) |
| `feature/mind-w2-persistent-mind` | pre-W46 (2026-09-25) |
| `feature/mind-w3-sleep-consolidation` | pre-W46 (2026-09-25) |
| `feature/mind-w6-gpu-acceleration` | pre-W46 (2026-09-25) |
| `feature/mind-w6-gpu-final` | pre-W46 (2026-09-25) |
| `feature/mind-w7-complete` | pre-W46 (2026-09-25) |
| `feature/mind-w7-credit-ledger` | pre-W46 (2026-09-25) |
| `safe-main` | named by the directive; its provenance is ambiguous |
| `release/v1.0` | pre-W46 (2026-09-25) |
| `dependabot/npm_and_yarn/matrix-web-ui/next-16.4.1` | a dependabot branch — preserved by instruction |
| `dependabot/npm_and_yarn/matrix-web-ui/sharp-0.35.5` | a dependabot branch — preserved by instruction |
| `dependabot/.../multi-d49ef80a9c` *(local only)* | not on the remote; it holds one commit that `develop` does not have |
| `dependabot/.../next-16.3.8` *(local only)* | not on the remote; it holds one commit that `develop` does not have |
| `dependabot/.../postcss-selector-parser-6.1.5` *(local only)* | not on the remote |
| `dependabot/.../source-map-js-1.2.2` *(local only)* | not on the remote |
| `_dependabot.rootowned-stale/.../source-map-js-1.2.2` *(local only)* | not on the remote; a root-owned stale dependabot ref |
| `gitverse/develop` | a different remote; outside the authorized scope; an ancestor of `origin/develop` |
| `gitverse/main` | a different remote; outside the authorized scope; an ancestor of `origin/develop` |

---

## 7. The important tags

The repository holds **45 tags**. The ones that matter for recovery are the seven archive
tags of §4 and the release tags:

| the tag | its target | its role |
|---|---|---|
| `archive/branch/docs-evid-base-integrated-state-20261115` | `f484f3bc5c` | the recovery reference of `docs/evid-base-integrated-state` <!-- no-link: this is a branch name, not a path --> **and** of `release-state` |
| `archive/branch/feature-concept-to-action-20261115` | `6ff0612bad` | the recovery reference of `feature/concept-to-action` |
| `archive/branch/feature-evid-base-evidence-to-action-20261115` | `43fba12464` | the recovery reference of `feature/evid-base-evidence-to-action` |
| `archive/branch/feature-evid-base-release-record-20261115` | `6d751ae7de` | the recovery reference of `feature/evid-base-release-record` |
| `archive/branch/feature-w53-distributed-cognition-20261115` | `8cfcf854b3` | the recovery reference of `feature/w53-distributed-cognition` |
| `archive/branch/fix-evid-base-shellcheck-20261115` | `7125b337dd` | the recovery reference of `fix/evid-base-shellcheck` |
| `archive/branch/release-state-20261115` | `f484f3bc5c` | the recovery reference of `release-state` |
| `v18.0.0-mind` | — | the newest release tag (2026-10-12) |
| `checkpoint/feature-mind-w{1,2,3}-…-20261009-230349` | — | the MIND-track checkpoints of 2026-10-09 |

**Every archive tag was created by the DevEx/CI workstream of this initiative and pushed to
`origin` on 2026-11-15.** Before that push they existed only in this clone, which is not a
durable reference.

---

## 8. What this inventory does not do

* **It deletes nothing.** No branch, tag or ref was removed. The seven archive tags were
  **added** and pushed; that is the only write to the remote, and it is additive.
* **It does not decide.** The five PRUNE-ELIGIBLE refs are a recommendation whose
  conditions are all met; the two OWNER-QUESTION refs need one owner answer each; the
  twenty KEEP refs are preserved.
* **It does not touch `main` or `develop`.** Nothing was rewritten, no force-push, no reset.
* **It does not repair the ruleset.** The measured gap — `main` is unprotected because the
  ruleset conditions on `~DEFAULT_BRANCH`, which is now `develop` — is reported in §3.3 and
  left to the owner.
* **It does not cover the tags of the repository other than the ones listed in §7**, and it
  does not propose to delete any tag: the directive forbids deleting a tag as a side effect.
