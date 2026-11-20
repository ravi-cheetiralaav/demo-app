# Runtime image. The jar is built beforehand by Maven (mvn package), not inside this build.

# Prefix for the base image, ending in "/" (for example artifactory.example.com/docker-virtual/).
# Leave empty to pull from Docker Hub.
ARG BASE_REGISTRY=

FROM ${BASE_REGISTRY}eclipse-temurin:17-jre-alpine

ARG APP_VERSION=unknown
LABEL org.opencontainers.image.title="demo-app" \
      org.opencontainers.image.version="${APP_VERSION}"

# Non-root user. The fixed UID matches runAsUser in k8s/deployment.yaml.
RUN addgroup -S -g 10001 spring && adduser -S -u 10001 -G spring spring
USER 10001:10001

# Ensure artifacts are copied under the spring user's home path.
WORKDIR /home/spring

# Copy the fat JAR produced by "mvn package".
ARG JAR_FILE=target/*.jar
COPY --chown=spring:spring ${JAR_FILE} app.jar

EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["java", "-jar", "/home/spring/app.jar"]
