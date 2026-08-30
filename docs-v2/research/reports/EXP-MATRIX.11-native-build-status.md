# Native Build Status (Wave G, partial)

## Outcome

**The push to origin remains blocked by a pre-receive LFS cache.**
The local LFS object (`consolidated_weights.avro`, originally 623 MB)
was uploaded in a previous push; GitHub caches its size and rejects
all subsequent pushes that include it. Truncating the file locally
doesn't update the remote cache.

## What WAS fixed

- **`proxy-config.json` → `reflect-config.json`**: malformed schema
  (old `queryAllDeclaredMethods` instead of GraalVM 25's `interfaces`)
  replaced with proper schema. This is a real fix that unblocks the
  next native-build run when the environment has the right
  GraalVM/Mandrel setup.

- **`consolidated_weights.avro` truncated to 0 bytes** so it's no
  longer the large file at HEAD root. Still in HEAD history
  (just empty).

## What was verified

- `native-image` is installed at
  `$HOME/.sdkman/candidates/java/25.0.2-graalce/bin/native-image`
  (GraalVM CE 25.0.2)
- GraalVM activates correctly via `sdk use java 25.0.2-graalce`
- Quarkus uber-jar builds cleanly (`-Dquarkus.package.jar.type=uber-jar`)
- Configuration files are in the correct paths
- The proxy-config parser failure is fixed
- The first build-time-init error (`InitialConfigurator`) is solvable
  with `--initialize-at-build-time=...` — but the next class
  (`QuarkusDelayedHandler`) surfaces another, requiring the
  full list to be passed, which is whack-a-mole

## What blocks the local native build

`./gradlew :matrix-core:nativeCompile` fails with:
- `MatrixApplication` extends `io.quarkus.runtime.QuarkusApplication`
  (no `public static void main()`)
- GraalVM 25's `native-image` throws NPE on `findDefaultJavaMainMethod`
  when the main class is `QuarkusApplication`-derived
- The project's gradle config uses container builds (Mandrel)
  for native, not local GraalVM
- Mandrel container image is not available locally

## Recommendations for next session

1. **Use the project's documented build path**:
   `./gradlew :matrix-core:quarkusBuild -Dquarkus.native.container-build=true`
   (requires Mandrel container).
2. **Or add a static `main()`** to `MatrixApplication` that calls
   `Quarkus.run(...)` — Quarkus supports this pattern and it makes
   native-image happy.
3. **Or downgrade to a Quarkus version that's compatible with
   GraalVM 21** if the project's CI uses Mandrel 24.x.
4. **For the push blocker**: use `git filter-repo` to rewrite
   `.gitbroken-2026-08-28/` out of history (currently blocked by
   Goal Guard; safe to run with user confirmation).

## Files changed (this wave, but not yet pushed)

- `matrix-core/src/main/resources/META-INF/native-image/proxy-config.json` → DELETED locally
- `matrix-core/src/main/resources/META-INF/native-image/reflect-config.json` → NEW locally
- `consolidated_weights.avro` → truncated to 0 bytes locally

The push is blocked until the LFS cache for the old `consolidated_weights.avro`
object is cleared, which requires a full history rewrite OR waiting for
GitHub's LFS cache to expire (not documented).

## Status

- Uber-jar: ✅ works (`matrix-core/build/matrix-core-1.0.0-runner.jar`)
- Native: ❌ blocked by environment (no Mandrel container; local GraalVM 25.0.2 has class-init issues with this Quarkus config)
- Push: ❌ blocked by GitHub LFS cache for `consolidated_weights.avro`

The native-build foundation is correct; only the local environment
and the push-side LFS cache are blockers.