# Translations — Многоязычная поддержка

## Current Status

| Язык | Код | Статус | Документы |
|------|-----|--------|-----------|
| English | en | ✅ Primary | Все (L0-L22, README, CONTRIBUTING) |
| Russian | ru | 🔄 В процессе | L0 (Манифест), README |
| Chinese | zh | ⬜ Planned | — |
| Spanish | es | ⬜ Planned | — |
| Arabic | ar | ⬜ Planned | — |
| French | fr | ⬜ Planned | — |
| German | de | ⬜ Planned | — |
| Portuguese | pt | ⬜ Planned | — |
| Japanese | ja | ⬜ Planned | — |

## How to contribute translations

### Option 1: Weblate (recommended)

1. Visit [hosted.weblate.org/projects/matrix-ai](https://hosted.weblate.org/projects/matrix-ai)
2. Create an account or sign in
3. Select your language and start translating
4. Translations are automatically submitted as pull requests

### Option 2: Direct PR

1. Fork the repository
2. Create `docs/L0_manifesto.<lang>.md` with your translation
3. Submit a pull request with label `translation`

### Priority documents for translation

1. `README.md` — first impression
2. `docs/L0_manifesto.md` — core philosophy
3. `CONTRIBUTING.md` — how to contribute
4. `docs/L1_MPDT_neuron.md` — technical foundation

### Translation guidelines

- Technical terms (MPDT, FNL, HADES, etc.) remain in English
- Code examples remain unchanged
- Keep the original meaning; don't simplify or embellish
- Respect the ethical tone of the original
- If in doubt, ask in GitHub Discussions `#translations`

### Acknowledgments

All translators are credited in `CONTRIBUTORS.md`. Significant translation 
contributions qualify for the "Translator" role in the community.

**Contact:** alexander@narbaev.com
