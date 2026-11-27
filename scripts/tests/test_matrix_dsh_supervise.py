"""Pure local tests for the DSH supervisor.

Run: python3 -m unittest discover -s scripts/tests -p 'test_matrix_dsh_supervise.py'
"""
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import argparse
import contextlib
import io
from unittest import mock

MODULE = Path(__file__).resolve().parents[1] / 'matrix_dsh_supervise.py'
spec = importlib.util.spec_from_file_location('matrix_dsh_supervise', MODULE)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class ParserTests(unittest.TestCase):
    def test_requires_complete_terminal_reason(self):
        self.assertTrue(module.final_completed({'final': {'reason': {'kind': 'completed'}}}, 0))
        self.assertFalse(module.final_completed({'final': {'reason': {'kind': 'stopped'}}}, 0))
        self.assertFalse(module.final_completed({'final': {'reason': {'kind': 'completed'}}}, 1))
        self.assertFalse(module.final_completed({'final': None}, 0))

    def test_usage_counts_only_step_end(self):
        with tempfile.TemporaryDirectory() as d:
            path = Path(d) / 'events.jsonl'
            path.write_text('\n'.join(map(json.dumps, [
                {'type': 'session', 'sessionId': 'session-fixed'},
                {'type': 'status', 'phase': 'step_end', 'usage': {'inputTokens': 20, 'outputTokens': 10}},
                {'type': 'status', 'phase': 'step_end', 'usage': {
                    'totalTokens': 70, 'inputTokens': 60, 'outputTokens': 10}},
                {'type': 'final', 'sessionId': 'session-fixed',
                 'reason': {'kind': 'completed'}, 'text': 'ok'},
            ])) + '\n')
            event = module.parse_events(path)
            self.assertEqual(event['tokens'], 100)
            self.assertEqual(event['session_id'], 'session-fixed')
            self.assertEqual(event['unmetered_steps'], 0)


class IntegrationTests(unittest.TestCase):
    def test_gate_drives_retry_without_codex_and_resumes_same_session(self):
        with tempfile.TemporaryDirectory() as d:
            d = Path(d)
            repo = d / 'repo'
            repo.mkdir()
            (repo / '.opencode/tmp').mkdir(parents=True)
            task = d / 'task.md'
            task.write_text('Make the independent acceptance test pass. Do not use hidden cases.\n')
            fake_bin = d / 'bin'
            fake_bin.mkdir()
            dsh = fake_bin / 'dsh'
            dsh.write_text("""#!/usr/bin/env python3
import json, os, sys
from pathlib import Path
base = Path(os.environ['FAKE_DSH_DIR'])
count_file = base / 'invocations'
num = int(count_file.read_text())+1 if count_file.exists() else 1
count_file.write_text(str(num))
args = sys.argv[1:]
if num > 1:
    assert '--session-id' in args and args[args.index('--session-id')+1]=='session-test'
print(json.dumps({'type':'session','sessionId':'session-test'}))
print(json.dumps({'type':'status','phase':'step_end','usage':{'inputTokens':100,'outputTokens':5}}))
print(json.dumps({'type':'final','sessionId':'session-test','reason':{'kind':'completed'},'text':'completed'}))
""")
            os.chmod(dsh, 0o755)
            count_file = d / 'gate_count'
            gate = d / 'gate.py'
            gate.write_text("""from pathlib import Path
p=Path(%r)
n=int(p.read_text())+1 if p.exists() else 1
p.write_text(str(n))
print('control still RED' if n==1 else 'control GREEN')
raise SystemExit(0 if n>=2 else 1)
""" % str(count_file))
            args = argparse.Namespace(
                repo=str(repo), worktree=str(repo), task_file=str(task),
                gate=shlex_quote(sys.executable) + ' ' + shlex_quote(str(gate)),
                permission='workspace-write', grant_git=False,
                token_budget=512000, max_runs=3, resume=False,
            )
            env = {'FAKE_DSH_DIR': str(d),
                   'PATH': str(fake_bin) + os.pathsep + os.environ['PATH']}
            output = io.StringIO()
            # Mock only the preflight's Git metadata lookup: do not git-init a repo.
            # All controller retry behavior and the external fake DSH/gate run unchanged.
            with mock.patch.object(module, 'verify_worktree', return_value=str(d / 'common-git')):
                with mock.patch.dict(os.environ, env):
                    with contextlib.redirect_stdout(output):
                        code = module.run(args)
            self.assertEqual(code, 0, output.getvalue())
            self.assertIn('VERIFIED | rounds=2', output.getvalue())
            state = json.loads((repo / '.opencode/tmp/matrix-dsh-supervisor/state.json').read_text())
            self.assertEqual(state['reported_root_tokens'], 210)
            self.assertEqual(state['session_id'], 'session-test')
            self.assertEqual(count_file.read_text(), '2')
            self.assertEqual((d / 'invocations').read_text(), '2')


def shlex_quote(value):
    import shlex
    return shlex.quote(value)


if __name__ == '__main__':
    unittest.main()
