# MATRIX Core — Multi-stage Docker build
# Stage 1: Build native binary (Mandrel container)
# Stage 2: Distroless runtime

FROM quay.io/quarkus/ubi-quarkus-mandrel-builder:23.1.6.0-Final-java21 AS builder

WORKDIR /build
COPY --chown=quarkus:quarkus gradlew gradle.properties settings.gradle ./
COPY --chown=quarkus:quarkus gradle/ gradle/
RUN ./gradlew --version

COPY --chown=quarkus:quarkus matrix-core/ matrix-core/
RUN ./gradlew :matrix-core:quarkusBuild -Dquarkus.native.enabled=true -Dquarkus.package.type=native --no-daemon -x test

FROM gcr.io/distroless/cc-debian12:nonroot AS runtime

COPY --from=builder /build/matrix-core/build/*-runner /app/matrix
COPY --from=builder /build/matrix-core/src/main/resources/application.properties /app/config/application.properties

EXPOSE 9091

ENTRYPOINT ["/app/matrix"]
CMD ["--help"]
