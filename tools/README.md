# tools/ — the formal verification toolchain

## tla2tools.jar

**The bytes are pinned and tracked.** `tla2tools.jar` is the TLA+ toolbox the formal
gate model-checks the repository's specifications with, and the repository keeps the
exact bytes it uses.

**Why the bytes are in the repository.** The tool was downloaded from the tlaplus
release `v1.8.0` on 2026-10-30, and that digest was recorded in `formal/mutations.json`
and in the CI workflow. The upstream asset was then **replaced in place**: the GitHub
API reports the `v1.8.0` release as created on `2026-11-06T00:39:06Z` with
`immutable: false` and `target_commitish: master`, and the asset it serves today has
the digest

    654aa50197faa48fa0136b41257270de6469d29ef323e7822ead59d2be43ce10   (4,509,176 bytes)

while the pinned bytes are

    fc6878c603f0fdc18b9fe3e5bf51860cb90ab5b1154e3c371660c036d6bdd63e   (4,504,421 bytes)

The tlaplus repository does not track the jar
(`GET /repos/tlaplus/tlaplus/commits?path=tla2tools.jar` → `[]`) and it is not published
to Maven Central, so there is no content-addressed upstream copy to point at. A
download is therefore no longer a reproducible source, and the CI's `sha256sum -c`
correctly refused the bytes the upstream served — the check was right and the SOURCE was
not immutable.

**The rule:** the local gate and the CI must model-check with the SAME bytes, and the
bytes must be verifiable without trusting a URL. So the jar is tracked, and the digest
is verified on every run:

```bash
sha256sum --check --strict <<<"fc6878c603f0fdc18b9fe3e5bf51860cb90ab5b1154e3c371660c036d6bdd63e  tools/tla2tools.jar"
```

**What this costs:** 4.5 MB in the repository's history. The repository already tracks
the Gradle wrapper jar, a native CUDA library, a BitNet weights file and an ONNX model,
so a tool the formal gate depends on is not out of place.

**What this does not do:** it does not pin the tool to an upstream release. The tool is
pinned to the bytes in this repository, which is what "the same verified tool artifact
locally and in CI" requires. A future upgrade is a deliberate act: change the jar,
change the digest in `formal/mutations.json` and in the workflow, and record why.
