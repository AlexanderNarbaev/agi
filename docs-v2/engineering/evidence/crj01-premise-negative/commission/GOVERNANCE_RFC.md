# RFC — Single-owner AI-assisted internal evaluation and bounded Git authority

Owner decision dated 2026-11-22 authorizes SINGLE_OWNER_AI_ASSISTED for internal
development review and evaluation. No independent human review is claimed.
The previous protocol remains pinned at 0549d27ee561afdf94214cbabe661c2532e08bae.
A versioned amendment may designate distinct AI implementer, evaluator and
freezing/review roles under the owner. Enforce isolated contexts and controlled
storage; keep hidden cases outside solver access until preregistered execution.
Freeze corpus, oracle, metric schema, solver SHA, budgets, seeds, order and
threshold before reveal. Publish model identities, roles, access attestations,
contamination risk and validation limits. Maintain the stronger evidence
requirements for any external scientific claim. Historical DEV is unchanged.

Proposed separate operation authorization, pending ratification: during this
milestone, allow commits and no-force pushes only on explicitly named
codex/crj-01-scoring-recovery and codex/crj-01-provenance branches, plus
draft PR creation targeting develop. Require protected CI and applicable
review before merge. No merge, force push, writes to protected branches, ruleset
change, tag, release, secret change or owner data operation. Record operation,
branch, commit and CI link. If CI or review cannot be satisfied, stop publication
at the boundary. This RFC does not itself grant standing Git authority.

Protocol integration: link the accepted owner decision in canonical governance,
retain the original protocol at its pinned revision, and mark the new internal
protocol NOT FROZEN until artifact hashes and access attestations exist. Keep
the adopted scientific thresholds fixed. The canonical documentation and CI
rules have independent review and publication paths.
