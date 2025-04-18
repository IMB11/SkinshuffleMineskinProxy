# Use Eclipse Temurin official image with Java 21
FROM eclipse-temurin:21-jdk


# Set working directory inside container
WORKDIR /app

# Copy gradle files first for better layer caching
COPY gradlew .
COPY gradle/ gradle/
COPY build.gradle.kts .
COPY settings.gradle.kts .

# Make gradlew executable
RUN chmod +x ./gradlew

# Run a gradle task to cache dependencies
RUN ./gradlew --no-daemon dependencies

# Copy the rest of the source code
COPY src/ src/

# Set up volume for Gradle cache to persist between container runs
VOLUME ["/root/.gradle", "/app/build"]

# Setup environment variables with defaults (must provide TOKEN_MINESKIN at runtime)
ENV APP_PORT="28433" \
    APP_USERAGENT="SkinShuffle/Proxy"

# Expose the default port (can be overridden by APP_PORT environment variable)
EXPOSE ${APP_PORT}

# Command to run when container starts
CMD ./gradlew --no-daemon run