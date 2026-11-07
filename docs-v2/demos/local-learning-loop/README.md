# The local learning loop

A locally runnable MATRIX instance that learns a real subject domain from real
licensed sources, answers from its own knowledge base, refuses a false premise,
and revises its model when new evidence arrives.

## Prerequisites

- JDK 25 (`JAVA_HOME` must point at it)
- no Docker, no native image, no model weights, no network access

## The one command

```bash
./gradlew :matrix-core:quarkusDev
```

The Workbench is then at <http://localhost:8080/workbench/>.

## The workspace

The demonstration uses the workspace `plate-tectonics`. It is not loaded
automatically; load it once with

```bash
curl -X POST "http://localhost:8080/v1/learning/workspaces/plate-tectonics/seed"
```

The seed corpus is the licensed corpus that ships with the application:

| source | licence | snapshot |
|---|---|---|
| USGS, *This Dynamic Earth* | US public domain | 2026-11-07 |
| Wikipedia, *Plate tectonics* | CC BY-SA 4.0 | oldid 1381934246 |

To reset the demonstration, stop the application, delete the database file and
start it again. The database lives beside the application state; the Workbench
status panel shows the path.

## Asking, learning and revising

- **Ask** — `POST /v1/workbench/ask` (the Workbench *Ask* panel).
- **Learn / research** — `POST /v1/learning/workspaces/{id}/learn?question=...`
  runs one bounded acquisition cycle over the research pool and returns a
  report: the uncovered terms, the documents considered, the documents
  acquired, their licences and their provenance, and what was skipped.
- **New evidence** — `POST /v1/learning/workspaces/{id}/revise?file=...`
  acquires one document of the research pool, compares the new claims with the
  existing model and, when the new evidence is stronger, supersedes the older
  claim and keeps it in the history.

## The acquisition boundary

The research pool is a versioned, licensed set of documents that ships with the
application and is listed in a manifest with the licence, the licence URL, the
canonical URL, the access date, the snapshot revision and the sha256 of the
imported bytes. **No request leaves the machine.** The learning cycle takes at
most three documents per question, and the report says so.

## After the demonstration

Stop the application with Ctrl-C, start it again and check that the acquired
sources, the revised model and the revision history are still there.
