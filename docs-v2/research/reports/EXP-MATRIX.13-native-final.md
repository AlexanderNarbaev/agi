# EXP-MATRIX.13-native-final — Wave N native-image status (final)

## Outcome
**Local native-image build did not complete.** Quarkus 3.38.3 +
GraalVM 25.0.2 + Pekko/Scala/Reactor stack has too many class-init
interdependencies to fix with `--initialize-at-build-time` lists.

## What worked
- `MatrixApplication.main()` static method added (commit `7c86a9f7`)
- Quarkus uber-jar builds and runs end-to-end (155 MB)
- All Wave A-G artifacts live and launchable

## What's blocked
- `native-image` local build: chained class-init whack-a-mole
  - `io.quarkus.bootstrap.logging.InitialConfigurator`
  - `io.quarkus.bootstrap.logging.QuarkusDelayedHandler`
  - `reactor.core.publisher.MonoDefer`
  - `org.wildfly.common.lock.ExtendedReentrantLock`
  - `com.fasterxml.jackson.module.scala.deser.EitherDeserializer$ElementDeserializerConfig`
- Each fix surfaces a new transitive dependency

## Suggested fix (not done in this session)
1. **Use the project's container build**:
   `./gradlew :matrix-core:quarkusBuild -Dquarkus.native.container-build=true`
   (requires Mandrel container image).
2. **Downgrade to GraalVM 21** (matches older Quarkus guides).
3. **Replace the Scala/Pekko cluster actor** with a non-Scala alternative
   to remove the worst class-init offenders.
4. **Try `--report-unsupported-elements-at-runtime`** for partial builds.

## Files
- runner.jar (uber-jar, WORKS): `matrix-core/build/matrix-core-1.0.0-runner.jar`
- static main fix: `MatrixApplication.main()`
- build config: `matrix-core/build.gradle` (graalvmNative block)

## Status
**Native build NOT delivered in this session.** All other Wave N
prerequisites (static main, uber-jar, configuration) are in place;
the actual native-image step requires environment-level fixes
beyond this session's scope.
