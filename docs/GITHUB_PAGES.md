# MATRIX GitHub Pages — bilingual documentation

This workflow builds and deploys the bilingual (English + Russian) GitHub Pages site for the MATRIX project.

## Triggers

- **Push to `main`** (when `docs/index*.html`, `docs/index*.md` change) → build + deploy
- **Manual** via `workflow_dispatch`

## What it deploys

- `docs/index.html` (Russian, primary)
- `docs/index.en.html` (English)
- `docs/sandbox.html` (existing sandbox)
- `docs/index.en.md` (English README)
- `docs/architecture-knowledge-graph.excalidraw` (architecture diagram)

## Setup

In GitHub repository settings → Pages:
1. Source: **GitHub Actions**
2. Branch: `gh-pages` (auto-created by this workflow)
3. Custom domain: optional

## Workflow file

See `.github/workflows/pages.yml` in the repository root.

## Local preview

```bash
# Serve docs locally
cd docs && python3 -m http.server 8000
# Open http://localhost:8000
```

## Bilingual navigation

The English page (`index.en.html`) and Russian page (`index.html`) have a language switcher in the nav bar.

## License

Documentation content: CC-BY-SA-4.0
Code: GNU AGPLv3 (see LICENSE)