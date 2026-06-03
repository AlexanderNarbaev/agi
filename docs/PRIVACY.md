# Privacy Policy (PRIVACY.md)

**Last updated:** June 3, 2026

## Scope

This privacy policy applies to the MATRIX project's:
- Public GitHub repository (github.com/AlexanderNarbaev/agi)
- Project website (GitHub Pages)
- MATRIX software itself (when run by users)

## What data we DO collect

### GitHub
- Public contributions (commits, issues, PRs) — as per GitHub's privacy policy
- No additional tracking, analytics, or cookies beyond what GitHub provides

### Project website
- **No cookies.** No analytics. No tracking scripts.
- Server logs (IP, timestamp, page) are not retained beyond 24 hours.
- The website is static HTML — no server-side processing.

### MATRIX software
- **No telemetry.** The software does not "phone home."
- No usage data is collected or transmitted.
- The Ethical Filter (L7) operates locally — no data leaves the instance.
- Docker Compose monitoring (Prometheus, Jaeger) runs locally — no cloud upload.

## What data we DO NOT collect

- No personal identification information
- No email addresses (beyond public GitHub profiles)
- No behavioral tracking
- No training data from users
- No conversation logs (unless explicitly configured by the user running their own instance)

## Data storage

- All project data (code, issues, discussions) is stored on GitHub's infrastructure
- The project does not operate independent servers for data collection
- User-run instances store data locally (the user controls their own data)

## GDPR compliance

- **Right to access:** All contributions are public. You can see everything you've contributed.
- **Right to erasure:** Request removal of personal data via GitHub issue or email.
- **Right to object:** No automated decision-making or profiling is performed.
- **Data portability:** All code and documentation is available via `git clone`.

## Contact

For privacy-related inquiries: alexander@narbaev.com

For GDPR requests: open a GitHub issue with label `privacy`.

## Changes

This policy may be updated. Changes will be committed to the repository with 
clear commit messages. Major changes will be announced via GitHub Discussions.
