#!/bin/bash
# Check active doc directories for file count
# warn > 15 .md files, error > 25
set -e
errors=0
warnings=0
for dir in docs/*/; do
    case "$dir" in
        docs/archive/*|docs/themes_engine_next/*) continue ;;
    esac
    count=$(find "$dir" -maxdepth 2 -name "*.md" | wc -l)
    if [ $count -gt 25 ]; then
        echo "ERROR: $dir has $count .md files (>25, needs archiving)"
        errors=$((errors + 1))
    elif [ $count -gt 15 ]; then
        echo "WARN: $dir has $count .md files (>15, consider archiving)"
        warnings=$((warnings + 1))
    fi
done
if [ $errors -gt 0 ]; then
    echo "check-doc-dir-size: $errors error(s), $warnings warning(s)"
    exit 1
fi
echo "check-doc-dir-size: PASSED (0 errors, $warnings warnings)"
