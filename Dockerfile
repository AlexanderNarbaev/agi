# MATRIX Core — Docker build (JVM, Quarkus fast-jar)
#
# Pre-build (outside the image):
#   ./gradlew :matrix-core:quarkusBuild --no-daemon
# Then:
#   docker build -t matrix-core:2.0.0 .
#
# Packaging: Quarkus' default is "fast-jar". That produces a directory
# layout, NOT a single jar:
#
#   matrix-core/build/quarkus-app/quarkus-run.jar   <- small launcher
#   matrix-core/build/quarkus-app/app/              <- application classes
#   matrix-core/build/quarkus-app/lib/              <- dependency jars
#   matrix-core/build/quarkus-app/quarkus/          <- generated config
#
# The whole quarkus-app/ directory is required; copying a single jar does
# not produce a runnable application. (The previous revision copied
# matrix-core/build/matrix-core-*-runner.jar, which no supported packaging
# mode of the current build produces — the image could never build.)
#
# Models are deliberately NOT baked into the image: /models is git-ignored
# and the checkpoints are large. Mount them at runtime, or point the
# application at a model directory via configuration. See docker-compose.yml
# and Dockerfile.minikube.

FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app

COPY matrix-core/build/quarkus-app/ /app/quarkus-app/

EXPOSE 9091

# eclipse-temurin:25-jre ships neither curl nor wget, so the health check
# uses bash's /dev/tcp. It checks the real readiness endpoint rather than
# whether the process merely exists.
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/9091 && printf "GET /q/health/ready HTTP/1.0\r\nHost: localhost\r\n\r\n" >&3 && grep -q "200 OK" <&3' || exit 1

ENTRYPOINT ["java", "-Xmx3g", "-jar", "/app/quarkus-app/quarkus-run.jar"]
CMD []
