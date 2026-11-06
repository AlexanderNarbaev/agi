---
name: Security Vulnerability
about: Report a security vulnerability
title: "[SECURITY] "
labels: security
assignees: ""
---

**There is currently no private reporting channel for this repository.**
GitHub's private vulnerability reporting is not enabled here, and this
project has no maintainer mailbox, no security mailbox and no chat. This
template used to point at a `SECURITY.md` that does not exist in the
repository — a pointer to nothing.

**So, if you have found a vulnerability, do this:**

1. **Do not describe the detail in a public issue.** A public issue is a
   public channel, and the disclosure is irreversible once it is written.
2. Open an issue with the title `[SECURITY] private channel requested` and
   **no technical content at all** — not the file, not the endpoint, not
   the reproduction. Say only that you have found something and that you
   are asking for a private channel.
3. The owner can then enable GitHub's private vulnerability reporting
   (repository settings → Security → Private vulnerability reporting) or
   arrange another private route, and you can disclose in that channel.

**What the project does say about its own security posture** — the boundary
is not a claim, it is a checked property:

- the gateway binds the loopback interface by default and refuses to start
  when it is exposed without a credential
  (`matrix-core/src/main/java/io/matrix/security/NetworkExposure.java`);
- the quality gate runs an anonymous-write refusal check on every clean
  runner (`scripts/quality-gate.sh`, the `security boundary` step);
- the frozen ethical layer is verified by model checking
  (`formal/`, `tools/tla2tools.jar`).

For a NON-critical concern that is safe to discuss in public — a
documentation error, a broken link, a usability problem — use the ordinary
bug report template instead.

The Code of Conduct is [`CODE_OF_CONDUCT`](CODE_OF_CONDUCT); the licence is
[`LICENSE`](LICENSE).
