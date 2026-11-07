# W50 — status

**Wave:** W50 — MATRIX local evidence-grounded demonstrator.
**State:** in progress. This file records what is built and measured, and what is
not built yet. It is written before the wave closes, so nothing here may be read
as a final claim.

## What exists now

### The corpus (W50-02)

| corpus | documents | licence | snapshot |
|---|---|---|---|
| seed | 2 | USGS *This Dynamic Earth* (US public domain), Wikipedia *Plate tectonics* (CC BY-SA 4.0) | 2026-11-07, oldid 1381934246 |
| research pool | 7 | Wikipedia, CC BY-SA 4.0 | per-file `oldid` in the manifest |

Every file is listed in a manifest with the sha256 of the imported bytes, the
licence, the licence URL, the canonical URL, the access date, the revision and
the purpose. The pool is the acquisition catalogue; it ships with the
application and no request leaves the machine.

Sources with an unclear or incompatible licence were rejected rather than
included (National Geographic Education). NOAA pages were rejected because the
article text is injected by script and cannot be extracted reproducibly.

### The learning path (W50-04)

- `acquisition/ClasspathCorpus` — reads a corpus out of the classpath, verifies
  it against the manifest, and keeps the provenance of every file.
- `acquisition/AcquisitionCycle` — the bounded cycle: the uncovered terms of
  the question, the declared catalogue, at most three documents, the ordinary
  import and validation path, and a report of what was acquired, skipped and
  entered.
- `ModelRevisionDecider` — decides whether new evidence revises the model:
  relatedness of the propositions, and strength as the authored confidence, the
  number of independent sources, the authority and the freshness. A weaker
  source never supersedes a stronger one. The rule is topic-agnostic; there is
  no branch for the demonstration subject.
- `acquireAndRevise` — acquires one document, compares its claims with the
  claims that existed before the import, and supersedes through the canonical
  `KnowledgeRefiner`, which keeps the earlier claim and the revision history.
- `LearningCycleResource` — `/v1/learning/catalogue`, `/seed`, `/learn`,
  `/revise`.

### The interface

The Workbench has a **Learn** tab with the learning cycle, the seed loader and
the new-evidence comparison, and reads the workspace from the URL
(`/workbench/?workspace=plate-tectonics`).

### The demonstration

`docs-v2/demos/local-learning-loop/` holds the setup (`README.md`), the four
prompts (`OWNER-SEQUENCE.md`) and the answer key (`ANSWER-KEY.md`). The answer
key is not loaded into the workspace.

## What is measured

- `:matrix-core:test --tests "io.matrix.knowledge.acquisition.*"` — 55 tests,
  0 failures (JUnit XML under `matrix-core/build/test-results/test/`).
- `:matrix-core:compileJava` — clean.
- The Workbench script block passes `node --check`.

## What is NOT done yet

- **The application has not been started and driven through the four turns.**
  The routes and the panels are implemented; the run itself has not happened.
- **No local model was evaluated** (W50-03). No hardware probe, no
  llama.cpp measurement, no extraction comparison against the deterministic
  baseline. The answering path in the repository makes no model call; that is a
  property of the code, not a measured proof under a blocked network.
- **No evaluation dataset and no measured report** (W50-06, W50-07).
- **The canonical registers are not refreshed**: `PROJECT-STATE.md`, `ROADMAP`,
  `OPEN-QUESTIONS`, `RISK-REGISTER`, `DECISION-REGISTER`,
  `CAPABILITY-REGISTRY`, `TEST-CATALOGUE`.
- **No release pull request** (W50-08).

## Open owner questions

- **Q-W50-01** — the corpus ships CC BY-SA 4.0 extracts from Wikipedia inside
  an AGPL-3.0 repository. The extracts are unmodified, so no share-alike
  obligation is triggered by the text itself, but the interaction between the
  two licences has not been signed off by the owner.
- **Q-W50-02** — the import still records the processing note rather than the
  extracted span in `Source.excerpt`, and the corpus trace reports the claim
  scope rather than the segment location. Both are audit defects inherited from
  the corpus foundation; neither is fixed in this slice.
