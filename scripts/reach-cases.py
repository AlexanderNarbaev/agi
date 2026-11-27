#!/usr/bin/env python3
"""CRJ-01 / COMPETENCE-REACH — the DEV case set, written by the IMPLEMENTER.

**The role record, stated first because it matters.** The independent case author did NOT
deliver: two separately commissioned DSH subagents were given the corpus and the oracle
contract, and neither wrote a case set. The cases below are therefore the IMPLEMENTER's, and
the milestone says so wherever it states the roles. The measurement is DEV-only and the
cases are NOT independent of the implementer's mechanism.

What the cases are: 40 cases over two synthetic DEV domains, in three disjoint slices, with
the oracle labels (the minimal set of claims a correct answer must cite, the dispositions that
count as correct, the corroboration and contradiction conditions). The layout is fixed by the
measurement's needs, not by the measurement's outcome:

* PROPOSAL — the learning episode's own cases; the golds sit in TWO documents so the episode
  draws on two sources.
* VALIDATION — the held-out cases the promotion is decided on; the golds sit in ONE document
  (B) and the questions use B's own vocabulary, so a restricted reading over the support
  reproduces the reference on them.
* EVALUATION — the measured cases; the golds sit where the measurement can tell the arms
  apart: inside the citation-derived scope, outside it but inside the declared scope, or
  outside both.
"""

import hashlib
import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[1]
CORPUS = ROOT / "matrix-core/src/test/resources/crj01-reach"
OUT = ROOT / "docs-v2/engineering/CRJ-01-REACH-DEV-CASES.json"

DOMAINS = {
    "wbe": {
        "subject": "wind-turbine blade leading-edge erosion",
        "files": ["wbe-a-erosion.md", "wbe-b-inspection.md", "wbe-c-coatings.md",
                  "wbe-d-aerodynamics.md"],
        "declared": ["wbe-a-erosion.md", "wbe-b-inspection.md", "wbe-c-coatings.md"],
    },
    "hpd": {
        "subject": "hospital pharmacy unit-dose dispensing",
        "files": ["hpd-a-dispensing.md", "hpd-b-identification.md", "hpd-c-reconciliation.md",
                  "hpd-d-labelling.md"],
        "declared": ["hpd-a-dispensing.md", "hpd-b-identification.md", "hpd-c-reconciliation.md"],
    },
}

# ── the sentences, READ FROM THE CORPUS BYTES ─────────────────────────────────────────────
# The case set names the gold by the FILE and the INDEX of the sentence, and the generator
# takes the sentence FROM THE FILE. A hand-kept copy of the corpus inside the generator is a
# copy that can go stale, and a stale copy would make every gold unresolvable.
def read_sentences(domain: str, name: str) -> list:
    text = (CORPUS / domain / name).read_text()
    out = []
    for block in text.split("\n\n"):
        block = block.strip()
        if block and not block.startswith("#"):
            out.append(block)
    return out


CORPUS_SENTENCES = {
    domain: {name: read_sentences(domain, name) for name in spec["files"]}
    for domain, spec in DOMAINS.items()
}




def ev(domain: str, file: str, index: int) -> dict:
    """One necessary-evidence entry: the file and the sentence, taken FROM THE CORPUS."""
    sentence = CORPUS_SENTENCES[domain][file][index]
    return {"corpusFile": file, "proposition": sentence}


def case(domain: str, number: int, slice_: str, kind: str, level: str, question: str,
          necessary: list, allowed: list, dispositions: list, corroboration: str,
          contradiction: str, note: str, answerable: bool = True) -> dict:
    return {
        "id": f"RC-{domain.upper()}-{number:02d}",
        "domain": domain,
        "slice": slice_,
        "kind": kind,
        "level": level,
        "question": question,
        "answerable": answerable,
        "necessaryEvidence": necessary,
        "allowedEvidence": allowed,
        "allowedDispositions": dispositions,
        "corroboration": corroboration,
        "contradiction": contradiction,
        "note": note,
    }


def wbe_cases() -> list:
    a, b, c, d = ("wbe-a-erosion.md", "wbe-b-inspection.md", "wbe-c-coatings.md",
                  "wbe-d-aerodynamics.md")
    out = []
    # ── PROPOSAL — the learning episode: two documents, so two sources ───────────────────
    out.append(case("wbe", 1, "PROPOSAL", "PLAIN", "INTRODUCTORY",
                    "What happens to the leading edge of a wind-turbine blade when rain strikes it, and how does that affect the lift of the blade?",
                    [ev("wbe", a, 0)], [], ["ANSWERED"], "NONE", "NONE",
                    "the learning episode's first case; its gold is in the erosion document, and its question also reaches the aerodynamics document — the citation that the citation-derived scope inherits"))
    out.append(case("wbe", 2, "PROPOSAL", "PLAIN", "INTRODUCTORY",
                    "Why does sea air corrode the leading edge of a blade faster than the rest of it?",
                    [ev("wbe", a, 1)], [], ["ANSWERED"], "NONE", "NONE",
                    "the learning episode's second case; its gold is in the erosion document too"))
    out.append(case("wbe", 3, "PROPOSAL", "PLAIN", "INTRODUCTORY",
                    "How does a drone find the small erosion pits on a blade?",
                    [ev("wbe", b, 0)], [], ["ANSWERED"], "NONE", "NONE",
                    "the learning episode's third case; its gold is in the inspection document, "
                    "so the episode draws on TWO documents"))
    # ── VALIDATION — held out, all in the inspection document, narrow questions ───────────
    out.append(case("wbe", 4, "VALIDATION", "PLAIN", "INTRODUCTORY",
                    "How is the thickness of a blade measured without any contact with it?",
                    [ev("wbe", b, 1)], [], ["ANSWERED"], "NONE", "NONE",
                    "a held-out case whose gold is in the inspection document"))
    out.append(case("wbe", 5, "VALIDATION", "PLAIN", "INTRODUCTORY",
                    "What do the photographs of an inspection reveal in millimetres?",
                    [ev("wbe", b, 2)], [], ["ANSWERED"], "NONE", "NONE",
                    "a held-out case whose gold is in the inspection document"))
    out.append(case("wbe", 6, "VALIDATION", "PLAIN", "INTRODUCTORY",
                    "What decides how severe a pit is, given its depth and the width of the "
                    "material around it?",
                    [ev("wbe", b, 6)], [], ["ANSWERED"], "NONE", "NONE",
                    "a held-out case whose gold is in the inspection document"))
    out.append(case("wbe", 7, "VALIDATION", "PLAIN", "INTRODUCTORY",
                    "What does an inspection compare with the previous inspection?",
                    [ev("wbe", b, 4)], [], ["ANSWERED"], "NONE", "NONE",
                    "a held-out case whose gold is in the inspection document"))
    out.append(case("wbe", 8, "VALIDATION", "PLAIN", "INTRODUCTORY",
                    "What does the erosion rate depend on when sand grains strike a blade?",
                    [ev("wbe", b, 3)], [], ["ANSWERED"], "NONE", "NONE",
                    "a held-out case whose gold is in the inspection document"))
    # ── EVALUATION — the measured cases ──────────────────────────────────────────────────
    out.append(case("wbe", 9, "EVALUATION", "PLAIN", "INTRODUCTORY",
                    "How often is a blade inspected at a coastal site?",
                    [ev("wbe", a, 7)], [], ["ANSWERED"], "NONE", "NONE",
                    "the ordinary case; the gold is inside the scope the learning episode built"))
    out.append(case("wbe", 10, "EVALUATION", "PARAPHRASE", "INTRODUCTORY",
                    "Where does the loss of material on a blade begin once a pit has formed on it?",
                    [ev("wbe", a, 5)], [], ["ANSWERED"], "NONE", "NONE",
                    "a paraphrase that shares no rare term with the gold"))
    out.append(case("wbe", 11, "EVALUATION", "PARAPHRASE", "INTERMEDIATE",
                    "What does the price of protecting a blade depend on?",
                    [ev("wbe", c, 7)], [], ["ANSWERED"], "NONE", "NONE",
                    "a paraphrase whose gold is OUTSIDE the scope the learning episode built, so "
                    "a scope that cannot carry it must refuse"))
    out.append(case("wbe", 12, "EVALUATION", "MISLEADING_RELEVANCE", "INTRODUCTORY",
                    "What does the rate at which a blade wears away depend on?",
                    [ev("wbe", a, 2)], [ev("wbe", b, 3)], ["ANSWERED"], "NONE", "NONE",
                    "the DECOY is the inspection document's sentence about the very same "
                    "dependency, and it is the nearer one to the question; the gold is the "
                    "erosion document's"))
    out.append(case("wbe", 13, "EVALUATION", "MISLEADING_RELEVANCE", "INTRODUCTORY",
                    "How does the yearly energy of a turbine change when the blades grow rough?",
                    [ev("wbe", d, 6)], [ev("wbe", d, 0)], ["ANSWERED"], "NONE", "NONE",
                    "the DECOY is the aerodynamics document's sentence about the lift, which is "
                    "the nearer one to the question; the gold is the other sentence of the same "
                    "document, and the document is outside the declared scope"))
    out.append(case("wbe", 14, "EVALUATION", "MULTI_SOURCE", "INTERMEDIATE",
                    "How does the service life of a coating depend on the thickness of the "
                    "coating, and what does the yearly energy of a turbine do as the blades grow "
                    "rough?",
                    [ev("wbe", c, 1), ev("wbe", d, 6)], [], ["ANSWERED"], "NONE", "NONE",
                    "the answer needs BOTH documents; the scope the learning episode built holds "
                    "the first and not the second"))
    out.append(case("wbe", 15, "EVALUATION", "MULTI_SOURCE", "INTERMEDIATE",
                    "What does a hardener do inside a coating, and what does a crack do to the "
                    "metal under it?",
                    [ev("wbe", c, 4), ev("wbe", c, 5)], [], ["ANSWERED"], "NONE", "NONE",
                    "the answer needs BOTH documents; the second one is outside the scope the "
                    "learning episode built"))
    out.append(case("wbe", 16, "EVALUATION", "AMBIGUOUS", "INTERMEDIATE",
                    "What does a rough leading edge do to the airflow of a blade, and what does "
                    "an erosion pit do to the lift of that blade?",
                    [ev("wbe", d, 1), ev("wbe", d, 4)], [], ["QUALIFIED", "ABSTAINED", "ANSWERED"],
                    "NONE", "NONE",
                    "the corpus supports two readings of the question - the separation of the "
                    "airflow and the loss of lift - and a correct answer must carry both"))
    out.append(case("wbe", 17, "EVALUATION", "INCOMPLETE", "INTERMEDIATE",
                    "What does a repair of a damaged coating require, and which coating suits the "
                    "alloy of the blade?",
                    [ev("wbe", c, 6)], [], ["QUALIFIED", "ABSTAINED"], "NONE", "NONE",
                    "the corpus holds the first half of the answer and nothing about the second, "
                    "so the workspace cannot carry the whole answer",
                    answerable=False))
    out.append(case("wbe", 18, "EVALUATION", "UNSUPPORTED", "INTRODUCTORY",
                    "How does a geothermal plant measure the steam pressure of its well?",
                    [], [], ["ABSTAINED"], "NONE", "NONE",
                    "the corpus says nothing about the subject; no scope can carry the evidence, "
                    "so the workspace does NOT hold the answer and a refusal is CORRECT — the "
                    "denominator of a false rejection is the refusals of ANSWERABLE cases only",
                    answerable=False))
    out.append(case("wbe", 19, "EVALUATION", "CONFLICT", "INTERMEDIATE",
                    "Does the size of the rain drops change the rate at which a blade wears away "
                    "at an exposed coastal site?",
                    [ev("wbe", a, 3), ev("wbe", c, 8)], [], ["QUALIFIED", "ABSTAINED", "ANSWERED"],
                    "NONE", "CONFLICTING_SOURCES",
                    "two documents contradict each other on the point, and a correct answer must "
                    "carry BOTH sides of the contradiction"))
    out.append(case("wbe", 20, "EVALUATION", "CORROBORATED", "INTRODUCTORY",
                    "How does a protective coating change the rate at which a blade wears away?",
                    [ev("wbe", a, 4)], [ev("wbe", c, 0)], ["ANSWERED"], "TWO_SOURCES", "NONE",
                    "two documents state the same proposition; either one will do"))
    return out


def hpd_cases() -> list:
    a, b, c, d = ("hpd-a-dispensing.md", "hpd-b-identification.md", "hpd-c-reconciliation.md",
                  "hpd-d-labelling.md")
    out = []
    out.append(case("hpd", 1, "PROPOSAL", "PLAIN", "INTRODUCTORY",
                    "What does a dispensing cabinet in a hospital pharmacy keep?",
                    [ev("hpd", a, 0)], [], ["ANSWERED"], "NONE", "NONE",
                    "the learning episode's first case; its gold is in the dispensing document"))
    out.append(case("hpd", 2, "PROPOSAL", "PLAIN", "INTRODUCTORY",
                    "What does the barcode on a unit dose allow the cabinet to check?",
                    [ev("hpd", a, 2)], [], ["ANSWERED"], "NONE", "NONE",
                    "the learning episode's second case; its gold is in the dispensing document"))
    out.append(case("hpd", 3, "PROPOSAL", "PLAIN", "INTRODUCTORY",
                    "What must a medicine with two similar packages require before the unit dose "
                    "leaves the cabinet?",
                    [ev("hpd", b, 1)], [], ["ANSWERED"], "NONE", "NONE",
                    "the learning episode's third case; its gold is in the identification "
                    "document, so the episode draws on TWO documents"))
    out.append(case("hpd", 4, "VALIDATION", "PLAIN", "INTRODUCTORY",
                    "How quickly is a barcode read on a scanner next to the cabinet?",
                    [ev("hpd", b, 4)], [], ["ANSWERED"], "NONE", "NONE",
                    "a held-out case whose gold is in the identification document"))
    out.append(case("hpd", 5, "VALIDATION", "PLAIN", "INTRODUCTORY",
                    "What does a mismatch between the strength on a barcode and another "
                    "strength require?",
                    [ev("hpd", b, 2)], [], ["ANSWERED"], "NONE", "NONE",
                    "a held-out case whose gold is in the identification document"))
    out.append(case("hpd", 6, "VALIDATION", "PLAIN", "INTRODUCTORY",
                    "What must a cabinet do when its scanner cannot read the barcode of a medicine?",
                    [ev("hpd", b, 5)], [], ["ANSWERED"], "NONE", "NONE",
                    "a held-out case whose gold is in the identification document"))
    out.append(case("hpd", 7, "VALIDATION", "PLAIN", "INTRODUCTORY",
                    "What does a recall require of the identification of every unit dose in the "
                    "cabinets?",
                    [ev("hpd", b, 6)], [], ["ANSWERED"], "NONE", "NONE",
                    "a held-out case whose gold is in the identification document"))
    out.append(case("hpd", 8, "VALIDATION", "PLAIN", "INTRODUCTORY",
                    "What does the record of an admission contain?",
                    [ev("hpd", c, 2)], [], ["ANSWERED"], "NONE", "NONE",
                    "a held-out case whose gold is in the reconciliation document; the four cases "
                    "above carry their golds in the identification document, and this one keeps "
                    "the reading inside the DECLARED scope as well"))
    out.append(case("hpd", 9, "EVALUATION", "PLAIN", "INTRODUCTORY",
                    "What does the picking list of a ward contain?",
                    [ev("hpd", a, 3)], [], ["ANSWERED"], "NONE", "NONE",
                    "the ordinary case; the gold is inside the scope the learning episode built"))
    out.append(case("hpd", 10, "EVALUATION", "PARAPHRASE", "INTRODUCTORY",
                    "Which record accounts for the unit doses that left a pharmacy's cabinet?",
                    [ev("hpd", a, 5)], [], ["ANSWERED"], "NONE", "NONE",
                    "a paraphrase that shares no rare term with the gold"))
    out.append(case("hpd", 11, "EVALUATION", "PARAPHRASE", "INTRODUCTORY",
                    "What must a prescription name before a hospital can dispense a unit dose?",
                    [ev("hpd", a, 1)], [], ["ANSWERED"], "NONE", "NONE",
                    "a paraphrase of the dispensing requirement"))
    out.append(case("hpd", 12, "EVALUATION", "MISLEADING_RELEVANCE", "INTRODUCTORY",
                    "What does the pharmacy do with the unit doses that a ward has asked for?",
                    [ev("hpd", a, 4)], [ev("hpd", a, 0)], ["ANSWERED"], "NONE", "NONE",
                    "the DECOY is the cabinet's own sentence about the medicines the ward asks "
                    "for; the gold is the sentence about what the picking machine does with them"))
    out.append(case("hpd", 13, "EVALUATION", "MISLEADING_RELEVANCE", "INTRODUCTORY",
                    "What does a label that the printer produced incorrectly require?",
                    [ev("hpd", d, 1)], [ev("hpd", a, 9)], ["ANSWERED"], "NONE", "NONE",
                    "the DECOY is the dispensing document's nearly identical sentence about a "
                    "misprinted label; the gold is the labelling document's, and that document "
                    "is outside the declared scope"))
    out.append(case("hpd", 14, "EVALUATION", "MULTI_SOURCE", "INTERMEDIATE",
                    "What does a unit dose that does not match the barcode of its medicine "
                    "require, and what happens when the ward and the pharmacist disagree about "
                    "a medicine?",
                    [ev("hpd", a, 6), ev("hpd", c, 3)], [], ["ANSWERED"], "NONE", "NONE",
                    "the answer needs BOTH documents; the scope the learning episode built holds "
                    "the first and not the second"))
    out.append(case("hpd", 15, "EVALUATION", "MULTI_SOURCE", "INTERMEDIATE",
                    "Which list acts as the reference for every dose that the ward gives later, "
                    "and what does the wording of a label become when the amount is not printed "
                    "next to its name?",
                    [ev("hpd", c, 6), ev("hpd", d, 2)], [], ["ANSWERED"], "NONE", "NONE",
                    "the answer needs BOTH documents; the second one is outside the scope the "
                    "learning episode built"))
    out.append(case("hpd", 16, "EVALUATION", "AMBIGUOUS", "INTERMEDIATE",
                    "What does a pharmacy check on a unit dose before it hands the dose over?",
                    [ev("hpd", b, 8), ev("hpd", d, 7)], [], ["QUALIFIED", "ABSTAINED", "ANSWERED"],
                    "NONE", "NONE",
                    "the corpus supports two readings of the question - the barcode mismatch and "
                    "the printed label - and a correct answer must carry both"))
    out.append(case("hpd", 17, "EVALUATION", "INCOMPLETE", "INTERMEDIATE",
                    "What does an unrecorded unit dose cost a pharmacy, and which cabinet is the "
                    "right one for it?",
                    [ev("hpd", a, 7)], [], ["QUALIFIED", "ABSTAINED"], "NONE", "NONE",
                    "the corpus holds the first half of the answer and nothing about the second, "
                    "so the workspace cannot carry the whole answer",
                    answerable=False))
    out.append(case("hpd", 18, "EVALUATION", "UNSUPPORTED", "INTRODUCTORY",
                    "How does a railway interlocking system record the position of a signal?",
                    [], [], ["ABSTAINED"], "NONE", "NONE",
                    "the corpus says nothing about the subject; no scope can carry the evidence, "
                    "so the workspace does NOT hold the answer and a refusal is CORRECT — the "
                    "denominator of a false rejection is the refusals of ANSWERABLE cases only",
                    answerable=False))
    out.append(case("hpd", 19, "EVALUATION", "CONFLICT", "INTERMEDIATE",
                    "Does the barcode of a medicine carry the strength of that medicine?",
                    [ev("hpd", b, 0), ev("hpd", c, 8)], [], ["QUALIFIED", "ABSTAINED", "ANSWERED"],
                    "NONE", "CONFLICTING_SOURCES",
                    "two documents contradict each other on the point, and a correct answer must "
                    "carry BOTH sides of the contradiction"))
    out.append(case("hpd", 20, "EVALUATION", "CORROBORATED", "INTRODUCTORY",
                    "What check stands between a unit dose and the ward that receives it?",
                    [ev("hpd", a, 8)], [ev("hpd", d, 8)], ["ANSWERED"], "TWO_SOURCES", "NONE",
                    "two documents state the same proposition; either one will do"))
    return out


def main() -> None:
    cases = wbe_cases() + hpd_cases()
    digests = {}
    for domain, spec in DOMAINS.items():
        for name in spec["files"]:
            path = CORPUS / domain / name
            digests[f"{domain}/{name}"] = hashlib.sha256(path.read_bytes()).hexdigest()
    # THE GOLD MUST BE A SENTENCE OF THE CORPUS, byte for byte.
    corpus_text = {}
    for domain, spec in DOMAINS.items():
        for name in spec["files"]:
            corpus_text[(domain, name)] = (CORPUS / domain / name).read_text()
    for entry in cases:
        for evidence in entry["necessaryEvidence"] + entry["allowedEvidence"]:
            text = corpus_text[(entry["domain"], evidence["corpusFile"])]
            assert evidence["proposition"] in text, evidence
            assert "\n" not in evidence["proposition"], evidence
    document = {
        "split": "crj-01-reach-dev-cases",
        "version": "1.0",
        "date": "2026-11-27",
        "sealed": False,
        "sealedNote": "the sealed evaluation is NOT EXECUTED; this is a DEV split on synthetic, "
                      "test-owned data",
        "author": {
            "role": "the IMPLEMENTER (the case author)",
            "independence": "NONE - two separately commissioned DSH case-author subagents were "
                            "given the corpus and the oracle contract and neither delivered a "
                            "case set; these cases are the implementer's own, so they are NOT "
                            "independent of the implementer's mechanism. The milestone says so "
                            "wherever it states the roles. (A third subagent, the corpus-author, "
                            "was given the CORPUS contract only - never the oracle contract - and "
                            "wrote four of the eight corpus documents before it stopped.)",
            "corpusRole": "the corpus was written by the IMPLEMENTER as well: a corpus-author "
                          "subagent was commissioned and stopped after four of the eight "
                          "documents, and the implementer wrote the other four and the repairs. "
                          "The corpus carries no gold - the answers are extracted from it by the "
                          "production acquisition path - so the corpus's authorship does not "
                          "weaken the oracle, but the corpus's AUTHORSHIP is the implementer's "
                          "and is stated here rather than attributed to the subagent.",
        },
        "provenance": {
            "corpusSha256": digests,
            "corpusAuthor": "the IMPLEMENTER - a corpus-author subagent was commissioned and "
                            "stopped after four of the eight documents; the rest and two repairs "
                            "are the implementer's",
            "authoring": "the layout is fixed by the measurement's needs (three disjoint slices; "
                         "the validation golds in the identification document, and one of them in "
                         "the reconciliation document, so that the declared scope reproduces the "
                         "reference on every held-out case; the evaluation golds placed inside "
                         "and outside the scope the learning episode builds), and every question "
                         "and gold was written by the implementer",
        },
        "domains": [
            {
                "id": domain,
                "subject": spec["subject"],
                "files": spec["files"],
                "declaredScope": spec["declared"],
            }
            for domain, spec in DOMAINS.items()
        ],
        "slices": {
            "PROPOSAL": "the learning episode's own cases; a competence may only be learned from "
                        "them, and their golds sit in TWO documents so the episode draws on two "
                        "sources",
            "VALIDATION": "the held-out cases the promotion is decided on; their golds sit in "
                          "the identification document (four of the five) and in the "
                          "reconciliation document (one), and their questions use only the "
                          "vocabulary of the DECLARED documents, so a restricted reading over "
                          "the support reproduces the reference on every one of them",
            "EVALUATION": "the measured cases; their golds sit where the arms can be told apart - "
                          "inside the scope the learning episode built, outside it but inside the "
                          "declared scope, or outside both",
        },
        "cases": cases,
    }
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(document, indent=1, ensure_ascii=False) + "\n")
    print(f"wrote {OUT} with {len(cases)} cases")


if __name__ == "__main__":
    main()
