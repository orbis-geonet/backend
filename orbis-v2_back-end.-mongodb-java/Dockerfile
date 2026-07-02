# Build
FROM gradle:7.5.1-jdk17 AS builder

WORKDIR /app

COPY gradlew gradlew
RUN chmod +x gradlew
COPY gradle/ gradle/
COPY build.gradle settings.gradle lombok.config ./

RUN ./gradlew dependencies --no-daemon || true

COPY src/ src/
RUN ./gradlew bootJar --no-daemon -x test


# Runtime
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

RUN addgroup --system spring && adduser --system --ingroup spring spring

COPY --from=builder /app/build/libs/orbisv2-backend-*.jar app.jar

USER spring

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
