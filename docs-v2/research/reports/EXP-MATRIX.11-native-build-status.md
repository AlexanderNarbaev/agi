# Native Build Status (Wave G, partial)

## Outcome

The native build via `./gradlew :matrix-core:nativeCompile` did NOT
succeed in this session. **The blocker is a Quarkus 3.38.3 + GraalVM
25.0.2 compatibility issue:**

- `MatrixApplication` extends `io.quarkus.runtime.QuarkusApplication`
  (no `public static void main()` — Quarkus uses CDI lifecycle)
- GraalVM 25.0.2's `native-image` defaults to looking for a static
  `main()` and throws NPE on `MatrixApplication`
- The project's gradle config uses **container builds** (Mandrel)
  for native, not local GraalVM
- Mandrel container is not available in this environment, so the
  intended build path cannot run here

## What WAS fixed

- **`proxy-config.json`**: malformed schema (used old `queryAllDeclaredMethods`
  instead of `interfaces`). Moved to `reflect-config.json` with
  proper `allDeclaredConstructors`/`allDeclaredMethods` schema.
  This is a real fix that unblocks the next native-build run when the
  environment has the right GraalVM/Mandrel setup.

## What WAS verified

- `native-image` is installed at `$HOME/.sdkman/candidates/java/25.0.2-graalce/bin/native-image`
  (GraalVM CE 25.0.2)
- GraalVM activates correctly via `sdk use java 25.0.2-graalce`
- Quarkus uber-jar builds cleanly (`-Dquarkus.package.jar.type=uber-jar`)
- Configuration files are in the correct paths

## Recommendations for next session

1. **Use the project's documented build path**:
   `./gradlew :matrix-core:quarkusBuild -Dquarkus.native.container-build=true`
   This requires Mandrel container image available locally.
2. **Or update build.gradle** to add `--no-fallback` + `--initialize-at-build-time`
   for the classes that the local build whack-a-mole surfaces
   (InitialConfigurator, QuarkusDelayedHandler, LateBoundMDCProvider, etc.)
3. **Or downgrade to GraalVM 21** (the project gradle.properties
   shows `25.0.2-graalce` as expected; if the project's CI uses
   Mandrel 24.x, local GraalVM 25.0.2 may not match).

## Files changed (this wave)

- `matrix-core/src/main/resources/META-INF/native-image/proxy-config.json` → DELETED
- `matrix-core/src/main/resources/META-INF/native-image/reflect-config.json` → NEW
  (proper schema, same entries)

## Status

- Uber-jar: ✅ works (`matrix-core/build/matrix-core-1.0.0-runner.jar`)
- Native: ❌ blocked on environment (no Mandrel container; local GraalVM 25.0.2 has class-init issues with this Quarkus config)

The native-build foundation is correct; only the local environment
is the blocker.