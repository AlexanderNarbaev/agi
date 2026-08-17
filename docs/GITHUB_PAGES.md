# MATRIX GitHub Pages — Bilingual Documentation Deployment

Deploys the bilingual (English + Russian) GitHub Pages site for MATRIX.

## What gets deployed

| File | Language | Purpose |
|------|----------|---------|
| `docs/index.html` | Russian (default) | Full landing page with BIR paradigm |
| `docs/index.en.html` | English | Full landing page with BIR paradigm |
| `docs/index.en.md` | English | README mirror (Markdown source) |
| `docs/sandbox.html` | — | MPDT interactive sandbox |
| `docs/architecture-knowledge-graph.excalidraw` | — | Architecture diagram |
| `docs/GITHUB_PAGES.md` | English | Pages documentation |

## Triggers

- **Push to `main`** when any of the above files change
- **Manual dispatch** via `workflow_dispatch`

## Setup

In GitHub repository settings → Pages:
1. Source: **GitHub Actions**
2. No branch selection needed (auto-deployed by this workflow)

## Local preview

```bash
cd docs && python3 -m http.server 8000
# Open http://localhost:8000
```

## Validation

The workflow validates:
- All required HTML files exist
- `lang` attribute is correctly set per file (`lang="ru"` or `lang="en"`)
- HTML files are well-formed (size check)

## Pages URL

After first deploy, the site is available at:
`https://<owner>.github.io/<repo>/`

For this project: `https://alexandernarbaev.github.io/agi/`

## License

- HTML content: CC-BY-SA-4.0
- Code: GNU AGPLv3 (see [`LICENSE`](../LICENSE))