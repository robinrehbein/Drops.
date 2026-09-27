# syntax=docker/dockerfile:1
# Build context: repository root (the server shares :core with the apps).

# The JVM build output is platform independent, so it always runs natively;
# only the small runtime stage is built per target (amd64 and arm64).
FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY core ./core
COPY server ./server
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon -Pdrops.serverOnly=true :server:installDist

FROM eclipse-temurin:21-jre
# curl for container health checks (Coolify runs them inside the container).
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
RUN groupadd --system drops && useradd --system --gid drops --home /app drops \
    && mkdir -p /data && chown drops:drops /data
COPY --from=build /src/server/build/install/server /app
USER drops
ENV PORT=8080 \
    DATABASE_PATH=/data/drops.db \
    JAVA_OPTS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
VOLUME ["/data"]
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD curl -fsS http://localhost:8080/healthz || exit 1
ENTRYPOINT ["/app/bin/server"]
