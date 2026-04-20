# Build stage
FROM eclipse-temurin:25-jdk-jammy AS build
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends unzip \
    && rm -rf /var/lib/apt/lists/*

# Copy Gradle wrapper and build files first for better caching
COPY gradlew gradlew.bat ./
COPY gradle ./gradle
COPY build.gradle.kts settings.gradle.kts ./
RUN chmod +x gradlew

# Download dependencies (cached layer)
RUN ./gradlew dependencies --no-daemon || true

# Copy source code
COPY src ./src

# Build the application
RUN ./gradlew bootJar --no-daemon

# Runtime stage
FROM eclipse-temurin:25-jre-jammy

# Create non-root user for security
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system spring \
    && useradd --system --gid spring --create-home spring

WORKDIR /app

# Create runtime directories with proper permissions
RUN mkdir -p .runtime/logs && chown -R spring:spring .runtime

# Copy JAR from build stage
COPY --from=build /app/build/libs/algotrading-bot.jar app.jar

# Change ownership to non-root user
RUN chown spring:spring app.jar

# Switch to non-root user
USER spring:spring

# Expose application port
EXPOSE 8080
EXPOSE 5005

# Set JVM options for container runtime
ENV JAVA_OPTS="-Xms512m -Xmx2g -XX:+UseZGC -XX:MaxMetaspaceSize=512m -XX:+HeapDumpOnOutOfMemoryError"
ENV JAVA_DEBUG_OPTS=""

# Health check configuration
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl --fail --silent http://localhost:8080/actuator/health > /dev/null || exit 1

# Run the application
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS $JAVA_DEBUG_OPTS -jar app.jar"]
