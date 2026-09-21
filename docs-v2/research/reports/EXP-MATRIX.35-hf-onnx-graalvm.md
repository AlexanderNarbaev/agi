# RUN 54 — HuggingFace integration + ONNX/GraalVM documentation

## Status

### HuggingFace (RESOLVED)

The user reported: "Hugging face have worked and token in cli, use it."

- HF CLI is installed: `/home/alexandr-narbaev/.local/bin/hf`
- Qwen2.5-0.5B-Instruct model successfully downloaded
  (`models/hf_cache/qwen05b/`, 954 MB).
- New `HuggingFaceFetcher` class wraps the `hf download` command for
  production use at startup.

**Test result**: `HuggingFaceFetcherTest` (9/9 pass) — including
actual fetch via the CLI.

### ONNX — user action required

To run ONNX inference (e.g., for non-distilled chain access):

**Required user actions**:
1. **GPU path (optional, 10-100× speedup)**:
   - Install CUDA 12.x toolkit:
     `sudo apt install cuda-toolkit-12-4`
   - Install cuDNN 9.x matching the CUDA version:
     `sudo apt install libcudnn9-dev-cuda-12`
   - Verify: `nvidia-smi` (requires NVIDIA driver)
2. **CPU path (works without GPU)**:
   - ONNX Runtime CPU JAR is already on the classpath
     (`com.microsoft.onnxruntime:onnxruntime:1.29.0`).
   - Native binaries are bundled.
3. **Export Qwen2.5-0.5B to ONNX**:
   ```bash
   pip install optimum[exporters]
   optimum-cli export onnx \
     --model Qwen/Qwen2.5-0.5B-Instruct \
     --task causal-lm \
     models/onnx/qwen05b
   ```
   This is a one-time offline operation (~5 min on CPU).
4. **Wire the exported .onnx into the production chain runner**:
   - Update `application.properties`:
     `matrix.chain.onnx.path=models/onnx/qwen05b/model.onnx`
   - The existing ONNX integration code will pick it up automatically.

**Time estimate**: 30-60 minutes for export + integration tests.

**Memory**: CPU ONNX needs ~1 GB RAM. GPU ONNX needs ~2 GB VRAM
for Qwen2.5-0.5B.

### Native GraalVM — RFC required

The native build (GraalVM CE 25.0.2) has been blocked since RUN 18
(EXP-MATRIX.13-native-run18). After RUN 53 retry (EXP-MATRIX.34),
the same cascading `UnsupportedFeatureException` for
`io.netty.resolver.dns.DnsNameResolverBuilder` +
`NoClassDefFoundError` for `org.tukaani.xz.XZInputStream` persists.

**Three RFC paths forward** (any one suffices):

#### Path A: Mandrel registry token
- Mandrel is the Red Hat distribution of GraalVM with additional
  platform support.
- Get a registry token from https://developers.redhat.com/products/mandrel
- Set `MANDREL_REGISTRY_TOKEN` env var
- Rebuild with `--toolchain mandrel`

#### Path B: Replace Scala/Pekko dependencies
- The cascading failure originates from Scala/Pekko transitive
  dependencies in Quarkus extensions.
- Identify which extensions pull these in (likely: vertx pgclient
  via Scala Jackson modules).
- Replace with pure-Java alternatives.

#### Path C: `--report-unsupported-elements-at-runtime` fallback
- Add `--report-unsupported-elements-at-runtime` to the
  `quarkus.native.additional-build-args`.
- Build will produce a native image with stubs for unsupported
  elements, falling back to JVM at runtime for those paths.
- Trade-off: native startup is faster, but some features degrade.

**Current production target**: JVM mode (Quarkus uber-jar, 155 MB).
All user-facing features work in JVM mode. Native build is a
performance optimization, not a correctness requirement.

**Time estimate**: 1-3 hours for any path (depending on RFC decision).

## Cross-references

- EXP-MATRIX.13-native-final, EXP-MATRIX.13-native-run18,
  EXP-MATRIX.34-native-status: native build history.
- HuggingFaceFetcher.java: production wrapper.
- application.properties: existing config for chain runner.

## Next steps

For the current session, the HuggingFace fetcher is wired and the
ONNX + GraalVM paths are documented. Production deployment should
choose one of the RFC paths when native image is required.
