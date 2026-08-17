# syntax=docker/dockerfile:1

# ---- Build stage ----
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /workspace

# Cache the Gradle wrapper and dependency metadata first for better layer caching
COPY gradlew settings.gradle build.gradle lombok.config ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

# Copy sources and build the executable (Spring Boot) jar
COPY src src
RUN ./gradlew --no-daemon clean bootJar -x test

# ---- Runtime stage ----
FROM eclipse-temurin:17-jre-jammy AS runtime
WORKDIR /app

# Run as a non-root user
RUN groupadd --system app && useradd --system --gid app --home /app app

# Copy only the Spring Boot fat jar (exclude the "-plain" library jar)
COPY --from=build /workspace/build/libs/*-SNAPSHOT.jar /app/app.jar

USER app

EXPOSE 8090

# Override Mongo connection / profile at runtime, e.g.:
#   -e SPRING_DATA_MONGODB_URI=mongodb://mongo:27017/?retryWrites=true
#   -e SPRING_PROFILES_ACTIVE=prod
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
