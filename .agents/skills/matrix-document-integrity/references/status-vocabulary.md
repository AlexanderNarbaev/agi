# The status vocabulary

Eleven values. The policy is `docs-v2/governance/DOCUMENT-STATUS-POLICY.md`; this file is
the working form of it.

| the value | what it means | who may set it |
|---|---|---|
| `NORMATIVE` | the document **binds** the work: violating it violates the project | **the owner only** |
| `CANONICAL` | the document is **the** authority for its subject — the one place it is written down | the coordinator, by the owner's directive |
| `ACTIVE` | the document describes **current** work and is maintained | the coordinator |
| `REFERENCE` | the document is **consulted**, not maintained | the coordinator |
| `RESEARCH` | the document records **evidence and findings**, not a commitment | the coordinator |
| `EXPERIMENTAL` | the document describes something being tried, with a stated falsification condition | the coordinator |
| `HISTORICAL` | the document was **true at the time it was written** and records a past state | the coordinator |
| `SUPERSEDED` | the document was **replaced by another one, which is named** | the coordinator |
| `DEPRECATED` | the document is obsolete but still in use — on its way out | the coordinator |
| `ARCHIVED` | the document is **kept for the record** and is not part of the current structure | the coordinator |
| `UNVERIFIED` | the document makes claims that nobody has checked | the coordinator |

## The rule that catches the most

**A status is an explicit line, never a title.** A document called "Architecture" is not
`NORMATIVE`; a document whose status line says `NORMATIVE` binds the work whether or not its
title sounds important. Reading a title as a status is how a superseded document keeps
governing.

## The status block

The status is written in the first lines of the document, as an explicit line:

```
**Status:** CANONICAL · **Date:** 2026-11-04
```

or, in the older house form, `**Статус: normative**`. The checker reads the explicit line
and reports a status outside the vocabulary as a defect. **A status line that says nothing
is itself a defect**: the document has no established status.

## Who may change a status

- `NORMATIVE` — **the owner only**. A coordinator who wants a document to bind the work
  asks the owner.
- `SUPERSEDED` — the coordinator, and the successor **must be named**. A superseded
  document without a named successor is a defect, not a retirement.
- Everything else — the coordinator, with the reason recorded.
