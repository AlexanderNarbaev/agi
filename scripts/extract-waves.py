#!/usr/bin/env python3
"""
Extract individual wave files from monolithic WAL.md.

Usage:
  ./extract-waves.py --start 297 --end 346 [--dry-run]
  ./extract-waves.py --latest 10  # Extract last 10 waves

Output: docs-v2/waves/wave-NNN-*.md

Wave numbering:
  WAL.md uses ## RUN N (or ## WAVE N, ## CHECKPOINT N) headers.
  This script splits the file on those headers and writes one
  per-wave markdown file, with checkpoint hashes derived from
  the current git HEAD.
"""

import argparse
import re
import subprocess
import sys
from pathlib import Path
from datetime import datetime


def parse_args():
    parser = argparse.ArgumentParser(description='Extract waves from WAL.md')
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument('--start', type=int, help='Start wave number')
    group.add_argument('--latest', type=int, help='Extract last N waves')
    parser.add_argument('--end', type=int, help='End wave number (required with --start)')
    parser.add_argument('--dry-run', action='store_true', help='Show what would be extracted')
    return parser.parse_args()


def parse_wal(wal_path: Path) -> list:
    """Parse WAL.md into list of wave sections.

    Splits on ## RUN N, ## WAVE N, ## CHECKPOINT N headers
    (with optional .N sub-version and trailing title after em-dash or colon).
    """
    content = wal_path.read_text(encoding='utf-8')

    # Split on wave headers. The lookahead keeps the header attached
    # to its section. Pattern matches at line start (MULTILINE).
    header_pattern = r'^## (?:RUN|WAVE|CHECKPOINT) \d+(?:\.\d+)?'
    sections = re.split(f'(?={header_pattern})', content, flags=re.MULTILINE)

    waves = []
    # First section is preamble (before any wave header); skip it.
    for section in sections:
        header_match = re.match(header_pattern, section)
        if not header_match:
            continue

        header = header_match.group(0)
        # Extract wave number
        wave_match = re.search(r'(\d+(?:\.\d+)?)', header)
        if not wave_match:
            continue
        wave_num = int(float(wave_match.group(1)))

        # Extract title from header line (anything after the number on same line)
        first_line = section.split('\n', 1)[0]
        # Remove the leading "## RUN N — " or "## RUN N: "
        title = re.sub(r'^## (?:RUN|WAVE|CHECKPOINT) \d+(?:\.\d+)?[\s\u2014:]+', '', first_line).strip()
        if not title:
            title = f'Wave {wave_num}'

        # Content is everything after the header line
        body = section[header_match.end():].lstrip('\n')

        waves.append({
            'number': wave_num,
            'header': header,
            'title': title,
            'content': body,
        })

    return waves


def get_git_branch() -> str:
    """Return current git branch name, or 'unknown' if not in a repo."""
    try:
        result = subprocess.run(
            ['git', 'rev-parse', '--abbrev-ref', 'HEAD'],
            capture_output=True, text=True, timeout=5
        )
        if result.returncode == 0:
            return result.stdout.strip()
    except (subprocess.TimeoutExpired, FileNotFoundError):
        pass
    return 'unknown'


def get_last_git_hash(short: bool = True) -> str:
    """Return current HEAD commit hash (short form by default)."""
    try:
        fmt = '%h' if short else '%H'
        result = subprocess.run(
            ['git', 'log', '-1', f'--format={fmt}'],
            capture_output=True, text=True, timeout=5
        )
        if result.returncode == 0:
            return result.stdout.strip()
    except (subprocess.TimeoutExpired, FileNotFoundError):
        pass
    return '<no-git>'


def format_wave(wave: dict) -> tuple:
    """Format wave content according to template. Returns (filename, content)."""
    date = datetime.now().strftime('%Y-%m-%d')
    branch = get_git_branch()
    checkpoint_hash = get_last_git_hash(short=True)

    # Clean up content
    body = wave['content'].strip()

    # Generate filename-safe title (word chars, spaces, hyphens only)
    safe_title = re.sub(r'[^\w\s-]', '', wave['title'])[:50]
    safe_title = re.sub(r'\s+', '-', safe_title).lower()

    filename = f"WAL-{wave['number']:03d}-{safe_title}.md"

    # Create formatted content
    formatted = f"""# WAL {wave['number']} \u2014 {wave['title']}

**Date:** {date}
**Branch:** `{branch}`
**Focus:** {wave['title']}

## Extracted from WAL.md

This wave was automatically extracted from the monolithic WAL.md file.

{body}

---

**Checkpoint Hash:** `{checkpoint_hash}` (current HEAD at extraction time)
**Previous Checkpoint:** see git log -1
**Next Wave:** W{wave['number'] + 1}

*Auto-extracted by extract-waves.py*
"""
    return filename, formatted


def main():
    args = parse_args()

    wal_path = Path('WAL.md')
    output_dir = Path('docs-v2/waves')

    if not wal_path.exists():
        print(f"Error: {wal_path} not found", file=sys.stderr)
        sys.exit(1)

    output_dir.mkdir(parents=True, exist_ok=True)

    # Parse all waves
    all_waves = parse_wal(wal_path)
    print(f"Parsed {len(all_waves)} waves from WAL.md")

    # Determine which waves to extract
    if args.latest:
        waves_to_extract = all_waves[-args.latest:]
    else:
        if not args.end:
            print("Error: --end required with --start", file=sys.stderr)
            sys.exit(1)
        waves_to_extract = [w for w in all_waves if args.start <= w['number'] <= args.end]

    print(f"Extracting {len(waves_to_extract)} waves...")

    for wave in waves_to_extract:
        filename, content = format_wave(wave)

        if args.dry_run:
            print(f"  Would create: {filename}")
        else:
            output_path = output_dir / filename
            output_path.write_text(content, encoding='utf-8')
            print(f"  Created: {filename}")

    if not args.dry_run:
        print(f"\nDone! Extracted {len(waves_to_extract)} waves to {output_dir}/")
        print("Next steps:")
        print("  1. Review extracted files")
        print("  2. Update INDEX.md with wave pointers")
        print("  3. Archive old waves (W1-W296) per SPEC-014")


if __name__ == '__main__':
    main()
