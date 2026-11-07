# The local learning loop

A locally runnable MATRIX instance that learns a real subject domain from real
licensed sources, answers from its own knowledge base, refuses a false premise,
and revises its model when new evidence arrives.

## Prerequisites

- JDK 25 (`JAVA_HOME` must point at it)
- no Docker, no native image, no model weights, no network access

## The one command

```bash
./scripts/run-local.sh
```

The script builds the application the first time it runs and starts it after
that. The Workbench is then at <http://127.0.0.1:9091/workbench/>. The first
start seeds the store and takes about two minutes; the log says so.

The run can be stopped with Ctrl-C. To start it again, run the same command.

## The workspace

The demonstration uses the workspace `plate-tectonics`. It is not loaded
automatically; press **Load the seed corpus** in the **Learn / research** panel
once, or run

```bash
curl -X POST "http://127.0.0.1:9091/v1/learning/workspaces/plate-tectonics/seed"
```

The seed corpus is the licensed corpus that ships with the application:

| source | licence | snapshot |
|---|---|---|
| USGS, *This Dynamic Earth* | US public domain | 2026-11-07 |
| Wikipedia, *Plate tectonics* | CC BY-SA 4.0 | oldid 1381934246 |

To reset the demonstration, stop the application, delete
`data/knowledge/workbench.sqlite` and start it again.

## Asking, learning, checking and revising

- **Ask** — `POST /v1/workbench/workspaces/{id}/ask` (the Workbench *Ask*
  panel). The answer names the claims it is built from, the sources behind
  them, the support score and the limits of the answer.
- **Learn / research** — `POST /v1/learning/workspaces/{id}/learn?question=...`
  runs one bounded acquisition cycle over the research pool and returns a
  report: the terms of the question the workspace could not cover, the
  documents considered, the documents acquired, their licences and their
  provenance, and what was skipped.
- **Check a premise** — `POST /v1/learning/workspaces/{id}/assess` with
  `{"assertion": "..."}` checks a statement against the evidence the workspace
  holds and returns a verdict (supported, contradicted, unsupported or
  unresolved) with the disputed premise and the contradicting claim and its
  source. The check never writes: an assertion is an input to evaluate, not a
  confirmation.
- **New evidence** — `POST /v1/learning/workspaces/{id}/revise?file=...`
  acquires one document of the research pool, compares the new claims with the
  existing model and, when the new evidence is about the same subject and
  denies what the earlier claim asserted, supersedes the older claim and
  keeps it in the history.

## The acquisition boundary

The research pool is a versioned, licensed set of documents that ships with the
application and is listed in a manifest with the licence, the licence URL, the
canonical URL, the access date, the snapshot revision and the sha256 of the
imported bytes. **No request leaves the machine.** The learning cycle takes at
most three documents per question, and the report says so.

## The four turns

The prompts, one at a time, are in [OWNER-SEQUENCE.md](OWNER-SEQUENCE.md). The
expected answers, the expected evidence and the expected state transitions are
in [ANSWER-KEY.md](ANSWER-KEY.md), which is deliberately kept outside the
application.

## After the demonstration

Stop the application with Ctrl-C, start it again and check that the acquired
sources, the revised model and the revision history are still there.
