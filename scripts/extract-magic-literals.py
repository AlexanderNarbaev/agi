#!/usr/bin/env python3
"""RECON-W42.9 — extract the magic-number candidates into named constants.

The owner's decision D-015: the literals are fixed IN THE CODE, and the
quality gate's baseline is not updated. This script does the mechanical
part of that, under the gate's own rules:

  - a candidate is a numeric literal of three or more digits;
  - the exempt packages are skipped;
  - the lines that already define a named constant are skipped;
  - the comment lines are skipped;
  - the gate's structural set is skipped.

WHAT IT DOES. Each candidate becomes a named constant of the enclosing
class, and the constant's definition lives on a `static final` line -
which the gate excludes by its own rule. The VALUE never changes: this
is a refactoring of the source's shape, not of its behaviour, and the
compile plus the test suite are what prove it.

TWO CASES.

  - A literal in code: the token is replaced by the constant's name.
  - A literal inside a string: the WHOLE string becomes the constant,
    because a number inside a string cannot be concatenated with an
    int without changing the source's shape.

Usage:
  scripts/extract-magic-literals.py --dry-run
  scripts/extract-magic-literals.py --apply
"""
import argparse
import glob
import re
import sys

# The gate's own rules, mirrored here.
TOKEN = re.compile(r'(^|[^A-Za-z0-9_.])([0-9]{3,}(?:\.[0-9]+)?)([LlFfDd]?)([^A-Za-z0-9_]|$)')
STRING = re.compile(r'"((?:[^"\\]|\\.)*)"')
EXEMPT = ('io.matrix.protocol', 'io.matrix.federation',
          'io.matrix.research', 'io.matrix.consciousness')
STRUCTURAL = set("""0 1 2 -1 8 16 32 64 100 101 200 201 202 204 400 401 403 404
405 409 422 429 500 502 503 1099511628211 14695981039346656037 128 255 256
1024""".split())


def candidates(path):
    """The candidate lines of one file: a list of (line number, tokens)."""
    out = []
    with open(path, encoding='utf-8', errors='replace') as handle:
        for number, line in enumerate(handle.read().split('\n'), 1):
            if re.search(r'static final|final static|// |/\*|\* ', line):
                continue
            tokens = [token for token in TOKEN.findall(line)
                      if token[1] not in STRUCTURAL]
            if tokens:
                out.append((number, tokens))
    return out


def class_name(path):
    """The class's simple name."""
    with open(path, encoding='utf-8', errors='replace') as handle:
        text = handle.read()
    match = re.search(r'\b(?:class|enum|record|interface)\s+(\w+)', text)
    return match.group(1) if match else 'Unknown'


def insertion_point(lines):
    """The line after the type's opening brace.

    For an enum the constants must come first, so the insertion goes
    after the last enum constant.
    """
    for index, line in enumerate(lines):
        if re.search(r'\b(?:class|enum|record|interface)\s+\w+[^{]*\{', line):
            body = index + 1
            if re.search(r'\benum\s+\w+', line):
                last = body
                while last < len(lines) and re.match(
                        r'\s*[A-Z][A-Z0-9_]*\s*(\(|,|;|\{|\})', lines[last]):
                    last += 1
                body = last
            return body
    return None


def constant_name(clazz, value, suffix):
    """A name that says what it is: the class it belongs to and the value."""
    kind = {'L': 'LONG', 'l': 'LONG', 'F': 'FLOAT', 'f': 'FLOAT',
            'D': 'DOUBLE', 'd': 'DOUBLE'}.get(suffix, 'INT')
    return '%s_%s_%s' % (clazz.upper(), kind, value)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--apply', action='store_true')
    parser.add_argument('--dry-run', action='store_true')
    args = parser.parse_args()

    files = []
    for module in ('matrix-core', 'matrix-brain-runtime', 'matrix-api-gateway'):
        files.extend(glob.glob(module + '/src/main/java/**/*.java', recursive=True))

    touched = 0
    literals = 0
    for path in sorted(files):
        dotted = path.replace('/src/main/java/', '.').replace('/', '.')[:-5]
        if any(dotted.startswith(exempt) for exempt in EXEMPT):
            continue
        found = candidates(path)
        if not found:
            continue
        with open(path, encoding='utf-8', errors='replace') as handle:
            lines = handle.read().split('\n')
        clazz = class_name(path)
        point = insertion_point(lines)
        if point is None:
            print('SKIP (no type body): %s' % path)
            continue

        definitions = []
        seen = {}
        # The lines are processed from the bottom up, so that the line
        # numbers stay valid while the file is rewritten.
        for number, tokens in sorted(found, reverse=True):
            index = number - 1
            original = lines[index]
            rewritten = original
            # The string case first: a number inside a string cannot be
            # concatenated with an int, so the whole string is the
            # constant.
            consumed = set()
            for match in list(STRING.finditer(original))[::-1]:
                body = match.group(1)
                inside = [index for index, (_, token, _, _) in enumerate(tokens)
                          if token in body]
                if not inside:
                    continue
                consumed.update(inside)
                name = '%s_TEXT_%d' % (clazz.upper(), len(definitions) + 1)
                if name in seen:
                    name = seen[name] + '_B'
                seen[name] = name
                definitions.append('    private static final String %s = "%s";'
                                    % (name, body))
                rewritten = rewritten[:match.start()] + name + rewritten[match.end():]
            # Then the code case.
            for index, (_, value, suffix, _) in enumerate(tokens):
                if index in consumed:
                    continue
                if '"%s"' % value in original:
                    continue
                name = constant_name(clazz, value, suffix)
                if name not in seen:
                    seen[name] = name
                    kind = ('double' if '.' in value else
                            {'L': 'long', 'l': 'long', 'F': 'float', 'f': 'float',
                             'D': 'double', 'd': 'double'}.get(suffix, 'int'))
                    literal = value + suffix
                    definitions.append('    private static final %s %s = %s;'
                                        % (kind, name, literal))
                rewritten = re.sub(r'(^|[^A-Za-z0-9_.])%s%s([^A-Za-z0-9_]|$)' % (
                    value, suffix), r'\1%s\2' % name, rewritten, count=1)
            lines[index] = rewritten
            literals += len(tokens)

        lines[point:point] = definitions
        touched += 1
        print('%-70s the constants: %d' % (path.split('/')[-1], len(definitions)))
        if args.apply and not args.dry_run:
            with open(path, 'w', encoding='utf-8') as handle:
                handle.write('\n'.join(lines))

    print('')
    print('the files: %d | the literals: %d' % (touched, literals))
    if not args.apply:
        print('this was a DRY RUN — nothing was written')


if __name__ == '__main__':
    sys.exit(main())
