# The CRJ-01 / ADAPTIVE-COMPETENCE development corpora

**Status:** `CANONICAL` · **Written:** 2026-11-27 · **Owner:** this milestone

**What these files are.** Two **synthetic** document sets on **two different technical
subjects**, written for the ADAPTIVE-COMPETENCE milestone and owned by it. They are the
protocol's "new corpus on a different technical subject", twice over, so that a finding on
one subject is not a finding about a subject. They are **synthetic**, so the milestone owns
its bytes outright and no licensed text is redistributed.

| domain | subject | documents |
|---|---|---|
| `dhx` | lithium-ion cell thermal management | `dhx-a-heat.md`, `dhx-b-cooling.md`, `dhx-c-degradation.md`, `dhx-d-instrumentation.md` |
| `dmy` | salt-marsh carbon sequestration | `dmy-a-sediment.md`, `dmy-b-microbes.md`, `dmy-c-erosion.md`, `dmy-d-instruments.md` |

**What is in a document.** A title line and twelve **declarative sentences**, one per
paragraph. The production acquisition path (`workbench.importDocument`) derives **one claim per
sentence**, and the claim's proposition is the sentence **byte for byte** — which is what
makes a case's gold verifiable against the corpus's own bytes. The sentences were written to
the extractor's own rules (a declarative verb from its fixed list, a subject phrase of at
most 120 characters, 20 to 600 characters in total), so every one of them is extracted.

**The arrangement of the documents.** The letters are the slots the DEV split uses:

| slot | file | what the split puts there |
|---|---|---|
| **A** | `*-a-*.md` | the gold of OBSERVED case #1, of **all five VALIDATION** cases and of **all five NOVEL** cases |
| **B** | `*-b-*.md` | the gold of OBSERVED case #2 (and nothing else) |
| **C** | `*-c-*.md` | the gold of **both OUT_OF_SUPPORT** cases |
| **D** | `*-d-*.md` | nothing — the bulk that makes the wide corpus larger than the support |

**Why the arrangement is what it is.** A procedure's support set is the union of the sources
its learning episode drew on, so the two OBSERVED golds sit in two different documents to
give the grid two support sizes (one document and two). The VALIDATION golds sit in one
document because a promotion requires the **restricted** reading to cite exactly the claims
the **reference** reading cites, and that can only hold when a validation question reaches
the claims of one document only. The NOVEL golds sit in the same document as the VALIDATION
ones because the NOVEL cases are the matched cases of the cost grid: the procedure has to
actually serve them in every cell of the grid, including the cell whose support covers one
document. The OUT_OF_SUPPORT golds sit in a document no support covers, because they are the
controls the applicability gate must refuse.

**How to re-derive the measurement of these corpora.**

```
export JAVA_HOME=$HOME/jdk25 GRADLE_USER_HOME=$PWD/.gradle/dsh-home
flock /tmp/gradle-matrix.lock ./gradlew :matrix-core:test --offline \
  --tests "io.matrix.crj.corpus.Crj01AdaptiveCorpusReportBuilder"
```

The instrument imports every document through the **production** import path and writes
`docs-v2/engineering/CRJ-01-ADAPTIVE-CORPUS-REPORT.json`: per document its digest, the
production source id it received, and every claim the production path derived from it.

**What is deliberately NOT here.** No case, no question, no gold and no answer: the split is
`docs-v2/engineering/CRJ-01-ADAPTIVE-DEV-SPLIT.json`, and it was written by an **independent
case author** that worked from the report above and from nothing else.
