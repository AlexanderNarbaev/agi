## Verdict

**No. The existing durable `KnowledgeRevision` ledger is not sufficient for explicit workspace-local source-revision selection with atomic rollback and restart without schema migration.**

It is valuable as an append-only history of semantic changes, but it does not model:

- a versioned logical source;
- a selected revision for that source;
- a durable selection pointer or expected-selection version;
- an atomic commit between revision creation, selection change, and dependent output invalidation.

## Reuse versus typed storage migration

| Existing excerpt | Reuse capability | Limitation |
|---|---|---|
| `KnowledgeRevision` has `workspaceId`, `targetType`, `targetId`, kind, evidence, and validity changes | Can record that a source or claim changed | `targetType` is an unconstrained string; there is no typed source-revision entity |
| `revisions(workspaceId)` returns durable, ordered revisions | Supports reconstruction and audit | Ordering by `recordedAt, id` is not an explicit selection state or concurrency token |
| `insertRevision` uses `INSERT OR REPLACE` | Can persist revision records | Does not establish revision uniqueness, selection semantics, compare-and-set, or transactional coupling |
| `KnowledgeSnapshot` contains workspace content and revisions | Provides a self-contained, deterministic snapshot and claims atomic import | It does not preserve a named historical source selection unless that selection is reconstructible from ordinary source IDs |
| `ask(..., sourceIds)` restricts retrieval to source IDs | Supports ad hoc source filtering | The excerpts show no durable selected-revision state, restart restoration, or atomic rollback |
| Source ID is `workspaceId + contentHash`; claim ID also includes content hash | Stable content addressing and deduplication | It identifies **content**, not a logical source lineage or revision |

Reusing the ledger by encoding selections as specially targeted revisions would overload `targetType`, `targetId`, and `RevisionKind`. It would still lack a typed, unambiguous active-selection record and concurrency control. That is semantic emulation, not a sound durable model.

A typed storage migration is therefore the smallest honest model for the stated requirement: a workspace-scoped logical source, immutable source revisions/content hashes, and a durable selected-revision pointer changed atomically. Revision creation and pointer movement should commit together; rollback moves the pointer rather than deleting history.

## Required semantics

- **Logical identity vs content ID:** `sourceId(workspaceId, hash)` is content-addressed. A changed document becomes a different source ID; it does not express “revision 2 of source X.” Claim IDs are likewise content/proposition-specific.
- **Stable scope:** `workspaceId` provides an adequate scope primitive, but selection must also be explicitly workspace-local and must never resolve source IDs globally.
- **Concurrency:** Selection updates need an expected current selection or comparable optimistic-concurrency condition. The shown ledger query and `INSERT OR REPLACE` provide no such guarantee.
- **Snapshots:** Snapshots can preserve selected content if selection and referenced revisions are included in the same aggregate/transaction. The current snapshot has no explicit selection field.
- **Historical selection:** Filtering by current content IDs is not historical revision selection. Prior source content must remain addressable and selectable.
- **Outputs:** An answer or other output must independently record which selected revisions supported it. Later invalidation must invalidate that dependency, not merely append an unrelated ledger entry.

## Smallest testable architecture

1. One immutable, content-addressed revision per logical source.
2. One durable workspace-scoped selected-revision pointer.
3. One transaction that creates a revision, switches selection, and records invalidation dependencies.
4. Snapshot/export and restart that reconstruct the same selected state.
5. Outputs carrying explicit selected-revision provenance.
6. Concurrency tests proving one competing selection update cannot silently overwrite another.

## Strongest unsafe shortcut

**Treat source content hashes or specially tagged `KnowledgeRevision` rows as an active revision pointer.** This appears migration-free but makes selection ambiguous, permits multiple conflicting “selected” records, and cannot guarantee atomic rollback, restart restoration, or output invalidation.

## Escalation criterion

Escalate to typed storage migration when the requirement includes **two or more revisions of one logical source plus durable, atomic, restart-stable selection**. If those semantics cannot be represented unambiguously in the existing schema without overloading unrelated ledger fields, the existing ledger cannot safely satisfy the requirement.