#!/usr/bin/env python3
"""Bounded, local DSH headless supervisor. No Codex/model call in the retry loop.

Usage: python3 scripts/matrix_dsh_supervise.py run --task-file FILE --worktree PATH \
  --gate 'flock /tmp/gradle-matrix.lock ./gradlew ...'

The gate must use public/development cases; NEVER expose sealed test failures to workers.
"""
from __future__ import annotations

import argparse
import fcntl
import hashlib
import json
import os
from pathlib import Path
import signal
import subprocess
import tempfile
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


def event_summary() -> dict:
    return {'session_id': None, 'final': None, 'error': None, 'tokens': 0,
            'metered_steps': 0, 'unmetered_steps': 0, 'tool_calls': 0,
            'usage': {}}


def consume_event(result: dict, line: bytes) -> bool:
    try:
        event = json.loads(line)
    except (ValueError, UnicodeError):
        return False
    if not isinstance(event, dict):
        return False
    kind = event.get('type')
    if kind == 'session':
        result['session_id'] = event.get('sessionId')
    elif kind == 'final':
        result['final'] = event
    elif kind == 'error':
        result['error'] = event.get('message') or str(event)[:1000]
    elif kind == 'tool_call':
        result['tool_calls'] += 1
    elif kind == 'status' and event.get('phase') == 'step_end':
        usage = event.get('usage')
        fields = ('inputTokens', 'outputTokens', 'cacheReadTokens', 'cacheWriteTokens')
        if isinstance(usage, dict):
            amounts = {key: usage[key] for key in fields
                       if type(usage.get(key)) is int and usage[key] >= 0}
            for key, value in amounts.items():
                result['usage'][key] = result['usage'].get(key, 0) + value
            total = usage.get('totalTokens')
            if type(total) is not int and {'inputTokens', 'outputTokens'} <= amounts.keys():
                total = sum(amounts.values())
            if type(total) is int and total >= sum(amounts.values()):
                result['tokens'] += total
                result['metered_steps'] += 1
                return True
        result['unmetered_steps'] += 1
    return True


def parse_events(path: Path) -> dict:
    result = event_summary()
    with path.open('rb') as stream:
        for line in stream:
            consume_event(result, line)
    return result


def terminate_group(process: subprocess.Popen) -> None:
    try:
        os.killpg(process.pid, signal.SIGTERM)
        deadline = time.monotonic() + 2
        while time.monotonic() < deadline:
            process.poll()
            os.killpg(process.pid, 0)
            time.sleep(0.05)
        os.killpg(process.pid, signal.SIGKILL)
    except ProcessLookupError:
        pass
    process.wait()


def run_headless(cmd, prompt, worktree, output, events, stderr, *, max_tools, timeout, idle_timeout, checkpoint, lock_fd):
    # The provider reports committed steps. A request can overrun the threshold
    # before its usage arrives, and dropped attempts and subagents may be absent.
    with tempfile.TemporaryFile() as request, events.open('rb') as stream:
        request.write(prompt.encode())
        request.seek(0)
        process = subprocess.Popen(cmd, stdin=request, stdout=output, stderr=stderr,
                                   cwd=worktree, start_new_session=True,
                                   pass_fds=() if lock_fd is None else (lock_fd,))
        started = last_progress = time.monotonic()
        result = event_summary()
        try:
            while True:
                offset = stream.tell()
                line = stream.readline()
                if line.endswith(b'\n'):
                    if consume_event(result, line):
                        last_progress = time.monotonic()
                        checkpoint(result)
                else:
                    stream.seek(offset)
                if result['tool_calls'] >= max_tools:
                    stop = 'TOOL_LIMIT'
                    break
                now = time.monotonic()
                if now - started >= timeout:
                    stop = 'ROUND_TIMEOUT'
                    break
                if now - last_progress >= idle_timeout:
                    stop = 'NO_PROGRESS'
                    break
                if not line.endswith(b'\n'):
                    code = process.poll()
                    if code is not None:
                        return code, None
                    time.sleep(0.02)
        finally:
            terminate_group(process)
        return process.returncode, stop


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
    round_timeout = getattr(args, 'round_timeout', 1800)
    idle_timeout = getattr(args, 'idle_timeout', 180)
    max_tools = getattr(args, 'max_tools', 100)
    resume_note = getattr(args, 'resume_note', '')
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
        if state.get('in_flight'):
            flight = state.pop('in_flight')
            path = Path(flight['events'])
            parsed = parse_events(path) if path.is_file() else event_summary()
            if state.get('session_id') and parsed['session_id'] not in (None, state['session_id']):
                raise ValueError('Interrupted round has a different session ID')
            state['session_id'] = parsed['session_id'] or state.get('session_id')
            state['reported_root_tokens'] += parsed['tokens']
            state['unmetered_steps'] += parsed['unmetered_steps']
            state['rounds'] = flight['round']
            state['round_evidence'].append({**flight, 'interrupted': True,
                                            'reported_root_tokens': parsed['tokens'],
                                            'usage': parsed['usage']})
            record(state_path, state, status='INTERRUPTED')
        record(state_path, state, status='RUNNING')
        last_gate_sig = state.get('last_gate_signature')
        stagnant = state.get('stagnant', 0)
        first_round = state['rounds'] + 1
        while True:
            index = state['rounds'] + 1
            prefix = f'{task_digest[:12]}-{time.time_ns()}-{index:02d}'
            ndjson = state_dir / f'round-{prefix}.jsonl'
            stderr = state_dir / f'round-{prefix}.stderr'
            if index == 1 and not args.resume:
                prompt = (state_dir / 'task.md').read_text()
            else:
                gate_note = state.get('last_gate_feedback', 'The last run stopped without a gate result. Resume the saved work.')
                prompt = ('Continue the SAME bounded milestone in the SAME worktree. '
                          'Do not create clones or new directories. Previous attempt did not satisfy '
                          'the independent development gate. Fix root cause; run applicable targeted tests. '
                          'No hidden sealed gold may be read or transmitted. Gate feedback:\n' + gate_note)
            if resume_note and index == first_round:
                prompt += '\nCoordinator recovery instruction: ' + resume_note
            cmd = ['dsh', 'headless', '--permission-mode', args.permission,
                   '--approval-policy', 'never', '--json']
            if state.get('session_id'):
                cmd += ['--session-id', state['session_id']]
            if args.grant_git:
                cmd += ['--add-dir', common]
            cmd += ['-']
            with ndjson.open('xb') as out, stderr.open('xb') as err:
                os.chmod(ndjson, 0o600)
                os.chmod(stderr, 0o600)
                flight = {'round': index, 'events': str(ndjson), 'stderr': str(stderr)}
                record(state_path, state, in_flight=flight)

                def checkpoint(events):
                    flight['session_id'] = events['session_id']
                    flight['reported_root_tokens'] = events['tokens']
                    flight['usage'] = events['usage']
                    flight['tool_calls'] = events['tool_calls']
                    record(state_path, state)

                try:
                    code, stop = run_headless(
                        cmd, prompt, worktree, out, ndjson, err,
                        max_tools=max_tools, timeout=round_timeout, idle_timeout=idle_timeout,
                        checkpoint=checkpoint, lock_fd=lock.fileno())
                except OSError as exc:
                    record(state_path, state, status='LAUNCH_FAILED', error=str(exc))
                    break
            state.pop('in_flight')
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
                    'events': str(ndjson), 'stderr': str(stderr),
                    'usage': parsed['usage'], 'tool_calls': parsed['tool_calls']}
            state['round_evidence'].append(item)
            record(state_path, state)
            if stop or not final_completed(parsed, code):
                reason = item['final_reason'] or {}
                detail = parsed['error'] or reason.get('message') or ''
                kind = reason.get('kind', '')
                if kind in ('quota_exhausted', 'insufficient_quota') or any(
                        word in str(detail).lower() for word in
                        ('quota exceeded', 'quota exhausted', 'insufficient balance')):
                    record(state_path, state, status='PROVIDER_QUOTA_EXHAUSTED', error=detail)
                    break
                # Checkpointed slices can continue after tool/time limits. A
                # repeated stop with the same durable milestone checkpoint
                # means another continuation has no new work to build on.
                milestone = worktree / '.opencode' / 'matrix-dsh-checkpoint.md'
                checkpoint_hash = hashlib.sha256(milestone.read_bytes()).hexdigest() if milestone.is_file() else None
                signature = [stop or kind or code, checkpoint_hash]
                if state.get('session_id') and signature != state.get('last_slice_failure'):
                    record(state_path, state, last_slice_failure=signature,
                           last_gate_feedback=f'Prior slice ended ({signature[0]}). If present, read '
                           '.opencode/matrix-dsh-checkpoint.md and execute the next unfinished action.')
                    continue
                record(state_path, state, status=stop or 'DSH_FAILED', error=detail)
                break
            gate_log = state_dir / f'gate-{prefix}.log'
            # An operator-supplied development gate is allowed to read public dev fixtures only.
            # Run under a shell to support flock and the project's Gradle command conventions.
            with gate_log.open('w') as log:
                os.chmod(gate_log, 0o600)
                try:
                    with subprocess.Popen(['/bin/bash', '-c', args.gate], cwd=worktree,
                                          stdout=log, stderr=subprocess.STDOUT,
                                          start_new_session=True) as gate:
                        try:
                            gate_code = gate.wait(timeout=round_timeout)
                        except subprocess.TimeoutExpired:
                            gate_code = 124
                            log.write('Development gate timed out.\n')
                        finally:
                            terminate_group(gate)
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
    run_parser.add_argument('--max-tools', type=int, default=100)
    run_parser.add_argument('--round-timeout', type=int, default=1800, help='Per round or gate, seconds')
    run_parser.add_argument('--idle-timeout', type=int, default=180, help='Seconds without a valid event')
    run_parser.add_argument('--resume', action='store_true')
    run_parser.add_argument('--resume-note', default='', help='Instruction appended to the first resumed turn')
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
    if min(args.max_tools, args.round_timeout, args.idle_timeout) < 1:
        parser.error('Runtime limits must be positive')
    try:
        return run(args)
    except (ValueError, OSError, subprocess.CalledProcessError, BlockingIOError) as exc:
        print(f'matrix-dsh: {exc}', file=sys.stderr)
        return 2


if __name__ == '__main__':
    sys.exit(main())
