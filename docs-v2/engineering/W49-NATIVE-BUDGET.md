# W49 — THE NATIVE-IMAGE RESOURCE BUDGET

**Wave:** W49 · **Task:** W49-02 · **Owner decision:** **OD-046** · **Date:** 2026-11-06

**What this document is.** The measured resource budget of the native-image
build, which OD-046 asked for: **the heap it needs and the runner it needs**.
Both numbers were obtained by running the build, not by reading a document.

**What this document is not.** It is not a claim that the native capability
works. **The native build does not succeed** — OD-046 is explicit that the
capability stays `BLOCKED` until a build succeeds, and §4 names the second
blocker precisely. The measurement is what makes the next attempt cheap.

---

## 1. The toolchain

| the fact | the value | how it was checked |
|---|---|---|
| the toolchain | **GraalVM CE 25.5.5.0.3** (`graalvm-community-25.5.5.0.3+1.1`) | installed under `~/.local/graalvm/` — **outside the repository**; the repository is unchanged by the installation |
| the provenance | `graalvm-community-jdk-25i5-25.0.5.0.3_linux-x64_bin.tar.gz`, sha256 `c6438dee6c3ade2220d40b7ccdcad24b512d30e233e61bb4fc70519522de34ef` | the published `.sha256` file, compared with `sha256sum` — they match |
| the native-image version | `native-image 25.0.5.0.3` | `native-image --version` |
| the JDK it runs | GraalVM CE 25.5.5.0.3+1.1 (build 25.0.5.0.3+1-jvmci-25-b26) | `java -version` |
| the host | 59 GiB total memory, 47 GiB available; 197 GiB free on disk | `free -g`, `df -h /` |

**The host was NOT equipped with GraalVM before this wave** — the toolchain was
absent (`~/.sdkman/candidates/java/` was empty, `which native-image` found
nothing). It was installed for this measurement, outside the repository, and it
is not a dependency of the project.

**The build command** — the same one the CI workflow runs
(`.github/workflows/native.yml`):

```bash
JAVA_HOME=<graalvm> ./gradlew :matrix-core:quarkusBuild \
  -Dquarkus.native.enabled=true \
  -Dquarkus.package.type=native \
  -Dquarkus.native.native-image-xmx=<heap> \
  --no-daemon -x test
```

The heap property is Quarkus's own: `quarkus.native.native-image-xmx`. The
CI workflow does **not** pass it, so the CI build runs on Quarkus's default of
**8 GB** — which is exactly the value that fails (§2).

---

## 2. The measurement

Two attempts, each with `/usr/bin/time -v` around the whole Gradle invocation
and the native-image builder's own "Build resources" line read out of the log.

| # | the heap | the outcome | the peak RSS | the wall clock |
|---|---|---|---|---|
| 1 | **8 GB** (Quarkus's default — the CI's value) | **FAILED** — `java.lang.OutOfMemoryError: Java heap space` → *"The Native Image build process ran out of memory."* | **8 894 456 kB ≈ 8.48 GiB** | 2 m 44 s |
| 2 | **16 GB** (`-Dquarkus.native.native-image-xmx=16g`) | the **memory wall is passed**; the build stops at a **different, non-memory blocker** (§4) | 9 605 572 kB ≈ 9.16 GiB¹ | 2 m 09 s |

¹ The second attempt's peak is NOT the full build's peak: the build stopped
before the memory-hungry phase of the image generation, so the number says
"the analysis fits in 16 GB", not "the whole build needs 16 GB".

**Attempt 1 reproduces the CI's failure exactly.** The recorded CI run
`41219224376` (2026-10-31) died with the same `OutOfMemoryError`, and its
native-image log says:

```
Build resources:
 - 7.64GB of memory (45.6% of system memory, set via '-Xmx8g')
 - 4 thread(s) (100.0% of 4 available processor(s), determined at start)
Terminating due to java.lang.OutOfMemoryError: Java heap space
```

### 2.1 The heap the build needs

**More than 8 GB.** 8 GB is not enough — it is the value the CI uses, and it is
the value that fails. 16 GB is enough to get past the memory wall: with it the
image generation proceeded through the analysis and stopped, 2 minutes later,
for a reason that has nothing to do with memory (§4).

**The exact minimum is not yet known**, and this document does not pretend
otherwise: the 16 GB attempt never reached the phase that is most expensive,
so a build that completes might need more. The honest statement is the
interval: **the requirement is > 8 GB, and 16 GB clears the memory wall.**

### 2.2 The runner the build needs

The CI's own log gives the runner's size without guessing:

```
7.64 GB = 45.6 % of system memory   ⇒   system memory ≈ 16.75 GB
```

That is GitHub's **standard `ubuntu-latest` runner: 4 vCPU, 16 GB RAM**
(`ubuntu-24.04`, runner image 20261021.618). The conclusion:

**The standard GitHub runner CANNOT host this build.** A build that needs
more than 8 GB of heap, on a machine with 16.75 GB in total, leaves the
operating system with the remainder — and the CI's own 8 GB attempt was
killed by the heap, not by the OS. A 16 GB heap does not fit on a 16.75 GB
machine either.

**The native workflow needs a runner with at least ~24 GB of memory** — a
larger GitHub runner (8 vCPU / 32 GB), or a self-hosted runner of that size.
The workflow's `runs-on: ubuntu-latest` line is the wrong line for this build,
and that is a **recorded, actionable finding**: the workflow cannot succeed on
the runner it selects, no matter how the code changes.

**The local host CAN host it** — 59 GiB total, 47 GiB available ✓ ✓ — which
is why the measurement could be taken here at all.

### 2.3 The cost of the attempt

| the cost | the measured value |
|---|---|
| the wall clock to the failure | 2 m 09 s (16 GB) / 2 m 44 s (8 GB) |
| the CPU | 675 % of the machine's cores during the attempt |
| the disk written | ~1.5 GB of file-system output during the 8 GB attempt |
| the toolchain download | 335 MB compressed |
| the temporary space | the native-image builder writes its intermediates to `TMPDIR`; the measurement pointed it at the disk, not at a tmpfs |

---

## 3. What the measurement changes

| the thing | before | after |
|---|---|---|
| the CI's native build | "it OOMs" — a symptom | "it needs more than 8 GB of heap and more than the standard runner's 16 GB of memory" — a **budget** |
| the native workflow | `runs-on: ubuntu-latest` | **cannot succeed on that runner** — a larger runner is required |
| the local feasibility | unknown | **feasible on this host** (47 GiB available), with GraalVM installed outside the repository |
| the capability's status | BLOCKED | **still BLOCKED** — the build does not succeed (§4) |

**What OD-046 asked for and what it got.** The decision reads: *"the native-image
build gets a measured resource budget in the next wave — the heap and the runner
size it actually needs are recorded — and the native capability stays BLOCKED
until a build succeeds."* Both numbers are now measured: **the heap** (> 8 GB,
16 GB clears the memory wall) and **the runner** (> 16 GB of memory; the
standard runner is too small). The capability stays `BLOCKED`, and the reason
it stays `BLOCKED` is now a *named defect* rather than an out-of-memory error.

---

## 4. The second blocker — named, with its fix

With the memory resolved, the image generation fails on something else:

```
Error: com.oracle.svm.core.util.UserError$UserException:
  Class initialization of org.apache.pdfbox.pdmodel.encryption.PublicKeySecurityHandler failed.
  This error is reported at image build time because class
  org.apache.pdfbox.pdmodel.encryption.PublicKeySecurityHandler is registered
  for linking at image build time ...
Caused by: java.lang.NoClassDefFoundError: org/bouncycastle/cms/CMSException
Caused by: java.lang.ClassNotFoundException: org.bouncycastle.cms.CMSException
```

**The cause.** `matrix-core/build.gradle:72` declares
`org.apache.pdfbox:pdfbox:3.0.3`. PDFBox declares `bcprov-jdk18on` and
`bcpkix-jdk18on` as **optional** dependencies — the application must add them
itself if it wants PDFBox's public-key encryption support. It does not, so the
class is missing from the runtime classpath, and the native-image build fails
while initialising it.

**The fix** (not applied in this wave — it is a dependency change, and it is
the owner's call whether MATRIX needs PDFBox's encryption support at all):

```gradle
// PDFBox 3.0.3 declares BouncyCastle as an OPTIONAL dependency; without it
// the public-key security handler cannot initialise, and the native-image
// build fails with NoClassDefFoundError: org/bouncycastle/cms/CMSException.
implementation 'org.bouncycastle:bcpkix-jdk18on:1.78.1'   // the version PDFBox 3.0.3's parent declares
```

**Why it was not applied here.** OD-046 asks for a measurement, and the
measurement is complete without it (§2). Adding a runtime dependency changes
the application's dependency graph, and whether MATRIX should carry BouncyCastle
at all — or exclude PDFBox's encryption handler instead — is a decision, not a
measurement. The finding is recorded here so the next attempt is one line away.

---

## 5. The commands, and what they returned

```bash
# the toolchain
curl -sSL -o graalvm.tar.gz https://github.com/graalvm/graalvm-ce-builds/releases/download/graal-25.5.5.0.3/graalvm-community-jdk-25i5-25.0.5.0.3_linux-x64_bin.tar.gz
sha256sum graalvm.tar.gz          # c6438dee… — matches the published .sha256

# attempt 1 — the CI's heap
JAVA_HOME=<graalvm> /usr/bin/time -v ./gradlew :matrix-core:quarkusBuild \
  -Dquarkus.native.enabled=true -Dquarkus.package.type=native --no-daemon -x test
#   -> OutOfMemoryError: Java heap space; BUILD FAILED in 2m 44s
#   -> Maximum resident set size (kbytes): 8894456

# attempt 2 — a bigger heap
JAVA_HOME=<graalvm> /usr/bin/time -v ./gradlew :matrix-core:quarkusBuild \
  -Dquarkus.native.enabled=true -Dquarkus.package.type=native \
  -Dquarkus.native.native-image-xmx=16g --no-daemon -x test
#   -> 16.00GiB of memory (26.9% of system memory, set via '-Xmx8g -Xmx16g')
#   -> NoClassDefFoundError: org/bouncycastle/cms/CMSException; BUILD FAILED in 2m 8s
#   -> Maximum resident set size (kbytes): 9605572

# the CI's own record of the same failure
gh api "repos/AlexanderNarbaev/agi/actions/jobs/124590096291/logs"
#   -> "7.64GB of memory (45.6% of system memory, set via '-Xmx8g')"
#   -> "Terminating due to java.lang.OutOfMemoryError: Java heap space"
```

## 6. The honest limits of this measurement

1. **The build does not succeed** — so the size of the produced binary is
   **NOT MEASURED**. No number is offered for it, because none was observed.
2. **The exact minimum heap is not known** — the requirement is stated as an
   interval (> 8 GB, 16 GB clears the wall), not as a single number.
3. **The 16 GB attempt's peak RSS is not the full build's peak** — the build
   stopped before the expensive phase.
4. **The measurement was taken on this host and on the CI's runner, not on
   every runner** — the runner's size is derived from the CI log's own
   percentage, not guessed.
5. **The toolchain is installed outside the repository** — a clone of this
   repository does not carry it, and the native build still needs it.
