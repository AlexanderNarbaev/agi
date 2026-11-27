#!/usr/bin/env python3
"""Bounded, local DSH headless supervisor. No Codex/model call in the retry loop.

Usage: python3 scripts/matrix_dsh_supervise.py run --task-file FILE --worktree PATH \
  --gate 'flock /tmp/gradle-matrix.lock ./gradlew ...' [--max-runs 6]

The gate must use public/development cases; NEVER expose sealed test failures to workers.
"""
from __future__ import annotations

import argparse
import fcntl
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import time


def write_json(path: Path, data: dict) -> None:
    tmp = path.with_name(path.name + '.new')
    tmp.write_text(json.dumps(data, indent=2, ensure_ascii=False) + '\n')
    os.chmod(tmp, 0o600)
    tmp.replace(path)


def read_json(path: Path) -> dict:
    return json.loads(path.read_text()) if path.exists() else {}


def git_output(repo: Path, *args: str) -> str:
    return subprocess.check_output(['git', '-C', str(repo), *args], text=True).strip()


def verify_worktree(worktree: Path, canonical: Path) -> str:
    if not worktree.is_dir():
        raise ValueError(f'Worktree does not exist: {worktree}')
    root = Path(git_output(worktree, 'rev-parse', '--show-toplevel')).resolve()
    if root != worktree:
        raise ValueError(f'Not a worktree root: {worktree} (resolved {root})')
    common = Path(git_output(worktree, 'rev-parse', '--path-format=absolute', '--git-common-dir')).resolve()
    expected = Path(git_output(canonical, 'rev-parse', '--path-format=absolute', '--git-common-dir')).resolve()
    if common != expected:
        raise ValueError(f'Wrong Git common directory: {common} (expected {expected})')
    return str(common)


def parse_events(path: Path) -> dict:
    result = {'session_id': None, 'final': None, 'error': None, 'tokens': 0, 'metered_steps': 0,
              'unmetered_steps': 0}
    with path.open(encoding='utf-8', errors='replace') as stream:
        for line in stream:
            try:
                e = json.loads(line)
            except json.JSONDecodeError:
                continue
            typ = e.get('type')
            if typ == 'session':
                result['session_id'] = e.get('sessionId')
            elif typ == 'final':
                result['final'] = e
            elif typ == 'error':
                result['error'] = e.get('message') or str(e)[:1000]
            elif typ == 'status' and e.get('phase') == 'step_end':
                u = e.get('usage')
                if not isinstance(u, dict):
                    result['unmetered_steps'] += 1
                else:
                    n = u.get('totalTokens')
                    if not isinstance(n, (float, int)):
                        inp, out = u.get('inputTokens'), u.get('outputTokens')
                        if isinstance(inp, (float, int)) and isinstance(out, (float, int)):
                            n = inp + out
                    if isinstance(n, (float, int)):
                        result['tokens'] += int(n)
                        result['metered_steps'] += 1
                    else:
                        result['unmetered_steps'] += 1
    return result


def final_completed(ev: dict, code: int) -> bool:
    if code != 0 or not isinstance(ev.get('final'), dict):
        return False
    reason = ev['final'].get('reason')
    return isinstance(reason, dict) and reason.get('kind') == 'completed'


def record(state_path: Path, state: dict, **kwargs: object) -> None:
    state.update(kwargs)
    state['updated_at_utc'] = time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime())
    write_json(state_path, state)


def run(args: argparse.Namespace) -> int:
    canonical = Path(args.repo).expanduser().resolve()
    worktree = Path(args.worktree).expanduser().resolve()
    task_file = Path(args.task_file).expanduser().resolve()
    if not task_file.is_file() or not task_file.read_text().strip():
        raise ValueError('Task file is absent or empty')
    if not args.gate.strip():
        raise ValueError('An independent development gate is required; no self-reported acceptance')
    common = verify_worktree(worktree, canonical)
    state_dir = canonical / '.opencode' / 'tmp' / 'matrix-dsh-supervisor'
    state_dir.mkdir(mode=0o700, parents=True, exist_ok=True)
    os.chmod(state_dir, 0o700)
    lock_path = state_dir / 'execution.lock'
    state_path = state_dir / 'state.json'
    task_digest = hashlib.sha256(task_file.read_bytes()).hexdigest()
    with lock_path.open('a+') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        state = read_json(state_path)
        if args.resume:
            if not state or state.get('task_sha256') != task_digest:
                raise ValueError('Cannot resume: no matching task state/digest')
            if state.get('worktree') != str(worktree):
                raise ValueError('Cannot resume in different worktree')
            if state.get('status') == 'VERIFIED':
                print('VERIFIED (already accepted); '+str(state_path))
                return 0
        else:
            if state and state.get('status') == 'RUNNING':
                raise ValueError('Another run is marked RUNNING; use status or --resume after diagnosis')
            state = {'task_sha256': task_digest, 'worktree': str(worktree),
                     'canonical_repo': str(canonical), 'git_common_dir': common,
                     'session_id': None, 'rounds': 0, 'reported_root_tokens': 0,
                     'unmetered_steps': 0, 'status': 'PREPARING', 'round_evidence': []}
            (state_dir / 'task.md').write_bytes(task_file.read_bytes())
            os.chmod(state_dir / 'task.md', 0o600)
        record(state_path, state, status='RUNNING')
        last_gate_sig = state.get('last_gate_signature')
        stagnant = state.get('stagnant', 0)
        while state['rounds'] < args.max_runs:
            if state['reported_root_tokens'] >= args.token_budget:
                record(state_path, state, status='BUDGET_EXHAUSTED')
                break
            index = state['rounds'] + 1
            ndjson = state_dir / f'round-{index:02d}.jsonl'
            stderr = state_dir / f'round-{index:02d}.stderr'
            if index == 1 and not args.resume:
                prompt = (state_dir / 'task.md').read_text()
            else:
                gate_note = state.get('last_gate_feedback', 'No gate result was available.')
                prompt = ('Continue the SAME bounded milestone in the SAME worktree. '
                          'Do not create clones or new directories. Previous attempt did not satisfy '
                          'the independent development gate. Fix root cause; run applicable targeted tests. '
                          'No hidden sealed gold may be read or transmitted. Gate feedback:\n' + gate_note)
            cmd = ['dsh', 'headless', '--permission-mode', args.permission,
                   '--approval-policy', 'never', '--json']
            if state.get('session_id'):
                cmd += ['--session-id', state['session_id']]
            if args.grant_git:
                cmd += ['--add-dir', common]
            cmd += ['-']
            with ndjson.open('w') as out, stderr.open('w') as err:
                os.chmod(ndjson, 0o600)
                os.chmod(stderr, 0o600)
                try:
                    code = subprocess.run(cmd, input=prompt, text=True, cwd=worktree,
                                          stdout=out, stderr=err, check=False).returncode
                except OSError as exc:
                    record(state_path, state, status='LAUNCH_FAILED', error=str(exc))
                    break
            parsed = parse_events(ndjson)
            if parsed['session_id']:
                if state.get('session_id') and parsed['session_id'] != state['session_id']:
                    raise ValueError('DSH session ID changed unexpectedly')
                state['session_id'] = parsed['session_id']
            state['rounds'] = index
            state['reported_root_tokens'] += parsed['tokens']
            state['unmetered_steps'] += parsed['unmetered_steps']
            item = {'round': index, 'dsh_exit_code': code, 'final_reason':
                    parsed['final'].get('reason') if parsed['final'] else None,
                    'reported_root_tokens': parsed['tokens'], 'unmetered_steps': parsed['unmetered_steps'],
                    'events': str(ndjson), 'stderr': str(stderr)}
            state['round_evidence'].append(item)
            record(state_path, state)
            if not final_completed(parsed, code):
                record(state_path, state, status='DSH_FAILED', error=parsed.get('error'))
                break
            if state['unmetered_steps']:
                # Reported tokens may omit retries/subagents. Never promise a hard 512K total.
                record(state_path, state, status='TOKEN_USAGE_UNVERIFIED')
                break
            gate_log = state_dir / f'gate-{index:02d}.log'
            # An operator-supplied development gate is allowed to read public dev fixtures only.
            # Run under a shell to support flock and the project's Gradle command conventions.
            with gate_log.open('w') as log:
                os.chmod(gate_log, 0o600)
                try:
                    gate_code = subprocess.run(args.gate, shell=True, cwd=worktree,
                                               stdout=log, stderr=subprocess.STDOUT, check=False,
                                               executable='/bin/bash').returncode
                except OSError as exc:
                    gate_code = 127
                    log.write(f'Gate launch failed: {exc}\n')
            item['gate_exit_code'] = gate_code
            item['gate_log'] = str(gate_log)
            if gate_code == 0:
                record(state_path, state, status='VERIFIED', last_gate_feedback='PASS')
                break
            tail = gate_log.read_text(errors='replace')[-2500:]
            sig = hashlib.sha256(tail.encode('utf-8')).hexdigest()
            stagnant = stagnant + 1 if sig == last_gate_sig else 0
            last_gate_sig = sig
            record(state_path, state, last_gate_signature=sig,
                   stagnant=stagnant, last_gate_feedback=tail)
            if stagnant >= 2:
                record(state_path, state, status='NO_PROGRESS')
                break
        else:
            record(state_path, state, status='MAX_RUNS_REACHED')
    summary = (f"{state['status']} | rounds={state['rounds']} | "
               f"reported_root_tokens={state['reported_root_tokens']} | "
               f"unmetered_steps={state['unmetered_steps']} | "
               f"session={state.get('session_id') or 'UNKNOWN'} | evidence={state_path}")
    print(summary)
    return 0 if state['status'] == 'VERIFIED' else 1


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='subcommand', required=True)
    run_parser = sub.add_parser('run')
    run_parser.add_argument('--repo', default='~/Projects/agi')
    run_parser.add_argument('--worktree', default='~/Projects/matrix-worktrees/dsh-writer')
    run_parser.add_argument('--task-file', required=True)
    run_parser.add_argument('--gate', required=True, help='Independent DEV-only acceptance command')
    run_parser.add_argument('--permission', default='workspace-write', choices=['workspace-write','read-only'])
    run_parser.add_argument('--grant-git', action='store_true', help='Explicitly permit shared .git metadata writes')
    run_parser.add_argument('--token-budget', type=int, default=512000)
    run_parser.add_argument('--max-runs', type=int, default=8)
    run_parser.add_argument('--resume', action='store_true')
    status_parser = sub.add_parser('status')
    status_parser.add_argument('--repo', default='~/Projects/agi')
    args = parser.parse_args()
    if args.subcommand == 'status':
        state_path = Path(args.repo).expanduser().resolve() / '.opencode/tmp/matrix-dsh-supervisor/state.json'
        state = read_json(state_path)
        print(json.dumps({'status': state.get('status','NO_RUN'), 'session_id': state.get('session_id'),
                          'rounds': state.get('rounds'), 'reported_root_tokens': state.get('reported_root_tokens'),
                          'unmetered_steps': state.get('unmetered_steps'), 'evidence': str(state_path)},
                         ensure_ascii=False))
        return 0
    if args.max_runs < 1 or args.token_budget < 1:
        parser.error('max-runs and token-budget must be positive')
    try:
        return run(args)
    except (ValueError, OSError, subprocess.CalledProcessError, BlockingIOError) as exc:
        print(f'matrix-dsh: {exc}', file=sys.stderr)
        return 2


if __name__ == '__main__':
    sys.exit(main())
