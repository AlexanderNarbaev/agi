# The MATRIX Learning Workbench — a first-run guide

**RECON-W40** · owner directive §18, §33, §34 · first release of the learning
application

A local application that acquires knowledge, answers questions from it with
its sources, assesses what was learned, and keeps everything across a
restart.

---

## 1. Start it

```
./gradlew :matrix-core:quarkusRun
```

Then open:

```
http://localhost:9091/workbench/
```

That is the whole procedure. There is **no** database server to install, **no**
model provider to configure, **no** API key, and **no** account. The
knowledge store is one SQLite file that the application creates for itself.

Requirements: a JDK (the build expects a JDK 25 at `~/jdk25` on this
machine) and the Gradle wrapper that is already in the repository. The
application starts in about a second and listens on port 9091; set
`QUARKUS_HTTP_PORT` to move it.

If the port is taken, the failure is visible in the log line that says
`Listening on: http://0.0.0.0:9091`.

## 2. Load the demonstration knowledge

Open the **Dashboard** tab and press **Load the demonstration knowledge
pack**.

The pack is the photosynthesis domain: 8 concepts, 17 claims, 4 sources,
18 evidence links, 13 representations across four levels of abstraction, and
one recorded revision. It ships inside the application, so the load needs no
network.

Every source in it was fetched and read before it was cited, and the
excerpts are recorded verbatim in
`matrix-core/src/main/resources/knowledge/photosynthesis.pack.json`:
a peer-reviewed review in *Faculty Reviews* (DOI 10.12703/b/9-5) and three
encyclopedia entries whose revision identifiers are in their locators. The
distinction is not cosmetic — a claim that rests on an encyclopedia entry
alone is marked `SUPPORTED`, not `VERIFIED`.

## 3. Ask it something

Go to the **Ask** tab. Type a question and choose a level:

| Level | Who it is for |
|---|---|
| Introductory | a reader with no background in biology |
| Intermediate | a reader who has finished an introductory course |
| Advanced | an undergraduate in plant physiology |
| Mechanistic | a researcher |

The same question gives a different account at each level. The answer shows
the claims it rests on, the sources behind those claims with their
locators, the limitations that apply, and a confidence.

Ask something the workspace does not know — *"What is the boiling point of
mercury?"* — and it will not answer. It says that no claim covers enough of
the question, and it names the terms it could not cover: `boil`, `point`,
`mercury`. That is the behaviour to look for. A system that never refuses is
one whose confidence cannot be read.

## 4. Test it

Go to the **Assess** tab. There are 22 items from a versioned dataset
(`photosynthesis-v1`). Each item carries the terms that decide whether an
answer passes, so the grading is transparent rather than a matter of
opinion. Half the items are **held out**: they may not be used while
learning, which is what makes the post-learning measurement independent of
the learning that produced it.

Answer an item, state how confident you are, and submit. The grade and the
resulting learning state appear at once.

## 5. Read the learning state

The **Progress** tab shows one row per capability per concept. There is no
single "intelligence percentage", because a concept can be recalled
accurately and still not be understood, and a skill can be operationally
competent while the theory behind it stays incomplete.

The stages are `UNKNOWN`, `EXPLORATION`, `INITIAL_LEARNING`, `PRACTICE`,
`COMPETENCE`, `UNDERSTANDING` and `KNOWLEDGE`. A stage is reached because
recorded outcomes met stated criteria — never because something declared
it. The note beside each row says how many outcomes it rests on, across
how many variants, and how many were held out.

If a later outcome contradicts an earlier success, the state **falls** and
the note says so. That is deliberate: keeping the old label would be the
easy thing to do and the dishonest one.

## 6. Stop it, start it again, and verify

This is the part that matters, so do it explicitly:

1. Press **Ctrl-C** in the terminal that is running the application.
2. Run `./gradlew :matrix-core:quarkusRun` again.
3. Open <http://localhost:9091/workbench/> again.
4. Go to the **Persistence** tab.

The counts shown there are read from the store on disk by the **new**
process: the concepts, the claims, the sources, the evidence links, the
representations, the revisions and the assessment outcomes. Nothing was
re-imported — the pack was not loaded a second time.

The store lives at `matrix-core/data/knowledge/workbench.sqlite`. It is one
file: copy it to back the workspace up, and put it back to restore it.

## 7. What this release does not do

- **It is not a chat assistant.** The answer is composed from the claims and
  representations in the store, not generated.
- **It does not search the internet.** External retrieval is an optional
  capability that is not implemented yet; the pack's sources were fetched
  and recorded by hand, with their revision identifiers.
- **The learning state is not a measurement of general intelligence.** It is
  a measurement of one domain against one dataset version, and the note on
  every row says which.
- **No stage above `COMPETENCE` has been reached yet.** `UNDERSTANDING` and
  `KNOWLEDGE` need their own evidence, and the criteria for them are
  deliberately stricter than for competence.
- **The taught capability is not demonstrated.** Machine-to-machine
  teach-back is the next milestone, not this one.

## 8. Where the pieces are

| Piece | Path |
|---|---|
| The records | `matrix-core/src/main/java/io/matrix/knowledge/` |
| The store | `KnowledgeStore.java` (SQLite, versioned schema, atomic imports) |
| The retriever | `KnowledgeRetriever.java`, `TextFeatures.java` |
| The measurement | `LearningStateEvaluator.java`, `AssessmentGrader.java` |
| The application | `KnowledgeWorkbench.java`, `LearningWorkbenchResource.java` |
| The interface | `matrix-core/src/main/resources/META-INF/resources/workbench/index.html` |
| The knowledge pack | `matrix-core/src/main/resources/knowledge/photosynthesis.pack.json` |
| The dataset | `matrix-core/src/main/resources/knowledge/photosynthesis.assessment.json` |
| The reconnaissance | `docs-v2/engineering/W40-RECONNAISSANCE.md` |

## 9. The API, if you would rather not use the browser

```
curl http://localhost:9091/v1/workbench/status
curl -X POST http://localhost:9091/v1/workbench/workspaces/photosynthesis/load
curl -X POST http://localhost:9091/v1/workbench/workspaces/photosynthesis/ask \
  -H 'Content-Type: application/json' \
  -d '{"question":"What is photosynthesis?","level":"MECHANISTIC"}'
curl http://localhost:9091/v1/workbench/workspaces/photosynthesis/learning-state
```
