#!/usr/bin/env python3
"""CRJ-01 / EPISTEMIC-NECESSITY — the case-author output checker.

The checker is the case contract's executable half. It verifies the SHAPE of an independent
case author's file and the ONE property that can be verified mechanically: every proposition the
author names must be a sentence of the corpus file the author names, byte for byte.

It cannot tell whether a necessary set is really minimal, whether a paraphrase really is a
paraphrase, or whether a contradiction really is a contradiction. Those are the author's
judgement and the milestone's reviewer's; the checker does not pretend to be them.

Usage:
    python3 scripts/necessity-case-check.py <file.json> [<file.json> ...]

Exit code 0 = every named file is green; 1 = at least one is red.
"""

import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
CORPUS = ROOT / "matrix-core/src/test/resources/crj01-reach"

CONDITIONS = {
    "LOWER_RANKED_NECESSARY",
    "JOINTLY_NECESSARY",
    "ALTERNATIVE_SUFFICIENT",
    "MISSING_PREREQUISITE",
    "CONTRADICTION",
    "SUPERSESSION",
    "CHANGED_BINDINGS",
    "AMBIGUITY",
    "UNANSWERABLE",
}

DISPOSITIONS = {"ANSWERED", "QUALIFIED", "ABSTAINED"}
SLICES = {"PROPOSAL", "VALIDATION", "EVALUATION"}
LEVELS = {"INTRODUCTORY", "INTERMEDIATE"}

# the assignment -> the domain it may write about, and the conditions it must cover
# the assignment -> the domain it may write about, the conditions it must cover and the slice
# mix it must use. A1-A4 are the FIRST ROUND; A5 and A6 are the FOLLOW-UP, commissioned because
# the production promotion rule needs five held-out cases and the first round produced two per
# domain.
ASSIGNMENTS = {
    "A1": ("wbe", ["LOWER_RANKED_NECESSARY", "JOINTLY_NECESSARY", "ALTERNATIVE_SUFFICIENT",
                   "MISSING_PREREQUISITE"], {"PROPOSAL": 1, "VALIDATION": 1, "EVALUATION": 2}),
    "A2": ("wbe", ["CONTRADICTION", "SUPERSESSION", "CHANGED_BINDINGS", "AMBIGUITY"],
           {"PROPOSAL": 1, "VALIDATION": 1, "EVALUATION": 2}),
    "A3": ("hpd", ["LOWER_RANKED_NECESSARY", "JOINTLY_NECESSARY", "ALTERNATIVE_SUFFICIENT",
                   "MISSING_PREREQUISITE"], {"PROPOSAL": 1, "VALIDATION": 1, "EVALUATION": 2}),
    "A4": ("hpd", ["CONTRADICTION", "SUPERSESSION", "CHANGED_BINDINGS", "UNANSWERABLE"],
           {"PROPOSAL": 1, "VALIDATION": 1, "EVALUATION": 2}),
    "A5": ("wbe", ["LOWER_RANKED_NECESSARY", "JOINTLY_NECESSARY", "ALTERNATIVE_SUFFICIENT",
                   "CHANGED_BINDINGS"], {"PROPOSAL": 0, "VALIDATION": 4, "EVALUATION": 0}),
    "A6": ("hpd", ["LOWER_RANKED_NECESSARY", "JOINTLY_NECESSARY", "ALTERNATIVE_SUFFICIENT",
                   "CHANGED_BINDINGS"], {"PROPOSAL": 0, "VALIDATION": 4, "EVALUATION": 0}),
}

ID_PATTERN = re.compile(r"^EN-(?P<domain>[A-Z]{3})-(?P<assignment>[A-Z]\d)-(?P<n>\d+)$")


def sentences_of(path: pathlib.Path) -> list:
    """The sentences of a corpus document, as the production extractor splits them.

    The production splitter is a REGEX (a blank line, optionally indented), so the checker
    must use one too: a plain str.split on the same characters would find nothing, and every
    proposition of every author would be reported as not verbatim.
    """
    out = []
    for block in re.split(r"\n\s*\n", path.read_text(encoding="utf-8")):
        trimmed = block.strip()
        if not trimmed or trimmed.startswith("#"):
            continue
        out.append(trimmed)
    return out


def corpus_sentences(domain: str) -> dict:
    """Every sentence of every file of a domain, by file name."""
    folder = CORPUS / domain
    return {path.name: sentences_of(path) for path in sorted(folder.glob("*.md"))}


def check_file(path: pathlib.Path) -> list:
    """The defects of one file; an empty list is green."""
    defects = []

    def fail(message: str) -> None:
        defects.append(message)

    try:
        raw = path.read_text(encoding="utf-8")
    except OSError as error:
        return [f"the file cannot be read: {error}"]
    try:
        doc = json.loads(raw)
    except json.JSONDecodeError as error:
        return [f"the file is not valid JSON: {error}"]

    assignment = doc.get("assignment")
    if assignment not in ASSIGNMENTS:
        return [f"the assignment is {assignment!r}; it must be one of {sorted(ASSIGNMENTS)}"]
    domain, expected_conditions, expected_slices = ASSIGNMENTS[assignment]

    if doc.get("domain") != domain:
        fail(f"the domain is {doc.get('domain')!r}; assignment {assignment} is for {domain!r}")
    if not str(doc.get("independence", "")).strip():
        fail("the independence field is empty; the milestone records the author's access")

    cases = doc.get("cases")
    if not isinstance(cases, list):
        return defects + ["the cases field is not a list"]
    if len(cases) != 4:
        fail(f"there are {len(cases)} cases; the assignment is exactly four")

    sentences = corpus_sentences(domain)
    for name, found in sentences.items():
        if not found:
            fail(f"the corpus file {name} yields no sentence — the checker cannot verify "
                 f"anything against it")
    ids = []
    questions = []
    slice_counts = {"PROPOSAL": 0, "VALIDATION": 0, "EVALUATION": 0}
    conditions = []
    gold_by_slice = {"PROPOSAL": set(), "VALIDATION": set(), "EVALUATION": set()}
    gold_owner = {}

    for index, case in enumerate(cases):
        label = f"case {index + 1}"
        if not isinstance(case, dict):
            fail(f"{label} is not an object")
            continue

        case_id = case.get("id")
        label = f"case {case_id!r}"
        if not isinstance(case_id, str) or not ID_PATTERN.match(case_id):
            fail(f"{label}: the id does not match EN-<DOMAIN>-<ASSIGNMENT>-<n>")
        else:
            ids.append(case_id)
            match = ID_PATTERN.match(case_id)
            if match.group("domain") != domain.upper():
                fail(f"{label}: the id's domain part is {match.group('domain')!r}, "
                     f"the assignment's domain is {domain.upper()!r}")
            if match.group("assignment") != assignment:
                fail(f"{label}: the id's assignment part is {match.group('assignment')!r}, "
                     f"the file's assignment is {assignment!r}")

        question = case.get("question")
        if not isinstance(question, str) or not question.strip():
            fail(f"{label}: the question is empty")
        else:
            questions.append(question)
            if not question.rstrip().endswith("?"):
                fail(f"{label}: the question does not end with a question mark")

        if case.get("domain") != domain:
            fail(f"{label}: the domain is {case.get('domain')!r}, the assignment's is {domain!r}")

        slice_name = case.get("slice")
        if slice_name not in SLICES:
            fail(f"{label}: the slice is {slice_name!r}")
        else:
            slice_counts[slice_name] += 1

        condition = case.get("condition")
        if condition not in CONDITIONS:
            fail(f"{label}: the condition is {condition!r}")
        else:
            conditions.append(condition)

        if case.get("level") not in LEVELS:
            fail(f"{label}: the level is {case.get('level')!r}")

        dispositions = case.get("allowedDispositions")
        if not isinstance(dispositions, list) or not dispositions:
            fail(f"{label}: the allowedDispositions are empty")
        else:
            for disposition in dispositions:
                if disposition not in DISPOSITIONS:
                    fail(f"{label}: the disposition {disposition!r} is not known")

        answerable = case.get("answerable")
        if not isinstance(answerable, bool):
            fail(f"{label}: the answerable field is not a boolean")
        elif condition in {"MISSING_PREREQUISITE", "UNANSWERABLE"} and answerable:
            fail(f"{label}: a {condition} case cannot be answerable")
        elif condition not in {"MISSING_PREREQUISITE", "UNANSWERABLE"} and not answerable:
            fail(f"{label}: a {condition} case is answerable")

        necessary = case.get("necessaryEvidence")
        if not isinstance(necessary, list) or not necessary:
            fail(f"{label}: the necessaryEvidence is empty")
            necessary = []
        for entry in necessary:
            problem = _proposition_problem(entry, domain, sentences, f"{label} necessary")
            if problem:
                fail(problem)
            elif slice_name in SLICES:
                key = (entry["corpusFile"], entry["proposition"])
                gold_by_slice[slice_name].add(key)
                if key in gold_owner and gold_owner[key] != slice_name:
                    fail(f"{label}: the necessary sentence {entry['corpusFile']} "
                         f"is also a necessary sentence of a {gold_owner[key]} case; "
                         f"a necessary sentence may not cross slices")
                gold_owner.setdefault(key, slice_name)

        alternatives = case.get("alternativeSufficientSets")
        if not isinstance(alternatives, list):
            fail(f"{label}: the alternativeSufficientSets is not a list")
            alternatives = []
        if condition == "ALTERNATIVE_SUFFICIENT" and not alternatives:
            fail(f"{label}: an ALTERNATIVE_SUFFICIENT case must name at least one "
                 f"alternative sufficient set")
        if condition != "ALTERNATIVE_SUFFICIENT" and alternatives:
            fail(f"{label}: the condition is {condition!r}, so no alternative sufficient set "
                 f"may be named")
        for position, alternative in enumerate(alternatives):
            if not isinstance(alternative, list) or not alternative:
                fail(f"{label}: the alternative sufficient set {position} is empty")
                continue
            for entry in alternative:
                problem = _proposition_problem(entry, domain, sentences,
                                               f"{label} alternative {position}")
                if problem:
                    fail(problem)

        for field in ("corroboration", "contradiction"):
            value = case.get(field)
            if not isinstance(value, str) or not value.strip():
                fail(f"{label}: the {field} field is empty")
        note = case.get("note")
        if not isinstance(note, str) or len(note.strip()) < 20:
            fail(f"{label}: the note is too short to show the judgement")

    if slice_counts != expected_slices:
        fail(f"the slices are {slice_counts}; the assignment is {expected_slices}")

    if sorted(conditions) != sorted(expected_conditions):
        fail(f"the conditions are {sorted(conditions)}; the assignment must carry "
             f"{sorted(expected_conditions)}")

    if len(set(ids)) != len(ids):
        fail("two cases share an id")
    if len(set(questions)) != len(questions):
        fail("two cases share a question")

    if not any(c in {"LOWER_RANKED_NECESSARY", "JOINTLY_NECESSARY", "ALTERNATIVE_SUFFICIENT"}
               for c in expected_conditions):
        # the assignment carries no condition whose necessary set is the point, so the
        # requirement does not apply to it
        pass
    else:
        evaluation_conditions = [case.get("condition") for case in cases
                                 if case.get("slice") == "EVALUATION"]
        if evaluation_conditions and not any(
                c in {"LOWER_RANKED_NECESSARY", "JOINTLY_NECESSARY", "ALTERNATIVE_SUFFICIENT"}
                for c in evaluation_conditions):
            fail("no EVALUATION case carries a condition whose necessary set is the point")

    return defects


def _proposition_problem(entry, domain: str, sentences: dict, label: str) -> str:
    """The defect of one {corpusFile, proposition} entry, or an empty string."""
    if not isinstance(entry, dict):
        return f"{label}: the entry is not an object"
    name = entry.get("corpusFile")
    proposition = entry.get("proposition")
    if name not in sentences:
        return f"{label}: the corpus file {name!r} is not a file of the domain {domain!r}"
    if proposition not in sentences[name]:
        return (f"{label}: the proposition is not a sentence of {name} VERBATIM "
                f"— it must be byte-identical to the corpus sentence")
    return ""


def main(argv) -> int:
    if len(argv) < 2:
        print("usage: necessity-case-check.py <file.json> [...]", file=sys.stderr)
        return 2
    red = 0
    for argument in argv[1:]:
        path = pathlib.Path(argument)
        defects = check_file(path)
        if defects:
            red += 1
            print(f"RED   {path}")
            for defect in defects:
                print(f"        - {defect}")
        else:
            print(f"GREEN {path}")
    print(f"{len(argv) - 1 - red}/{len(argv) - 1} files green")
    return 1 if red else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
