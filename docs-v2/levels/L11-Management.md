# L11 — Cell Management, Snapshots, Governance

**Status:** normative · **Layer:** 11 (governance) · **Date:** 

## 1. Scope

L11 covers the stewardship of cells (instances), the rules for
snapshots and lineage, and how changes to specs and code are
ratified. Governance is decentralised and meritocratic; technical
specs are primary; code is derived.

## 2. Cell Lifecycle

A cell is a deployed Matrix instance. Stages: provision (Operator
creates CR, ServiceMonitor, log labels), initialise (Ethical FROZEN
seed loaded), train off-line (producer pipeline emits LUTs),
promote to STABLE, serve, suspend, decommission. State transitions
are recorded in the audit chain (L8) and the cell's lineage object.
No transition may alter or bypass the Ethical FROZEN FNL (L7).

## 3. Snapshots and Lineage

A snapshot is a versioned bundle of: instance configuration, FROZEN
seeds, STABLE LUTs for each FNL, lineage pointer to its parent
snapshot, audit digest. Snapshots are content-addressed. A snapshot
is published only after the Ethical Filter clears it and a quorum of
Mediators signs the digest. Imports run the same filter before
integration. Every imported neuron carries its provenance hash;
hidden provenance is rejected.

## 4. Roles

Observer, Contributor, Maintainer, Coordinator. Promotion is by
demonstrated, sustained contribution; demotion by inactivity
(> 6 months without notice) or by Code of Conduct ruling. No role
overrides the FROZEN prohibitions or grants authority over the
Ethical Filter.

## 5. Decision Tiers

| Tier | Scope | Quorum |
|---|---|---|
| Local | PR to a module | single module Maintainer |
| Module | public API change | module Maintainer consensus |
| Architectural | spec change L0–L10 | all Maintainers, RFC ≥ 2 weeks |
| Fundamental | L0 axioms or prohibitions | 90 % Maintainers + Coordinators, ≥ 1 month public comment |

If consensus stalls, a vote opens. Vote weight is proportional to
verified contribution over the trailing 12 months (Proof-of-
Accuracy); quorum is ≥ 50 % of Maintainers; passing threshold is
≥ 2/3 by weight.

## 6. RFC Procedure

Proposal opens as a GitHub Discussion or Issue with the `rfc`
label. The discussion window is set by tier. Objections are
recorded in writing. Maintainers seek an amendment that addresses
substantive objections. If no consensus is reached the proposal is
held for further study or moved to vote.

## 7. Code of Conduct

Five rules: respect; the three prohibitions apply to community
behaviour as well as code; inclusivity; criticism aimed at ideas;
confidentiality of personal data. Reports route to Coordinator
mailbox and chat moderators; response within 48 h; outcomes:
warning, temporary block, permanent removal.

## 8. Tools

Source of truth is the Git repository (mirror on GitLab for
redundancy). CI runs Checkstyle, SpotBugs, SonarQube, and the
deterministic test suite. Async on GitHub; sync on Matrix /
Discord plus a monthly call; announcements on a moderated mailing
list (archived).

## 9. Finance (when funds exist)

Sources: voluntary donations via Open Collective / GitHub Sponsors;
grants (NLNet, Prototype Fund, EU Horizon); paid hosting. Quarterly
public report. Spend decided by Coordinator consensus and approved
by Maintainers. Funding does not buy governance rights.

## 10. External Representation

Official channels: project site, Mastodon / X, talks at FOSDEM,
KubeCon, NeurIPS. Co-operation with other open AI efforts is
encouraged, provided the partner shares the three prohibitions.

## 11. Invariants

FROZEN components are outside normal PR scope; only the
architectural or fundamental tier may change them, and only
through an RFC. Every spec change cites the affected level and
the rationale; the change log is append-only.

Next: L12 pilots — how cells are first validated under load.