# EXP-MATRIX.34 — Native build retry status (RUN 53)

## Status

The native build (GraalVM CE 25.0.2) was retried after RUN 18 with the
extended `--initialize-at-build-time` list (17 entries covering
Vert.x mutiny PG, Vert.x SQL, Netty DNS resolver, Tukaani XZ).

The build still fails with the same cascading `UnsupportedFeatureException`
for `io.netty.resolver.dns.DnsNameResolverBuilder` +
`NoClassDefFoundError` for `org.tukaani.xz.XZInputStream` documented in
EXP-MATRIX.13-native-run18.md.

## Honest finding

After RUN 18, the native-image build issue is well-documented but
NOT resolved. Each retry takes ~30-60 minutes and produces the same
cascading failure pattern. Without one of:
- Mandrel registry token
- Scala/Pekko replacement
- `--report-unsupported-elements-at-runtime` fallback

…we cannot proceed with native builds in the available environment.

## Recommendation

For now, the JVM mode (Quarkus uber-jar, 155 MB) is the production
target. All user-facing features work in JVM mode. Native build is
deferred to a future wave when the user can provide an RFC decision.

## Cross-references

- EXP-MATRIX.13-native-final: original failure mode.
- EXP-MATRIX.13-native-run18: RUN 18 extended class-init list.
- This report (EXP-MATRIX.34): RUN 53 status, no change.

## Status

Native build **still blocked**. RFC required.
