#!/usr/bin/env python3
"""C1 — the independent re-measurement of the MATRIX learning window.

LANDED 2026-11-15. The C1 diagnosis (docs-v2/engineering/C1-LEARNING-WINDOW-DIAGNOSIS.md)
was written against a measurement script that lived outside the repository, so a third
party could not re-run it. This is that script, unchanged in substance: it re-implements
the PRODUCTION path read-only and prints the numbers the diagnosis states.

WHAT IT IS NOT: it is a re-implementation, not the production code. Its agreement with
the production path is asserted by the diagnosis (18 of 18 real and 6 of 6 synthetic
numbers matched), and the production measurement is the one that decides.

Reimplements the PRODUCTION path exactly (read-only, no Gradle):
  EpisodeFeatureExtractor.encode(entry)                       -> matrix-brain-runtime/.../EpisodeFeatureExtractor.java:41-46
      = PersistentHdcStore.hashToVector(input + " " + reply, 256)   -> PersistentHdcStore.java:394-407
      packed LSB-first into 4 longs                             -> EpisodeFeatureExtractor.java:55-66
  TsetlinTrainer.pack(words)  (inputBits = 16)                 -> matrix-core/.../TsetlinTrainer.java:361-376
      XOR-parity fold: source bit j -> target bit (j % 16)
  Production label rule                                         -> RealSleepScheduler.java:249
      confidence >= 0.7 AND accepted
  Production window selection                                   -> RealSleepScheduler.java:237-240
      last K_MAX = 20 entries in file order
  E1 chronological split 60/20/20                              -> E1LearningEvidenceTest.java:113-122
"""
import json, hashlib, collections, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
REAL = ROOT / "data" / "mind" / "episodic.ndjson"
SYN  = ROOT / "matrix-brain-runtime/src/test/resources/e1/synthetic-episodes.ndjson"
DIM = 256
TRAINER_BITS = 16
K_MAX = 20

# ---- production primitives -------------------------------------------------

def java_lower(s):
    # Java String.toLowerCase() under en_US.UTF-8 == Python .lower() for this corpus
    # (verified: no uppercase non-ASCII, no Turkish-I exposure).
    return s.lower()

def java_split_ws(s):
    # Java String.split("\\s+") with Java's \s = [ \t\n\x0B\f\r]
    import re
    return re.split(r"[ \t\n\x0b\f\r]+", s)

def hash_to_vector(text, dim):
    """PersistentHdcStore.hashToVector — FNV-1a 32-bit per token, one bit per token."""
    bits = set()
    if text is None or text.strip() == "":
        return bits
    for tok in java_split_ws(java_lower(text)):
        if tok.strip() == "":
            continue
        h = 0x811c9dc5
        for ch in tok:              # Java charAt == UTF-16 code unit
            cp = ord(ch)
            if cp > 0xFFFF:         # surrogate pair -> two UTF-16 units
                cp -= 0x10000
                for u in (0xD800 + (cp >> 10), 0xDC00 + (cp & 0x3FF)):
                    h ^= u
                    h = (h * 0x01000193) & 0xFFFFFFFF
                continue
            h ^= cp
            h = (h * 0x01000193) & 0xFFFFFFFF
        bits.add((h & 0x7fffffff) % dim)
    return bits

def encode_feature(input_text, reply_text):
    """EpisodeFeatureExtractor.encode(Entry) -> set of set-bit indices in 256 bits."""
    return hash_to_vector(input_text + " " + reply_text, DIM)

def pack_words(bitset):
    """EpisodeFeatureExtractor.pack — word i>>>6, bit (i&63) inside it (Java shift wraps at 64)."""
    words = [0] * ((DIM + 63) // 64)
    for i in bitset:
        w = i >> 6
        if w < len(words):
            words[w] |= (1 << (i & 63))
    return words

def fold(words, input_bits=TRAINER_BITS):
    """TsetlinTrainer.pack — XOR-parity: source bit j -> target bit (j % input_bits)."""
    packed = 0
    src = 0
    for word in words:
        for b in range(64):
            if (word >> b) & 1:
                packed ^= 1 << (src % input_bits)
            src += 1
    return packed

def feature_key(input_text, reply_text):
    return tuple(pack_words(encode_feature(input_text, reply_text)))

def label_of(e):
    """RealSleepScheduler.java:249 — the production label rule."""
    return bool(e.get("confidence", 0.0)) and float(e.get("confidence", 0.0)) >= 0.7 and bool(e.get("accepted"))

def informative_bits(rows, width):
    """TsetlinTrainer.countInformativeBits (word-indexed, post-fix)."""
    saw0 = [False]*width; saw1 = [False]*width
    for words in rows:
        for j in range(width):
            w = j >> 6
            set_ = w < len(words) and ((words[w] >> (j & 63)) & 1) != 0
            if set_: saw1[j] = True
            else:    saw0[j] = True
    return sum(1 for j in range(width) if saw0[j] and saw1[j])

# ---- data ------------------------------------------------------------------

def sha256(p):
    h = hashlib.sha256()
    with open(p, "rb") as f:
        for b in iter(lambda: f.read(65536), b""):
            h.update(b)
    return h.hexdigest()

raw_lines = [l for l in REAL.read_text(encoding="utf-8").splitlines() if l.strip()]
entries = [json.loads(l) for l in raw_lines]

print("=" * 78)
print("C1 — independent re-measurement (no build; pure data + source reimplementation)")
print("=" * 78)
print("real log path        :", REAL.relative_to(ROOT))
print("real log sha256      :", sha256(REAL))
print("manifest sha256      :", json.loads((ROOT/"matrix-brain-runtime/src/test/resources/e1/DATASET-MANIFEST.json").read_text())["real"]["sha256"])
print("non-blank lines      :", len(raw_lines))
print("parsed entries       :", len(entries))

# --- 1. label distribution of the whole real log
labels = [label_of(e) for e in entries]
pos = sum(labels); neg = len(labels) - pos
print()
print("[A] REAL EPISODIC LOG — total=%d  positives=%d  negatives=%d" % (len(entries), pos, neg))

# --- 2. distinct inputs + top repeats
inp = [e.get("input","") for e in entries]
rep = [e.get("reply","") for e in entries]
cnt_in = collections.Counter(inp)
cnt_out = collections.Counter(rep)
print("[B] distinct inputs=%d  distinct replies=%d" % (len(cnt_in), len(cnt_out)))
print("    top repeated inputs (count):")
for s, c in cnt_in.most_common(12):
    print("      %3d  %r" % (c, s[:78]))
print("    top repeated replies (count):")
for s, c in cnt_out.most_common(6):
    print("      %3d  %r" % (c, s[:78]))

# --- 3. feature extraction for all entries
feats = [pack_words(encode_feature(e.get("input",""), e.get("reply",""))) for e in entries]
folds = [fold(w) for w in feats]

# --- 4. ACTIVE production window: last K_MAX=20 entries in file order
w = entries[-K_MAX:]
wl = labels[-K_MAX:]
win_in = collections.Counter(e.get("input","") for e in w)
print()
print("[C] ACTIVE PRODUCTION WINDOW (last %d entries, RealSleepScheduler.java:237-240)" % K_MAX)
print("    size=%d  positives=%d  negatives=%d  distinct inputs=%d  distinct replies=%d"
      % (len(w), sum(wl), len(wl)-sum(wl),
         len({e.get("input","") for e in w}), len({e.get("reply","") for e in w})))
print("    window inputs (count):")
for s, c in win_in.most_common():
    print("      %3d  %r" % (c, s[:78]))
wfeats = [feats[i] for i in range(len(entries)-K_MAX, len(entries))]
wfolds = [folds[i] for i in range(len(entries)-K_MAX, len(entries))]
print("    window distinct 256-bit features=%d ; distinct 16-bit folds=%d"
      % (len({tuple(x) for x in wfeats}), len(set(wfolds))))

# --- 5. chronological 60/20/20 split (E1LearningEvidenceTest.java:113-122)
size = len(entries)
ntr = round(size*0.6); nva = round(size*0.2)
sp = {"train": entries[:ntr], "validation": entries[ntr:ntr+nva], "test": entries[ntr+nva:]}
print()
print("[D] CHRONOLOGICAL REAL SPLIT 60/20/20 (E1LearningEvidenceTest.java:113-122)")
for name in ("train","validation","test"):
    seg = sp[name]
    sl = [label_of(e) for e in seg]
    print("    %-11s size=%3d  positives=%3d  negatives=%3d  distinct inputs=%2d  distinct 256-bit feats=%2d  distinct folds=%2d"
          % (name, len(seg), sum(sl), len(sl)-sum(sl),
             len({e.get("input","") for e in seg}),
             len({tuple(pack_words(encode_feature(e.get("input",""), e.get("reply","")))) for e in seg}),
             len({fold(pack_words(encode_feature(e.get("input",""), e.get("reply","")))) for e in seg})))

# --- 6. collisions over the WHOLE log
print()
print("[E] COLLISIONS — whole real log")
fold2feat = collections.defaultdict(set)
fold2lab  = collections.defaultdict(set)
feat2lab  = collections.defaultdict(set)
for i, e in enumerate(entries):
    fk = tuple(feats[i]); fv = folds[i]
    fold2feat[fv].add(fk)
    fold2lab[fv].add(labels[i])
    feat2lab[fk].add(labels[i])
exact = {k: v for k, v in fold2feat.items() if len(v) > 1}
cross = {k: v for k, v in fold2lab.items() if len(v) > 1}
exactfeat = {k: v for k, v in feat2lab.items() if len(v) > 1}
print("    distinct 256-bit features : %d" % len({tuple(x) for x in feats}))
print("    distinct 16-bit folds     : %d" % len(set(folds)))
print("    folded groups with >1 distinct full feature (EXACT collisions)      : %d" % len(exact))
print("    folded groups carrying BOTH labels (CROSS-LABEL collisions)         : %d" % len(cross))
print("    distinct full features carrying BOTH labels                       : %d" % len(exactfeat))
print("    informative bits: whole log=%d  train split=%d"
      % (informative_bits(feats, DIM), informative_bits([pack_words(encode_feature(e.get("input",""), e.get("reply",""))) for e in sp["train"]], DIM)))
print()
for fv, fset in exact.items():
    print("    EXACT COLLISION folded value 0x%04x:" % fv)
    for fk in fset:
        idxs = [i for i in range(len(entries)) if tuple(feats[i]) == fk]
        print("      feature bits=%d idx=%s labels=%s" % (sum(bin(x).count('1') for x in fk), idxs,
              [labels[i] for i in idxs]))
        for i in idxs:
            print("        [%d] input=%r reply=%r conf=%s accepted=%s"
                  % (i, entries[i].get("input","")[:60], entries[i].get("reply","")[:50],
                     entries[i].get("confidence"), entries[i].get("accepted")))
for fv, labs in cross.items():
    print("    CROSS-LABEL COLLISION folded value 0x%04x labels=%s" % (fv, sorted(labs)))
    idxs = [i for i in range(len(entries)) if folds[i] == fv]
    for i in idxs:
        print("        [%d] input=%r reply=%r conf=%s accepted=%s label=%s"
              % (i, entries[i].get("input","")[:60], entries[i].get("reply","")[:50],
                 entries[i].get("confidence"), entries[i].get("accepted"), labels[i]))

# --- 7. label-rule anatomy: what drives positivity
print()
print("[F] LABEL RULE ANATOMY (confidence >= 0.7 AND accepted)")
import statistics
confs = [float(e.get("confidence", 0.0)) for e in entries]
accs = [bool(e.get("accepted")) for e in entries]
print("    confidence>=0.7 : %d / %d   (below: %d)" % (sum(c >= 0.7 for c in confs), len(confs), sum(c < 0.7 for c in confs)))
print("    accepted=true   : %d / %d   (false: %d)" % (sum(accs), len(accs), len(accs)-sum(accs)))
print("    confidence<0.7 AND accepted=true   : %d" % sum(1 for c,a in zip(confs,accs) if c < 0.7 and a))
print("    confidence>=0.7 AND accepted=false  : %d" % sum(1 for c,a in zip(confs,accs) if c >= 0.7 and not a))
print("    distinct confidence values: %s" % sorted(set(confs)))
print("    confidence histogram:")
ch = collections.Counter(confs)
for c in sorted(ch):
    n = ch[c]; np_ = sum(1 for e in entries if float(e.get("confidence",0.0))==c and label_of(e))
    print("      conf=%.4f  n=%3d  labelled-positive=%3d" % (c, n, np_))
print("    timestamps: first=%d last=%d" % (min(int(e.get('ts',0)) for e in entries),
                                             max(int(e.get('ts',0)) for e in entries)))

# --- 8. cross-check against the harness artifact
art = json.loads((ROOT/"docs-v2/engineering/E1-LEARNING-EVIDENCE.json").read_text())
print()
print("[G] CROSS-CHECK vs harness artifact docs-v2/engineering/E1-LEARNING-EVIDENCE.json")
checks = [
  ("real episodes", len(entries), art["real"]["distribution"]["episodes"]),
  ("real positives", pos, art["real"]["distribution"]["positives"]),
  ("real negatives", neg, art["real"]["distribution"]["negatives"]),
  ("distinct inputs", len(cnt_in), art["real"]["distribution"]["distinctInputs"]),
  ("distinct replies", len(cnt_out), art["real"]["distribution"]["distinctReplies"]),
  ("train size", len(sp["train"]), art["real"]["partitions"]["train"]),
  ("validation size", len(sp["validation"]), art["real"]["partitions"]["validation"]),
  ("test size", len(sp["test"]), art["real"]["partitions"]["test"]),
  ("distinct full features", len({tuple(x) for x in feats}), art["real"]["collisions"]["distinctFullFeatures"]),
  ("distinct folded values", len(set(folds)), art["real"]["collisions"]["distinctFoldedValues"]),
  ("exact collisions", len(exact), art["real"]["collisions"]["exactCollisions"]),
  ("cross-label collisions", len(cross), art["real"]["collisions"]["crossLabelCollisions"]),
  ("informative bits (log)", informative_bits(feats, DIM), art["real"]["collisions"]["informativeBits"]),
  ("informative bits (train)", informative_bits([pack_words(encode_feature(e.get("input",""), e.get("reply",""))) for e in sp["train"]], DIM), art["real"]["informativeBitsOnTrain"]),
  ("window size", art["productionWindow"]["size"], len(w)),
  ("window positives", art["productionWindow"]["distribution"]["positives"], sum(wl)),
  ("window negatives", art["productionWindow"]["distribution"]["negatives"], len(wl)-sum(wl)),
  ("window distinct inputs", art["productionWindow"]["distribution"]["distinctInputs"], len(win_in)),
]
bad = 0
for name, mine, theirs in checks:
    ok = (mine == theirs)
    bad += (not ok)
    print("    %-26s mine=%-6s artifact=%-6s %s" % (name, mine, theirs, "OK" if ok else "*** MISMATCH ***"))
print("    mismatches: %d" % bad)

# --- 9. synthetic fixture cross-check (validates the reimplementation itself)
print()
print("[H] SYNTHETIC FIXTURE cross-check (validates the reimplementation)")
slines = [l for l in SYN.read_text(encoding="utf-8").splitlines() if l.strip()]
sents = [json.loads(l) for l in slines]
sfeat = [pack_words(encode_feature(e.get("input",""), e.get("reply",""))) for e in sents]
sfold = [fold(w) for w in sfeat]
sfl2f = collections.defaultdict(set); sfl2l = collections.defaultdict(set)
for i, e in enumerate(sents):
    sfl2f[sfold[i]].add(tuple(sfeat[i])); sfl2l[sfold[i]].add(bool(e.get("label")))
sart = art["synthetic"]
checks2 = [
  ("syn episodes", len(sents), sart["distribution"]["episodes"]),
  ("syn positives", sum(1 for e in sents if e.get("label")), sart["distribution"]["positives"]),
  ("syn distinct full feats", len({tuple(x) for x in sfeat}), sart["collisions"]["distinctFullFeatures"]),
  ("syn distinct folds", len(set(sfold)), sart["collisions"]["distinctFoldedValues"]),
  ("syn exact collisions", sum(1 for v in sfl2f.values() if len(v)>1), sart["collisions"]["exactCollisions"]),
  ("syn cross-label collisions", sum(1 for v in sfl2l.values() if len(v)>1), sart["collisions"]["crossLabelCollisions"]),
]
for name, mine, theirs in checks2:
    print("    %-28s mine=%-6s artifact=%-6s %s" % (name, mine, theirs, "OK" if mine==theirs else "*** MISMATCH ***"))

print()
print("=" * 78)
print("DONE")
print("=" * 78)
