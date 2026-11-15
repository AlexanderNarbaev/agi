# C1 — Learning-window diagnosis (Workstream C, CONCEPT-TO-ACTION)

**Status:** `RESEARCH` — a diagnosis, not a decision. **No fold option (A/B/C) is proposed,
recommended or implied anywhere in this document.**
**Scope:** why the learning subsystem's active production window contains only positive labels.
**Branch / HEAD:** `develop` @ `429eba27` (read-only; no build was run, no file was modified
except this one).
**Date:** 2026-11-15.
**Measured against:** the runtime log `data/mind/episodic.ndjson` as it stood at that moment —
**108 records**, sha256 `c2035b35478fc11b165ff24576fc277a9797c92208b8e8a5a83aecf13b336df1`.
**The log has since grown**: the later collection of real observations (see
`docs-v2/engineering/C2A-LEARNING-EVIDENCE.md`) appended 59 records, so the file holds **at least
167** — and it keeps growing, because the runtime appends to it on every served interaction (226
after the wave's own collection was re-run while the opt-in flag was verified) — and the tail-20
window is **10 positive / 10 negative** instead of 20 / 0. Every number below is a
statement about the FIRST 108 records, and they still reproduce: `scripts/c1-measure-learning-window.py`
— the measurement script, now in the repository — re-measures them, and the first 108 lines still
hash to the sha256 above.

**What this document is:** the implementation map of the learning path, the production label
rule quoted from source, an evidence-graded verdict on six candidate causes, the measured
numbers behind each verdict, the collision analysis, and the exact blocker that must be
removed before a two-class window can exist.

**What this document is not:** it does not choose a fold, does not change the label rule,
does not change `K_MAX`, the trainer width, or the fold, and does not claim any build,
test or Gradle run. Every number below is traceable to a command or a file.

---

## 1. The implementation map

All paths are relative to the repository root.

| # | Component | Where | What it does |
|---|---|---|---|
| 1 | `EpisodeFeatureExtractor` | `matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/EpisodeFeatureExtractor.java:41-46` | `encode(Entry)` → hashes `input + " " + reply` into a 256-bit vector. Default width `:18` (`DEFAULT_DIM = 256`). Packing `:55-66` (LSB-first, 4 longs). |
| 2 | Token → bit hashing | `matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/PersistentHdcStore.java:394-407` | FNV-1a 32-bit per whitespace token; one bit per token at `(h & 0x7fffffff) % dim`. |
| 3 | `RuleInductionEngine` | `matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/RuleInductionEngine.java:104-195` | `induce(ids, features, labels)`: 80/20 split `:116-135`, Tsetlin train `:136`, MpdtGa `:140`, fidelity `:227-235`, register `:166-192`. Clause synthesis `:198-212` (**positives only**). |
| 4 | Trainer construction (width 16) | `matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/RuleInductionEngine.java:88` | `new TsetlinTrainer(16, 64, 200, new Random(seed))` → `inputBits = 16`. |
| 5 | **Sleep-cycle admission / window selection** | `matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/RealSleepScheduler.java:232-268` | `runInduction()`: `:233` `episodicLog.readAll()`; **`:237-240` take the LAST 20 entries** (`int n = Math.min(entries.size(), 20); … subList(entries.size()-n, entries.size())`); `:242-250` feature + label; `:256` `ruleEngine.induce(...)`. |
| 6 | **Production label rule** | `matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/RealSleepScheduler.java:249` | `labels[i] = e.confidence() >= 0.7 && e.accepted();` — see §2. |
| 7 | `K_MAX = 20` | `matrix-brain-runtime/.../RuleInductionEngine.java:32`; `matrix-brain-runtime/.../KMaxEnforcer.java:10,16`; `matrix-core/src/main/java/io/matrix/neuron/TruthTable.java:46` | `K_MAX = 20`. Enforced on the **number of episodes** at `RuleInductionEngine.java:113-114` (`KMaxEnforcer.enforce(features.length)`), not on bits. |
| 8 | **The 256 → 16 fold** | `matrix-core/src/main/java/io/matrix/tsetlin/TsetlinTrainer.java:361-376` (`pack`), public alias `:359` (`packForTest`) | XOR-parity: source bit *j* → target bit *j mod 16*; each target bit is the parity of 16 source bits. Kernel dimension 240. |
| 9 | Trainer input consumption | `matrix-core/.../TsetlinTrainer.java:276-277` (`trainStep` calls `pack(inputWords)`), `:309-317` (`trainBatch`), `:320-325` (`predict(long)`), `:161-179` (`fires`) | Training and serving both consume the **folded** value → they agree. `:134` caps `inputBits` at 20. |
| 10 | Informative-bit counter | `matrix-core/.../TsetlinTrainer.java:84-103` | `countInformativeBits` (word-indexed; the pre-2026-11-14 aliasing defect was fixed, `E1-LEARNING-EVIDENCE.md:108-116`). |
| 11 | Production write path | `matrix-api-gateway/src/main/java/io/matrix/api/MinimalHttpServer.java:199-206` (log at `mindDir/episodic.ndjson`), `:587` (Cyrillic→Latin transliteration), `:597` (`pbc.cycle(processedInput, inputText, …)`); `matrix-api-gateway/src/main/java/io/matrix/api/brain/ProductionBrainClient.java:363-364` (retrieval form vs original), `:388-389` (`episodicLog.append(input, mr.reply(), mr.confidence(), mr.accepted(), mr.modulatorsFired())`) | Every served MindCycle interaction is appended to the episodic NDJSON. |
| 12 | Write-time promotion gate | `matrix-brain-runtime/.../EpisodicLog.java:206-226` (`append` → `PromotionGate.evaluate` → `appendUnchecked`); `matrix-brain-runtime/.../PromotionGate.java:283-325` (`evaluate`), `:233-247` (`forInteraction`), `:66` (`MIN_SIMILARITY = 0.20`), `:74` (`DEFAULT_CONFIDENCE = 0.75`) | Decides what may enter the log at all. **Does not** apply the label rule. |
| 13 | Confidence composition | `matrix-brain-runtime/.../TrueMindCycle.java:479-492` (`composeConfidence` = `max(stage.confidence(), salience.score())`), `:494-499` (`accepted = confidence >= 0.40`), `:392-402` (modulator set); `matrix-brain-runtime/.../MindCycle.java:236,246,306-319` (same shape) | This is what actually decides `confidence` and `accepted` for a served episode. |
| 14 | Retrieval → confidence mapping | `matrix-brain-runtime/.../ContentSimilarity.java:68` (`RETRIEVAL_FLOOR = 0.20`), `:470-481` (`confidenceFor`: score ∈ [0.20, 1] → confidence ∈ [0.5, 1.0]) | The retrieval-score → confidence transform. |
| 15 | Salience score | `matrix-brain-runtime/.../stages/SaliencyStage.java:25-54` (baseline 0.50 `:26`; question-form +0.25 `:30-34`; arithmetic +0.30 `:36-38`; short (<8 chars) −0.30 `:43-46`; clamp `:47-48`) | The second term of `max(stage, salience)`. |
| 16 | Tsetlin fallback confidence | `matrix-brain-runtime/.../stages/TsetlinStage.java:46-48` (`miss()` → confidence `0.40`) | The value used when no stage matches. |
| 17 | Existing tests that materialise the window | `matrix-brain-runtime/src/test/java/io/matrix/brain/runtime/E1LearningEvidenceTest.java:99-110` (real log + label rule), `:113-122` (chronological 60/20/20), `:230-254` (collision analysis), `:417-423` (production window); `matrix-brain-runtime/src/test/java/io/matrix/brain/runtime/RuleInductionDownstreamAccuracyTest.java:83-84` (window), `:92` (label rule), `:143-160` (window + fold buckets) | The real log **is** a file on disk (`data/mind/episodic.ndjson`), so it needs no running system. |

---

## 2. The label rule, quoted verbatim from the source

`matrix-brain-runtime/src/main/java/io/matrix/brain/runtime/RealSleepScheduler.java:242-250`
(the admission block that builds the production window):

```java
        long[][] features = new long[recent.size()][];
        java.util.List<String> ids = new java.util.ArrayList<>();
        boolean[] labels = new boolean[recent.size()];
        for (int i = 0; i < recent.size(); i++) {
            EpisodicLog.Entry e = recent.get(i);
            features[i] = featureExtractor.encode(e);
            ids.add(e.id());
            labels[i] = e.confidence() >= 0.7 && e.accepted();
        }
```

The rule itself — **`RealSleepScheduler.java:249`**:

```java
            labels[i] = e.confidence() >= 0.7 && e.accepted();
```

Two mirrors of the same rule exist in test code and are byte-equivalent:

- `matrix-brain-runtime/src/test/java/io/matrix/brain/runtime/E1LearningEvidenceTest.java:107`
  `entry.confidence() >= 0.7 && entry.accepted(), "", false));`
- `matrix-brain-runtime/src/test/java/io/matrix/brain/runtime/RuleInductionDownstreamAccuracyTest.java:92`
  `labels[index] = entry.confidence() >= 0.7 && entry.accepted();`

and it is documented as such in `matrix-brain-runtime/src/test/resources/e1/DATASET-MANIFEST.json:28`.

**Where the rule is applied:** at *read* time, on the window — never at write time. The
write-time gate (`PromotionGate`, `EpisodicLog.java:213-215`) applies a **different** set of
criteria and does not contain the 0.7 threshold. This distinction matters for §6.

---

## 3. The six candidate causes

| # | Cause | Verdict | Evidence (file:line + measured count) |
|---|---|---|---|
| 1 | Real behaviour mostly produces positive labels | **REFUTED** | On the retained log the production rule yields **64 positive / 44 negative** (59.3 % positive) — `python3 /tmp/c1_measure.py` §[A]; harness artifact `docs-v2/engineering/E1-LEARNING-EVIDENCE.json` `real.distribution`. The pre-gate fossil `data/mind/episodic.ndjson.pre-w31.1.20261002T185338Z` yields **851 positive / 264 negative** of 1115. Both streams contain a substantial negative class; the label rule demonstrably produces negatives. *Measured caveat:* the tail after 2026-10-03T13:35:13Z is **31/31 positive** — the rule can produce negatives, and none was produced at the tail. |
| 2 | The label rule is insufficiently discriminating | **REFUTED** (as the cause of the one-class window) | The rule separates **64 / 44** on the very same log it is accused of failing to discriminate. Its anatomy is sharp, not blurry: `accepted` is `true` for **107 / 108** entries (the conjunct is inert on this data); `confidence >= 0.7` holds for **64 / 108**; `confidence < 0.7 AND accepted == true` = **43**; `confidence >= 0.7 AND accepted == false` = **0**. The confidence distribution is strongly bimodal with an **empty band around the threshold**: 41 entries sit at exactly `0.5`, then one at `0.6354`, one at `0.6977`, then nothing until `0.7592` — i.e. the cut at 0.7 separates two tight clusters. *Measured caveat with diagnostic value:* the rule does **not** track answer correctness — **42 of the 44 negatives carry a correct answer** (the transliterated-capital family: `'stolitsa frantsii'`→`Paris`, `'stolitsa anglii'`→`London`, `'capital of italy'`→`Rome`; 41 of them at confidence exactly `0.5`, one at `0.6977`) and all 64 positives are correct answers too. "Negative" therefore means *low retrieval confidence*, not *wrong*. That is a different defect from the cause under examination, and it is **not** what emptied the window. |
| 3 | The window selection is biased | **SUPPORTED** | The selector is a **pure time tail** with no class awareness: `RealSleepScheduler.java:237-240` (`entries.subList(entries.size()-n, entries.size())`). Measured: the active window is **20/20 positive** while the log is 64/44; of the **89** contiguous 20-entry windows in the log, **12 are 100 % positive**, and the active window is the **last of 12 consecutive** such windows (start indices 77…88). **All 44 negatives sit at indices ≤ 76**; indices 77…107 (the last 31, which contain the whole window) are 100 % positive. The selected window is class-degenerate with respect to the log it is drawn from. |
| 4 | Repeated inputs dominate | **SUPPORTED** | **20 distinct inputs** in 108 entries; the top two (`'stolitsa frantsii'` ×28, `'What is the capital of Kenya?'` ×26) are **54 / 108 = 50 %** of the log. In the active window: **17 / 20 = 85 %** is one single input, with only **3 distinct inputs and 3 distinct 256-bit features**. Because the label is a deterministic function of the input for **19 of the 20** inputs (the only input carrying both labels is `'gravity'`, 1 positive + 1 negative), repetition does not add information — it adds copies. The window's effective sample size is **3**, not 20. |
| 5 | The encoder loses useful distinctions | **REFUTED** for the encoder; the **fold's** loss is real, measured small, and **not** the binding constraint | **Encoder (256-bit): lossless on this data.** 24 distinct `(input, reply)` pairs → 24 distinct ordered token sequences → 24 distinct token multisets → **24 distinct 256-bit features**: zero collisions at every level (`/tmp/c1_measure.py` §[E]). **Fold (256 → 16):** 24 features → **23** distinct folded values, **1 exact** and **1 cross-label** collision (§5). Both collisions are *created by the fold*, since **0** distinct full features carry both labels. **Not binding:** the collision sits at entries `[0]`/`[1]`, inside the *training* partition, and E1 measured that the informative projection does **not** beat the fold on either split (`docs-v2/engineering/E1-LEARNING-EVIDENCE.md:80-89`). Decisively: **no representation can create a class that is absent from the window.** The fold's information cost is 1 collision of 24 features (4.2 %); the window's defect is 0 of 20. |
| 6 | The current experiment lacks representative tasks | **SUPPORTED** | The real evaluation partition is degenerate: chronological test = **21 entries, 21 positive, 0 negative, 3 distinct inputs, 4 distinct features** (`E1LearningEvidenceTest.java:113-122`; artifact `real.partitions`, `real.collisions`). The production window is **3 distinct inputs**. The synthetic split is explicitly *not* representative: `DATASET-MANIFEST.json:15` — `"CONTROLLED SYNTHETIC — mechanism fixtures, never real-world evidence"`; it is 144 episodes over a 36-word synthetic vocabulary (`scripts/e1-build-dataset.py:44-50`) labelled by a content rule, and it shares **0** episode texts with the real log (`E1LearningEvidenceTest.java:392-400`, asserted). Neither split contains a representative task mixture. |

**Summary:** two causes are SUPPORTED as the operating explanation of the one-class window
(3 and 4 — both are properties of *which data the window is drawn from*), one is SUPPORTED
for the experiment's evaluability (6), and three are REFUTED as causes (1, 2, 5). None of the
six is NOT-ESTABLISHED on the retained data. The sub-questions that the retained data
**cannot** settle are listed in §7.

---

## 4. The measured numbers

### 4.1 Provenance of the real log

| Item | Value | Source |
|---|---|---|
| Path | `data/mind/episodic.ndjson` | `MinimalHttpServer.java:199-200` (`mindDir/episodic.ndjson`); `DATASET-MANIFEST.json:18` |
| sha256 | `c2035b35478fc11b165ff24576fc277a9797c92208b8e8a5a83aecf13b336df1` | `sha256sum data/mind/episodic.ndjson` — **identical** to `DATASET-MANIFEST.json:20` |
| Physical non-blank lines | 108 | `wc -l data/mind/episodic.ndjson`; all 108 parse as JSON |
| Last write | 2026-10-04T14:28:41.588Z (entry 107) | `ts` field of the last entry |
| File mtime | 2026-10-04 17:28:41 +0300 | `stat -c '%y' data/mind/episodic.ndjson` |
| Git status | **runtime data, not repository data** — ignored by `.gitignore:83` (`data/mind/*`); `git ls-files --error-unmatch` → *did not match any file(s) known to git* | `.gitignore:83` |
| Is it file-accessible? | **Yes.** No running system is required to read it. | `EpisodicLog.readAll()` reads the file directly (`EpisodicLog.java:321-365`) |

### 4.2 The real episodic log — size and label distribution

| Metric | Value | Command / file |
|---|---|---|
| Total records | **108** | `/tmp/c1_measure.py` §[A]; artifact `real.distribution.episodes = 108` |
| Positives (`confidence >= 0.7 AND accepted`) | **64** | idem; artifact `real.distribution.positives = 64` |
| Negatives | **44** | idem; artifact `real.distribution.negatives = 44` |
| Distinct inputs | **20** | idem; artifact `real.distribution.distinctInputs = 20` |
| Distinct replies | **23** | idem; artifact `real.distribution.distinctReplies = 23` |
| Distinct `(input, reply)` pairs | **24** | `/tmp/c1_measure.py` §[E] |
| Distinct 256-bit features | **24** | idem; artifact `real.collisions.distinctFullFeatures = 24` |
| Distinct 16-bit folded values | **23** | idem; artifact `real.collisions.distinctFoldedValues = 23` |
| `accepted == true` | **107 / 108** | idem §[F] |
| `confidence >= 0.7` | **64 / 108** | idem §[F] |
| Entries with confidence exactly `0.5` | **41** | idem §[F] |
| Negatives that are the transliterated-capital family (correct answers, labelled negative) | **42** of the 44 | idem, negative-class breakdown |
| Informative bits over the whole log | **71 / 256** | idem; artifact `real.collisions.informativeBits = 71` |
| Informative bits in the training split | **46 / 256** | idem; artifact `real.informativeBitsOnTrain = 46` |

### 4.3 The active production window (the last 20 entries)

| Metric | Value | Command / file |
|---|---|---|
| Window size | **20** | `RealSleepScheduler.java:237-240`; artifact `productionWindow.size = 20` |
| Positives | **20** | artifact `productionWindow.distribution.positives = 20`; `/tmp/c1_measure.py` §[C] |
| **Negatives** | **0** | artifact `productionWindow.distribution.negatives = 0`; idem |
| Distinct inputs | **3** (`'What is the capital of Kenya?'` ×17, `'What is the capital of Peru?'` ×2, `'What is the capital of Germany?'` ×1) | idem §[C]; artifact `distinctInputs = 3` |
| Distinct replies | **3** | artifact `productionWindow.distribution.distinctReplies = 3` |
| Distinct 256-bit features | **3** | idem §[C] |
| Distinct 16-bit folds | **3** | idem §[C] |
| Confidence range in the window | `0.7821 … 0.7842` (all ≥ 0.7592) | idem §[C] |
| Informative bits in the window | 9 source / 5 target | idem, window block |

### 4.4 The chronological real test partition (E1's evaluation split)

| Metric | Value | Command / file |
|---|---|---|
| Split rule | chronological 60 / 20 / 20, no shuffle | `E1LearningEvidenceTest.java:113-122` |
| train | **65** entries — **24 positive / 41 negative** — 13 distinct inputs, 14 distinct features | `/tmp/c1_measure.py` §[D]; artifact `real.partitions.train = 65` |
| validation | **22** entries — **19 positive / 3 negative** — 12 distinct inputs, 13 distinct features | idem; artifact `real.partitions.validation = 22` |
| **test** | **21** entries — **21 positive / 0 negative** — 3 distinct inputs, 4 distinct features | idem; artifact `real.partitions.test = 21` |
| Test inputs already seen in train | **17 of 21** | artifact `leakage.realTestInputsSeenInTrain = "17 of 21"`; `E1LearningEvidenceTest.java:411-416` |

### 4.5 Distinct inputs and the top repeated inputs

| Input | Occurrences | Label of every occurrence |
|---|---|---|
| `'stolitsa frantsii'` | **28** | negative (27 at conf 0.5, 1 at conf 0.6977) |
| `'What is the capital of Kenya?'` | **26** | positive (all) |
| `'Skolko budet 5+3?'` | **13** | positive (all) |
| `'stolitsa anglii'` | **13** | negative (all, conf 0.5) |
| `'What is the capital of Peru?'` | **5** | positive |
| `'What is the capital of Germany?'` | **4** | positive |
| `'What is the capital of Chile?'` | **3** | positive |

Top replies: `'Paris'` ×28, `'Kenya capital Nairobi'` ×21, `'5 + 3 = 8'` ×13, `'London'` ×13.
Source: `/tmp/c1_measure.py` §[B].

### 4.6 The temporal structure of the class distribution

| Measurement | Value | Command / file |
|---|---|---|
| Indices of all negative episodes | 1…76 (**44 of them**, none after 76) | `/tmp/c1_measure.py` §[E] |
| Entries 77…107 (the last 31, containing the whole window) | **31 positive / 0 negative** | idem |
| Contiguous 20-windows in the log | **89** total; **12** are 100 % positive (start indices 77…88) | idem, window-bias block |
| Active window's position | start index 88 = the **last** of the 12 consecutive all-positive windows | idem |
| Pre-gate segment (ts < 2026-10-02T18:53:38Z) | 57 entries, **16 positive / 41 negative** | idem |
| Post-gate segment | 51 entries, **48 positive / 3 negative** | idem |
| Pre-gate fossil `episodic.ndjson.pre-w31.1.20261002T185338Z` | 1115 entries, **851 positive / 264 negative** | idem |

### 4.7 How the numbers were produced

The measurements above come from **two independent sources that agree exactly**:

1. **The harness artifact** `docs-v2/engineering/E1-LEARNING-EVIDENCE.json`, written by
   `E1LearningEvidenceTest` (not typed by hand; `E1LearningEvidenceTest.java:434-441`).
2. **A standalone re-measurement script** `/tmp/c1_measure.py` (outside the repository; it
   reads nothing but `data/mind/episodic.ndjson` and
   `matrix-brain-runtime/src/test/resources/e1/synthetic-episodes.ndjson`). It re-implements
   the production path from source — `PersistentHdcStore.hashToVector` (`:394-407`),
   `EpisodeFeatureExtractor.pack` (`:55-66`), `TsetlinTrainer.pack` (`:361-376`),
   the label rule (`RealSleepScheduler.java:249`), the window (`RealSleepScheduler.java:237-240`),
   the E1 chronological split (`E1LearningEvidenceTest.java:113-122`) — and it was validated
   against the harness artifact on **18 real-log numbers and 6 synthetic numbers with
   0 mismatches** (script output §[G], §[H]).

**No Gradle build and no test run was performed for this document.** The script is pure
Python over the retained data; the JVM was never invoked.

---

## 5. The collisions analysis

### 5.1 Counts

| Measurement | Value | Source |
|---|---|---|
| Distinct 256-bit features (whole log) | **24** | §4.2 |
| Distinct 16-bit folded values (whole log) | **23** | §4.2 |
| **Exact collisions** — folded groups containing > 1 distinct 256-bit feature | **1** | artifact `real.collisions.exactCollisions = 1`; `/tmp/c1_measure.py` §[E] |
| **Cross-label collisions** — folded groups carrying both labels | **1** | artifact `real.collisions.crossLabelCollisions = 1`; idem |
| Distinct **full** (256-bit) features carrying both labels | **0** | idem §[E] — the cross-label collision is **created by the fold**, not present in the encoder |
| Synthetic fixture, for comparison | 144 features → 141 folded values; 3 exact, **0** cross-label | artifact `synthetic.collisions`; script §[H] |

### 5.2 The colliding feature values (verbatim)

The single exact collision **and** the single cross-label collision are the **same folded
value `0x0020`** (target bit 5), from two consecutive entries:

| Entry | input | reply | confidence | accepted | label | 256-bit set-bit indices | folded |
|---|---|---|---|---|---|---|---|
| `[0]` | `gravity` | `gravity` | `1.0` | `true` | **positive** | `{69}` | `0x0020` |
| `[1]` | `gravity` | `9.8 m/s^2` | `0.3333333333333333` | `false` | **negative** | `{66, 69, 98}` | `0x0020` |

Why they collide, exactly (the fold is `TsetlinTrainer.pack`, `:361-376`, target bit
`= source bit mod 16`):

- entry `[0]`: source bit 69 → target `69 mod 16 = 5` → `0x0020`.
- entry `[1]`: source bits 66, 69, 98 → targets `2, 5, 2` → XOR → bit 2 cancels itself
  (66 and 98 land on the same target), leaving bit 5 → `0x0020`.

So the fold's parity map turned a genuinely different feature into the same 16-bit value, and
the two colliding episodes carry **opposite labels**. This is the only such collision in the
log, it lies inside the *training* partition (entries 0–1 of 0–64), and it is 1 of 24
distinct features (4.2 %).

### 5.3 What the fold preserves on this data

| Measurement | Value |
|---|---|
| Informative source bit positions (256) | 71 |
| Informative target bit positions (16) after the fold | **16 of 16** (every target bit takes both values somewhere in the log) |
| Ideal (project the 16 most informative source bits 1:1) | 16 informative targets — **the same count** |
| In the production window | 9 informative source bits → **5** informative target bits (3 distinct features; not meaningful) |

So on the retained log the fold does **not** destroy the informative structure (16/16 targets
survive); its measured cost is the single collision in §5.2.

### 5.4 The window's own feature map

| folded value | first entry | input | reply |
|---|---|---|---|
| `0x9b38` | `[88]` | `What is the capital of Kenya?` | `Kenya capital Nairobi` |
| `0x9939` | `[103]` | `What is the capital of Peru?` | `Peru capital Lima` |
| `0xd92a` | `[106]` | `What is the capital of Germany?` | `Germany capital Berlin` |

Three features, three folds, twenty episodes, one class.

---

## 6. The exact blocker to a two-class window

**There is no gate, threshold, representation or rule standing between the serving path and
a negative-labelled episode in the window. The blocker is that no low-confidence episode
has been served after 2026-10-03T13:35:13Z, and the gateway that writes the log has not run
since 2026-10-04.**

Stated precisely, as four facts:

1. **The window is a pure time tail.** `RealSleepScheduler.java:237-240` takes the last 20
   entries of `data/mind/episodic.ndjson` in file order. It has no class awareness, no
   stratification, and no recency-vs-diversity rule. So the window's class composition is
   *exactly* the class composition of the last 20 served interactions.

2. **The tail is one-class.** Entries 77…107 (the last 31, which contain the entire window)
   are **31 positive / 0 negative**. Every negative episode in the log — all 44 — sits at
   index ≤ 76. The last negative is entry `[76]`, `2026-10-03T11:43:35.979Z`
   (`'audio:frame=8 hdc_dim=256'`, confidence `0.6354`); the next entry, `[77]`
   (`2026-10-03T13:35:13.897Z`), is positive, and nothing negative has been served since.

3. **The write-time gate is not the blocker.** The `PromotionGate` that decides what enters
   the log (`EpisodicLog.java:213-215`, `PromotionGate.java:283-325`) does **not** contain
   the 0.7 threshold, and it does not reject low confidence. For the measured
   negative-producing family the verdict is *allow*:
   `isFrozenProbe` — the frozen corpus holds the **Cyrillic** probes `'столица франции'` /
   `'столица англии'` (`EvalBattery.java:88-89`), while the served and logged form is the
   **transliterated** `'stolitsa frantsii'` (`MinimalHttpServer.java:587`;
   `ProductionBrainClient.java:388` logs the retrieval form), so the probe strings do not
   match; `isFiction` — no; `NOT_PROMOTED_BY_STAGE` — `accepted == true`; `EMPTY` — no;
   `REFUSAL` — `'Paris'`/`'London'`/`'Rome'` are not refusals; `DEFAULTED_CONFIDENCE` — the
   value is `0.5`, not `0.75` (`PromotionGate.java:74`, `:310-315`);
   `BELOW_SIMILARITY_FLOOR` — the similarity is `NaN` for every entry in this log, because
   `PromotionGate.Candidate.forInteraction` only derives a similarity when a modulator tag
   contains `"hdc"` (`PromotionGate.java:236-244`) and **no entry in the log has such a
   tag** (measured: 0 of 108). Empirically these entries *did* pass the gate — they are in
   the file.

4. **The label rule is not the blocker.** It yields 44 negatives on this same log (§3
   cause 2). It is applied at read time on the window, not at write time.

### 6.1 The production behaviour that must change (or the collection process that must run)

**A negative-labelled episode is produced by a MindCycle interaction whose composed
confidence lands below 0.7.** From source, the composed confidence is
`max(stage.confidence(), salience.score())`
(`TrueMindCycle.java:479-492`; `MindCycle.java:306-319`), and:

- `salience.score()` = `0.50` baseline (`SaliencyStage.java:26`), `+0.25` for a question
  form (`:30-34`), `+0.30` for arithmetic (`:36-38`), `−0.30` for an input shorter than
  8 characters (`:43-46`), clamped to `[0,1]` (`:47-48`);
- a retrieval hit's confidence is `0.5 + 0.5·(score − 0.20)/0.80` for `score ≥ 0.20`
  (`ContentSimilarity.java:470-481`, floor at `:68`);
- the no-stage-matched fallback is `max(0.40, salience)` (`TsetlinStage.java:46-48`).

Therefore **the recipe for a negative-labelled episode is an interaction whose retrieval match
lands in the floor band `score ∈ [0.20, 0.52)` (so `confidenceFor < 0.70`) AND whose
salience score is below 0.70 — i.e. an input that is *not* interrogative-form (no trailing
`?`, no `what/how/why/when/who/where` prefix).** The measured instances are exactly that
shape:

| Input family | salience (computed from `SaliencyStage.java:25-54`) | confidence | label |
|---|---|---|---|
| `'stolitsa frantsii'`, `'stolitsa anglii'`, `'capital of italy'` (17–18 chars, no `?`, no interrogative prefix) | **0.50** | 0.50 (one at 0.6977) | **negative** |
| `'What is the capital of Kenya?'` etc. (interrogative form) | **0.75** | 0.7821–0.7842 | positive |
| `'Skolko budet 5+3?'` (arithmetic) | **1.00** | 1.0 | positive |

*Verification of the composition rule:* the salience model re-implemented from
`SaliencyStage.java:25-54` predicts a salience score for all 108 entries; **0 of 108**
entries have an observed confidence below their predicted salience — consistent with
`composeConfidence = max(stage, salience)` on every entry of the log.

**The exact blocker, named:**

> The serving path must produce at least one interaction whose retrieval lands in the floor
> band `score ∈ [0.20, 0.52)` **after** the last positive burst, and that interaction must be
> among the **last 20** episodes in `data/mind/episodic.ndjson`. Concretely: serve the
> non-interrogative transliterated-capital family (`'stolitsa frantsii'`,
> `'stolitsa anglii'`, `'capital of italy'` — the measured negative-producing family, and
> the only recipe verified to yield `confidence < 0.7` on this system's own store) **after**
> the current 31-positive tail, and re-measure the tail-20 class distribution.

**And the collection process that must run:** the gateway itself —
`matrix-api-gateway/src/main/java/io/matrix/api/MinimalHttpServer.java`, whose
`ProductionBrainClient.cycle()` appends each served interaction to the episodic log
(`ProductionBrainClient.java:388-389`). It is **not running now**: `data/mind/gateway.log`
ends at `2026-10-04 17:28:39` with `matrix-api-gateway listening on http://0.0.0.0:8765`,
no `java` process is alive, and the log file has not been written since
`2026-10-04T14:28:41.588Z`.

**This is a collection gap, not a design defect.** Nothing in the label rule, the gate, the
encoder, the fold, the trainer width or `K_MAX` prevents a two-class window. The window is
empty of negatives because the last 20 served interactions were 20 high-confidence
retrieval hits.

---

## 7. What must be collected to settle each open question

The six causes are adjudicated from retained data (§3). What the retained data **cannot**
settle are the following sub-questions. Each entry gives the measurement that would settle
it and a concrete collection procedure (no code change; no fold decision).

### 7.1 Does the **current** build produce a balanced label stream? — NOT-ESTABLISHED

*Why it is open:* the retained log was written by a superseded serving path. **Five commits
touching the serving/confidence path landed after the last log write**: `0ea3f325` (2026-10-10,
harmful-intent classifier into the refusal path), `0c005c53` (2026-10-11, W36.3 — *remove the
per-cycle Tsetlin call*, which is the fallback branch of `composeConfidence`),
`8a994f96` and `7a013d91` (2026-10-12, the neuromodulator retrieval floor wired through the
serving path), `7210484f` (2026-10-17, W38.0 — retrieval floor applied and reported at misses
and hits). **The current HEAD has never written an episode.** The retained label
distribution therefore describes the *old* build.

*Measurement that settles it:* the class distribution of the last 20 episodes written by the
**current** build.

*Collection procedure:* (1) start the gateway from the current tree
(`MinimalHttpServer`, `MATRIX_MODE=production`); (2) serve **≥ 40** interactions against
`data/mind/episodic.ndjson` in one uninterrupted session, in the order
*positive-family first, then negative-family, then positive-family again* — positive family =
interrogative form that hits a stored fact (`'What is the capital of Kenya?'`);
negative family = the non-interrogative floor-band queries of §6.1
(`'stolitsa frantsii'`, `'stolitsa anglii'`, `'capital of italy'`), which the retained log
measures at confidence `0.5` and `0.6977` — both `< 0.7`; (3) after the session, re-run the
window measurement (`/tmp/c1_measure.py` §[C], or the equivalent read of
`RealSleepScheduler.runInduction`) and record: window size, positives, negatives, distinct
inputs, distinct features, distinct folds; (4) repeat with the negative family **last** so
the tail-20 itself contains negatives, and record the same four numbers. A window with both
classes and > 24 distinct features is the precondition the E1 report names for the A/B/C
decision (`docs-v2/engineering/EVID-BASE-FINAL-REPORT.md:170`, `:183-184`).

### 7.2 Does the label rule track answer *correctness*? — NOT-ESTABLISHED (measured suspicion only)

*Measured so far:* on the retained log **42 of the 44 negatives carry a correct answer**
(`'Paris'`, `'London'`, `'Rome'` for the transliterated capital queries — 41 of them at
confidence exactly `0.5`, one at `0.6977`; the two remaining negatives are `'gravity'` →
`'9.8 m/s^2'` (also correct, but `accepted=false`) and one audio-perception episode) and all
64 positives are correct answers too. So the rule's negative class is a
*low-retrieval-confidence* class, not a *wrong-answer* class — but this was not measured
against an independent reference, it was read off the operator's own knowledge of a handful
of questions.

*Measurement that settles it:* the confusion matrix of `label = (confidence >= 0.7 AND
accepted)` against an independent correctness reference, over ≥ 200 episodes.

*Collection procedure:* for each served interaction, keep the produced `confidence`,
`accepted`, `input`, `reply` (already in the episodic NDJSON), **and** a reference answer.
The reference must come from a source outside the mind — e.g. the frozen
`EvalBattery.standardBattery()` expected values (`EvalBattery.java:55-107`) for the
standard probes, and an operator-authored answer sheet for the non-battery traffic. Then
report: positives with a correct reply, positives with an incorrect reply, negatives with a
correct reply, negatives with an incorrect reply. Note the firewall constraint: the frozen
probes are **blocked from the learning feed** by EPI-4 (`PromotionGate.java:288-292`), so
their replies must be harvested from the *serving response*, not from the episodic log —
otherwise the very episodes used as the reference are excluded from the data under test.

### 7.3 Is the tail-20 a biased estimator of the log's class distribution? — PARTLY SETTLED

*Settled:* on the retained log the active window is 20/20 positive against a log that is
64/44, and 12 of 89 contiguous windows are 100 % positive (§4.6) — the tail is measurably
not representative.

*Not settled:* whether this is a property of the *traffic* (one operator hammering one
query) or of the *selector* (a tail-20 would be one-class even for a balanced stream if the
traffic were bursty). The two are not distinguishable from a single 108-entry log.

*Measurement that settles it:* on a ≥ 400-entry log, compare the class distribution of (a)
the tail-20, (b) a uniformly sampled 20, and (c) a **stratified** 20 (both classes present).
The tail is a biased estimator iff (a) differs materially from (b) and (c) *within the same
log*.

*Collection procedure:* the §7.1 session, extended to ≥ 400 interactions **interleaved**
rather than blocked (so no run of 20 consecutive same-class episodes), with the
negative-family query issued at a controlled rate of roughly 1 in 4; then compute (a), (b),
(c) from the resulting file. This is a property of the *collection*, not a change to the
selector.

### 7.4 How much of the fold's cost is visible at a realistic scale? — NOT-ESTABLISHED

*Settled on 24 features:* 1 exact + 1 cross-label collision (§5); the encoder is lossless
(§3 cause 5).

*Not settled:* the cross-label collision rate scales with the number of distinct features,
and 24 features cannot support a rate estimate. E1's own precondition for the fold decision
is a window with **both classes and more than 24 distinct features**
(`docs-v2/engineering/E1-LEARNING-EVIDENCE.md:94-99`).

*Measurement that settles it:* re-run `E1LearningEvidenceTest` against the new log (it
writes `docs-v2/engineering/E1-LEARNING-EVIDENCE.json` when
`-Dmatrix.e1.result=<path>` is set; `E1LearningEvidenceTest.java:434-441`) once the
window has ≥ 2 classes and > 24 distinct features, and read `real.collisions`
(`distinctFullFeatures`, `distinctFoldedValues`, `exactCollisions`, `crossLabelCollisions`).
The same run also re-measures whether the informative projection beats the fold — the
comparison E1 could not settle at 24 features.

### 7.5 Does the evaluation partition contain a representative task mixture? — NOT-ESTABLISHED

*Settled:* the current evaluation partition is degenerate (21/21 positive, 3 distinct
inputs) and the synthetic split is explicitly not representative (§3 cause 6).

*Not settled:* what a *representative* mixture is for this mind, and therefore whether the
future window is representative.

*Measurement that settles it:* the capability-category histogram of the real window,
compared against the frozen battery's category list (`EvalBattery.Category`: ANALOGY, RU,
GENERALIZATION, RETRIEVAL, …). If the window's category histogram is concentrated in one
category — as it is now, where 85 % of the window is one question — the window is not
representative.

*Collection procedure:* drive the gateway with a scripted workload spanning the
capability categories that are **not** the frozen probes' exact texts (the probe firewall
blocks probe traffic from the learning feed), interleaved with the negative family of §6.1;
then histogram the window by category and by distinct input.

---

## 8. Limits — what was not verifiable without a build

1. **No build, no test run, no Gradle invocation.** This diagnosis was produced by reading
   source and by a standalone Python re-implementation of the production encoding path
   (`/tmp/c1_measure.py`, outside the repository). The JVM was never invoked, so:
   - the Java-side numbers in `docs-v2/engineering/E1-LEARNING-EVIDENCE.json` were **not
     regenerated**; they were consumed as a retained artifact and **independently
     reproduced** by the script (18/18 real-log and 6/6 synthetic numbers matched);
   - `RuleInductionEngine.induce` was **not executed** on the measured window. Whether the
     20-entry one-class window yields a particular fidelity, a particular clause count or a
     particular registry delta was therefore **not measured here**.
   - `TsetlinTrainer.trainBatch` / `predict` were not run; the fold's *representation* cost
     was measured (collisions, informative bits) but its *learner* cost was not re-measured.

2. **The re-implementation is a re-implementation.** `/tmp/c1_measure.py` mirrors
   `PersistentHdcStore.hashToVector`, `EpisodeFeatureExtractor.pack`, `TsetlinTrainer.pack`,
   the label rule and the window rule from the cited source lines. It is validated by
   reproducing the harness artifact exactly, and by the fact that no entry's observed
   confidence contradicts the re-implemented salience model (§6.1). It is still a mirror: a
   divergence in Java's `String.toLowerCase()` (default locale) or `String.split("\\s+")`
   on some untested input would not be caught by it. The corpus contains only lowercase
   Cyrillic and ASCII, no uppercase non-ASCII and no Turkish-`I` exposure (checked
   explicitly), so the mirror is exact for this data.

3. **The real log is runtime data, not repository data** (`.gitignore:83`; confirmed absent
   from `git ls-files`). It is therefore **not reproducible from the repository alone** —
   it survives only on this machine, and it is 42 days stale (last write
   2026-10-04T14:28:41.588Z). §7.1's collection procedure is required before the fold
   decision can move off `INSUFFICIENT_DATA`.

4. **The current build's serving behaviour is unobserved.** Five serving-path commits
   landed after the last log write (§7.1); the current HEAD has never written an episode.
   Every statement in this document about "the production path" describes the **source as
   it stands at `429eba27`**, while the measured label distribution describes the **build
   that wrote the log in October 2026**. The two are not the same, and §7.1 exists precisely
   to close that gap.

5. **Not verified: the promotion gate's verdict on the negative-producing family.** The
   §6 reasoning walks `PromotionGate.evaluate` line by line and concludes *allow*; that
   conclusion was not executed in the JVM. The supporting empirical fact is that these
   entries **are in the file** — but they were written on 2026-09-26…29, i.e. **before**
   the gate existed, so their presence does not prove the current gate admits them.

6. **Not verified: whether the Cyrillic probe firewall gap is exploitable.** The observation
   in §6.3 — the frozen probes are Cyrillic (`EvalBattery.java:88-89`) while the logged and
   gated form is the transliterated one (`MinimalHttpServer.java:587`;
   `ProductionBrainClient.java:388`) — is a **file:line fact**, but whether a probe can
   actually reach the learning feed in that form was **not tested**. It is recorded here
   because it is directly adjacent to the blocker (it concerns the same transliteration
   step) and it is **not** one of the six causes.

7. **The E1 synthetic fixture was not regenerated** (`scripts/e1-build-dataset.py` was read,
   not run); its 144/99/45 counts and sha256 were taken from the retained
   `DATASET-MANIFEST.json` and reproduced from the fixture file by the script.

8. **Scope boundary honoured.** No fold option (A, B or C) is proposed or implied; the
   label rule was not changed and is not proposed to be changed to manufacture balance; no
   production file was modified; nothing was staged or committed; `OpeAi/` and
   `.dsh-project-memory/` were not touched.

---

## Appendix — the numbers at a glance

| Quantity | Value |
|---|---|
| Real log records | **108** |
| Real log label distribution | **64 positive / 44 negative** |
| Distinct inputs / replies / 256-bit features / 16-bit folds (log) | **20 / 23 / 24 / 23** |
| Top-2 input repetition | **54 / 108 = 50 %** |
| **Active production window** | **20 entries — 20 positive, 0 negative, 3 distinct inputs, 3 distinct features** |
| Chronological test partition | **21 entries — 21 positive, 0 negative, 3 distinct inputs, 4 distinct features** |
| Train / validation partitions | **65 (24 pos / 41 neg)** / **22 (19 pos / 3 neg)** |
| Exact collisions (256 → 16) | **1** (folded value `0x0020`, entries `[0]`/`[1]`) |
| Cross-label collisions (256 → 16) | **1** (same folded value `0x0020`) |
| Cross-label collisions at full 256 bits | **0** |
| Informative bits | **71** (log) / **46** (train) of 256 |
| Negative episodes at index > 76 | **0** (the last 31 entries are 31/31 positive) |
| Contiguous 20-windows that are 100 % positive | **12 of 89** |
| **Blocker** | **No low-confidence episode has been served since 2026-10-03T13:35:13Z; the gateway has not run since 2026-10-04T14:28:41Z.** |
