# MATRIX — local one-window Codex → DSH/MiniMax workflow

**Status:** proposed operational helper; no new server, clone, model credential, or framework. **Audience:** the sole project owner and local Codex CLI.

## Objective

Use Codex once to set a bounded cognitive milestone, then run implementation, regression repair, subagent review and evidence recording through the already configured MiniMax-backed DeepSeek Harness. Stop manually transferring intermediate DSH reports back to Codex or ChatGPT.

## Installed components

- Repo skill: .agents/skills/matrix-dsh/SKILL.md
- Local controller: scripts/matrix_dsh_supervise.py (Python 3 standard library, Linux)
- Offline tests: scripts/tests/test_matrix_dsh_supervise.py
- Existing canonical checkout: ~/Projects/agi
- Existing linked write worktree: ~/Projects/matrix-worktrees/dsh-writer
- One fixed, ignored state directory: ~/Projects/agi/.opencode/tmp/matrix-dsh-supervisor/

No per-attempt checkout or large prompt artefact directories are created. Old scientific artefacts remain in the canonical evidence ledger.

## One-time verification

From the canonical checkout:

    python3 -m unittest discover -s scripts/tests -p 'test_matrix_dsh_supervise.py' -v
    dsh headless --help
    python3 scripts/matrix_dsh_supervise.py status

Verify the currently configured DSH provider is actually MiniMax under the intended subscription. The supervisor does not select or override the existing provider; it must not introduce a fallback. Never paste API keys into a commission.

Codex detects the new skill after updating the repository and restarting Codex if necessary.

## What the owner types

    cd ~/Projects/agi
    codex

Then one short instruction:

    Use $matrix-dsh to complete the next approved MATRIX cognitive milestone.
    Commission the configured MiniMax-backed DSH once, use a real DEV-only
    acceptance test, let the controller automatically retry inside that DSH
    session, and return only the verified outcome and PR. Do not create
    repositories, worktrees or new MCP servers.

Codex authors one commission in the ignored fixed state directory and invokes the local script. DSH executes the work and its own bounded subagents. The script retains events and retries upon a failing independent development gate. Codex only evaluates the terminal state and reviewed diff once, not each DSH retry.

## Direct operator command

Replace ActualTargetTest with the task's real negative-control test:

    python3 scripts/matrix_dsh_supervise.py run \
      --task-file .opencode/tmp/matrix-dsh-supervisor/commission.md \
      --worktree "$HOME/Projects/matrix-worktrees/dsh-writer" \
      --gate 'flock /tmp/gradle-matrix.lock ./gradlew :matrix-core:test --offline --tests "io.matrix.knowledge.ActualTargetTest"'

Query current status in the same repository:

    python3 scripts/matrix_dsh_supervise.py status

The script prints only one summary line and retains complete evidence under the fixed state path.

## Integrity and execution boundaries

1. The development gate must be independent of DSH's self-reported completion. It should check the new negative control plus affected serving behavior. A successful test process is not proof of general scientific improvement; independent frozen evaluation stays separately isolated.
2. Never feed hidden evaluation gold or its failing answers back to the implementing DSH model. Only developer-visible tests can drive automatic retries.
3. Require process exit 0 and DSH final.reason.kind == completed before any gate acceptance. Preserve failed JSONL and stderr.
4. The default workspace-write permission applies to the existing dsh-writer worktree, not the owner's databases or shared Git metadata. The optional --grant-git exposes the common .git directory including hooks and config; do not enable by default.
5. Run only one heavy matrix-core Gradle task at a time using the shared flock; do not run two supervisors in parallel.
6. MiniMax-M3.1-Flash uses a recommended 512K active context with native DSH compaction. Cumulative root usage is for observability only. Input, cache reads and writes, and output are reported separately when available. Subagent or discarded-attempt usage may be uninstrumented. Check subscription consumption separately in MiniMax Token Plan. Persist the current decisions, evidence and next action in the worktree before compaction.
7. A repeated failure without new progress, confirmed provider quota exhaustion or a material owner boundary returns a named condition. Do not launch another 256-round goal.
8. After the gate passes, Codex reviews the diff and the PR's required technical checks once before claiming integration. The controller itself does not push, merge or touch main.

## Reproducibility

The offline test uses a synthetic dsh executable and a gate that fails once and then passes. It proves the same-session retry and token parsing without requiring Codex or MiniMax credits. The first live MATRIX run still needs verification on the owner's actual DSH model profile, source permissions and sandbox.

## Source documentation

- DeepSeek Harness headless CLI: https://github.com/deepseek-ai/deepseek-harness/blob/master/packages/bundle/headless/README.md
- Codex skills: https://developers.openai.com/codex/skills/
- Internal authority and worktree constraints: AGENTS.md and docs-v2/governance/AGENT-OPERATING-PROTOCOL.md

**Value classification:** ENGINEERING_MAINTENANCE. This helper reduces orchestration overhead; it creates no new cognitive product capability by itself.
