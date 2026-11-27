"""A deterministic FALLBACK split builder for CRJ-01 / ADAPTIVE-COMPETENCE.

It exists because the milestone's independent case author did not deliver the questions in
the time available. Every choice is derived from the SEPARATION PROBE — the instrument that
measured, for every claim of the corpus, the focused question about it and the documents
the reference reading of that question cites — so the split is reproducible byte for byte.

The LIMITATION is declared rather than hidden: the questions are FORMULAIC (one form, the
probe's), where a human case author would have written natural questions. The gold is the
production extraction's own sentence, byte for byte, and the corpus is the milestone's own
synthetic data, so the cases remain verifiable against the corpus's bytes.

    python3 .opencode/adaptive-split-fallback.py <output-path>
"""

import json
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
REPORT = REPO / "docs-v2/engineering/CRJ-01-ADAPTIVE-CORPUS-REPORT.json"
PROBE = REPO / "docs-v2/engineering/CRJ-01-ADAPTIVE-SEPARATION-PROBE.json"

# The arrangement, per domain. The slots are the documents the cases are drawn from:
#
#   OBSERVED       the learning episode's own observations — the competence is LEARNED from
#                  them, so they can never be a held-out case
#   VALIDATION     the cases the first promotion is VALIDATED on
#   NOVEL          the cases the REVISION is validated on
#   OUT_OF_SUPPORT the questions the first competence cannot answer; the first of them is
#                  the FAILURE EPISODE the revision is proposed from
#   SUBSEQUENT     the DISTINCT SUBSEQUENT TASK: a question of the failure episode's own
#                  document that no other case uses. The first competence cannot answer it
#                  (its support does not hold that document), and the revision — whose
#                  support does hold it — can. It is therefore the task on which the
#                  changed competence is MEASURED, and it is a different question, not the
#                  failure question with another name.
#
# The arrangement is by SLOT and the slot is part of the case's identity: the cases of a
# slot come from ONE document, so a case's document is decidable from its subset alone.
SUBSETS = [("OBSERVED", 2), ("VALIDATION", 5), ("NOVEL", 5), ("OUT_OF_SUPPORT", 2),
           ("SUBSEQUENT", 1)]


def slot_of(name):
    """The slot letter a document's file name carries."""
    parts = name.split("-")
    return parts[1] if len(parts) > 2 else parts[-1]


def main():
    output = Path(sys.argv[1]) if len(sys.argv) > 1 else REPO / "docs-v2/engineering/CRJ-01-ADAPTIVE-DEV-SPLIT.json"
    report = json.loads(REPORT.read_text(encoding="utf-8"))
    probe = json.loads(PROBE.read_text(encoding="utf-8"))

    # the probe is the authority for the question and for the NARROW claims
    narrow = {}
    for line in probe["claims"]:
        if line["narrow"]:
            narrow.setdefault((line["domain"], line["ownDocument"]), []).append(line)

    cases = []
    for domain_report in report["domains"]:
        domain = domain_report["domain"]
        files = {document["file"]: document for document in domain_report["documents"]}
        # the PRODUCTION claim id of a proposition, as the corpus report records it
        production_id = {}
        for document in domain_report["documents"]:
            for claim in document["claims"]:
                production_id[claim["proposition"]] = claim["claimId"]
        counts = {name: len(narrow.get((domain, name), [])) for name in files}
        # the SUPPORT slot is the document with the most narrow claims; it carries the
        # OBSERVED case that gives the one-document support, the five VALIDATION golds and
        # the five NOVEL golds, so that every cell of the grid can serve them
        ranked = sorted(files, key=lambda name: (-counts[name], name))
        support_slot = ranked[0]
        second_slot = ranked[1]
        # the OUT_OF_SUPPORT slot is a document no support covers, with narrow claims of its own
        out_slot = next(name for name in ranked[2:] if counts[name] >= 2)
        print("%s: support=%s (%d narrow) second=%s (%d narrow) out=%s (%d narrow)" % (
            domain, support_slot, counts[support_slot], second_slot, counts[second_slot],
            out_slot, counts[out_slot]))

        support_claims = narrow[(domain, support_slot)]
        second_claims = narrow[(domain, second_slot)]
        out_claims = narrow[(domain, out_slot)]
        if len(out_claims) < 3:
            raise SystemExit("the out slot of %s holds only %d narrow claim(s): the distinct "
                             "subsequent task needs a third one" % (domain, len(out_claims)))
        plan = [("OBSERVED", support_claims[0]), ("OBSERVED", second_claims[0])]
        plan += [("VALIDATION", claim) for claim in support_claims[1:6]]
        plan += [("NOVEL", claim) for claim in support_claims[6:11]]
        plan += [("OUT_OF_SUPPORT", claim) for claim in out_claims[:2]]
        # THE DISTINCT SUBSEQUENT TASK: the THIRD narrow claim of the failure episode's
        # document, used by no other case
        plan += [("SUBSEQUENT", out_claims[2])]
        for index, (subset, claim) in enumerate(plan, start=1):
            name = claim["ownDocument"]
            cases.append({
                "id": "AC-%s-%02d" % (domain.upper(), index),
                "domain": domain,
                "class": subset if subset in ("OUT_OF_SUPPORT", "SUBSEQUENT") else "TRANSFER",
                "subset": subset,
                "level": "INTRODUCTORY",
                "question": claim["question"],
                "positive": True,
                "expectedField": "PREMISE",
                "expectedDisposition": "SUPPORTED",
                "expectedProvenance": "",
                "corpusFile": name,
                "corpusSha256": files[name]["sha256"],
                "goldClaims": [{"proposition": claim["proposition"],
                                "claimId": production_id[claim["proposition"]],
                                "corpusFile": name}],
                "idempotencyKey": "ac-%s-%02d" % (domain, index),
            })

    split = {
        "split": "crj-01-adaptive-dev-split",
        "version": "1.0",
        "date": "2026-11-27",
        "sealed": False,
        "sealedNote": "the sealed evaluation is NOT EXECUTED; this is a DEV split on synthetic, "
                      "test-owned data",
        "provenance": "the questions and the gold are selected from the separation probe's "
                      "narrow claims; the questions are the probe's own focused questions",
        "arrangement": {
            "subsets": {
                "OBSERVED": "the learning episode's own observations - the competence is "
                            "LEARNED from them, so they can never be a held-out case",
                "VALIDATION": "the cases the FIRST promotion is validated on",
                "NOVEL": "the cases the REVISION is validated on",
                "OUT_OF_SUPPORT": "the questions the first competence cannot answer; the "
                                   "first of them is the FAILURE EPISODE the revision is "
                                   "proposed from",
                "SUBSEQUENT": "the DISTINCT SUBSEQUENT TASK - a claim of the failure "
                              "episode's own document that NO OTHER CASE USES. The first "
                              "competence cannot answer it and the revision can; it is the "
                              "task on which the changed competence is measured, and it is a "
                              "DIFFERENT question, not the failure question with another name"
            },
            "disjointness": "the SUBSEQUENT case is disjoint from the learning episode's "
                            "observations, from the failure episode's question and from the "
                            "revision's validation cases: it is a different claim of the "
                            "failure episode's own document, and no other case uses that "
                            "claim",
            "generator": "the split is generated by .opencode/adaptive-split-fallback.py, "
                         "retained at docs-v2/engineering/evidence/crj01-adaptive-competence/"
                         "adaptive-split-fallback.py; the suite asserts the disjointness"
        },
        "domains": [{"id": d["domain"], "subject": d["domain"], "files": d["files"]}
                    for d in report["domains"]],
        "cases": cases,
    }
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(split, indent=2), encoding="utf-8")
    print("cases", len(cases), "->", output)
    for case in cases:
        print(" ", case["id"], case["subset"], case["corpusFile"], "|", case["question"][:72])


if __name__ == "__main__":
    main()
