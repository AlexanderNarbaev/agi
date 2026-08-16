# Discord Server Setup Guide

## Server Structure

```
MATRIX AI — Discord Server
├── 📢 ANNOUNCEMENTS
│   ├── #announcements     — Release notes, milestones, events
│   ├── #blog              — Auto-post from docs/blog/
│   └── #media             — Videos, talks, press mentions
│
├── 💬 COMMUNITY
│   ├── #general           — General discussion
│   ├── #introductions     — New members say hello
│   ├── #showcase          — What are you building with MATRIX?
│   └── #off-topic         — Anything goes (within CoC)
│
├── 🛠️ DEVELOPMENT
│   ├── #core-dev          — Core engine (MPDT, evolution, clusters)
│   ├── #pilots            — Pilot projects (GridWorld, ChatBot, ESP32)
│   ├── #infra             — K8s, Docker, CI/CD
│   ├── #ethics            — Ethical filter, safety, governance
│   └── #beginners         — No question too basic
│
├── 🌍 INTERNATIONAL
│   ├── #russian           — Русскоязычное обсуждение
│   ├── #chinese           — 中文讨论
│   ├── #spanish           — Discusión en español
│   └── #arabic            — مناقشة بالعربية
│
├── 📚 RESEARCH
│   ├── #papers            — Academic papers, preprints, discussion
│   ├── #open-problems     — Research directions (OPEN_PROBLEMS.md)
│   └── #collaborations    — Find research partners
│
└── 🔧 META
    ├── #github-feed       — Auto-post GitHub activity
    ├── #moderation        — Report issues, discuss moderation
    └── #server-meta       — Server improvements, bot config
```

## Roles

| Role | Permissions |
|------|------------|
| @Admin | Full server management |
| @Moderator | Kick, ban, delete messages, timeout |
| @Core Team | Access to #core-dev, manage GitHub integrations |
| @Contributor | Access to #development channels, special color |
| @Translator | Access to international channels management |
| @Researcher | Access to #research channels |
| @Community | Default role, access to public channels |

## Bots

| Bot | Purpose |
|-----|---------|
| **GitHub Bot** | Post commits, PRs, issues to #github-feed |
| **Weblate Bot** | Translation status updates |
| **Welcome Bot** | Greet new members, link to CONTRIBUTING |
| **ModMail Bot** | Private moderation tickets |

## Moderation

- All moderators follow the CODE_OF_CONDUCT.md
- Three-strike system: warning → 24h timeout → ban
- Immediate ban for: hate speech, spam, Three Prohibitions violations
- Appeals: open a ModMail ticket

## How to join

1. Discord invite link: *(to be created after server setup)*
2. Read #rules-and-info
3. Introduce yourself in #introductions
4. Pick your role in #roles

## Alternative: Matrix

For those who prefer open protocols:
- Matrix room: `#matrix-ai:matrix.org` *(to be created)*
- Bridged to Discord for seamless communication
