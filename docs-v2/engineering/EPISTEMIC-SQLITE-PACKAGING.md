# EPISTEMIC-SQLITE-PACKAGING — the packaged application opens its own store

**Record:** the packaging defect of the SQLite native library (EPISTEMIC-INTEGRITY, P0.5)
**Date:** 2026-11-15
**Status:** the JVM packaging is **VERIFIED_LOCAL** — the packaged application opens its
store with no ad-hoc property, from a working directory outside the repository; the
native-image packaging is **FIXED-BY-CONSTRUCTION but NOT VERIFIED** (that build is red
on this host for an unrelated reason, §7)
**Owner:** the coordinator; **implementation:** the packaging specialist

---

## 1. What this document is

The project recorded a defect that blocked every packaged run touching a store:

> "the **packaged application cannot open the SQLite store on this host** — the driver's
> own resource extraction fails inside the application's class loader ('No native library
> found for os.name=Linux, os.arch=x86_64'), so every packaged-app run that touches the
> store needs `-Dorg.sqlite.lib.path=<the directory that holds libsqlitejdbc.so>`"
> — `docs-v2/governance/PROJECT-STATE.md:233`

The mandate (P0.5) asks for the problem to be **reproduced and fixed** so that an
ordinary supported packaged application accesses the store **without an ad-hoc property**,
tested **from a clean build** and **from a non-repo working directory** (§12 E).

This document records the measurement, the change, the proof and the guard. It corrects
one claim of the inherited record: **the class loader is not the broken part.**

---

## 2. What the measurement shows

### 2.1 The driver and where its library is

| the fact | the evidence |
|---|---|
| the driver | `org.xerial:sqlite-jdbc:3.43.0.0` — `matrix-core/build.gradle:57` |
| the packaged driver | `matrix-core/build/quarkus-app/lib/main/org.xerial.sqlite-jdbc-3.43.0.0.jar` |
| the native library inside it | `org/sqlite/native/Linux/x86_64/libsqlitejdbc.so` (the driver ships Linux, Mac, Windows, FreeBSD and the musl and Android variants) |
| how the driver loads it | `org.sqlite.SQLiteJDBCLoader.loadSQLiteNativeLibrary()` → `extractAndLoadLibraryFile()` writes `<java.io.tmpdir>/sqlite-<version>-<uuid>-libsqlitejdbc.so` (with a `.lck` sibling) and calls `System.load` on it |

### 2.2 The message is the driver's LAST RESORT, not a class-loader verdict

The recorded message is the driver's fallback, emitted after its extraction has failed
and its second attempt (`System.loadLibrary` over `java.library.path`) has failed too.
An isolated probe of the driver alone reproduces the whole chain:

```
$ java -Djava.io.tmpdir=<a directory that cannot be written> -cp <driver> Probe
Failed to open directory: <the directory>
java.nio.file.NoSuchFileException: <the directory>/sqlite-3.43.0.0-<uuid>-libsqlitejdbc.so.lck
	at org.sqlite.SQLiteJDBCLoader.extractAndLoadLibraryFile(SQLiteJDBCLoader.java:194)
	at org.sqlite.SQLiteJDBCLoader.loadSQLiteNativeLibrary(SQLiteJDBCLoader.java:329)
Failed to load native library through System.loadLibrary
java.lang.UnsatisfiedLinkError: no sqlitejdbc in java.library.path: ...
	at org.sqlite.SQLiteJDBCLoader.loadNativeLibraryJdk(SQLiteJDBCLoader.java:283)
FAILED: java.sql.SQLException: Error opening connection
  caused by: java.lang.Exception: No native library found for os.name=Linux, os.arch=x86_64, paths=[...]
```

**The resource lookup succeeds; the WRITE into the extraction target fails.** The
inherited diagnosis ("the driver's own resource lookup fails inside the application's
class loader") is therefore wrong: the driver finds its own resource, and the failure is
in the directory it writes that resource into.

### 2.3 The extraction target of the packaged application

The application does not use the JVM's temporary directory. `io.matrix.Scratch.prepare()`
(`matrix-core/src/main/java/io/matrix/Scratch.java:58-72`, called from
`MatrixApplication.main():37`) points `java.io.tmpdir` at a directory of the
application's own — `<working directory>/data/tmp/run-<pid>` — and removes the runs before
it. That relocation exists because of the measured `/tmp` quota incident (R48, NR-034:
52 ONNX extractions = 19.5 GB, the per-user quota of the `/tmp` tmpfs exhausted, and the
build, the gates and the shell all died with `EDQUOT`).

So the extraction target is under the application's control, and the failure mode is:
**the target cannot be written** (a full or read-only `/tmp` when the relocation could
not be prepared; a `noexec` mount; a directory that was removed under the process).

### 2.4 The recorded failure did NOT reproduce on this host

With the packaged build of 2026-11-15 11:39 the store opens **without any property**,
from a non-repo working directory **and** from the repository root. The library was
extracted by the driver itself and mapped from the application's own scratch directory:

```
/tmp/matrix-packaging-probe/data/tmp/run-48977/sqlite-3.43.0.0-<uuid>-libsqlitejdbc.so
```

The knowledge store was created and the report route answered from it. The recorded
failure is therefore **not reproducible in the JVM packaging on this host**; what
reproduces is the mechanism of §2.2 — the unusable extraction target.

---

## 3. The change

Three changes, all in the packaging or in the tooling that demonstrated the defect. **No
class-loader hack, no new dependency, no version upgrade, and no change to the
application's own code.**

### 3.1 `matrix-core/src/main/resources/application.properties` — the native image

`quarkus.native.additional-build-args` now carries
`--initialize-at-build-time=org.sqlite.SQLiteJDBCLoader` (line 88). A native image
cannot extract a file and `dlopen` it at run time; the extraction has to happen while
the image is built, where the resource is still a resource. Without this entry the image
reaches the driver's last resort and reports exactly the recorded message. The
resources are already in the image (`quarkus.native.resources.includes=**`).

### 3.2 `matrix-core/build.gradle` — the same for the GraalVM plugin path, and a guard

* `--initialize-at-build-time=org.sqlite.SQLiteJDBCLoader` was added to the
  `graalvmNative` build arguments (next to `org.sqlite.util.ProcessRunner`), so the two
  native build paths agree.
* A new task `verifyPackagedSqliteNativeLibrary` (line 649) reads the **packaged
  output** and fails the build when the driver is missing from it or when it carries no
  native library for **this** platform. It is a build-time guard for a defect that is
  otherwise invisible until a store is opened — by a route, not by the build.

```
$ ./gradlew :matrix-core:verifyPackagedSqliteNativeLibrary --offline --console=plain
> Task :matrix-core:verifyPackagedSqliteNativeLibrary
the packaged application carries the SQLite native library: org/sqlite/native/Linux/x86_64/, org/sqlite/native/Linux/x86_64/libsqlitejdbc.so
BUILD SUCCESSFUL in 3s
```

### 3.3 `scripts/c2a-http-demonstration.py` — the workaround is gone

The demonstration used to take the native library out of the driver jar **by hand** and
point `-Dorg.sqlite.lib.path` at it. That was a copy of the driver's own job, and it is
the "ad-hoc property" the mandate forbids. The function is deleted; the run now refuses
to start if the environment carries the property, and it fails loudly — with the
driver's own message in the server log — if the store cannot be opened.

---

## 4. The proof

### 4.1 The build

```
$ rm -rf matrix-core/build/quarkus-app
$ flock /tmp/gradle-matrix.lock bash -c 'time ./gradlew :matrix-core:quarkusBuild \
      :matrix-core:verifyPackagedSqliteNativeLibrary --offline --console=plain'
```

The packaged output was deleted and rebuilt; the Java compilation was **up-to-date**
because the module's sources belong to other agents and had not changed. The packaged
artifact carries the changed `application.properties` (checked inside
`quarkus-app/app/matrix-core-1.0.0.jar`).

| the measurement | the value |
|---|---|
| the packaged build, deleted output, sources unchanged | **7 s** (11 tasks, 6 executed) |
| the packaging check alone | **3 s** |
| the packaged smoke, including two application starts | **19 s** |
| the unit lane's guard (6 tests) | **5 s** |
| the cold path-sensitive compile of matrix-core (main + test) in a **fresh clone** with a warm dependency cache — measured by another agent on this host, not by this work | **17 s** |
| the **full** matrix-core suite in that same fresh clone — the regression the FAST lane omits | **504 s**, and it was **red** (another agent's measurement, in flight with other agents' changes) |

### 4.2 The run, from a directory outside the repository

`scripts/packaged-sqlite-smoke.py` starts the packaged application with
`cwd=<a fresh temporary directory>`, with **no `org.sqlite.lib.path` on the command
line and none in the environment** (the script refuses to run if the environment
carries it), and it then exercises the product route that touches the store:

```
$ python3 scripts/packaged-sqlite-smoke.py --skip-build
the application is ready on port 37871 (health 503)
the corpus was seeded: 200
the report request answered 201
the application is ready on port 37871 (health 503)
the request after the restart answered 200 (REPLAYED)
the packaged application opened its store and served the report with no org.sqlite.lib.path property anywhere
```

The machine-readable report (`<the work directory>/packaged-sqlite-smoke.json`):

| the check | the result |
|---|---|
| the store opened | `storeOpened: true` |
| the driver's own extraction | `data/tmp/run-86935/sqlite-3.43.0.0-<uuid>-libsqlitejdbc.so` — the application's own scratch directory |
| the report route | `201`, one artifact of 52 977 bytes |
| the digest | the file's SHA-256 `2280c10d…228f` equals the digest the response reported — **match** |
| the repeated request | `200`, disposition `REPLAYED`, **one** artifact |
| the request without consent | `403`, **nothing written** |
| four concurrent requests, one identity | `201, 200, 200, 200` — **one** artifact added |
| the request after a **restart** of the application | `200`, disposition `REPLAYED`, the artifact count unchanged |

---

## 5. The guard

| the guard | the lane | what it fails on |
|---|---|---|
| `matrix-core/src/test/java/io/matrix/packaging/PackagedSqliteNativeLibraryTest.java` | the **unit** lane (`./gradlew :matrix-core:test --tests "io.matrix.packaging.*"`) | the driver on the classpath stops carrying the native library; the driver's own extraction stops working without the property; the recorded failure's mechanism changes |
| `:matrix-core:verifyPackagedSqliteNativeLibrary` (Gradle) | the **packaging** lane (runs with `quarkusBuild`) | the packaged application stops carrying the driver, or the driver stops carrying the library for this platform |
| `scripts/packaged-sqlite-smoke.py` | the **MANUAL / RELEASE** lane | the packaged application cannot open its store without the property, or the report action regresses (digest, idempotence, consent, concurrency, restart) |

The unit-lane test observes the extraction in a **child JVM**, because the driver
extracts and loads its library once per JVM and the surrounding suite has already opened
databases. It asserts three things: the driver carries the library for this platform;
a fresh JVM opens a store with **no property**, extracting the library into the scratch
directory it was given; and the **negative control** — an unusable extraction target —
reproduces the recorded "No native library found" message. The negative control is what
turns "it works today" into a pinned mechanism.

**The unit lane's evidence:**

```
$ flock /tmp/gradle-matrix.lock bash -c 'rm -rf matrix-core/build/test-results && \
      ./gradlew :matrix-core:test --tests "io.matrix.packaging.*" --offline --console=plain'
BUILD SUCCESSFUL in 5s

$ cat matrix-core/build/test-results/test/TEST-io.matrix.packaging.PackagedSqliteNativeLibraryTest.xml
<testsuite name="io.matrix.packaging.PackagedSqliteNativeLibraryTest"
           tests="6" skipped="0" failures="0" errors="0" time="0.637">
```

Six tests, zero failures, zero errors, zero skipped — and the six are: the driver is on
the classpath; the driver carries the native library for this platform; the driver opens
a store by itself (no property, no manual step); the recorded failure is an unusable
extraction target, not the class loader; the driver's loader is reachable; a store opens
in this JVM as well.

### 5.1 The guard is not a bystander — the mutation proof

The defect was **put back** into the packaged artifact: the native library was removed
from the packaged driver jar (the jar was copied to `/tmp` first, the copy was edited,
and the copy was restored afterwards; `cmp` reported the restored file byte-identical to
the original). With that mutation:

* the **packaging check** went red:

```
$ ./gradlew :matrix-core:verifyPackagedSqliteNativeLibrary -x quarkusBuild --offline
> the packaged SQLite driver carries no native library for Linux/x86_64
  (expected a resource under org/sqlite/native/Linux/x86_64/): [org.xerial.sqlite-jdbc-3.43.0.0.jar]
BUILD FAILED in 2s
```

* the **smoke** went red, and the application reproduced the recorded failure **in the
  packaged application**, verbatim:

```
$ python3 scripts/packaged-sqlite-smoke.py --skip-build
the corpus was seeded: 500
the report request answered 500
FAILED: the packaged application did not open its store
FAILED: the driver did not extract its native library
…
$ grep "No native library" <work>/server-one.log
ERROR […] HTTP Request to /v1/learning/workspaces/packaged-smoke/seed failed:
  java.lang.IllegalStateException: Cannot open the knowledge store at <work>/knowledge.sqlite
Caused by: java.sql.SQLException: Error opening connection
Caused by: java.lang.Exception: No native library found for os.name=Linux, os.arch=x86_64, paths=[…]
```

* the **unit lane** went red when the stripped driver was the only driver on the
  classpath (the test's own methods, run outside JUnit so that the mutation could be
  observed):

```
FAILED: theDriverCarriesTheNativeLibraryForThisPlatform -> AssertionError: the SQLite driver
  /tmp/stripped-driver.jar must carry the native library for Linux/x86_64; without it the
  first connection fails with "No native library found"
FAILED: theDriverOpensAStoreByItself -> AssertionError: the driver must open the store
  without the org.sqlite.lib.path property; the child JVM said: … No native library found …
```

With the intact driver all three pass. The packaged artifact was restored and
`cmp` reported it byte-identical.

**This is also the reproduction of the inherited defect**: the packaged application
cannot open its store when the packaged driver does not carry the native library, and it
reports exactly the message the project recorded.


---

## 6. The reproducible instructions for an operator or a CI runner

```
# 1. the packaged application, and the packaging check
flock /tmp/gradle-matrix.lock ./gradlew :matrix-core:quarkusBuild \
    :matrix-core:verifyPackagedSqliteNativeLibrary --offline --console=plain

# 2. the guard of the packaging lane (MANUAL / RELEASE)
python3 scripts/packaged-sqlite-smoke.py

# 3. the guard of the unit lane
flock /tmp/gradle-matrix.lock bash -c 'rm -rf matrix-core/build/test-results && \
    ./gradlew :matrix-core:test --tests "io.matrix.packaging.*" --offline --console=plain'
```

**Nothing anywhere needs `-Dorg.sqlite.lib.path`.** The driver finds its own library in
its own jar, extracts it into the application's own scratch directory
(`<working directory>/data/tmp/run-<pid>`, off the `/tmp` tmpfs and its quota), and
loads it there. The only requirement is a **writable working directory** — the
application's scratch directory lives under it, and the driver's extraction target is
that directory.

---

## 7. What was NOT verified

1. **The native image was not built.** The GraalVM build is red on this host for an
   unrelated reason (out of memory, since 2026-10-31). The build-time initialisation of
   the driver's loader (§3.1) is therefore **fixed by construction and not verified by a
   run**; it is the standard recipe of the driver, and the resource is already inside the
   image.
2. **No platform other than this one was tested** (Linux x86_64, JDK 25, Quarkus 3.38.3,
   sqlite-jdbc 3.43.0.0). The packaging check maps the platform to the driver's own
   directory names (Linux/Mac/Windows × x86_64/aarch64/x86), but only this host was run.
3. **The recorded failure itself was not reproduced** in the JVM packaging (§2.4). What
   was reproduced is its mechanism (§2.2). A reader who wants the original failure needs
   an environment whose extraction target cannot be written — the negative control of the
   unit-lane test is exactly that, and it is the reproducible form of the defect.
4. **The demonstration script was not re-run end to end**; the change to it is the
   removal of the workaround, and the equivalent journey was run by the smoke script.
5. **The store's own behaviour under a second writer** is out of scope: the writer lock
   (`io.matrix.cognitive.WriterLock`) refuses a second process, and the smoke script
   runs one process at a time.

---

## 8. The inherited records that this document corrects

| the record | the correction |
|---|---|
| `PROJECT-STATE.md:233`, `ROADMAP.md:569` (C2A.6), `C2A-FINAL-REPORT.md` §G.5.1 — "the driver's own resource extraction fails inside the application's class loader" | the resource lookup **succeeds**; the failure is the driver's **write into its extraction target** (§2.2) |
| the same records — "every packaged-app run that touches the store needs `-Dorg.sqlite.lib.path`" | **not reproducible** in the JVM packaging on this host; the packaged application opens its store with no property, from a non-repo working directory (§4.2) |
| `CAPABILITY-REGISTRY.yml:2424` — the capability's limitation names the property as a property of the route | the limitation belongs to the packaging, and the packaging no longer needs the property; the route's own limitation (a written report is a record, not evidence) stands |
