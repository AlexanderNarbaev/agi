# The W50 evaluation set — plate tectonics

**Version:** 1.1 · **Date:** 2026-11-07 · **Wave:** W50 ·
**Domain:** plate tectonics (one bounded domain) ·
**Workspaces of the measured run:** `plate-tectonics-eval`,
`plate-tectonics-eval-seed`, `plate-tectonics-eval-neg`,
`plate-tectonics-eval-neg2`

**Version 1.1 — what changed and why.** Cases R-04 and R-05 were added after
R-02 had been measured and had failed. The expectation of both new cases
(no supersession) was fixed **before** they were run, and both failed as
well; the measurement of R-02 was not changed. The scope table of §2.2 and
the document table of §2.1 were extended to name the two pool documents
these two cases use. No other expectation was touched.

This file is **not** loaded into the application. It is the owner's check
against the run: what the workspace is expected to say, which evidence it is
expected to cite, and which state it is expected to reach. The application
never reads it, and the answers it holds are not written into it.

The measured counterpart is `docs-v2/engineering/W50-EVALUATION.md`; every
number in that report comes from an HTTP response recorded under
`/tmp/w50-eval/`.

## 1. What this dataset is, and what it is not

It is a **documented set of cases for one domain**, each case naming the
question or the assertion, the workspace state it is valid for, and the
behaviour that would make it pass. It is not a benchmark file, not a
cross-domain suite, and not a set of training data.

Three properties are deliberate and are stated up front, because they bound
what the measured numbers mean:

1. **The split is by source document, not by random sentences.** No sentence
   of any corpus document is split between the "training" and the "held-out"
   side. A document is either in the workspace or it is not, and every case
   names the documents that must be present for the case to be valid.
2. **The held-out part is the seed corpus.** The answerable questions of
   §4.1 have their answers in the two seed documents and nowhere else, and
   they are asked on a workspace that holds nothing but the seed corpus.
   A question that the pool documents could also answer is not in this
   family.
3. **The corpus is small and fixed.** Nine documents (two seed, seven pool),
   one bounded domain, a lexical retriever, a pre-cached and licensed pool.
   The numbers that this dataset produces describe this corpus. They are not
   evidence about any other subject, and they are not evidence about the
   system's behaviour on live text.

## 2. The split, by source document

### 2.1 The documents

| split | file | source | licence | revision | sha256 (first 12) | bytes |
|---|---|---|---|---|---|---|
| **S** | `usgs-this-dynamic-earth.md` | USGS, *This Dynamic Earth* | US public domain | snapshot 2026-11-07 | `a82d4ca7143a` | 17 144 |
| **S** | `wikipedia-plate-tectonics.md` | Wikipedia, *Plate tectonics* | CC BY-SA 4.0 | oldid 1381934246 | `db794272ec30` | 58 972 |
| **P** | `wikipedia-continental-drift.md` | Wikipedia, *Continental drift* | CC BY-SA 4.0 | oldid 1376987312 | `0c7724c9a5dc` | 19 426 |
| **P** | `wikipedia-mid-ocean-ridge.md` | Wikipedia, *Mid-ocean ridge* | CC BY-SA 4.0 | oldid 1383981232 | `0cb70b434112` | 17 922 |
| **P** | `wikipedia-subduction.md` | Wikipedia, *Subduction* | CC BY-SA 4.0 | oldid 1381148652 | `a44b13d8f873` | 29 731 |
| **R** | `wikipedia-slab-pull.md` | Wikipedia, *Slab pull* | CC BY-SA 4.0 | oldid 1379867088 | `3bddce4afba6` | 3 259 |
| **R** | `wikipedia-paleomagnetism.md` | Wikipedia, *Paleomagnetism* | CC BY-SA 4.0 | oldid 1381061770 | `af0b1cece2be` | 10 981 |
| **R** | `wikipedia-seafloor-spreading.md` | Wikipedia, *Seafloor spreading* | CC BY-SA 4.0 | oldid 1372521821 | `ebd0d04c772bf` | 15 697 |
| **R** | `wikipedia-mantle-convection.md` | Wikipedia, *Mantle convection* | CC BY-SA 4.0 | oldid 1382079218 | `7993295c188f` | 9 218 |

The seed documents are in `matrix-core/src/main/resources/corpus/plate-tectonics/`,
the pool documents in `matrix-core/src/main/resources/research-pool/plate-tectonics/`.
The hashes are the ones the manifests carry
(`corpus-manifest.json` in both directories) and that the importer verifies.

All seven pool documents are exercised: three by the bounded cycle
(continental drift, mid-ocean ridge, subduction) and four by the revision
path (slab pull, paleomagnetism, seafloor spreading, mantle convection). The
seven pool documents and the two seed documents are the whole corpus; no
other document takes part in any case.

### 2.2 The scopes

A case is valid in exactly one scope. The scopes are the workspace states,
and every scope is reachable with one or two calls.

| scope | the workspace | the workspace state | how it is reached |
|---|---|---|---|
| **S1** | `plate-tectonics-eval-seed` | the two seed documents, 348 claims, 2 sources, no pool document | `POST /v1/learning/workspaces/{id}/seed` |
| **P1** | `plate-tectonics-eval` | S1 plus the three documents the bounded cycle acquires (continental drift, mid-ocean ridge, subduction), 672 claims, 5 sources | `POST /v1/learning/workspaces/{id}/seed` then `POST /v1/learning/workspaces/{id}/learn?question=…&documents=3` |
| **R1** | `plate-tectonics-eval` | P1 plus `wikipedia-slab-pull.md`, acquired by the revision endpoint, 687 claims, 7 source records (6 documents) | `POST /v1/learning/workspaces/{id}/revise?file=wikipedia-slab-pull.md` |
| **R2** | `plate-tectonics-eval` | R1 plus `wikipedia-paleomagnetism.md`, acquired by the revision endpoint, 756 claims | `POST /v1/learning/workspaces/{id}/revise?file=wikipedia-paleomagnetism.md` |
| **P1′** | `plate-tectonics-eval-neg` | a fresh copy of P1 (seed, then the same cycle), 672 claims | `POST /v1/learning/workspaces/{id}/seed` then `POST /v1/learning/workspaces/{id}/learn?question=…&documents=3` |
| **P1″** | `plate-tectonics-eval-neg2` | a second fresh copy of P1, 672 claims | as above |

The claim and source counts of S1, P1 and R1 are the ones the application
itself reported (`/tmp/w50-eval/01-seed.json`, `/tmp/w50-eval/03-learn.json`,
`/tmp/w50-eval/30-revise-slab-pull.json`); they are written here so that a
reader can tell whether the workspace was in the expected state before a case
was asked.

**Why there are four workspaces.** The seed corpus and the pool are the two
halves of the split, and the acquisition cannot be undone: there is no route
that removes a document from a workspace, and re-seeding a workspace adds
nothing and removes nothing. A workspace that holds the pool can therefore
never be a scope-S1 workspace again. The seed scope was measured in a
workspace of its own, and the two negative-revision attempts of §4.7 were
each measured in their own fresh copy of P1, so that every attempt starts
from the same recorded state. None of the four workspaces is a demonstrator
workspace; the demonstrator's own `plate-tectonics` and `plate-tectonics-v2`
were not read, written or listed by this evaluation.

## 3. How a case is judged

Every case is judged against the **response**, not against a reading of the
answer. The checks are mechanical:

- **answerable** — `answered = true` in the response, the named source file
  is among `sources[].title`, and every proposition the case names is
  present **verbatim** in the `claims[].proposition` array. A different
  wording of the same fact does not pass: the claim must be the claim.
- **unanswerable** — `answered = false`, `statement` empty, `sources` empty.
  An invented answer fails, and so does an answer that admits the gap but
  still answers.
- **false premise** — the verdict of the finding is the verdict the case
  names, the disputed premise is named, the contradicting claim and its
  source are named, and `promoted = false`.
- **multi-claim** — as *answerable*, and in addition the number of distinct
  cited claims (and, where the case says so, the number of distinct cited
  sources) is at least the number the case requires. A question that needs
  two stored claims and gets one is a failure even when the one claim is
  correct.
- **knows what it does not know** — `answered = true`, the partial answer
  cites the claims the case names, **and** the gap list names the term the
  case says is missing. An answer that covers the topic and does not say
  what it does not cover fails.
- **revision** — the counts of the revision report, the status and the
  version of the claims before and after, and the reason the application
  gives.

The expectations below were written from the corpus documents and from the
workspace's own claim list **before** any evaluation question was asked. The
raw responses are in `/tmp/w50-eval/`.

## 4. The cases

### 4.1 Answerable — the held-out seed split (scope S1)

The answers live in the seed corpus only. Each case names the source file
and the propositions that must be cited.

#### A-01 — What is plate tectonics and what does it say about the Earth's lithosphere?

- **Split:** S · **Scope:** S1 · **Family:** answerable
- **Call:** `POST /v1/workbench/workspaces/{id}/ask` with
  `{"question":"What is plate tectonics and what does it say about the Earth's lithosphere?","level":"INTRODUCTORY"}`
- **Expectation:** answered; at least 2 distinct cited claims; the source
  `wikipedia-plate-tectonics.md` among the answer's sources; both
  propositions present verbatim among the cited claims:
  - "Plate tectonics (from Latin tectonicus, from Ancient Greek τεκτονικός (tektonikós) 'pertaining to building') is the scientific theory that Earth's lithosphere comprises a number of large tectonic plates, which have been slowly moving since 3–4 billion years ago."
  - "Earth's lithosphere, the rigid outer shell of the planet including the crust and upper mantle, is fractured into seven or eight major plates (depending on how they are defined) and many minor plates or \"platelets\"."

#### A-02 — What are the three types of plate boundaries and what happens at each of them?

- **Split:** S · **Scope:** S1 · **Family:** answerable
- **Call:** as above, with the question
  `What are the three types of plate boundaries and what happens at each of them?`
- **Expectation:** answered; at least 3 distinct cited claims; the source
  `wikipedia-plate-tectonics.md` among the answer's sources; these
  propositions present verbatim:
  - "Types of plate boundaries Three types of plate boundaries exist, characterized by the way the plates move relative to each other."
  - "Where the plates meet, their relative motion determines the type of plate boundary (or fault): convergent, divergent, or transform."
  - "Convergent boundaries (destructive boundaries or active margins) occur where two plates slide toward each other to form either a subduction zone (one plate moving underneath the other) or a continental collision."

#### A-03 — What drives the motion of the tectonic plates, and which force is considered the strongest?

- **Split:** S · **Scope:** S1 · **Family:** answerable
- **Call:** as above, with the question
  `What drives the motion of the tectonic plates, and which force is considered the strongest?`
- **Expectation:** answered; at least 3 distinct cited claims; the source
  `wikipedia-plate-tectonics.md` among the answer's sources; these
  propositions present verbatim:
  - "Driving forces of plate motion Tectonic plates are able to move because of the relative density of oceanic lithosphere and the relative weakness of the asthenosphere."
  - "The greater density of old lithosphere relative to the underlying asthenosphere allows it to sink into the deep mantle at subduction zones, providing most of the driving force for plate movement."
  - "In modern views, gravity is invoked as the major driving force, through slab pull along subduction zones."

#### A-04 — How is the rate of plate movement measured with GPS and other space-geodetic techniques?

- **Split:** S · **Scope:** S1 · **Family:** answerable
- **Call:** as above, with the question
  `How is the rate of plate movement measured with GPS and other space-geodetic techniques?`
- **Expectation:** answered; at least 3 distinct cited claims; the source
  `usgs-this-dynamic-earth.md` among the answer's sources; these
  propositions present verbatim:
  - "GPS Satellite and Ground Receiver [63 k] Current plate movement can be tracked directly by means of ground-based or space-based geodetic measurements; geodesy is the science of the size and shape of the Earth."
  - "However, because plate motions are global in scale, they are best measured by satellite-based methods."
  - "The three most commonly used space-geodetic techniques -- very long baseline interferometry (VLBI), satellite laser ranging (SLR), and the Global Positioning System (GPS) -- are based on technologies developed for military and aerospace research, notably radio astronomy and satellite tracking."

#### A-05 — What is seafloor spreading and what evidence supports it?

- **Split:** S · **Scope:** S1 · **Family:** answerable
- **Call:** as above, with the question
  `What is seafloor spreading and what evidence supports it?`
- **Expectation:** answered; at least 4 distinct cited claims; **both** seed
  sources among the answer's sources; these propositions present verbatim:
  - "Oceanic crust is formed at sea-floor spreading centers."
  - "The lost surface is balanced by the formation of new oceanic crust along divergent margins by seafloor spreading, keeping the total surface area constant in a tectonic \"conveyor belt\"."
  - "When these magnetic patterns were mapped over a wide region, the ocean floor showed a zebra-like pattern: one stripe with normal polarity and the adjoining stripe with reversed polarity."
  - "Seafloor spreading over the past 100 to 200 million years has caused the Atlantic Ocean to grow from a tiny inlet of water between the continents of Europe, Africa, and the Americas into the vast ocean that exists today."

#### A-06 — How does subduction at a convergent boundary produce deep earthquakes and volcanoes?

- **Split:** S · **Scope:** S1 · **Family:** answerable
- **Call:** as above, with the question
  `How does subduction at a convergent boundary produce deep earthquakes and volcanoes?`
- **Expectation:** answered; at least 3 distinct cited claims; the source
  `usgs-this-dynamic-earth.md` among the answer's sources; these
  propositions present verbatim:
  - "The descending plate also provides a source of stress as the two plates interact, leading to frequent moderate to strong earthquakes."
  - "Subduction processes in oceanic-oceanic plate convergence also result in the formation of volcanoes."
  - "Magmas that form island arcs are produced by the partial melting of the descending plate and/or the overlying oceanic lithosphere."

### 4.2 Answerable — the pool split (scope P1)

#### P-01 — What is a mid-ocean ridge and what happens at one?

- **Split:** P · **Scope:** P1 · **Family:** answerable
- **Call:** as in A-01, with the question
  `What is a mid-ocean ridge and what happens at one?`
- **Expectation:** answered; at least 3 distinct cited claims; the source
  `wikipedia-mid-ocean-ridge.md` among the answer's sources; these
  propositions present verbatim:
  - "This feature is where seafloor spreading takes place along a divergent plate boundary."
  - "It typically has a depth of about 2,600 meters (8,500 ft) and rises about 2,000 meters (6,600 ft) above the deepest portion of an ocean basin."
  - "In the 1960s, geologists discovered and began to propose mechanisms for seafloor spreading."

#### P-02 — What is subduction, and what does it produce?

- **Split:** P · **Scope:** P1 · **Family:** answerable
- **Call:** as in A-01, with the question
  `What is subduction, and what does it produce?`
- **Expectation:** answered; at least 3 distinct cited claims; the source
  `wikipedia-subduction.md` among the answer's sources; these propositions
  present verbatim:
  - "Oceanic trenches are the deepest parts of the ocean floor."
  - "Beyond the forearc basin, volcanoes are found in long chains called volcanic arcs."
  - "Since the Earth's circumference has not changed over geologic time, Hess concluded that older seafloor has to be consumed somewhere else, and suggested that this process takes place at oceanic trenches, where the crust would be melted and recycled into the Earth's mantle."

### 4.3 Unanswerable (scopes S1 and P1)

The expected behaviour is **abstention**: `answered = false`, an empty
`statement`, no source cited, and a reason that names the terms of the
question the workspace does not cover. A partial answer that invents the
missing part fails, and so does an answer that quietly answers from the
nearest claim it does hold.

#### U-01 — What is the boiling point of water at the summit of Mount Kilimanjaro in degrees Celsius?

- **Split:** S · **Scope:** S1 · **Family:** unanswerable
- **Expectation:** abstention; the workspace holds no claim about Kilimanjaro
  and none about the boiling point of water; the gap list names the terms it
  does not cover.

#### U-02 — What is the annual tourism revenue of Iceland in euros?

- **Split:** S · **Scope:** S1 · **Family:** unanswerable
- **Expectation:** abstention. The corpus mentions Iceland (the spreading
  centre, Krafla, Reykjavík) and nothing about tourism or revenue; the
  answer must not borrow the Iceland claims to answer a question about
  money.

#### U-03 — What is the probability of a magnitude 8 earthquake occurring in Istanbul within the next decade?

- **Split:** S · **Scope:** S1 · **Family:** unanswerable
- **Expectation:** abstention. The corpus holds earthquakes and magnitudes
  and nothing about Istanbul, probabilities or risk estimates.

#### U-04 — How many tonnes of basalt are produced each year by the Mid-Atlantic Ridge?

- **Split:** S · **Scope:** S1 · **Family:** unanswerable
- **Expectation:** abstention. **This is the near-miss case of the family:**
  the corpus holds the Mid-Atlantic Ridge and basalt, so the question is one
  lexical step away from the material it names. The workspace must still
  abstain, because the quantity asked for is in no claim.

#### U-05 — Which company announced the discovery of the exoplanet 55 Cancri e?

- **Split:** S · **Scope:** S1 · **Family:** unanswerable
- **Expectation:** abstention. The corpus mentions exoplanets and the
  conditions for plate tectonics on other planets, and nothing about the
  planet 55 Cancri e or about who announced what.

#### U-06 — How many research vessels are surveying the mid-ocean ridges at the moment?

- **Split:** P · **Scope:** P1 · **Family:** unanswerable
- **Expectation:** abstention. The pool holds the mid-ocean ridge documents
  and nothing about research vessels or how many there are.

### 4.4 False premise (scopes S1 and P1)

The verdict vocabulary is the application's own: `SUPPORTED`,
`CONTRADICTED`, `UNSUPPORTED`, `UNRESOLVED`. Every case of this family also
requires `promoted = false` and an unchanged claim count: the check must not
write. The disputed premise and the contradicting source must be **named**
in the response, not merely implied by the verdict.

#### F-01 — a true premise (expected verdict: SUPPORTED)

- **Split:** S · **Scope:** S1 · **Family:** false premise
- **Call:** `POST /v1/learning/workspaces/{id}/assess` with
  `{"assertion":"Plate tectonics is the scientific theory that Earth's lithosphere comprises a number of large tectonic plates."}`
- **Expectation:** verdict `SUPPORTED`; the evidence named is the claim
  "Plate tectonics (from Latin tectonicus, from Ancient Greek τεκτονικός (tektonikós) 'pertaining to building') is the scientific theory that Earth's lithosphere comprises a number of large tectonic plates, which have been slowly moving since 3–4 billion years ago."
  from `wikipedia-plate-tectonics.md`; `promoted = false`.

#### F-02 — a premise the corpus contradicts (expected verdict: CONTRADICTED)

- **Split:** S · **Scope:** S1 · **Family:** false premise
- **Call:** as above, with the assertion
  `Trenches are not created by subduction.`
- **Expectation:** verdict `CONTRADICTED`; the disputed premise is named;
  the contradicting claim is "Trenches are the deepest parts of the ocean
  floor and are created by subduction." from
  `usgs-this-dynamic-earth.md`; `promoted = false`.

#### F-03 — a premise nothing bears on (expected verdict: UNSUPPORTED)

- **Split:** S · **Scope:** S1 · **Family:** false premise
- **Call:** as above, with the assertion
  `The national budget of Iceland was increased by 12 percent in 2019.`
- **Expectation:** verdict `UNSUPPORTED`; the reason says that no claim is
  about the same subject and that the absence of evidence is not a
  contradiction; `promoted = false`.

#### F-04 — a premise the corpus denies twice (expected verdict: UNRESOLVED)

- **Split:** S · **Scope:** S1 · **Family:** false premise
- **Call:** as above, with the assertion
  `The Earth's lithosphere is not fractured into plates that move.`
- **Expectation:** verdict `UNRESOLVED`; the workspace holds two claims that
  deny it — "Earth's lithosphere, the rigid outer shell of the planet
  including the crust and upper mantle, is fractured into seven or eight
  major plates (depending on how they are defined) and many minor plates or
  \"platelets\"." and "Plate tectonics (from Latin tectonicus, from Ancient
  Greek τεκτονικός (tektonikós) 'pertaining to building') is the scientific
  theory that Earth's lithosphere comprises a number of large tectonic
  plates, which have been slowly moving since 3–4 billion years ago." — and
  the two do not settle each other, so the workspace does not decide;
  `promoted = false`.

#### F-05 — the demonstration's false premise (expected verdict: CONTRADICTED)

- **Split:** P · **Scope:** P1 · **Family:** false premise
- **Call:** as above, with the assertion
  `The idea that the continents could move was widely accepted by geologists when it was first proposed in the early twentieth century.`
- **Expectation:** verdict `CONTRADICTED`; the disputed premise is named;
  the contradicting claim is "When initially proposed as a scientific theory
  in the early 20th century, the idea that continents could move was widely
  rejected." from `wikipedia-continental-drift.md` (CC BY-SA 4.0, oldid
  1376987312); `promoted = false` and the claim count of the workspace is
  unchanged by the check. This is the premise of turn 3 of
  `OWNER-SEQUENCE.md`; the expectation comes from `ANSWER-KEY.md`.

### 4.5 Multi-claim (scopes S1 and P1)

These questions need **more than one stored claim**. The pass condition is
the one of §4.1 plus the number of distinct claims and sources the case
requires.

#### M-01 — the three boundary types (scope S1)

- **Split:** S · **Scope:** S1 · **Family:** multi-claim
- **Call:** as in A-01, with the question
  `What are the three types of plate boundaries, and what happens at each of them?`
- **Expectation:** answered; at least **3** distinct cited claims; the source
  `wikipedia-plate-tectonics.md` among the answer's sources; these
  propositions present verbatim:
  - "Types of plate boundaries Three types of plate boundaries exist, characterized by the way the plates move relative to each other."
  - "Convergent boundaries (destructive boundaries or active margins) occur where two plates slide toward each other to form either a subduction zone (one plate moving underneath the other) or a continental collision."
  - "The San Andreas Fault in California is an example of a transform boundary exhibiting dextral motion."

#### M-02 — the driving forces (scope S1)

- **Split:** S · **Scope:** S1 · **Family:** multi-claim
- **Call:** as in A-01, with the question
  `What drives the motion of the tectonic plates?`
- **Expectation:** answered; at least **3** distinct cited claims; the source
  `wikipedia-plate-tectonics.md` among the answer's sources; these
  propositions present verbatim:
  - "Driving forces of plate motion Tectonic plates are able to move because of the relative density of oceanic lithosphere and the relative weakness of the asthenosphere."
  - "The greater density of old lithosphere relative to the underlying asthenosphere allows it to sink into the deep mantle at subduction zones, providing most of the driving force for plate movement."
  - "In modern views, gravity is invoked as the major driving force, through slab pull along subduction zones."

#### M-03 — the gap question of the demonstration (scope P1)

- **Split:** P · **Scope:** P1 · **Family:** multi-claim
- **Call:** as in A-01, with the question
  `How do we know the continents have moved? What is the evidence for continental drift, and how do mid-ocean ridges and subduction zones fit together?`
- **Expectation:** answered; at least **6** distinct cited claims; at least
  **3** distinct cited sources; these propositions present verbatim:
  - "Similar plant and animal fossils are found around the shores of different continents, suggesting that they were once joined."
  - "The fossils of Mesosaurus, a freshwater reptile rather like a small crocodile, found both in Brazil and South Africa, are one example; another is the discovery of fossils of the land reptile Lystrosaurus in rocks of the same age at locations in Africa, India, and Antarctica."
  - "This feature is where seafloor spreading takes place along a divergent plate boundary."
  - "Beyond the forearc basin, volcanoes are found in long chains called volcanic arcs."
  - "Oceanic trenches are the deepest parts of the ocean floor."

### 4.6 Knows what it does not know (scope S1)

The corpus touches the subject of these questions and does not cover the whole
of it. The workspace must answer the part it holds **and** name the part it
does not hold. An answer that covers the subject and does not report the gap
fails the case, and so does an abstention that throws the covered part away.

#### K-01 — plate boundaries and tsunami

- **Split:** S · **Scope:** S1 · **Family:** knows what it does not know
- **Call:** as in A-01, with the question
  `What are the three types of plate boundaries and which can produce a tsunami?`
- **Expectation:** answered; the cited claims include "Types of plate
  boundaries Three types of plate boundaries exist, characterized by the way
  the plates move relative to each other."; the **gap list names the tsunami
  term** — the corpus holds nothing about tsunamis.

#### K-02 — subduction, island arcs and tsunami

- **Split:** S · **Scope:** S1 · **Family:** knows what it does not know
- **Call:** as in A-01, with the question
  `How do subduction zones produce earthquakes and volcanic island arcs, and how are tsunamis generated in them?`
- **Expectation:** answered; the cited claims include "The trenches are the
  key to understanding how island arcs such as the Marianas and the Aleutian
  Islands have formed and why they experience numerous strong earthquakes."
  or "Magmas that form island arcs are produced by the partial melting of the
  descending plate and/or the overlying oceanic lithosphere."; the **gap list
  names the tsunami term**.

### 4.7 Revision (scopes R1 and R2)

The revision path is the one the wave was built for: a new document is
acquired, compared with what the workspace already believes, and the belief
is either revised or left standing — and the report says which and why.

#### R-01 — a revision that must happen (scope P1 → R1)

- **Split:** R · **Scope:** P1 before the call, R1 after it · **Family:**
  revision
- **Call:** `POST /v1/learning/workspaces/{id}/revise?file=wikipedia-slab-pull.md`
- **Expectation:**
  - the document is acquired and the report says so (claims added > 0);
  - **at least one** supersession is applied;
  - the superseded claim is "Slab pull: Scientific opinion is that the
    asthenosphere is insufficiently competent or rigid to directly cause
    motion by friction along the base of the lithosphere." — after the call
    its status is `SUPERSEDED` and its version is 2, and it is still in the
    workspace;
  - the new claim "However, most scientists working today believe that the
    asthenosphere does not directly cause motion by the friction of such
    basal forces." is `SUPPORTED` at version 2;
  - the reason the application gives names the **denial**: the new evidence
    is about the same subject and denies what the earlier claim asserted.
    The relatedness the report carries is at least the 0.30 floor of
    `ModelRevisionDecider`.

#### R-02 — a revision that must NOT happen (scope R1 → R2)

- **Split:** R · **Scope:** R1 before the call, R2 after it · **Family:**
  revision
- **Call:** `POST /v1/learning/workspaces/{id}/revise?file=wikipedia-paleomagnetism.md`
- **Expectation:**
  - the document is acquired and the report says so (claims added > 0);
  - **no** supersession is applied (`revisionsApplied = 0`), and no claim of
    the workspace changes its status because of this acquisition;
  - the reason the application gives is one of the two reasons the decider
    writes when it leaves the model alone: that no established claim is
    about the same subject, so the new claim is an addition rather than a
    revision; or that the new evidence is not stronger than the evidence
    the claim already rests on, so the claim stands.
  The two documents are a pair of opposites for the test: *Slab pull* denies
  what the seed corpus asserted about the cause of plate motion, and
  *Paleomagnetism* adds evidence about the record of the magnetic field
  without denying anything the workspace already holds.

#### R-03 — the answer after the revision (scope R1)

- **Split:** R · **Scope:** R1 · **Family:** revision
- **Call:** as in A-01, with the question
  `What drives the motion of the tectonic plates?`
- **Expectation:** answered; at least **5** distinct cited claims; at least
  **5** distinct cited sources (the five of scope P1 plus
  `wikipedia-slab-pull.md`); these propositions present verbatim:
  - "This force and slab suction account for almost all of the force driving plate tectonics."
  - "However, most scientists working today believe that the asthenosphere does not directly cause motion by the friction of such basal forces."
- The same question as M-02, asked at a different scope: the difference
  between the two answers is what the revision did.

#### R-04 — a second revision that must NOT happen (scope P1′)

- **Split:** R · **Scope:** P1′ before the call · **Family:** revision
- **Call:** `POST /v1/learning/workspaces/{id}/revise?file=wikipedia-seafloor-spreading.md`
- **Expectation:**
  - the document is acquired and the report says so (claims added > 0);
  - **no** supersession is applied (`revisionsApplied = 0`), and no claim of
    the workspace changes its status because of this acquisition;
  - the reason the application gives is one of the two reasons the decider
    writes when it leaves the model alone (no established claim is about the
    same subject, so the new claim is an addition; or the new evidence is
    not stronger than the evidence the claim already rests on, so the claim
    stands).
  *Seafloor spreading* is a document of the pool that states what the seed
  corpus already states; it is the second candidate for a negative revision
  case. This case was added after R-02 had failed; its expectation was fixed
  before it was run.

#### R-05 — a third revision that must NOT happen (scope P1″)

- **Split:** R · **Scope:** P1″ before the call · **Family:** revision
- **Call:** `POST /v1/learning/workspaces/{id}/revise?file=wikipedia-mantle-convection.md`
- **Expectation:** as R-04 — the document is acquired, **no** supersession is
  applied, and the reason the application gives is one of the two reasons the
  decider writes when it leaves the model alone.
  *Mantle convection* is the pool's "earlier model of the driving force" and
  is the third candidate for a negative revision case; this case was added
  after R-02 had failed, and its expectation was fixed before it was run.

## 5. What this dataset does not measure

- **No generalisation.** One domain, nine documents, one lexical retriever.
  A number measured here describes this corpus and this retriever.
- **No live acquisition.** The pool is pre-cached, versioned and licensed;
  no request leaves the machine and no live web search is performed. The
  dataset cannot measure what the application would do with text it has
  never seen.
- **No semantic check.** The premise check is lexical: it compares terms and
  negations, so a premise that shares terms with a claim is "about the same
  subject" whether or not it means the same thing. The verdicts of §4.4
  measure that rule, not meaning.
- **No calibrated confidence.** The support score of an answer is 0.0 for
  imported claims, because an imported claim carries no authored
  confidence. The signal is in the claim count, the source count and the
  evidence coverage.
- **No fairness, no robustness, no latency.** The dataset has one question
  per fact family and no adversarial, typo or multilingual variant, so it
  says nothing about those.

## 6. The case index

| family | seed split (S) | pool split (P) | revision split (R) | total |
|---|---|---|---|---|
| answerable | A-01 … A-06 (6) | P-01, P-02 (2) | — | 8 |
| unanswerable | U-01 … U-05 (5) | U-06 (1) | — | 6 |
| false premise | F-01 … F-04 (4) | F-05 (1) | — | 5 |
| multi-claim | M-01, M-02 (2) | M-03 (1) | — | 3 |
| knows what it does not know | K-01, K-02 (2) | — | — | 2 |
| revision | — | — | R-01 … R-05 (5) | 5 |
| **total** | **19** | **5** | **5** | **29** |
