# EXP-MATRIX.13-NATIVE-RUN18 — Native image build status (RUN 18 update)

## Status

**Local native-image build did NOT complete.** The reactive-PG /
Scala / Reactor stack has too many class-init interdependencies to
fix with `--initialize-at-build-time` lists.

## What was tried in RUN 18

The RUN 18 plan called for building the native image via the local
GraalVM CE 25.0.2 toolchain (already installed at
`~/.sdkman/candidates/java/25.0.2-graalce/`). We extended the
class-init list in `application.properties`:

```
--initialize-at-build-time=io.vertx.mutiny.pgclient.PgPool_d1X4BXWWOXCUVicCEg66CwLl6pA_Synthetic_Bean
--initialize-at-build-time=io.vertx.mutiny.pgclient.PgPool_Observer_Synthetic_53kOBafTmDZHb2yMOYL2juXI4SI
--initialize-at-build-time=io.vertx.mutiny.sqlclient
--initialize-at-build-time=io.vertx.pgclient
--initialize-at-build-time=io.vertx.sqlclient
--initialize-at-build-time=io.quarkus.bootstrap.logging.InitialConfigurator
--initialize-at-build-time=io.quarkus.bootstrap.logging.QuarkusDelayedHandler
--initialize-at-build-time=reactor.core.publisher.MonoDefer
--initialize-at-build-time=org.wildfly.common.lock.ExtendedReentrantLock
--initialize-at-build-time=com.fasterxml.jackson.module.scala.deser.EitherDeserializer
--initialize-at-build-time=com.fasterxml.jackson.module.scala.deser.EitherDeserializer$ElementDeserializerConfig
--initialize-at-build-time=org.tukaani.xz
--initialize-at-build-time=org.tukaani.xz.XZInputStream (run-time, override)
--initialize-at-build-time=io.netty.resolver.dns
--initialize-at-build-time=io.netty.resolver
--initialize-at-build-time=io.netty.handler.codec.dns
```

Plus `quarkus.native.exclude-config-jar-with-unused-classes=true` to
strip un-used classes.

## What's still blocked

After fixing the initial wave, a second class of failures emerged:

1. `io.netty.resolver.dns.DnsNameResolverBuilder` — DNS resolver
   brought in by the reactive HTTP client chain. Marking it
   `--initialize-at-build-time` triggers a sub-chain of
   `NoClassDefFoundError` for `org.tukaani.xz.XZInputStream`.

2. The fix for XZ throws a new class-init error for
   `DnsNameResolverBuilder` itself, completing the cycle.

3. Each fix surfaces a new transitive dependency in the
   reactive PG / DNS resolver graph — exactly the
   "whack-a-mole" pattern documented in
   [EXP-MATRIX.13-native-final](EXP-MATRIX.13-native-final.md).

## User RFC required

The PLAN.md lists three options for the user:

1. **Mandrel container build** — pull
   `quay.io/quarkus/ubi-quarkus-mandrel-builder:25.0.1-java25` from
   the Quarkus registry. Requires user setup of a registry token;
   current host returns `401 UNAUTHORIZED` for anonymous pulls.
2. **Replace Scala/Pekko** — remove the cluster actor package in
   favor of a non-Scala alternative. Substantial code refactor;
   needs RFC approval per AGENTS.md.
3. **Try `--report-unsupported-elements-at-runtime`** for partial
   builds. May produce a working binary with reduced feature set;
   needs RFC.

## What's NOT blocked

The Quarkus uber-jar builds and runs end-to-end (155 MB). All
user-facing features (chat, training, chain reload, lm-head) work
in JVM mode. Native image is **deployment optimization**, not a
correctness requirement.

## Test status

No new tests added in RUN 18 (native build is a deploy artifact,
not a tested code path). All 66 existing tests still pass.

## Cross-references

- [EXP-MATRIX.13-native-final](EXP-MATRIX.13-native-final.md) —
  original EXP-13 status (2026-08-30)
- `matrix-core/src/main/resources/application.properties` —
  RUN 18 class-init extensions
- `docs-v2/engineering/PLAN.md` — RUN 18 task definition
