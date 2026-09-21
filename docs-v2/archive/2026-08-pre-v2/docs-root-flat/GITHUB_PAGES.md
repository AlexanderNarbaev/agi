# MATRIX GitHub Pages

Bilingual documentation site for MATRIX, deployed via GitHub Actions to `https://alexandernarbaev.github.io/agi/`.

## Pages

| Page | English | Russian | Description |
|------|---------|---------|-------------|
| Overview | `index.en.html` | `index.html` | BIR paradigm, principles, architecture, metrics, quick start |
| Documentation | `docs.en.html` | `docs.html` | Specs, designs, ADRs, research, training, papers, API |
| Algorithms | `algorithms.en.html` | `algorithms.html` | Deep dive into 10 algorithms with code, hypothesis, reproduction |
| Benchmarks | `benchmarks.en.html` | `benchmarks.html` | JMH methodology, raw numbers, reproduction instructions |
| Research | `research.en.html` | `research.html` | 34 hypotheses, papers, experiment protocols, training data |

Each page has:
- Sticky topbar with nav links + EN/RU language switcher
- Sticky sidebar TOC with section anchors
- Responsive layout (mobile: sidebar hidden)
- Deep linking via section IDs

## Triggers

- Push to `main` when any `docs/*.html` or `docs/*.md` file changes
- Manual dispatch via `workflow_dispatch`

## Setup

In GitHub repository settings → Pages:
1. Source: **GitHub Actions**
2. No branch selection needed

## Local preview

```bash
cd docs && python3 -m http.server 8000
# Open http://localhost:8000
```

## CI/CD

The workflow (`.github/workflows/pages.yml`) validates:
- All 11 bilingual pages exist
- Correct `lang` attribute per page (`lang="ru"` or `lang="en"`)
- Cross-links between EN and RU pages

## Content

All pages reflect the BIR (Boolean Intermediate Representation) paradigm:
- Three equivalent forms: TT, CLAUSESET, BDD
- FROZEN ethics layer (compile-time enforcement)
- 10 implemented algorithms with preregistered hypotheses
- 8 measured metrics (tests, coverage, latency, FPR/TPR)
- 8-layer C4 architecture

## License

- HTML content: CC-BY-SA-4.0
- Code: GNU AGPLv3 with Ethical Restrictions