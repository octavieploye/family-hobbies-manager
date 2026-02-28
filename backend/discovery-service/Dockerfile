# ============================================================
# Stage 1: Build
# ============================================================
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /workspace

# -- Copy parent POM and module POMs for dependency caching --
COPY pom.xml ./pom.xml
COPY error-handling/pom.xml ./error-handling/pom.xml
COPY common/pom.xml ./common/pom.xml
COPY discovery-service/pom.xml ./discovery-service/pom.xml

# -- Download dependencies (cached unless POMs change) --
RUN mvn dependency:go-offline -pl discovery-service -am -B -q

# -- Copy source code --
COPY error-handling/src ./error-handling/src
COPY common/src ./common/src
COPY discovery-service/src ./discovery-service/src

# -- Build the JAR (skip tests -- tests run in CI, not in Docker build) --
RUN mvn package -pl discovery-service -am -DskipTests -B -q

# ============================================================
# Stage 2: Runtime
# ============================================================
FROM eclipse-temurin:17-jre-alpine AS runtime

# -- Security: create non-root user --
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# -- Copy JAR from build stage --
COPY --from=build /workspace/discovery-service/target/*.jar app.jar

# -- Set ownership to non-root user --
RUN chown -R appuser:appgroup /app

# -- Switch to non-root user --
USER appuser

# -- Expose service port --
EXPOSE 8761

# -- Health check: probe Actuator health endpoint --
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8761/actuator/health || exit 1

# -- JVM tuning for containers --
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
