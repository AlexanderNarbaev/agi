# RFC — Explicit source revision applicability for Chain A

Status: proposed. Base revision `3fcbfdc10ed32d6c7a5bc8856f309a2b55f4babe`.
Requested approval: add the version 21→22 SQLite migration and implement,
exercise, and integrate it through synthetic databases and test CI. The
owner's live databases are outside this request. Deployment is separate.

## Contract and placement

DocumentImporter already gives each revision immutable content identity
within a workspace (DocumentImporter.java:462, 482). That identity omits
a logical document key. Same bytes may be observed through multiple
documents, and a reused filename does not establish lineage. The generic
KnowledgeRevision record has a single target, a kind and explanatory lists.
RevisionKind.SUPERSESSION is used for claim withdrawal. KnowledgeStore.java:2869
uses INSERT OR REPLACE for that record. Snapshot export preserves sources,
claims, evidence and generic revisions (KnowledgeStore.java:5166), but neither
the schema nor workspace API models an authoritative document membership or
a selected revision with an expected predecessor. Treating prose or record
times as machine-readable authorization would add an implicit, potentially
ambiguous shadow schema. Leave that ledger's meaning intact.

Use one additive relation in the existing SQLite store for workspace-local
document-to-source selection events. The caller supplies a stable logical
document key, a registered local source ID and its verified digest, and the
expected previous event ID. Append one immutable event with a parent event
and source ID. The first event explicitly registers V1. A unique root and
one successor per parent, checked in the existing write transaction and
guarded by a uniqueness constraint, make current selection the sole reachable
tip. Duplicate operation identities with identical arguments replay the
stored event. Mismatched arguments, missing or foreign sources, stale
predecessors and ambiguous ownership fail closed without a write. Never
derive selection from dates, filenames or claim truth status.

An applicability selector names CURRENT or an explicit historical event.
Both retain unrelated source observations. Until independent memberships can
be distinguished, reject ambiguous shared-content lineage registration rather
than silently excluding shared evidence. Keep old source records and links
resolvable. Preserve independent supporting evidence via its own source and
claim links even when a claim's introducing source stops applying. Imported
V2 claims retain existing validation status. A changed revision does not mark
a claim false.

## Consumers

Preserve a complete archival KnowledgeSnapshot and derive one scoped serving
view per request. Extend existing snapshot export/import and validation with
the event records and referential constraints so a round trip preserves policy.
The current report path starts in ArtifactReportResource.java:337, selects a
method and calls KnowledgeWorkbench.ask, which creates KnowledgeRetriever
and passes the same scoped snapshot to PremiseSemantics and PremiseAssessment.
Apply source filtering before retrieval and premise assessment and to
LearnedClaims overlays. An explicitly restricted method intersects its source
set with the same view. Make stale support ineligible at planner, forced
method execution, and ReportProcedureLearner validation. Keep prior
procedural records for audit. Revalidate against current sources explicitly.

Use a deterministic revision-view identity in WorkspaceFingerprint and the
served report replay check. Include the selected policy, event and revision
IDs, digests and relevant evidence identity. Use one pinned view for
reasoning and artifact verification. Refuse stale same-key reports when
recomputation changes their bytes and preserve historical artifacts. A new
request identity can create a fresh V2 artifact under existing writer rules.

## Migration, verification and rollback

Add a transactionally created table, constraints and version bump to the
existing KnowledgeStore migration chain. Default existing stores and older
snapshot payloads to an empty event list and their existing reasoning
behavior. Do not infer historical lineage during upgrade. Keep canonical
source and claim IDs. Rehearse migration from a synthetic existing v21
database, rollback after an injected failure, reopening after commit,
snapshot export/import and concurrent compare-and-set. No current answer or
cache may be stamped with an event selected after its evidence was read.

Feature rollback can stop creating selections and keep event records intact.
Returning a store with active selections to an older binary may silently
restore stale reasoning. Roll back the binary only together with a verified
schema-21 backup, retaining the newer store separately for audit and export.
An operational rollout, copy or restore of the owner's data needs its own
explicit decision. No destructive migration is proposed.

Use the separate evaluator's frozen two-workspace served fixture and all
ten requested controls. Preserve exact request/response, claim IDs, evidence
spans, content digests, event IDs and disposition, with one heavy Gradle run
at a time. Run the three repository migration fixtures for schema, knowledge
store and workspace snapshot. Include learned and forced procedure use,
restart and stale same-key replay. Only update the capability registry when
the controls, independent review and applicable CI pass.

### Control evidence required for integration

| Control | Observation required |
|---|---|
| Reimport identical bytes | One document observation identity and no new selection event |
| Reuse filename for changed bytes | Both sources retained and no implicit successor |
| Explicit V1 to V2 | A current selects V2 and A historical selects V1, both with independently resolved digests and spans. B still cites its own V1 |
| Independent corroboration | A separate applicable source remains citable when V1 is no longer selected |
| Cross-workspace identity | A rejects B's source ID and records no event |
| Missing or competing predecessor | Selection fails closed and the previous event remains the only tip |
| Interrupted write | Inject failure immediately before commit and verify no detached or partial event after reopening |
| Restart | The same current and historical views resolve to the same event IDs and source digests |
| Learned dependency | Automatic and forced invocation cannot use a procedure supported only by inactive V1. Explicit revalidation requires currently applicable support |
| Cached report | Revision-only view change invalidates replay even with identical claim rows. Preserve the prior artifact and require a fresh action identity when the served bytes change |

The evaluator owns expected tuples and fixture bytes. Only the contract and
public developer regressions go to the implementing role.
