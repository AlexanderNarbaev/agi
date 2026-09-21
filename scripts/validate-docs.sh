#!/bin/bash
# scripts/validate-docs.sh — Validates Jekyll build for Liquid errors
# Usage: ./scripts/validate-docs.sh

set -e

echo "=== Documentation Validation ==="
echo ""

# Check for required files
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
    echo "  ✅ $f"
  else
    echo "  ❌ Missing: $f"
    missing=$((missing + 1))
  fi
done

echo ""

# Check for Liquid syntax issues in published docs
echo "Checking for Liquid syntax issues in published docs..."
liquid_errors=$(grep -rn "{{" docs-v2/ --include="*.md" 2>/dev/null | grep -v "_raw/" | grep -v "_drafts/" | wc -l)
if [ "$liquid_errors" -gt 0 ]; then
  echo "  ❌ Found $liquid_errors potential Liquid syntax issues"
  grep -rn "{{" docs-v2/ --include="*.md" 2>/dev/null | grep -v "_raw/" | grep -v "_drafts/" | head -5
else
  echo "  ✅ No Liquid syntax issues found in published docs"
fi

echo ""

# Check for required directories
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
    echo "  ✅ $d ($count markdown files)"
  else
    echo "  ❌ Missing directory: $d"
  fi
done

echo ""

# Check for Mermaid diagrams
echo "Checking for Mermaid diagrams..."
mermaid_count=$(grep -r "```mermaid" docs-v2/ --include="*.md" 2>/dev/null | wc -l)
echo "  📊 Found $mermaid_count Mermaid diagrams"

echo ""

# Check for LaTeX math
echo "Checking for LaTeX math..."
latex_count=$(grep -r '\$.*\$' docs-v2/ --include="*.md" 2>/dev/null | grep -v "_raw/" | wc -l)
echo "  📊 Found $latex_count files with LaTeX math"

echo ""

# Check for Russian translations
echo "Checking Russian translations..."
if [ -d "docs-v2/ru" ]; then
  ru_count=$(find docs-v2/ru -name "*.md" | wc -l)
  echo "  📊 Found $ru_count Russian translations"
else
  echo "  ⚠️  No Russian translations found"
fi

echo ""

# Summary
echo "=== Validation Summary ==="
if [ $missing -eq 0 ] && [ $liquid_errors -eq 0 ]; then
  echo "✅ All checks passed!"
  exit 0
else
  echo "❌ Some checks failed"
  exit 1
fi
