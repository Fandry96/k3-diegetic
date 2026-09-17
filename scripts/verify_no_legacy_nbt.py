#!/usr/bin/env python3
"""
verify_no_legacy_nbt.py
-----------------------
Scans all Java source files in the Minecraft mod project for banned pre-1.21
legacy ItemStack NBT methods:
  - ItemStack#getTag()
  - ItemStack#setTag()
  - ItemStack#getOrCreateTag()
  - ItemStack#getOrCreateSubTag()
  - ItemStack#getSubTag()

Includes tokenizer-aware comment and string-literal stripping to eliminate false positives.
Exit Code 0: Clean (zero violations).
Exit Code 1: Violations detected.
"""

import os
import re
import sys
from pathlib import Path

# Ensure UTF-8 output on Windows consoles
if sys.stdout.encoding != 'utf-8':
    try:
        sys.stdout.reconfigure(encoding='utf-8')
    except Exception:
        pass

BANNED_PATTERN = re.compile(r'(\.|\b)(getTag|setTag|getOrCreateTag|getOrCreateSubTag|getSubTag)\s*\(')
STRING_LITERAL_PATTERN = re.compile(r'"(?:\\.|[^"\\])*"')
SINGLE_LINE_COMMENT = re.compile(r'//.*$')

def scan_file(file_path: Path):
    violations = []
    with open(file_path, 'r', encoding='utf-8', errors='replace') as f:
        lines = f.readlines()

    in_block_comment = False
    for idx, raw_line in enumerate(lines, 1):
        line = raw_line

        # Handle multi-line block comments
        if in_block_comment:
            if '*/' in line:
                line = line[line.index('*/') + 2:]
                in_block_comment = False
            else:
                continue

        while '/*' in line:
            start_idx = line.index('/*')
            end_idx = line.find('*/', start_idx + 2)
            if end_idx != -1:
                line = line[:start_idx] + line[end_idx + 2:]
            else:
                line = line[:start_idx]
                in_block_comment = True
                break

        # Strip single line comments and string literals
        line = SINGLE_LINE_COMMENT.sub('', line)
        line = STRING_LITERAL_PATTERN.sub('""', line)

        match = BANNED_PATTERN.search(line)
        if match:
            violations.append({
                'line': idx,
                'method': match.group(2),
                'snippet': raw_line.strip()
            })

    return violations

def main():
    root_dir = Path(__file__).resolve().parent.parent
    src_dir = root_dir / "src"

    if not src_dir.exists():
        print(f"[ERROR] Source directory not found: {src_dir}")
        sys.exit(1)

    java_files = list(src_dir.rglob("*.java"))
    total_files = len(java_files)
    total_violations = 0

    print(f">> Starting Anti-Legacy-NBT Audit across {total_files} Java source files in {src_dir}...")

    all_violations = []
    for jf in sorted(java_files):
        v = scan_file(jf)
        if v:
            all_violations.append((jf, v))
            total_violations += len(v)

    if total_violations > 0:
        print("\n" + "=" * 80)
        print(f"[FAIL] AUDIT FAILED: {total_violations} legacy NBT calls detected in {len(all_violations)} files!")
        print("Minecraft 1.21 permanently replaced ItemStack NBT tags with DataComponentType<T>.")
        print("-" * 80)
        for path, violations in all_violations:
            rel_path = path.relative_to(root_dir)
            for item in violations:
                print(f"  * {rel_path}:{item['line']} [{item['method']}()] -> {item['snippet']}")
        print("=" * 80 + "\n")
        sys.exit(1)
    else:
        print(f"[PASS] AUDIT PASSED: 0 occurrences of getTag/setTag/getOrCreateTag across {total_files} Java files.\n")
        sys.exit(0)

if __name__ == '__main__':
    main()
