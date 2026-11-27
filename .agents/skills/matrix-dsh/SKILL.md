---
name: matrix-dsh
description: Delegate bounded MATRIX implementation and tests from Codex to the already configured MiniMax-backed DeepSeek Harness. Automatically retry public development gates in one DSH session without spending additional Codex tokens. Use instead of manually copying agent reports.
license: project
---

# MATRIX DSH — one commission, autonomous execution

This skill runs in the existing canonical ~/Projects/agi checkout and existing ~/Projects/matrix-worktrees/dsh-writer linked worktree. Never create another repository, agent installation, worktree, or MCP bridge for routine delegation.

1. Verify the current develop HEAD, current task scope, applicable governance, and the previously confirmed MiniMax subscription-backed DSH route. Never change credentials or expose secrets. The script does not select a model or enforce no-fallback; confirm the existing DSH profile before the first live call.
2. Write exactly one bounded English milestone commission under ~/Projects/agi/.opencode/tmp/matrix-dsh-supervisor/commission.md. Include: observed defect, correct behavior, scope, negative controls, allowed source disclosures, a deterministic DEV-only acceptance gate, resources, stop conditions and evidence outputs.
3. Run the local supervisor once from the canonical repo:

    python3 scripts/matrix_dsh_supervise.py run --task-file .opencode/tmp/matrix-dsh-supervisor/commission.md --worktree "$HOME/Projects/matrix-worktrees/dsh-writer" --gate 'flock /tmp/gradle-matrix.lock ./gradlew :matrix-core:test --offline --tests "io.matrix.knowledge.ActualTargetTest"' --token-budget 512000 --max-runs 8

Replace ActualTargetTest with a real, task-specific regression test. A generic compile or diff check is not sufficient for an end-to-end claim. The supervisor uses the existing ignored state root and returns one compact result line. It invokes only DSH and trusted local gates, never Codex.
4. DSH performs implementation, tests, reviewer roles and retries. A failed gate triggers same-session DSH continuation without another Codex invocation. Hidden sealed evaluation cases and results MUST remain isolated and may not be used as feedback gates.
5. After a VERIFIED result, Codex reviews only the changed file list, relevant diff, gate evidence and applicable CI; do not load full DSH JSONL into context. Publish an approved PR to develop under existing owner authorization. Do not publish production code solely because the script reports success.
6. On failure, read the saved status with:

    python3 scripts/matrix_dsh_supervise.py status

Only escalate an actual missing authority, material architecture choice, repeated failure, or exhausted budget. Do not restart a 256-round goal or reconstruct entire project history.

The DSH output is stored under .opencode/tmp/matrix-dsh-supervisor/; promote durable science and test evidence to existing canonical evidence paths before declaring success. No other temporary checkout is created.

Important limits: the token budget is measured using headless parent-agent reported usage; subagent or missing-usage tokens cannot be reliably capped by this script. Provider-side quotas are required for a hard 512K aggregate limit. If usage is incomplete, automatic retries stop. The DSH invocation is synchronous and runs one task before exiting, but the script may resume its saved DSH session for a bounded test/fix cycle. No perpetual background processing or spontaneous Codex callbacks are claimed.

By default DSH may edit the writer worktree but cannot write the shared Git directory. The optional --grant-git gives broad access to Git metadata including hooks; use it only after explicit source-scope review, never by default. Preserve owner data, protected paths, frozen controls and hidden gold.
