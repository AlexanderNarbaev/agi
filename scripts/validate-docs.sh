#!/bin/bash
# scripts/validate-docs.sh - Validates Jekyll build for Liquid errors

set -e

echo "=== Documentation Validation ==="
echo ""

echo "Checking required files..."
required_files=(
  "docs-v2/_config.yml"
  "docs-v2/SUMMARY.md"
  "docs-v2/STYLE-GUIDE.md"
  ".github/workflows/pages.yml"
)

missing=0
for f in "${required_files[@]}"; do
  if [ -f "$f" ]; then
    echo "  OK $f"
  else
    echo "  MISSING $f"
    missing=$((missing + 1))
  fi
done

echo ""

echo "Checking for Liquid syntax issues in published docs..."
liquid_errors=$(grep -rn "{{" docs-v2/ --include="*.md" 2>/dev/null | grep -v "_raw/" | grep -v "_drafts/" | wc -l)
if [ "$liquid_errors" -gt 0 ]; then
  echo "  ISSUES: Found $liquid_errors potential Liquid syntax issues"
else
  echo "  OK: No Liquid syntax issues found in published docs"
fi

echo ""

echo "Checking required directories..."
required_dirs=(
  "docs-v2/story"
  "docs-v2/guide"
  "docs-v2/science-v2"
  "docs-v2/archive"
  "docs-v2/templates"
)

for d in "${required_dirs[@]}"; do
  if [ -d "$d" ]; then
    count=$(find "$d" -name "*.md" | wc -l)
    echo "  OK $d ($count markdown files)"
  else
    echo "  MISSING directory $d"
  fi
done

echo ""

echo "Checking for Mermaid diagrams..."
mermaid_count=$(grep -r 'mermaid' docs-v2/ --include="*.md" 2>/dev/null | wc -l)
echo "  Found $mermaid_count Mermaid code blocks"

echo ""

echo "Checking for Russian translations..."
if [ -d "docs-v2/ru" ]; then
  ru_count=$(find docs-v2/ru -name "*.md" | wc -l)
  echo "  OK: Found $ru_count Russian translations"
else
  echo "  WARNING: No Russian translations found"
fi

echo ""

echo "=== Validation Summary ==="
if [ $missing -eq 0 ] && [ $liquid_errors -eq 0 ]; then
  echo "OK All checks passed!"
  exit 0
else
  echo "FAILED Some checks failed"
  exit 1
fi
