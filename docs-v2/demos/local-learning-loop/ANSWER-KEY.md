# The answer key — what the four turns must produce

This file is **not** loaded into the application. It is the owner's check
against the run: what the workspace is expected to say, which evidence it is
expected to cite, and which state it is expected to reach. The numbers below
are the ones measured on the run of 2026-11-07 (packaged jar, `matrix-core/build/quarkus-app/quarkus-run.jar`,
`http://127.0.0.1:9091`, workspace `plate-tectonics`).

## Turn 1 — initial knowledge

The answer is composed from the seed corpus only: 2 sources, 153 claims cited,
`evidenceCoverage` 0.458, `scopeMatch MATCHES`, no abstention. The three
boundary types and the idea of plate tectonics must be present, each with the
source it came from (USGS, *This Dynamic Earth*; Wikipedia, *Plate tectonics*,
oldid 1381934246).

**Not acceptable**: an answer that reads like prose without sources, or an
answer that mentions a document the workspace never imported.

## Turn 2 — the gap and the acquisition

The cycle must report the terms of the question it could not cover and the
documents it acquired. Measured: 3 documents of the pre-cached pool
(`wikipedia-continental-drift.md`, `wikipedia-mid-ocean-ridge.md`,
`wikipedia-subduction.md`), 324 claims added, every step carrying its
licence (CC BY-SA 4.0), its source URL, its `oldid` revision and its access
date. The report must state the boundary: a pre-cached, versioned and licensed
pool that ships with the application, no request leaves the machine.

The follow-up question is then answered from 5 sources / 28 claims with
`evidenceCoverage` 0.301 and one reported gap.

**Not acceptable**: a cycle that reports "live web search" (there is none), or
a cycle that silently changes the workspace without a report.

## Turn 3 — the false premise

The verdict must be **CONTRADICTED**, not "unsupported" and not "supported".
The disputed premise must be named, and the contradiction must point at the
claim that carries it and at the source that claim came from:

> "When initially proposed as a scientific theory in the early 20th century, the
> idea that continents could move was widely rejected."
> — `wikipedia-continental-drift.md` (CC BY-SA 4.0, oldid 1376987312)

The report must also say that the assertion was **not promoted**: no claim, no
evidence and no status was created by the check. The summary must read "1
contradicted".

**Not acceptable**: storing the user's statement as a claim, or reporting the
absence of evidence as a contradiction (absence is not contradiction — the
verdict `UNSUPPORTED` says that, and it is a different answer).

## Turn 4 — the revision

The acquisition of `wikipedia-slab-pull.md` adds 14 claims and applies
**exactly one** supersession, with relatedness 0.83. The reason must say that
the new evidence is about the same subject and denies what the earlier claim
asserted. The state after the turn:

- the earlier claim is **SUPERSEDED**, version 2 — it is kept in the history,
  not deleted;
- the new claim ("However, most scientists working today believe that the
  asthenosphere does not directly cause motion by the friction of such basal
  forces.") is **SUPPORTED**, version 2;
- the answer to "What drives the motion of the tectonic plates?" changes from
  118 claims of 5 sources to 128 claims of 7 sources, and now carries the
  slab-pull claims ("This force and slab suction account for almost all of
  the force driving plate tectonics.", "The ridge push at rifts contributes
  only 5 to 10%.").

**Not acceptable**: a report that says "the new document was added" without a
supersession, or a revision that deletes the earlier claim.

## After the four turns — persistence

After stopping and starting the application, the database is still there
(schema version 19), the six sources and the 687 claims are still there, the
superseded claim is still there with its status and its version, and the
answer is the same as before the restart.

## What the run does not prove

- The false-premise check is **lexical**, not semantic: it matches terms, so a
  truncated premise can read as contradicted by any claim that covers its
  terms. It is a deterministic check, and it says so.
- The support value of an answer is **0.0**, because an imported claim carries
  no authored confidence. The signal is in `evidenceCoverage`, the claim count
  and the source count. The confidence scale of the answering path is not
  calibrated; see the wave report.
- The acquisition is a **pre-cached pool**, not a live web search.
