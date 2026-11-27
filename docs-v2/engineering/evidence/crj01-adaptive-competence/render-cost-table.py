"""Renders the cost table of the milestone document FROM THE RETAINED ARTEFACT.

The table is the one place where a number can drift: the timings are machine state and
every re-run moves them, so a table written by hand is a table that will be wrong. This
script re-derives it, and the document names the command.

    python3 docs-v2/engineering/evidence/crj01-adaptive-competence/render-cost-table.py
"""

import json
from pathlib import Path

EVIDENCE_DIR = Path(__file__).resolve().parent
COST = EVIDENCE_DIR / "CRJ-01-ADAPTIVE-COST-ROWS.json"
DOCUMENT = EVIDENCE_DIR.parent.parent / "CRJ-01-ADAPTIVE-COMPETENCE.md"


def millis(nanos):
    return "{:.2f}".format(nanos / 1_000_000.0)


def signed(nanos):
    """A number with its sign, the way a reader writes it.

    :param nanos: the number
    :return: the signed number in milliseconds
    """
    return ("+" if nanos >= 0 else "−") + "{:.2f}".format(abs(nanos) / 1_000_000.0)


def interval(values):
    """The paired interval, with the sign of each bound.

    The sign is the bound's OWN: a bound that is positive is written with a plus, and the
    reader can see which side of zero the interval sits on without doing arithmetic.

    :param values: the (low, high) bounds
    :return: the interval, in milliseconds
    """
    low, high = values
    return "[{}, {}] ms".format(signed(low), signed(high))


def main():
    cost = json.loads(COST.read_text(encoding="utf-8"))
    rows = ["| cell | reference (warm median) | procedure (warm median) | the paired interval "
            "| verdict |",
            "|---|---|---|---|---|"]
    cheaper = 0
    for key, cell in cost["cells"].items():
        verdict = cell["verdict"]
        if verdict == "CHEAPER":
            cheaper += 1
        rows.append("| `{}` | {} ms | {} ms | {} | {} |".format(
            key, millis(cell["referenceWarmMedianNanos"]),
            millis(cell["procedureWarmMedianNanos"]),
            interval(cell["pairedDifferenceIntervalNanos"]),
            "**`CHEAPER`**" if verdict == "CHEAPER" else "`NOT ESTABLISHED`"))
    table = "\n".join(rows)

    document = DOCUMENT.read_text(encoding="utf-8")
    start = document.index("| cell | reference (warm median) |")
    end = document.index("\n\n", document.index("| `dmy/wide/2`")) if "| `dmy/wide/2`" in document \
        else document.index("\n\n", start)
    document = document[:start] + table + document[end:]

    not_established = len(cost["cells"]) - cheaper
    # ONLY THE TABLE IS REWRITTEN. The prose carries no count that a re-run could falsify,
    # so there is nothing in it for this tool to keep in step — a tool that rewrites prose
    # it does not understand is a tool that will one day rewrite prose wrongly.
    DOCUMENT.write_text(document, encoding="utf-8")
    print("the cost table is rendered from the retained artefact: {} CHEAPER, {} NOT "
          "ESTABLISHED".format(cheaper, not_established))


if __name__ == "__main__":
    main()
