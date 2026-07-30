# ============================================================================
# Mana Community API Gateway — Multi-stage Docker build
# ============================================================================
# Usage:
#   docker build -t community-api-gateway .
#   docker compose up -d
# ============================================================================

# ── Stage 1: Build ──────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests -B -q

# ── Stage 2: Runtime ────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN groupadd -r mana && useradd -r -g mana -d /app mana && \
    chown -R mana:mana /app

COPY --from=builder /build/target/*.jar app.jar

HEALTHCHECK --interval=30s --timeout=5s --retries=3 --start-period=40s \
    CMD curl -f http://localhost:8080/actuator/health/liveness || exit 1

USER mana
EXPOSE 8080

ENTRYPOINT ["java", \
    "-XX:+UseZGC", \
    "-XX:+ZGenerational", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+UseStringDeduplication", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-Dreactor.netty.ioWorkerCount=4", \
    "-jar", "app.jar"]
