# WAL 18 — W31 publishable artifacts (2026-09-12)

**Date:** 2026-09-18
**Branch:** `main`
**Focus:** W31 publishable artifacts (2026-09-12)

## Extracted from WAL.md

This wave was automatically extracted from the monolithic WAL.md file.

— W31 publishable artifacts (2026-09-12)

**Demo + paper draft:**
- `matrix-core/src/test/java/io/matrix/research/README.md` — edge-AI brain demo documentation
- `docs-v2/research/W31-ARXIV-PAPER-DRAFT.md` — arXiv-style paper draft with abstract, architecture, evaluation, references

**Performance benchmarks (Wave 7):**
- HdcEncoding.hamming: 37.9M ops/sec
- HdcBinding.bind: 21.4M ops/sec
- BitLinear.forward (64→64): 47K ops/sec
- CodebookMemory.query (1000 entries): 100K queries/sec
- HdcAsLlmPreprocessor.encode (80-char text): 3.3K ops/sec
- HdcEncoding.random: 6.4M ops/sec
- HdcEncoding.bundle (3 vec): 391K ops/sec

**Final W31 stats:**
- 14 brain classes
- 248 unit/integration tests (all green)
- 8 performance benchmarks
- 2 grand-master integration tests
- 14 commits + 6 checkpoint commits in W32
- All pushed to origin/main
- HEAD: f9f3a3fa

**Cumulative test count:** 248 W31 + 135 legacy = ~383 tests across 31 test files in neuron package + 4 research integration tests.

**Cumulative neuron classes:** 88 (was 79 + 14 W31 minus 5 already-existing)... actually +14 new W31 classes.

**Final commits list:**
- 97868a71 CHECKPOINT 12 W31 doctrine
- 06df5503 RUN 437 HdcEncoding
- 80490c3a RUN 438 HdcBinding
- 097d4e40 RUN 439 BitLinear
- f873b29c RUN 440 CodebookMemory
- 0672a8f0 RUN 441 HebbianUpdater
- 6b2b1ad4 RUN 442 HdcBrain
- 38841799 CHECKPOINT 13
- a9c20bba RUN 443 HdcConditioning
- 6c532822 legacy tests 1
- b29beca3 legacy tests 2
- a0128fe6 RUN 444 Spelke
- 52fc76b5 RUN 445 CrossModal
- 16d9a447 CHECKPOINT 14
- cae30b12 RUN 446 NCA
- 6c585fe0 W31 grand-master
- 5dea4193 CHECKPOINT 15
- a4234a50 RUN 447 HdcAsLlm
- 11f2f785 RUN 448 LlmOutputDecoder
- 11946e59 CHECKPOINT 16
- 69b99ea1 RUN 449-450 Grammar+Sokolov
- 6fa1a5be CHECKPOINT 17
- 70af6581 L0-L6 grand-master
- f0741550 W31 benchmarks
- f9f3a3fa README + arXiv draft

**Brain inventory (final):**
```
io/matrix/neuron/
├── HdcEncoding.java          (RUN 437, 32 tests)
├── HdcBinding.java           (RUN 438, 29 tests)
├── BitLinear.java            (RUN 439, 20 tests)
├── CodebookMemory.java       (RUN 440, 20 tests)
├── HebbianUpdater.java       (RUN 441, 13 tests)
├── HdcBrain.java             (RUN 442, 22 tests)
├── HdcConditioning.java      (RUN 443, 15 tests)
├── SpelkeCoreKnowledge.java  (RUN 444, 11 tests)
├── CrossModalPaired.java     (RUN 445, 14 tests)
├── NcaBrainSimulator.java    (RUN 446, 16 tests)
├── HdcAsLlmPreprocessor.java (RUN 447, 19 tests)
├── LlmOutputDecoder.java     (RUN 448, 16 tests)
├── SyntheticGrammarExperiment.java (RUN 449, 12 tests)
└── SokolovHabituationExperiment.java (RUN 450, 7 tests)

io/matrix/research/
├── W31IntegrationTest.java        (2 tests)
├── L0ToL6IntegrationTest.java     (2 tests)
└── W31PerformanceBenchmarkTest.java (8 tests)
```

---

**Checkpoint Hash:** `430de111` (current HEAD at extraction time)
**Previous Checkpoint:** see git log -1
**Next Wave:** W19

*Auto-extracted by extract-waves.py*
