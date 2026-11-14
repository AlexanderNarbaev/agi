# REP-SUBSTRATE.0 — the baseline, and the loss of the `/tmp/w54` harnesses

**Status:** `CANONICAL` for the wave's first stage · **Date:** 2026-11-13 ·
**Branch:** the working tree of `develop` (the wave has no branch yet — it is cut from the
agreed `develop` when the owner releases the reconciliation)

**What this document is.** The record of the wave's first stage: *what the raw material was,
what happened to it, what is left, and what has to be rebuilt before any number of this wave
may be published.* It is written **before** the measurements, because the wave's own key
criterion is that the results must not be lost — and because a document written after the
fact cannot say what was there before.

---

## 1. The loss — stated first, because it changes the stage

The wave's harnesses lived in `/tmp/w54`. **They are no longer there.**

```
$ ls -la /tmp/w54/
ls: cannot access '/tmp/w54/': No such file or directory
$ du -sh /tmp/w54
du: cannot access '/tmp/w54': No such file or directory
$ ls -d /tmp/w54* /tmp/*w54* 2>/dev/null
(no output)
```

Nothing in the repository holds them either — a search for the harness's own numbers finds
only the road map's mention of them:

```
$ grep -rln "112.65" docs-v2/ --include="*.md" --include="*.json"
docs-v2/governance/ROADMAP.md
```

**What this means for the wave's plan.** The first stage was *"restore and preserve the
`/tmp/w54` harnesses, fix the environment and the inputs → a reproducible baseline"*. The
restoration is now impossible: the directory is gone, and a `/tmp` directory is not a place
where a result can live. **The stage changes from "preserve" to "rebuild"** — and the
rebuild is what §3 below starts. Nothing was published from the lost harnesses, so no
published number is falsified; what is lost is the *ability to reproduce* the numbers they
produced.

## 2. What survives, and where it survives

| what | where it is now | how trustworthy |
|---|---|---|
| the FFM downcall against the Java path: **112.65 ns** against **6–16 ns** | the owner's directive of 2026-11-13, quoted in `docs-v2/governance/ROADMAP.md` §4.14 | a quoted observation, **not** a measurement this repository can repeat — §4 says what the rebuild must measure and why the absolute figures are not admissible yet |
| the spread between two harnesses of the same workload: **2.7×** (6.09 against 16.45 ns/op) | the owner's directive, and §4.14 of the road map | the reason the stage exists: two harnesses of one workload disagreed, so **only ratios and laws are admissible** until one reproducible harness exists |
| the property of the production fold, verified byte-for-byte against `TsetlinTrainer.pack` | §5 of this document, and the test `matrix-core/src/test/java/io/matrix/tsetlin/TsetlinPackInformationLossTest.java` | **measured on the production code** — this part no longer depends on the lost harness |
| **the native Hamming path never activates** (§4) | measured on this host on 2026-11-13; the cause is named and the mechanism is checked | **measured** — the finding does not depend on the lost harness either |
| the landscape of the representations (the five incompatible HDC forms, the `int[1024]` of `bundle`, the `JvmSimdBackend` without callers, the hardcoded `0.96`) | the code of the repository, and `docs-v2/architecture/CAPABILITY-REGISTRY.yml` | re-derivable from the tree at any time |

## 3. What the rebuild starts from — the environment, fixed

The stage demands a fixed environment and fixed inputs. They are fixed here, in the
repository, so that a reader can repeat a measurement instead of trusting it.

| the item | the value |
|---|---|
| the JDK | `openjdk 25.0.4.1+1-1-26.04.4-Ubuntu` (the repository's JDK at `$HOME/jdk25`) |
| the build | Gradle **9.6.0**, `--offline`, Kotlin 2.3.21, Groovy 4.0.32 |
| the processor | AMD Ryzen 9 9955HX, 32 logical cores |
| the memory | 59 GiB total, 43 GiB available at the measurement |
| the kernel | Linux 7.0.0-43-generic x86_64 |
| the test JVM | `maxHeapSize = 16g`, `-Xmx16g`, `--add-modules=jdk.incubator.vector`, `--enable-native-access=ALL-UNNAMED` (`matrix-core/build.gradle:119-123`) |
| the benchmark harness | the `me.champeau.jmh` plugin **0.7.3**, JMH **1.37**, the JSON result format, warmup 5 / iterations 10 / fork 2, the scope filter `-PjmhBenchmark=<Name>` (`matrix-core/build.gradle:6,328-342`) |
| the seeds | every random vector of the new test is drawn from the fixed seed **42**; the acceptance model's own WiSARD baseline uses the same seed (`AcceptanceModel.java:151`) |

**The inputs.** The production fold's input is a vector of source bits; the widths that
matter are fixed by the code: the trainer's width is 16 (`TextFeatureVector.WIDTH`), the HDC
vector is 1024 bits in 16 longs (`HdcEncoding.DIM`, `HdcEncoding.WORDS`), and the source
widths of the loss are 256 bits (four words) and 1024 bits (sixteen words).

## 4. What the rebuild must measure — and the finding that changes the premise

### 4.1 The native library is present, and the Java path is already the fallback

The native library **is** in the repository — `matrix-core/src/main/c/libtruthy/` holds
`libtruthy_hamming.so` (a real ELF x86-64 shared object, with `hamming_distance_u64` and
`hamming_distance_batch` in its dynamic symbol table) and its C source. So the wave's
premise — *"FFM 112.65 ns against Java's 6–16 ns argues against the native path"* — is
measurable here, and **the measurement has to be corrected before it is used**.

**The finding.** `io.matrix.imports.HammingNative` loads the library with
`System.load(<absolute path>)` and then asks `Linker.nativeLinker().defaultLookup()` for the
symbol. **A library loaded through `System.load` is not in the default lookup**, so the
symbol is never found, the static initialiser swallows the exception, and
`HammingNative.isNativeAvailable()` is **false** — the native path **never runs**, and every
call silently falls back to `Long.bitCount`.

Measured on this host on 2026-11-13 (JDK 25.0.4.1):

```
$ java --enable-native-access=ALL-UNNAMED  (the lookup diagnostic)
defaultLookup finds the symbol: false
libraryLookup finds it: result = 2
$ java -cp matrix-core/build/classes/java/main:/tmp/nativecheck NativeCheck
native available: false
hamming(0b1011, 0b0010) = 2 (java says 2)
```

`SymbolLookup.libraryLookup(path, arena)` finds the same symbol and calls it correctly — the
FFM mechanism is sound; only this class's lookup is wrong. `PanamaNativeBridge`, the other
FFM user, uses `SymbolLookup.libraryLookup` and is therefore not affected.

**What it means for the wave's premise.** The "Java 6–16 ns" side of the comparison was
measured through a path that **never uses the native library** — it *is* the fallback. So
the comparison "FFM downcall against Java" was never a comparison of two live paths in this
repository: one side was a downcall and the other was the silent fallback. **The ratio is
therefore still the right thing to measure, but it must be measured with the native side
actually reached** — and until that harness exists, the owner's 112.65 ns is a quoted
observation, not a result this repository can repeat.

**What is deliberately NOT done about it.** The defect is not fixed here. The owner's
directive for this wave is explicit — *"Не нужно пока возвращать C, JNI, FFM или GPU в
рабочий runtime"* — and fixing the lookup would **activate** a native path that is
deliberately dormant. The finding is recorded, measured and left alone; whether the native
path should exist at all is one of the wave's decisions (REP-03/REP-04), not a side effect
of a stage that was asked to preserve a baseline.

### 4.2 The rule this stage sets

The rebuild measures the two sides **inside one harness**, with the same algorithm on both
sides, and reports the **ratio**. A harness's algorithm is verified **byte-for-byte against
the production code** before its numbers are transferred — the fold is verified that way in
§5. The absolute cost of a bit count is **not** published: the two lost harnesses disagreed
on it by 2.7× (6.09 against 16.45 ns/op), and one harness that has not yet been run
replaces neither of them.

## 5. The first result of the rebuild — the information loss of the production fold

**The fold.** `TsetlinTrainer.pack(long[])` (`matrix-core/src/main/java/io/matrix/tsetlin/TsetlinTrainer.java:354-369`)
maps a vector of source bits onto the trainer's width by XOR-parity: the source bit `j`,
counted over the whole vector little-endian inside each word, is folded into the output bit
`j mod inputBits`. For a trainer of 16 bits and a 256-bit source that is **sixteen parity
groups of sixteen source bits**.

**The claim, now proved on the production code** (the test
`matrix-core/src/test/java/io/matrix/tsetlin/TsetlinPackInformationLossTest.java`, 5 tests,
0 failures):

1. **The kernel has dimension 240.** The sixteen unit source bits of the first group map
   onto the sixteen unit output bits, and each output bit is a parity over a group that no
   other output bit touches — so the image is the whole of F₂¹⁶, the rank is 16, and the
   kernel's dimension is 256 − 16 = **240**. Each group of sixteen bits leaves fifteen free
   bits (the test counts the even-parity patterns of one group: 2¹⁵ of 2¹⁶), so
   16 × 15 = 240 free bits. **2²⁴⁰ different vectors share one folded value.**
2. **The fold cannot tell a vector from its complement.** A parity over an *even* number of
   bits does not see the flip of all of them, so `F(v) = F(¬v)` — not as a possibility but
   as a property of the fold for every group size that is even, which is the case here. For
   a bipolar HDC this is decisive: the polarity **is** part of the representation. The test
   folds a random 1024-bit vector and its complement to the same value while
   `HdcEncoding.similarity(v, ¬v)` is **−1.0** and their Hamming distance is the full 1024 —
   the fold merges exactly the vectors the HDC layer calls maximally distant.
3. **One production path is unaffected; the other one is not — and the affected one is
   served.** There are **two** production consumers of the trainer, and the first answer
   this stage gave ("none today") was wrong: it was derived from a search of
   `matrix-core` alone, and the second consumer lives in the **brain-runtime module**.
   - **`io.matrix.knowledge.AcceptanceModel`** builds the trainer with
     `TextFeatureVector.WIDTH` (= 16) bits and feeds it **one word per example**
     (`AcceptanceModel.java:139-142`), the feature being a sixteen-bit packed value. For
     such a feature the fold is the identity — the test asserts it for the whole width of
     the feature space. `io.matrix.agent.AgentBrainService` imports the trainer and does
     not use it.
   - **`io.matrix.brain.runtime.RuleInductionEngine`** — **chain B, and served**. The
     gateway's `MinimalHttpServer` constructs it with an
     `io.matrix.brain.runtime.EpisodeFeatureExtractor` and hands both to the sleep
     scheduler, logging *"rule induction ARMED"*; the extractor's production width is
     **256 bits** (`EpisodeFeatureExtractor.DEFAULT_DIM`, four longs), the engine trains a
     **16-bit** trainer on them (`RuleInductionEngine.java:88,136`), and the serving path
     folds the same way (`RuleInductionEngine.predict`: *"the caller MUST pass raw words,
     not a pre-folded long, or training and serving disagree"*).
   - **On that path the fold's kernel has dimension 240** — the owner's number is the
     production number, not a hypothetical — the parity collapse holds (each group has
     sixteen members, an even number), and **two hundred and fifty six bits of the feature
     are represented by sixteen**. It is proved there, on the production width and on the
     real episodic log, by
     `matrix-brain-runtime/src/test/java/io/matrix/brain/runtime/EpisodeFeatureFoldLossTest.java`
     (3 tests, 0 failures).

**Therefore the answer to the wave's third question — *which real tasks use this path?* — is:
the brain runtime's rule induction does, and it is served.** The loss is **not** a latent
trap: it is live on chain B's sleep cycle. What the real data shows about its size is
measured, not assumed:

> `[REP-01] the real episodic log: 108 entries, 24 distinct texts, 24 distinct 256-bit
> features, 23 distinct folded values, 1 folded values carry more than one text, 2 entries
> fall into a collision`

— that is, **one pair of distinct real episodes is already indistinguishable** to the
learner. The information-theoretic loss is 2²⁴⁰ preimages per folded value; on this small
and sparse log the observed cost is 1 of 24 distinct episodes, because the hash-based
features set few bits and the folded values are therefore far from uniformly distributed.
**The loss grows with the diversity of the episodes**, and the growth is not linear.

**The decision this wave has to make is therefore not "fix the fold" but "say what the
fold is for"**: either the trainer refuses a source that does not fit its width, or the
fold is replaced by a projection that keeps the information the task needs. Widening the
trainer to 256 bits is **not** the answer: it changes the learning space and its cost,
which is exactly what the owner's directive forbids. **This touches serving code, so it is
the owner's decision and not this phase's.**

**What is still open in this stage.** The downstream accuracy on an independent sample, and
the question whether Tsetlin needs the whole vector width or a smaller informative feature
set, are the next measurements of REP-01; the honest comparisons of the existing paths are
REP-02. The comparison harness this stage is to produce must measure the two sides of every
comparison inside one harness — the rule of §4.

## 6. The stage's own verdict

- **The raw material of `/tmp/w54` is lost** — the directory does not exist and the
  repository never held it. The stage's task changes from *preserve* to *rebuild*.
- **The environment and the inputs are fixed in the repository** (§3), so a measurement can
  be repeated instead of remembered.
- **One of the wave's priorities is now settled on the production code** (§5): the fold's
  kernel has dimension 240, it cannot distinguish a vector from its complement, and **the
  path is served** — chain B's rule induction trains a 16-bit trainer on 256-bit features,
  and the real episodic log already contains a pair of distinct episodes that the fold
  makes indistinguishable. **The first answer of this stage ("no production task uses the
  path today") was wrong**: it came from a search of `matrix-core` alone, and the second
  consumer lives in the brain-runtime module. The correction is recorded here rather than
  quietly overwritten, because a wrong "it is not live" is exactly the kind of claim that
  would have let a live loss survive.
- **The premise of the second priority had to be corrected** (§4): the native Hamming path
  never activates, so the "FFM against Java" comparison was made against a silent fallback.
  The defect is recorded and deliberately left unfixed, because the wave's directive keeps
  the native path out of the runtime.
- **No absolute number is published**: the bit-count figure of the lost harnesses is quoted
  as an observation and marked as not reproducible here, and the rule of §4.2 forbids
  publishing a new absolute figure before one harness measures both sides. **The harness
  now exists** — §4.2's rule is carried out in `docs-v2/research/REP-SUBSTRATE-02-workloads.md`,
  where both sides of the comparison are measured inside one harness and only the ratio is
  published.
