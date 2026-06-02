FROM gradle:9-jdk25 AS build
WORKDIR /app
COPY build.gradle settings.gradle gradlew ./
COPY gradle/ gradle/
RUN ./gradlew dependencies --no-daemon -q
COPY matrix-core/ matrix-core/
RUN ./gradlew :matrix-core:quarkusBuild -Dquarkus.package.jar.type=uber-jar --no-daemon -q

FROM eclipse-temurin:25-jre-noble
WORKDIR /app
RUN groupadd -r matrix && useradd -r -g matrix matrix && \
    mkdir -p /data/snapshots && chown -R matrix:matrix /app /data
COPY --from=build /app/matrix-core/build/matrix-core-*-runner.jar /app/matrix-core.jar
USER matrix
EXPOSE 9091
HEALTHCHECK --interval=15s --timeout=5s --retries=3 \
    CMD curl -f http://localhost:9091/q/health/live || exit 1
ENTRYPOINT ["java", "-XX:+UseZGC", "-Xms256m", "-Xmx512m", "-jar", "/app/matrix-core.jar"]
