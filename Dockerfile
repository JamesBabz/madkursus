FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /workspace

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY src ./src

RUN chmod +x gradlew \
    && ./gradlew --no-daemon clean bootJar \
    && cp "$(find build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' | head -n 1)" /workspace/application.jar

FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S madkursus && adduser -S madkursus -G madkursus

WORKDIR /app
COPY --from=builder --chown=madkursus:madkursus /workspace/application.jar /app/application.jar

USER madkursus
EXPOSE 8080

ENV SERVER_PORT=8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/application.jar"]
