#!/bin/bash
# Scan docs/**/*.md (except archive/ and themes_engine_next/), assert each has YAML frontmatter
# warn: missing frontmatter; error: no frontmatter
set -e
violations=0
for f in $(find docs -name "*.md" -not -path "docs/archive/*" -not -path "docs/themes_engine_next/*"); do
    if ! head -1 "$f" | grep -q "^---"; then
        echo "WARN: $f has no frontmatter"
        violations=$((violations + 1))
    fi
done
if [ $violations -gt 0 ]; then
    echo "check-frontmatter: $violations file(s) missing frontmatter"
    exit 1
fi
echo "check-frontmatter: PASSED (all files have frontmatter)"
