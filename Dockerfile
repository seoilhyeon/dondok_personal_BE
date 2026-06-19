# syntax=docker/dockerfile:1

# Build stage: create the Spring Boot executable JAR inside a fixed JDK image.
FROM eclipse-temurin:17.0.11_9-jdk-jammy AS build
WORKDIR /workspace

# Copy Gradle metadata first so dependency layers can be reused by BuildKit cache.
COPY gradlew .
COPY gradle ./gradle
COPY build.gradle settings.gradle ./

# The Gradle wrapper needs execute permission inside the Linux build container.
RUN chmod +x ./gradlew

# Warm up Gradle dependencies with a cache mount.
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew dependencies --no-daemon

# Copy source after dependency resolution to avoid invalidating cache too often.
COPY src ./src

# CI already owns tests. The image build only creates the runtime artifact.
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew bootJar -x test --no-daemon \
    && JAR_FILE="$(find build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*plain.jar' | head -n 1)" \
    && test -n "${JAR_FILE}" \
    && cp "${JAR_FILE}" /workspace/app.jar

# Runtime stage: keep only the JRE and the executable JAR.
FROM eclipse-temurin:17.0.11_9-jre-jammy
WORKDIR /app

# Run the application as a non-root user.
RUN addgroup --system --gid 10001 appgroup \
 && adduser --system --uid 10001 --ingroup appgroup appuser \
 && mkdir -p /app/logs \
 && chown -R appuser:appgroup /app/logs

# Copy only the bootable application JAR from the build stage.
COPY --from=build /workspace/app.jar app.jar

USER appuser

# Spring Boot container port.
EXPOSE 8080

# Let the JVM respect container memory limits.
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "/app/app.jar"]
