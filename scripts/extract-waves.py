#!/usr/bin/env python3
"""
Extract individual wave files from monolithic WAL.md

Usage:
  ./extract-waves.py --start 297 --end 346 [--dry-run]
  ./extract-waves.py --latest 10  # Extract last 10 waves

Output: docs-v2/waves/wave-NNN-*.md
"""

import argparse
import re
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


def parse_wal(wal_path: Path) -> list[dict]:
    """Parse WAL.md into list of wave sections."""
    content = wal_path.read_text(encoding='utf-8')
    
    # Split by wave markers (## RUN, ## WAVE, ## CHECKPOINT)
    pattern = r'^(## (?:RUN|WAVE|CHECKPOINT) \d+(?:\.\d+)?(?: — .+)?).*?(?=^(## (?:RUN|WAVE|CHECKPOINT) \d+)|\Z)'
    matches = re.findall(pattern, content, flags=re.MULTILINE | re.DOTALL)
    
    waves = []
    for i, match in enumerate(matches):
        header = match[0].strip()
        # Extract wave number from header
        wave_match = re.search(r'(\d+(?:\.\d+)?)', header)
        if wave_match:
            wave_num = int(float(wave_match.group(1)))
            # Extract title from header (after — or :)
            title_match = re.search(r'[—:]\s*(.+?)(?:\n|$)', header)
            title = title_match.group(1).strip() if title_match else f'Wave {wave_num}'
            
            waves.append({
                'number': wave_num,
                'header': header,
                'title': title,
                'content': match[1] if len(match) > 1 else ''
            })
    
    return waves


def format_wave(wave: dict) -> str:
    """Format wave content according to template."""
    date = datetime.now().strftime('%Y-%m-%d')
    
    # Clean up content
    content = wave['content'].strip()
    
    # Generate filename-safe title
    safe_title = re.sub(r'[^\w\s-]', '', wave['title'])[:50]
    safe_title = re.sub(r'\s+', '-', safe_title).lower()
    
    filename = f"wave-{wave['number']:03d}-{safe_title}.md"
    
    # Create formatted content
    formatted = f"""# WAL: Wave {wave['number']} — {wave['title']}

**Date:** {date}  
**Branch:** `feature/liquid-federation-dynamic-modulators`  
**Focus:** {wave['title']}

## Extracted from WAL.md

This wave was automatically extracted from the monolithic WAL.md file.

{content}

---

**Checkpoint Hash:** `<pending>`  
**Previous Checkpoint:** `<pending>`  
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
        # Extract last N waves
        waves_to_extract = all_waves[-args.latest:]
    else:
        # Extract range
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
        print("  2. Create LATEST.md symlink")
        print("  3. Update INDEX.md")
        print("  4. Archive old waves (W1-W296)")


if __name__ == '__main__':
    main()
