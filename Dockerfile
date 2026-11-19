# syntax=docker/dockerfile:1

# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Cache dependencies separately from source for faster rebuilds.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
ARG SKIP_TESTS=false
RUN mvn -B package -DskipTests=${SKIP_TESTS} \
    && java -Djarmode=tools -jar target/demo-app.jar extract --layers --launcher --destination target/extracted

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-alpine
ARG APP_VERSION=1.1.2
LABEL org.opencontainers.image.title="demo-app" \
      org.opencontainers.image.version="${APP_VERSION}"

RUN addgroup -S -g 10001 app && adduser -S -u 10001 -G app app
WORKDIR /app

COPY --from=build --chown=app:app /workspace/target/extracted/dependencies/ ./
COPY --from=build --chown=app:app /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=build --chown=app:app /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /workspace/target/extracted/application/ ./

USER 10001
EXPOSE 8080
ENV APP_VERSION=${APP_VERSION} \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
