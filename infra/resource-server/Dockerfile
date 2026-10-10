# syntax=docker/dockerfile:1.6
#
# resource-server Dockerfile — multi-stage, with Maven cache mount.
#
# Why multi-stage: the final image carries only the runtime JRE + the fat jar,
# not Maven + the entire source tree.
#
# Why BuildKit cache mount (`--mount=type=cache`): Maven downloads can take
# minutes on a cold run; this persists ~/.m2 between builds so rebuilds reuse
# resolved dependencies. Requires BuildKit (default on Docker Desktop ≥ 23).
#
# Build:   docker build -t resource-server:local -f infra/resource-server/Dockerfile .
#          (run from repo root — the build context is the whole reactor)
# Run:     docker run --rm -p 8096:8096 \
#              -e SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI=http://host.docker.internal:8095 \
#              resource-server:local
#
# ---- Build stage ------------------------------------------------------------
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /workspace

# Copy only the pom files first — this layer is cached unless a pom changes.
# Dramatic speedup when iterating on source only.
COPY pom.xml ./
COPY lib/common-lib/pom.xml              lib/common-lib/
COPY infra/resource-server/pom.xml       infra/resource-server/

# Resolve dependencies in a cacheable step. Without this, the subsequent
# `mvn package` would hit Maven Central every build.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -pl infra/resource-server -am -DskipTests dependency:go-offline || true

# Now copy only what resource-server actually needs to compile.
COPY lib/common-lib/src                  lib/common-lib/src
COPY infra/resource-server/src           infra/resource-server/src

RUN --mount=type=cache,target=/root/.m2 \
    mvn -pl infra/resource-server -am -DskipTests clean package

# ---- Runtime stage ----------------------------------------------------------
FROM eclipse-temurin:17-jre
WORKDIR /app

# Non-root user: smaller blast radius if the JVM ever gets exploited.
RUN groupadd --system app && useradd --system --gid app --home /app app
USER app

# JVM-friendly defaults for containerised runs.
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75 -Djava.security.egd=file:/dev/./urandom"

COPY --from=build /workspace/infra/resource-server/target/*.jar app.jar

EXPOSE 8096

# Spring Boot Actuator health probe — Docker + K8s both honour this.
HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=3 \
    CMD wget --quiet --spider http://localhost:8096/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
