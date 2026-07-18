# syntax=docker/dockerfile:1

# --- Build stage: compile and package the app with Maven -----------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Resolve dependencies first so this layer is cached unless pom.xml changes.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# Build the jar. Tests run in CI, not in the image build.
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# --- Runtime stage: slim JRE image running as a non-root user ------------------
FROM eclipse-temurin:21-jre AS runtime

# curl is used by the container HEALTHCHECK to hit the actuator endpoint.
RUN groupadd --system spring \
    && useradd --system --gid spring --home /app spring \
    && apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
USER spring

EXPOSE 8080

# /actuator/health is public (permitAll), so no token is needed here.
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -fsS http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
