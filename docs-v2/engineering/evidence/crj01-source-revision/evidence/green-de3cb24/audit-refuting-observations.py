import hashlib
import json
from pathlib import Path

fixture = Path("../../green-frozen-v2")
documents = {hashlib.sha256(path.read_bytes()).hexdigest(): path.read_bytes()
             for path in fixture.glob("*.txt")}
rows = [json.loads(line) for line in Path("served-and-import-traces.jsonl").read_text().splitlines()]
imports = {(row["record"]["workspace"], row["record"]["claim"]): row["record"]
           for row in rows if row["type"] == "import"}

def importer_evidence_id(claim, source):
    result = 0xcbf29ce484222325
    for byte in f"{claim}|evidence|{source}".encode("utf-8"):
        result = ((result ^ byte) * 0x100000001b3) & ((1 << 64) - 1)
    return format(result & 0xffffffff, "x")

counts = {"refuted": 0, "unsupported": 0, "distinct_canonical_observer": 0}
for row in rows:
    if row["type"] != "served" or row["status"] >= 400 or not row["response"].get("premise"):
        continue
    premise = row["response"]["premise"]
    observations = premise["refutingObservations"]
    if premise["status"] == "UNSUPPORTED":
        assert not observations
        counts["unsupported"] += 1
        continue
    if premise["status"] != "REFUTED":
        continue
    assert len(observations) == 1
    observation = observations[0]
    refuter = imports[(row["workspace"], observation["observationClaimId"])]
    canonical = imports[(row["workspace"], observation["canonicalClaimId"])]
    assert canonical["proposition"] == refuter["proposition"]
    assert observation["sourceId"] == refuter["source"]
    assert observation["sourceSha256"] == refuter["hash"]
    assert observation["evidenceId"] == importer_evidence_id(refuter["claim"], refuter["source"])
    assert observation["excerpt"] == refuter["proposition"]
    span = observation["segmentSpan"]
    document = documents[refuter["hash"]]
    assert span["unit"] == "CHARACTERS"
    assert 0 <= span["start"] <= refuter["spanStart"] < refuter["spanEnd"] <= span["end"] <= len(document)
    excerpt = observation["excerpt"].encode("ascii")
    segment = document[span["start"]:span["end"]]
    assert segment.count(excerpt) == 1
    assert span["start"] + segment.index(excerpt) == refuter["spanStart"]
    assert refuter["spanStart"] + len(excerpt) == refuter["spanEnd"]
    assert premise["refutingClaimIds"] == [refuter["claim"]]
    assert premise["refutingSourceIds"] == [refuter["source"]]
    counts["refuted"] += 1
    if observation["canonicalClaimId"] != observation["observationClaimId"]:
        counts["distinct_canonical_observer"] += 1
assert counts["refuted"] > 0 and counts["unsupported"] > 0
assert counts["distinct_canonical_observer"] == 1
print(json.dumps(counts, sort_keys=True))
