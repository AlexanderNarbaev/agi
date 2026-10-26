# Container Builds — supported variants and requirements

**Status:** Active — enforced by the `container-images` CI job
**Applies to:** `Dockerfile`, `Dockerfile.minikube`, `deploy/docker/Dockerfile.api-gateway`
**Introduced:** RECON-W38.4, 2026-10-26

## Why this document exists

Three container definitions existed and **none of them could build**:

| File | Previous `COPY` / task | Outcome |
|---|---|---|
| `Dockerfile` | `matrix-core/build/matrix-core-*-runner.jar` | no such artifact is produced by any supported packaging mode |
| `Dockerfile.minikube` | `matrix-core/build/matrix-core-1.0.0-runner.jar` | same |
| `deploy/docker/Dockerfile.api-gateway` | `:matrix-api-gateway:nativeCompile` | task does not exist — the module applies no GraalVM plugin |

They were also **not covered by CI**: the `docker` job built only
`deploy/docker/Dockerfile.api-gateway` and `Dockerfile.web-ui`, and the
gateway build failure was masked by `continue-on-error: true`. The root
Dockerfiles had no CI job at all.

## Supported variants

### 1. `matrix-core` — JVM (Quarkus fast-jar)

```bash
./gradlew :matrix-core:quarkusBuild --no-daemon
docker build -t matrix-core:2.0.0 .
docker run -d -p 9091:9091 matrix-core:2.0.0
```

**Packaging.** Quarkus' default `fast-jar` produces a *directory*, not a jar:

```
matrix-core/build/quarkus-app/quarkus-run.jar   launcher
matrix-core/build/quarkus-app/app/              application classes
matrix-core/build/quarkus-app/lib/              dependency jars
matrix-core/build/quarkus-app/quarkus/          generated config
```

The whole `quarkus-app/` directory is copied into the image. Copying a single
jar does not produce a runnable application.

**Runtime requirements.** `matrix-core` has hard startup dependencies and
**will not start without them**:

| Dependency | Default | Override |
|---|---|---|
| Redis | `redis://localhost:6379` | `REDIS_URI` |
| PostgreSQL | `postgresql://localhost:5433/matrix` | `DB_HOST`, `DB_PORT`, `DB_USER`, `DB_PASS` |

Kafka is optional at startup (the client logs a reconnect warning and
continues).

**Health.** `GET /q/health/ready` returns `200` with the database checks.
The image's `HEALTHCHECK` uses `bash`'s `/dev/tcp` — `eclipse-temurin:25-jre`
ships neither `curl` nor `wget`.

**Models.** Not baked into the image. `/models` is git-ignored and the
checkpoints are large; mount them at runtime instead.

### 2. `matrix-core` — Minikube / development (JVM, fast-jar)

```bash
./gradlew :matrix-core:quarkusBuild --no-daemon
docker build -f Dockerfile.minikube -t matrix-core:minikube .
```

Same packaging as variant 1, with a lower memory ceiling and ZGC. Models are
mounted from the host via a `hostPath` volume (see `infra/k8s/minikube/`).

### 3. `matrix-api-gateway` — JVM distribution

```bash
docker build -f deploy/docker/Dockerfile.api-gateway -t matrix-api-gateway:2.0.0 .
docker run -d -p 8765:8765 matrix-api-gateway:2.0.0
```

**Packaging.** The module now applies the Gradle `application` plugin, so
`./gradlew :matrix-api-gateway:installDist` produces a self-contained
distribution:

```
matrix-api-gateway/build/install/matrix-api-gateway/bin/matrix-api-gateway
matrix-api-gateway/build/install/matrix-api-gateway/lib/*.jar
```

The entry point is `io.matrix.api.MinimalHttpServer` (JDK `HttpServer`).
The module applies **no** Quarkus plugin on purpose — its JAX-RS resources are
plain classes and the routes are served by that server — so a JVM
distribution matches the application exactly. A native image would require
reflection configuration that no build currently provides; the previous
attempt to build one invoked a task that did not exist.

**Health.** `GET /health/ready` returns
`{"status":"UP","checks":{...}}`.

**Note.** With no `MATRIX_MODE` set the gateway starts in `stub` mode
(`StubBrainCycle`) and reports `"core":"STUB"`. Setting `MATRIX_MODE` to the
real brain is a separate configuration decision.

## CI enforcement

The `container-images` job in `.github/workflows/ci.yml` is **required** and:

1. builds `matrix-core`, `matrix-core` (minikube) and `matrix-api-gateway`
   from a fresh checkout;
2. starts each image;
3. asserts the readiness endpoint answers `200`;
4. fails the workflow otherwise.

The `docker` (publish) job now depends on `container-images`, so an image is
never pushed unless it was verified.

## Verification performed

Locally on 2026-10-26, with Docker 29.9.2 and JDK 25.0.4.1:

| Image | Build | Startup | Health |
|---|---|---|---|
| `matrix-core:recon-w38` | OK | OK | Docker health = `healthy`; `GET /q/health/ready` → 200 |
| `matrix-core:minikube-recon` | OK | OK | Docker health = `healthy`; `GET /q/health/ready` → 200 |
| `matrix-api-gateway:recon-w38` | OK | OK | Docker health = `healthy`; `GET /health/ready` → 200 |

## Known limitations

- The container images were built and smoke-tested on one host. The CI job
  reproduces the same steps on every push.
- `matrix-core` needs Redis and PostgreSQL; a standalone `docker run` of the
  image alone will fail at startup. Use `docker-compose.yml` or the CI
  job's service wiring.
- `Dockerfile.native` (Mandrel) is unchanged and is not covered by the
  `container-images` job: it needs a GraalVM/Mandrel toolchain and a
  multi-minute build. `native.yml` covers it separately.
- `deploy/docker/Dockerfile.web-ui` is unchanged; it depends on npm
  packages and is not part of this gate.
