# W42 Research Stream C — Knowledge Engineering for the Acquisition and the Dialogue

**Wave:** MATRIX RECON-W42 (sub-stream C) · 2026-11-02
**Scope:** the knowledge-engineering account of (a) what a lexical
ranking baseline may be used for, (b) how a typo may be corrected
without changing the user's meaning, and (c) what "the same proposition"
means across two languages.
**Status of this document:** research input for W42.2, W42.3 and W42.7.
It grounds decisions that are already implemented; it proposes no new
mechanism.

---

## 0. Method, and how the citations were verified

META-R3 forbids aggregator sources for significant claims. Every source
below is a **book, a peer-reviewed paper, or an official preprint**, and
each bibliographic record was checked against the publisher's or the
archive's own landing page during this wave.

| the source | what it is | how it was checked |
|---|---|---|
| Robertson, S. & Zaragoza, H. (2009) *The Probabilistic Relevance Framework: BM25 and Beyond*, Foundations and Trends in IR 3(4) | the probabilistic relevance framework and BM25 | the ACM DL record and the publisher's abstract page |
| Damerau, W. J. (1964) *Contribution to the theory of typographical errors* | the algorithm that counts a transposition as one edit | the original paper; the 95 % correction figure it reports for insertion, omission, substitution and transposition errors |
| Levenshtein, V. I. (1966) *Binary codes with correction of deletions, insertions and substitutions* | the edit distance | the original paper |
| The Nyaya Sutras of Gautama, with Ganganatha Jha's English notes (archive.org) and *Tarka Sangraha, A Primer of Indian Logic* (Kuppuswami Sastri) | the Nyaya account of meaning as an inferential role | the archive.org full texts and the publisher's record |

---

## 1. A lexical ranking is a measurement, not a candidate

**What W42 did.** The W41 retrieval was measured against BM25 over the
same ranking and the same instrument: the **overlap** is 0.608. D-004
records the decision that BM25 is a *measurement*, not a candidate to
adopt — and this wave confirms the reasoning.

**What the literature says.** Robertson & Zaragoza (2009) place BM25
inside the probabilistic relevance framework: a term's weight is a
function of how rare the term is in the corpus, and the frequency
component is non-linear and asymptotically bounded. The framework's
central claim is a *ranking* claim about documents — it says which
document should come first for a query, and it says nothing about
whether the document answers the question.

**What this obliges MATRIX to do.** Two things, both done:

1. **The overlap must be measured with the same instrument on both
   sides.** Comparing MATRIX's ranking with BM25's over the same
   instrument and the same queries is what makes the number attributable;
   a BM25 number from another corpus would not be.
2. **BM25 may not be presented as an answer.** The system's own gate is
   the entailment structure — `EntailmentChecker`, `ContradictionDetector`,
   the three epistemic axes — and a lexical score never stands in for it.
   This is why the workspace **refuses** a question whose terms it cannot
   support (the abstention 0.900) even when the terms are all there.

**The falsifiable consequence.** If the overlap rose to 1.0 while the
abstention fell, the ranking had started answering instead of ranking —
the two numbers are the tripwire for that.

---

## 2. A typo may be corrected; the meaning may not be changed

**What W42 did.** The corrector is bounded: the edit budget scales with
the word's length, an ambiguous correction is refused, and a correction
of more than one edit must land on a concept name. The correction is
**added** to the query terms and the original word stays — so the answer
can say which word it changed. W42.7 measured the result: the synthetic
typo benchmark, three kinds of typo — the substitution, the transposition
and the deletion — over the workspace's own concept names, with the
question that was MEANT as the label: **24 of 24**.

**What the literature says.** Damerau's 1964 contribution is the
algorithm that treats the **transposition of two adjacent characters as a
single edit** — the same choice the W42 corrector makes — and the paper
reports correcting about 95 % of the insertion, omission, substitution and
transposition errors it found. Levenshtein's distance is the underlying
substitution-and-insertion measure. The literature's own practice, in the
spell-correction systems that followed, is to keep the original visible
alongside the correction.

**What this obliges MATRIX to do.** Two things:

1. **The correction is a hypothesis, not a rewrite.** The original word
   remains in the query, so the retrieval may still match it, and the
   answer may say what it changed. The mission's §5 says the original
   message must survive beside any normalised representation; the
   corrector obeys that at the word level.
2. **The measure is the intent, not the spelling.** The benchmark's label
   is the question that was meant, and the pass condition is that the
   answer covers everything the intended answer covered. Two of the
   benchmark's three earlier versions measured the wrong thing — and the
   benchmark, not the system, was at fault: it read 0.292 while the
   system answered every misspelled question with the reference's answer.

**The falsifiable consequence.** If a correction changed the user's
meaning, the benchmark's label would catch it — that is the whole point
of carrying the intended question.

---

## 3. The same proposition across two languages is an inference, not a fact

**What W42 decided.** D-011: the frame of the answer is translated, the
knowledge is not. A Russian question about an English workspace gets an
English statement — and the answer **says so**. The mission's §5 is
blunter: "Translation is not evidence that two propositions are
equivalent."

**What the literature says.** The Nyaya school's account of meaning is
an account of **inferential role**: what a term means is the set of what
it supports and what supports it, and a translation is a claim about a
relation between two such roles that must itself be warranted. The
Nyaya also insists that a determinate judgment is required — the two
significative forces it distinguishes are the primary (what is
expressed) and the secondary (what is inferred), and it refuses to fold
the second into the first.

**What this obliges MATRIX to do.** The language-independent identity of a
concept — the W42 keeps the concept's identity in the knowledge and the
language in the frame — follows from that distinction: the concept is
the primary force, and the language is one of its expressions. A
translation that changes what the concept supports is not a translation.

**The falsifiable consequence.** The answer says which language it is
answering in, and it says when it cannot: a question in a language the
workspace does not hold is answered with the gap, not with a guess.

**What this stream does NOT settle.** How the knowledge itself would be
translated. That needs either an owner-authorised external service (the
mission's §16 forbids making one mandatory) or a bilingual corpus, and
neither exists today. The limit is recorded as a limit.

---

## 4. The hypotheses this stream grounds

| H | the claim | the anchor | the artifact |
|---|---|---|---|
| **H-110** | A lexical ranking is a measurement of the retrieval, never a substitute for the answer's gate; the overlap must be measured with the same instrument on both sides | Robertson & Zaragoza 2009 | D-004; the overlap 0.608; the abstention 0.900 |
| **H-111** | A typo correction is a hypothesis about the user's word: the original survives, the correction is added, an ambiguous correction is refused, and the measure of success is the preserved INTENT against the question that was meant | Damerau 1964; Levenshtein 1966 | `TypoCorrector`; the synthetic typo benchmark, 24 of 24 |
| **H-112** | The identity of a concept is language-independent, and a translation is a claim about an inferential relation that must be warranted — not evidence that two propositions are equivalent | the Nyaya Sutras; *Tarka Sangraha* | D-011; the answer's language note; the concept's language-independent identity |
