#!/usr/bin/env python3
"""Check description coverage for all JSON rule files.

Scans every *.json file under feature/analysis/src/main/resources/rules/elements/
and reports:
  - Total number of attrType entries
  - How many have a "description" field
  - How many are missing one
  - Per-file breakdown of missing descriptions

Usage:
    python .opencode/skills/md-to-rule/check_coverage.py
"""

import os
import re

# Resolve paths relative to the project root (parent of .opencode/)
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.normpath(os.path.join(SCRIPT_DIR, "..", "..", ".."))
JSON_DIR = os.path.join(PROJECT_ROOT, "feature", "analysis", "src", "main", "resources", "rules", "elements")


def check_file(filepath):
    """Check a single JSON file. Return (total_attrs, with_desc, [(attr_name, ...)])."""
    with open(filepath, "r", encoding="utf-8") as f:
        lines = f.read().split("\n")

    in_attrtypes = False
    in_attr = False
    current_attr = None
    has_desc = False
    total = 0
    with_count = 0
    missing = []

    for line in lines:
        if '"attrTypes"' in line:
            in_attrtypes = True
            continue
        if in_attrtypes and not in_attr and line.strip() == "}" and not line.startswith("    "):
            in_attrtypes = False
            continue
        if in_attrtypes and not in_attr:
            m = re.match(r'^    "(\w+)"\s*:\s*\{', line)
            if m:
                in_attr = True
                current_attr = m.group(1)
                has_desc = False
                total += 1
            continue
        if in_attr:
            if '"description"' in line:
                has_desc = True
            if re.match(r'^    \}', line):
                in_attr = False
                if has_desc:
                    with_count += 1
                else:
                    missing.append(current_attr)

    return total, with_count, missing


def main():
    total_attrs = 0
    with_desc = 0
    without_desc = 0
    without_desc_files = []

    for root, dirs, files in sorted(os.walk(JSON_DIR)):
        for fn in sorted(files):
            if not fn.endswith(".json"):
                continue
            fp = os.path.join(root, fn)
            total, found, missing = check_file(fp)
            total_attrs += total
            with_desc += found
            without_desc += len(missing)
            if missing:
                rel = os.path.relpath(fp, PROJECT_ROOT).replace("\\", "/")
                without_desc_files.append((rel, missing))

    print(f"Total attrTypes: {total_attrs}")
    print(f"With description: {with_desc}")
    print(f"Without description: {without_desc}")
    if total_attrs > 0:
        pct = with_desc * 100 // total_attrs
        print(f"Coverage: {with_desc}/{total_attrs} = {pct}%")
    print()
    if without_desc_files:
        print("Files with missing descriptions:")
        for rel, attrs in without_desc_files:
            print(f"  {rel}: {', '.join(attrs)}")
    else:
        print("All attrTypes have descriptions!")


if __name__ == "__main__":
    main()
