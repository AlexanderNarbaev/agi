# The retained manifests of the CRJ-01 / H2 served transfer measurement

**Written:** 2026-11-27 · **Purpose:** say exactly which manifest verifies where, and what it
does and does not cover.

Every file below is a **digest record**. A digest record is only meaningful together with a
statement of **what it is relative to** and **whether the named files travel with it**.

## The kinds

| kind | what it means | how to verify |
|---|---|---|
| **RETAINED** | every file the manifest names is **in this repository**, beside it | `cd <the manifest's directory> && sha256sum -c <manifest>` |
| **HELD BACK** | the manifest names material the mandate forbids publishing — here, the **sealed** evaluation's cases and gold, which do not exist here at all | against the **owner's own copy**; the digests are the receipt |
| **POINT-IN-TIME** | the manifest records a payload **as it was proposed** | it identifies bytes; it does not assert the tree still matches |

## The manifests, one by one

### `CHECKSUMS.sha256` — RETAINED (20 files)

It names the 15 GREEN JUnit XMLs, the two deliberate RED XMLs, the retained rows and this
directory's two Markdown records. **It does not name itself**: a manifest that lists its own
digest can never verify, and the first version of this one did exactly that — it carried the
empty string's digest for itself and the verification failed. The manifest is now built over
every file **except** itself.

```
cd docs-v2/engineering/evidence/crj01-h2-served-transfer
sha256sum -c CHECKSUMS.sha256          # 20 of 20 OK
```

The **RED** XML is part of the retained set on purpose: it is the record of a defect that was
found, and the repository's convention is that a defect's reproduction is evidence, not
rubbish. It is a JUnit XML that reports **7 failures** — the pre-correction behaviour of the
served path, which refused the promotion with "the candidate disagreed with the reference on
the held-out case 'H2-A05', so it was not promoted".

### The corpus, the split and the rows — POINT-IN-TIME, and RE-HASHABLE

The three artefacts the rows are made of live **outside** this directory, in
`docs-v2/engineering/`, and the evidence artefact pins the two it depends on:

| artefact | path | pinned by |
|---|---|---|
| the corpus report | `docs-v2/engineering/CRJ-01-H2-CORPUS-REPORT.json` | `CRJ-01-H2-EVIDENCE.json` → `corpusReportSha256` |
| the DEV split | `docs-v2/engineering/CRJ-01-H2-DEV-SPLIT.json` | `CRJ-01-H2-EVIDENCE.json` → `splitSha256` |
| the rows | the generated CRJ-01-H2-EVIDENCE.json in the engineering directory (the **working** copy, rewritten by every run) and `docs-v2/engineering/evidence/crj01-h2-served-transfer/CRJ-01-H2-EVIDENCE.json` (the **retained** copy, the canonical one) | `CHECKSUMS.sha256` for the retained copy; this milestone's result document for both |

```
sha256sum docs-v2/engineering/CRJ-01-H2-CORPUS-REPORT.json \
          docs-v2/engineering/CRJ-01-H2-DEV-SPLIT.json \
          docs-v2/engineering/CRJ-01-H2-EVIDENCE.json
```

and compare the two digests with the `corpusReportSha256` and `splitSha256` fields of
`CRJ-01-H2-EVIDENCE.json`.

### The source bytes of the corpus — RETAINED (4 files)

The corpus is **synthetic** and owned by this milestone, so its bytes travel with the
repository: `matrix-core/src/test/resources/crj01-h2/*.md`. Their digests are frozen in the
corpus report (`documents[].sha256`) and repeated in every case of the split
(`corpusSha256`), and the split's validator re-reads the bytes and compares them on every run.

```
sha256sum matrix-core/src/test/resources/crj01-h2/*.md
```

### The sealed evaluation — HELD BACK (and NOT RUN)

**There is no sealed evaluation for this milestone.** The isolation a sealed split needs — an
implementer that cannot read the gold and an evaluator that cannot read the solver — has not
been established from the executor that ran it. Nothing is held back, because nothing was
produced: the measurement is a **DEV** run on the solver's own split, and the evidence
artefact says so in its own `sealed` and `sealedNote` fields.

## The three legacy artefacts that are NOT in this set

`docs-v2/engineering/CRJ-01-EVIDENCE.json`, `CRJ-01-EVIDENCE-DOMAIN-B.json` and
`CRJ-01-FREEZE-RECORD.json` are rewritten by the legacy corpus-wide runners
(`io.matrix.crj.runner.*`) whenever they run. One broad run during this milestone rewrote
them. Their substantive content is **identical** to the baseline — the same grounded counts
(6/9 for arm A, 0/9 for arm B on novel tasks, 36 correct in domain B), the same digests and
the same role placeholders — and only the JSON key order and the wall-clock timings differ.
**They are not identical to the baseline, and the differences are named rather than
glossed:** the two evidence files differ in their **wall-clock timings only** — every grounded
count, every digest and every role placeholder is unchanged — and the **freeze record differs in
one frozen field**: the legacy runner re-pointed `fields/branchSha` from
`0b34741dc7f0001069e1e7501511a20e771bc824` at the current HEAD
`7d8c695fa75385ba09e70267e11b7f7301e415c2`. The protocol's §5.5 names `branchSha` as a frozen
field, and §5.6.4 says a change to the freeze voids it — so that re-pointing is **a protocol
concern in its own right**, and it is the reason these three files must not ship with this
change set. They are **not** this milestone's change set, they are **not** retained here, and
they must not be presented as its evidence.
