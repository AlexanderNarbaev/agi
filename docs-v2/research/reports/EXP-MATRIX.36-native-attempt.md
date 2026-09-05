# EXP-MATRIX.36 — Native build attempt log (RUN 55)

## Summary

This report documents native-image build attempts during RUN 55.
None succeeded. **JVM mode (Quarkus uber-jar, 155 MB) remains the
production target.**

## Attempted paths

### Path 1: Local GraalVM CE 25.0.2 (default)
**Status**: FAILED.

**Error sequence** (each step required a new flag):
1. `org.tukaani.xz.XZInputStream NoClassDefFoundError`
   - Fix: add `--initialize-at-build-time=org.tukaani.xz` +
     `--initialize-at-run-time=org.tukaani.xz.XZInputStream`.
2. `org.apache.commons.compress.compressors.xz.XZCompressorInputStream`
   - Fix: add `--initialize-at-run-time=org.apache.commons.compress.compressors.xz.XZCompressorInputStream`.
3. `java.util.Random found in image heap`
   - Fix: add `--initialize-at-run-time=java.util.Random`,
     `java.util.SplittableRandom`, `java.util.concurrent.ThreadLocalRandom`,
     `org.slf4j.LoggerFactory`. ALSO add
     `--initialize-at-run-time=io.matrix.SystemDemo` (which has a
     static Random field).
4. `io.netty.resolver.dns.DnsAddressResolverGroup found in image heap`
   - Cannot fix: netty's own native-image.properties mandates RUN_TIME
     init for these classes, but they end up in the image heap
     regardless.
   - Override to BUILD_TIME fails with "Incompatible change of
     initialization policy" (the netty library's properties win).

### Path 2: Mandrel container (recommended)
**Status**: FAILED (authentication required).

```
quay.io/quarkus/ubi-quarkus-mandrel-builder:25.0.1-java25
→ 401 Unauthorized
```

Mandrel images require a Red Hat registry token.

### Path 3: Public GraalVM container
**Status**: FAILED (image not found).

```
container-registry.oracle.com/graalvm/native-image:25
→ network unreachable / 401 / image not found
ghcr.io/graalvm/native-image:25.0.2
→ not found
```

## Conclusion

Native build is **NOT feasible in this environment** without one of:
1. **Mandrel registry token** (Red Hat developer account)
2. **Network access to public GraalVM registry**
3. **Code refactor** to remove DnsAddressResolverGroup from image heap

For now, **JVM mode is the production target**. All user-facing
features work in JVM mode. Native is a performance optimization,
not a correctness requirement.

## Action items for the user

If native build is required:
1. Set up Red Hat developer account → get Mandrel token.
2. Provide network access to container registries.
3. OR authorize a code refactor to remove the static netty DnsNameResolver
   usage (probably in Quarkus vert.x initialization).

## Test code

No new tests added (build attempts only).

## Cross-references

- EXP-MATRIX.13-native-final: original failure
- EXP-MATRIX.13-native-run18: RUN 18 extension
- EXP-MATRIX.34-native-status: RUN 53 status
- EXP-MATRIX.35-hf-onnx-graalvm: HF/ONNX/GraalVM documentation
- This report (EXP-MATRIX.36): RUN 55 attempt log
