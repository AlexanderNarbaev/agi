# MATRIX Core — Docker build
# Pre-build: ./gradlew :matrix-core:quarkusBuild -Dquarkus.package.jar.type=uber-jar
# Then:      docker build -t matrix-core:2.0.0 .

FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app
COPY matrix-core/build/matrix-core-*-runner.jar /app/matrix-core.jar

# Pretrained weights (Qwen2.5-0.5B + SmolLM2-135M)
COPY models/pretrained/ /app/models/pretrained/

EXPOSE 9091
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD java -jar /app/matrix-core.jar --help || exit 1

ENTRYPOINT ["java", "-Xmx512m", "-jar", "/app/matrix-core.jar"]
CMD []
